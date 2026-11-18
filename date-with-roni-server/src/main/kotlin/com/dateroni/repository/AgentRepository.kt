package com.dateroni.repository

import com.dateroni.Env
import com.dateroni.orchestrator.OrchestratorContext
import com.dateroni.orchestrator.RoniOrchestrator
import com.dateroni.orchestrator.subagents.HistorySummarizer
import com.dateroni.orchestrator.subagents.Offer
import com.dateroni.orchestrator.subagents.OfferWriter
import com.dateroni.orchestrator.subagents.ProfileExtractor
import com.dateroni.orchestrator.subagents.MatchAnalyzer
import com.dateroni.orchestrator.subagents.AbuseWatcher
import com.dateroni.orchestrator.subagents.FarewellWriter
import com.dateroni.objects.HerProfile
import com.dateroni.objects.Speaker
import com.dateroni.orchestrator.llm.AnthropicLlmClient
import com.dateroni.orchestrator.agent.AgentHistory
import com.dateroni.orchestrator.llm.CountingLlmClient
import com.dateroni.orchestrator.llm.ModelTier
import com.dateroni.orchestrator.llm.LlmClient

import com.dateroni.state.AppStateManager
import com.dateroni.objects.Girl
import com.dateroni.objects.Image
import com.dateroni.objects.Outbox
import com.dateroni.objects.Turn
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

// ============================================================
//  הרפוזיטורי של הסוכן.
//
//  חסר מצב בכוונה. הוא מחזיק את לקוח ה-LLM — משאב יקר שנבנה
//  פעם אחת — ולא שום דבר ששייך לשיחה מסוימת.
//
//  לכל הודעה הוא בונה הקשר מצומצם ומנצח חדש, ומשחרר אותם
//  כשהיא נגמרת. זה מה שהופך זליגה בין שיחות ללא-אפשרית: אין
//  אובייקט ששורד בין שתיים.
// ============================================================

private val log = LoggerFactory.getLogger("AgentRepository")

/**
 * מתחת ליתרה הזו הסוכן מתחיל לרמוז לה שהחשמל אוזל.
 *
 * מוקדם מספיק כדי שיישארו כמה תורות אחרי האזהרה — אזהרה
 * שמגיעה עם ההודעה האחרונה היא לא אזהרה.
 */
private val LOW_POWER_AT = Env.get("LOW_POWER_AT")?.toIntOrNull() ?: 50_000

/**
 * כל כמה טוקנים מתמצתים את ההיסטוריה.
 *
 * ההיסטוריה נשלחת מחדש בכל קריאה של הסוכן, ולכן היא מוכפלת
 * בעומק: ארבעים תורות בעומק ארבעה-עשר הן ארבעים תורות שעלו
 * על הקו ארבעה-עשר פעמים בסבב אחד. זה מה שאוכל את התקציב,
 * לא התשובות עצמן. תמצות מחליף את הגוף הארוך בכמה שורות.
 */
private const val COMPACT_EVERY = 25_000

/** מאיזה אחוז התאמה מציעים דייט, ומאיזה וואטסאפ. */
private const val DATE_AT = 85
private const val WHATSAPP_AT = 60

/**
 * כמה זמן ממתינים לעוד הודעה לפני שמתחילים לחשוב.
 *
 * כל עוד היא כותבת, ההודעות נאספות לאותה מנה — כדי שהיא תקבל
 * תשובה אחת שמכסה את הכל, לא תשובה לכל הודעה. הסבב מתחיל רק
 * כשהיא נחה מעבר לחלון הזה. ה-"מקליד..." מוצג כל זמן ההמתנה,
 * אז מבחינתה הוא פשוט קורא את מה שכתבה.
 */
private val INTERRUPT_DEBOUNCE_MS = Env.get("INTERRUPT_DEBOUNCE_MS")?.toLongOrNull() ?: 1200L

class AgentRepository(
    private val db: DbRepository,
    private val state: AppStateManager,
    private val llmClient: LlmClient? = buildClient(),
) {

    /**
     * בדיקת דופק לספק, פעם אחת בעלייה.
     *
     * בלעדיה המצב "נגמרו הקרדיטים" מתגלה רק כשמישהי מנסה
     * לשלוח — כלומר הראשונה אחרי הנפילה מקבלת שגיאה במקום
     * שלט "בשיפוצים". כאן אנחנו משלמים בערך עשרה טוקנים פעם
     * אחת כדי שאף אחת לא תשלם בחוויה.
     *
     * למה קריאת הודעה אמיתית ולא GET /v1/models: רשימת
     * המודלים מאמתת שהמפתח תקין, אבל מחזירה 200 גם כשיתרת
     * הקרדיט אפס. רק קריאה שבאמת מייצרת טוקנים נתקלת בקיר.
     */
    suspend fun probeProvider() {
        val client = llmClient
        if (client == null) {
            state.markAgentOffline("אין ANTHROPIC_API_KEY")
            return
        }

        val probe = CountingLlmClient(client)
        try {
            probe.sendMessage(
                agentId = "startup-probe",
                systemPrompt = "ענה במילה אחת.",
                history = listOf(AgentHistory.UserInput("hi")),
                availableCapabilities = emptyList(),
                tier = ModelTier.FAST,
            )
            state.markAgentOnline()
            log.info("הספק עונה. הסוכן מחובר ({} טוקנים לבדיקה)", probe.tokensUsed)
        } catch (e: Exception) {
            if (probe.providerOutOfService) {
                state.markAgentOffline("בדיקת עלייה: ${e.message}")
            } else {
                // תקלה חולפת בעלייה היא לא סיבה להכריז שיפוצים.
                // אם באמת אין קרדיטים, השיחה הראשונה תגלה.
                log.warn("בדיקת העלייה נכשלה, אבל לא בגלל הספק: {}", e.message)
            }
        }
    }

    /**
     * מרוקן את תיבת הנכנס עד שהיא ריקה.
     *
     * לולאה ולא קריאה אחת: אם היא כתבה שוב בזמן שהסוכן עבד,
     * ההודעה החדשה כבר בתיבה — והסבב הבא יאסוף אותה ויסמן
     * למתכנן שהיא התפרצה. ככה אין שתי עבודות מקבילות שנלחמות
     * על אותה היסטוריה, ואין הודעה שנשארת בלי מענה.
     *
     * התשובה נשלחת דרך ctx.sendMessage — כלומר היא מגיעה אליה
     * בפינג, לא בערך חוזר.
     */
    /**
     * ההודעה הראשונה, מיד אחרי שהיא נכנסה.
     *
     * סבב אחד ויחיד, בלי תיבת נכנס: אין לה עדיין מה לומר. אם
     * זה נכשל, לא נשלחת הודעת שגיאה — היא תראה צ׳אט ריק ותכתוב
     * בעצמה, וזה עדיף על "משהו השתבש" כמשפט הפתיחה.
     */
    suspend fun open(girl: Girl, outbox: Outbox, herName: String? = null) {
        val client = llmClient ?: run {
            state.markAgentOffline("אין ANTHROPIC_API_KEY")
            return
        }

        val ctx = ScopedContext(girl, CountingLlmClient(client), outbox, db, herName)
        try {
            val result = RoniOrchestrator(ctx).openConversation()
            if (result.text.isNotBlank()) {
                outbox.add(result.text, ctx.collectedImages)
                outbox.markReplied()
            }
        } catch (e: Exception) {
            log.warn("פתיחת שיחה נכשלה עבור {}: {}", girl.email, e.message)
        } finally {
            ctx.tokensUsed().let { spent ->
                if (spent > 0) {
                    db.addTokens(girl.email, spent)
                    log.info("פתיחת שיחה עלתה {} טוקנים", spent)
                }
            }
        }
    }

    suspend fun run(
        girl: Girl,
        outbox: Outbox,
        tokensRemaining: Int = Int.MAX_VALUE,
        tokensSpent: Int = 0,
        herName: String? = null,
    ) {
        val client = llmClient
        if (client == null) {
            outbox.drainInbound()
            state.markAgentOffline("אין ANTHROPIC_API_KEY")
            outbox.add("הסוכן לא מחובר עדיין.", isError = true)
            return
        }

        var interrupted = false
        var remaining = tokensRemaining
        var spentTotal = tokensSpent
        while (true) {
            var batch = outbox.drainInbound()
            if (batch.isEmpty()) break

            // Debounce: כל עוד היא ממשיכה לכתוב, אוספים את ההמשך
            // לאותה מנה לפני שמתחילים לחשוב — כך היא מקבלת תשובה
            // אחת שמכסה את הכל, לא אחת לכל הודעה. הסבב מתחיל רק
            // כשהיא נחה. ה-pending נשאר true בזמן ההמתנה, אז
            // ה-"מקליד..." ממשיך להופיע אצלה.
            while (true) {
                delay(INTERRUPT_DEBOUNCE_MS)
                val more = outbox.drainInbound()
                if (more.isEmpty()) break
                batch = batch + more
            }

            // הקשר ומנצח טריים לכל סבב. אין אובייקט ששורד.
            val ctx = ScopedContext(girl, CountingLlmClient(client), outbox, db, herName)

            // לפני הסבב ולא אחריו: הסבב הזה הוא שנהנה מההיסטוריה
            // המקוצרת, ובאותו ctx — כך שהעלות של התמצות עצמו
            // נספרת יחד עם שאר הסבב ולא נעלמת מהחשבון.
            compactIfNeeded(girl, ctx, spentTotal)

            // פסילה על התעללות ממושכת. שומר הוויב קורא את השיחה
            // כולה — לא נפסלת על הודעה בודדת, רק על דפוס. אם כן:
            // פרידה, וחסימה לנצח (submit/poll כבר לא יפעילו סוכן).
            var disqualified = false
            if (AbuseWatcher(ctx).isAbuse(batch)) {
                batch.forEach { ctx.appendTurn(Turn(Speaker.HER, it)) }
                db.setMatchPercent(girl.email, -1)
                sendFarewell(ctx)
                disqualified = true
            } else {
                val result = RoniOrchestrator(ctx).handle(batch, interrupted, remaining <= LOW_POWER_AT)
                outbox.add(result.text, result.images + ctx.collectedImages)

                // מכאן ה"מקליד" נכבה. מה שרץ אחר כך — חילוץ פרופיל
                // וניתוח התאמה — הוא עבודה שלנו, לא המתנה שלה.
                outbox.markReplied()

                // חילוץ נתונים עליה — אוטומטי, בקוטלין ולא דרך המתכנן.
                // אם נכנס מידע חדש, מריצים ניתוח התאמה. דיל-ברייקר (-1)
                // מפעיל פרידה וחסימה גם הוא.
                if (ProfileExtractor(ctx).extract()) {
                    val cur = db.matchPercent(girl.email)
                    if (cur >= 0) {
                        MatchAnalyzer(ctx).analyze(cur)?.let { newPct ->
                            db.setMatchPercent(girl.email, newPct)
                            if (newPct < 0) {
                                sendFarewell(ctx); disqualified = true
                            } else {
                                maybeOffer(ctx, girl.email, newPct, outbox)
                            }
                        }
                    }
                }
            }

            // אחרי הסבב ולא לפניו: רק כאן ידוע כמה *באמת* נצרך,
            // כולל כל תת-סוכן שהמתכנן בחר להפעיל בדרך.
            ctx.tokensUsed().let { spent ->
                if (spent > 0) {
                    db.addTokens(girl.email, spent)
                    log.info(
                        "סבב עלה {} טוקנים ({}), נשארו {}",
                        spent,
                        if (remaining <= LOW_POWER_AT) "מצב חיסכון" else "רגיל",
                        (remaining - spent).coerceAtLeast(0),
                    )
                    // סבב נוסף באותו טיפול מתחיל מיתרה נמוכה יותר.
                    remaining -= spent
                    spentTotal += spent
                }
            }

            // אחרי כל סבב, לא רק בכישלון: תשובה אחת שהצליחה
            // מספיקה כדי להכריז שהספק חזר.
            if (ctx.providerOutOfService()) {
                state.markAgentOffline("אנטרופיק סירבה — קרדיטים או מפתח")
            } else {
                state.markAgentOnline()
            }

            // מכאן והלאה, כל סבב נוסף פירושו שהיא כתבה בזמן שעבדנו.
            interrupted = true

            // נפסלה — מפסיקים לטפל בה. מכאן submit/poll חוסמים אותה.
            if (disqualified) break
        }
    }

    /** פרידה אחרונה לפני חסימה: כותב, רושם בהיסטוריה, ושולח. */
    private suspend fun sendFarewell(ctx: OrchestratorContext) {
        val bye = FarewellWriter(ctx).write()
        ctx.appendTurn(Turn(Speaker.AGENT, bye))
        ctx.sendMessage(bye)
    }

    /**
     * מציע וואטסאפ או דייט כשההתאמה מצדיקה את זה.
     *
     * שתי החלטות נפרדות, ושתיהן כאן ולא אצל המודל:
     *
     *   **מתי** — סף על אחוז ההתאמה. דייט מ-85, וואטסאפ מ-60.
     *   **האם כבר** — ה-DB. הצעה נאמרת פעם אחת ולתמיד, גם אם
     *   השרת עלה מחדש והשיחה נמחקה מהזיכרון.
     *
     * מה שנשאר למודל הוא רק הניסוח. זו החלוקה שמונעת את הדבר
     * שהורס הצעות: חזרה עליהן. הצעה שנאמרת פעמיים כבר לא
     * נשמעת כייפית, היא נשמעת נואשת.
     *
     * דייט קודם: מי שעברה 85 עברה גם 60, ואין טעם להציע לה
     * להתכתב כשאפשר להציע להיפגש.
     */
    private suspend fun maybeOffer(
        ctx: OrchestratorContext,
        email: String,
        percent: Int,
        outbox: Outbox,
    ) {
        val made = db.offersMade(email)

        val offer = when {
            percent >= DATE_AT && !made.date -> Offer.DATE
            percent >= WHATSAPP_AT && !made.whatsapp -> Offer.WHATSAPP
            else -> return
        }

        val text = OfferWriter(ctx).write(offer)
        if (text == null) {
            log.warn("ניסוח ההצעה נכשל עבור {} — לא מסמנים, ננסה בסבב הבא", email)
            return
        }

        // הסימון אחרי השליחה ולא לפניה: כישלון בניסוח לא שורף
        // את ההזדמנות היחידה.
        outbox.add(text)
        ctx.appendTurn(Turn(Speaker.AGENT, text))
        when (offer) {
            Offer.DATE -> db.markDateOffered(email)
            Offer.WHATSAPP -> db.markWhatsappOffered(email)
        }
        log.info("הוצע ל-{}: {} (התאמה {}%)", email, offer, percent)
    }

    /**
     * מתמצת כשחוצים כפולה של [COMPACT_EVERY].
     *
     * ההשוואה היא בין *מספרי חלונות* ולא בין ספירות טוקנים,
     * וזה מה שנותן את ההתנהגות שרוני ביקש: 25, 50, 75 — וגם
     * האיפוס. כשהוא מאפס מכסה, החלון קופץ מ-4 ל-0, וזה שינוי
     * כמו כל אחר. השיחה נכנסת לחלון החדש עם היסטוריה שכבר
     * מקוצרת, במקום לגרור לתוכו את כל מה שהצטבר.
     *
     * כישלון בתמצות לא מוחק כלום: עדיף היסטוריה ארוכה מהיסטוריה
     * שנמחקה בלי תחליף.
     */
    private suspend fun compactIfNeeded(
        girl: Girl,
        ctx: OrchestratorContext,
        spentTotal: Int,
    ) {
        val window = spentTotal / COMPACT_EVERY
        if (window == girl.compactionWindow) return

        val before = girl.turns.size
        val summary = HistorySummarizer(ctx).summarize(girl.summary, girl.turns)

        // גם אם התמצות נכשל — מסמנים את החלון, אחרת ננסה שוב
        // בכל הודעה עד סוף החלון.
        girl.compactionWindow = window

        if (summary == null) {
            log.warn("תמצות נכשל עבור {} — ההיסטוריה נשארת כמו שהיא", girl.email)
            return
        }

        girl.compact(summary)
        log.info(
            "היסטוריה תומצתה עבור {}: {} תורות → {} + תקציר (חלון {})",
            girl.email, before, girl.turns.size, window,
        )
    }

    // ------------------------------------------------------------
    //  המימוש של החוזה. פרטי — אף אחד מחוץ לכאן לא בונה אותו.
    // ------------------------------------------------------------
    private class ScopedContext(
        private val girl: Girl,
        override val llmClient: CountingLlmClient,
        private val outbox: Outbox,
        private val db: DbRepository,
        override val herName: String?,
    ) : OrchestratorContext {

        val collectedImages = mutableListOf<Image>()

        override val herEmail: String get() = girl.email

        override fun turns(): List<Turn> = girl.turns.toList()

        override suspend fun hasTalkedBefore(): Boolean =
            (db.tokensSpent(girl.email) ?: 0) > 0

        override suspend fun markWhatsappGiven() = db.markWhatsappGiven(girl.email)

        override suspend fun scheduleDate(whenText: String, place: String, at: java.time.Instant?) =
            db.setDate(girl.email, whenText, place, at)

        override suspend fun cancelDate() = db.cancelDate(girl.email)

        override suspend fun currentDate() = db.getDate(girl.email)

        override fun sentPhotos(): Set<Int> = girl.sentPhotos.toSet()

        override fun markPhotoSent(index: Int) {
            girl.sentPhotos += index
            girl.lastPhotoTurn = girl.turns.size
        }

        /** כמה טוקנים הסבב הזה צרך — המתכנן וכל תת-סוכן. */
        fun tokensUsed(): Int = llmClient.tokensUsed

        /** הספק סירב לשרת אותנו בסבב הזה. */
        fun providerOutOfService(): Boolean = llmClient.providerOutOfService

        override fun summary(): String? = girl.summary

        override fun photosThisRound(): Int = collectedImages.size

        override fun turnsSinceLastPhoto(): Int =
            if (girl.lastPhotoTurn < 0) Int.MAX_VALUE
            else girl.turns.size - girl.lastPhotoTurn

        override fun appendTurn(turn: Turn) = girl.append(turn)

        override fun profile(): HerProfile = girl.profile

        override fun sendMessage(text: String, images: List<Image>) {
            outbox.add(text, images)
        }

        override fun attachImage(image: Image) {
            collectedImages += image
        }
    }

    companion object {
        private fun buildClient(): LlmClient? {
            val key = Env.get("ANTHROPIC_API_KEY")
            if (key.isNullOrBlank()) {
                log.warn("אין ANTHROPIC_API_KEY — הסוכן לא יענה")
                return null
            }
            return AnthropicLlmClient(
                apiKey = key,
                workspaceId = Env.get("ANTHROPIC_WORKSPACE_ID"),
            )
        }
    }
}
