package com.dateroni.orchestrator.tools

import com.dateroni.orchestrator.OrchestratorContext
import com.dateroni.orchestrator.agent.Tool
import com.dateroni.orchestrator.roni.RoniPhotos
import com.dateroni.orchestrator.subagents.ActionAdvisor
import com.dateroni.orchestrator.subagents.HumorWriter
import com.dateroni.orchestrator.subagents.MoodReader
import com.dateroni.orchestrator.subagents.PhotoAdvisor
import com.dateroni.orchestrator.subagents.ReplyReviewer

// ============================================================
//  תת-הסוכנים היועצים, חשופים למתכנן ככלים.
//
//  כולם Tool ולא Action: הם שופטים ומייעצים, לא משנים כלום.
//  היוצא מן הכלל הוא attach_photo — הוא מסמן שתמונה נשלחה,
//  אבל הסימון הזה חי בזיכרון השיחה בלבד ולא בעולם, אז הוא
//  נשאר בצד הקריאה.
//
//  המתכנן בוחר את מי להפעיל. אף אחד מהם לא רץ אוטומטית —
//  אחרת כל הודעה הייתה עולה חמש קריאות למודל.
// ============================================================

class CheckMoodTool(private val ctx: OrchestratorContext) : Tool {
    override val name = "check_mood"
    override val description =
        "קורא את מצב הרוח של השיחה ומחזיר המלצת טון — זורמת, נעצרת, " +
            "זהירה, משחקית. השתמש כשאתה לא בטוח באיזה טון לענות, או " +
            "כשמשהו בשיחה השתנה."

    override suspend fun execute(args: Map<String, Any>): String = MoodReader(ctx).read()
}

class SuggestActionTool(private val ctx: OrchestratorContext) : Tool {
    override val name = "what_now"
    override val description =
        "מציע מה כדאי לעשות עכשיו: מה לשאול, איזה נושא להעלות, או אם " +
            "הגיע הרגע להציע וואטסאפ או דייט. השתמש כשהשיחה דורכת במקום " +
            "או כשאתה מתלבט אם להתקדם."

    override suspend fun execute(args: Map<String, Any>): String = ActionAdvisor(ctx).advise()
}

class SuggestHumorTool(private val ctx: OrchestratorContext) : Tool {
    override val name = "suggest_joke"
    override val description =
        "מציע תגובה מצחיקה בסגנון של רוני. השתמש כשיש רגע שמזמין בדיחה. " +
            "אם הוא יחזיר שאין מקום לבדיחה — אל תדחוף אחת בכוח."

    override val parameters = mapOf(
        "situation" to "מה קורה בשיחה עכשיו, במשפט",
    )

    override suspend fun execute(args: Map<String, Any>): String =
        HumorWriter(ctx).suggest(args["situation"]?.toString().orEmpty())
}

class ReviewReplyTool(private val ctx: OrchestratorContext) : Tool {
    override val name = "review_reply"
    override val description =
        "בודק תשובה לפני שליחה ומחזיר APPROVED או הצעת שיפור. שווה " +
            "להשתמש כשהתשובה ארוכה, כשאתה לא בטוח בטון, או כשהיא נוגעת " +
            "במשהו רגיש."

    override val parameters = mapOf(
        "draft" to "הטקסט שאתה מתכוון לשלוח",
    )

    override suspend fun execute(args: Map<String, Any>): String =
        ReplyReviewer(ctx).review(args["draft"]?.toString().orEmpty())
}

// ------------------------------------------------------------
//  צירוף תמונה
//
//  שואל את היועץ אם מתאים ומה, ואם כן — מצרף. שתי השאלות
//  יחד, כי אי אפשר לענות על "האם" בלי לדעת מה קיים.
//
//  שלוש תקרות, וכולן כאן ולא בפרומפט. ליועץ יש כללי קצב
//  משלו, אבל הוא מודל — הוא ממליץ, הוא לא נאכף. מה שבאמת
//  מונע הצפה זה הקוד שרץ לפניו:
//
//    • תמונה אחת להודעה. בלי זה המנצח קורא לכלי שוב ושוב
//      באותו סבב ומצרף ערימה.
//    • צינון של כמה תורות אחרי כל תמונה.
//    • תקרה לכל השיחה.
// ------------------------------------------------------------
private const val MAX_PHOTOS_PER_MESSAGE = 1
private const val MAX_PHOTOS_WHEN_ASKED = 2
private const val PHOTO_COOLDOWN_TURNS = 4
private const val MAX_PHOTOS_PER_CONVERSATION = 12

class AttachPhotoTool(private val ctx: OrchestratorContext) : Tool {
    override val name = "attach_photo_if_fits"
    override val description =
        "בודק אם מתאים לצרף תמונה לתשובה הנוכחית, ואם כן — מצרף אותה. " +
            "כשהיא מבקשת תמונה — תמיד השתמש בזה. לא שולח אותה תמונה פעמיים. השתמש גם כשהיא שאלה " +
            "על משהו שיש לו תמונה, ביקשה תמונה, או שיש רגע שתמונה תחזק."

    override val parameters = mapOf(
        "her_message" to "מה היא כתבה עכשיו",
        "she_asked" to "true אם היא ביקשה תמונה במפורש (\"תראה לי\", \"יש עוד?\"), " +
            "false אם זו יוזמה שלך",
    )

    override suspend fun execute(args: Map<String, Any>): String {
        val herMessage = args["her_message"]?.toString().orEmpty()
        val asked = args["she_asked"]?.toString()?.lowercase() == "true"

        val perMessage = if (asked) MAX_PHOTOS_WHEN_ASKED else MAX_PHOTOS_PER_MESSAGE
        if (ctx.photosThisRound() >= perMessage)
            return "צירפת מספיק תמונות להודעה הזאת. ענה בלי עוד."

        if (ctx.sentPhotos().size >= MAX_PHOTOS_PER_CONVERSATION)
            return "שלחת מספיק תמונות בשיחה הזאת. ענה במילים."

        // הצינון קיים כדי שלא ישלח תמונה בכל הודעה מיוזמתו.
        // כשהיא ביקשה במפורש הוא לא רלוונטי — לסרב למי שביקשה
        // תמונה זה קמצנות, לא ריסון.
        if (!asked && ctx.turnsSinceLastPhoto() < PHOTO_COOLDOWN_TURNS)
            return "שלחת תמונה ממש עכשיו. תן לשיחה לזוז לפני עוד אחת — ענה בלי."

        val index = PhotoAdvisor(ctx).advise(herMessage, asked)
            ?: return "לא מתאים לצרף תמונה עכשיו. ענה בלי."

        val photo = RoniPhotos.load(listOf(index)).firstOrNull()
            ?: return "התמונה לא נטענה. ענה בלי, ואל תבטיח לה תמונה."

        ctx.attachImage(photo)
        ctx.markPhotoSent(index)

        return "צורפה תמונה: \"${photo.alt}\". היא תוצג לה אוטומטית — " +
            "אל תתאר אותה במילים. אפשר משפט קצר לצידה."
    }
}
