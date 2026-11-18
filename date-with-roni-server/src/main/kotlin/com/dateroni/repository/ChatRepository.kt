package com.dateroni.repository

import com.dateroni.Env
import com.dateroni.Tokens
import com.dateroni.state.AppStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

// ============================================================
//  שיחה: שליחה, פינג, ובקשת חשמל.
//
//  submit לא מחזיר תשובה. הוא מוודא שהסשן חי ושיש תקציב, מסמן
//  שתשובה בדרך, ומשחרר את הבקשה. הסוכן רץ ב-scope שהוזרק לכאן
//  (בבעלות שורש ההרכבה) ומניח את התשובה בתיבת היוצא, והלקוח
//  אוסף אותה בפינג.
//
//  זה מה שמאפשר לסוכן לקחת עשר שניות בלי שה-HTTP ייתקע, וגם
//  לשלוח יותר מהודעה אחת על תור אחד.
// ============================================================

private val log = LoggerFactory.getLogger("ChatRepository")

/**
 * תקרת הטוקנים לכל כתובת, לכל החיים.
 *
 * ניתן לשינוי ב-.env בלי בנייה מחדש, כי המספר הנכון מתגלה
 * רק מהשימוש: סבב אחד עם המנצח ותתי-הסוכנים שורף אלפים.
 */
private val TOKEN_BUDGET = Env.get("TOKEN_BUDGET")?.toIntOrNull() ?: 50_000

/**
 * מה שהיא מקבלת כשנגמר. קבוע ולא נוצר על ידי המודל — בדיוק
 * ברגע הזה אין תקציב להפעיל אותו.
 */
private const val POWER_OUT_MESSAGE =
    "נגמר לי החשמל במיכל ואני לא מצליח להשיב לך. " +
        "אם רוני היה משקיע בי סוללה במקום בהומור, היינו ממשיכים."

/**
 * מה שהיא מקבלת כשרוני הטעין אותה.
 *
 * גם זה קבוע: ברגע שהוא חוזר לחיים אין עדיין תור פעיל שאפשר
 * לתלות עליו תשובה, והיא צריכה לדעת שהדלת נפתחה בלי לנחש.
 */
private const val POWER_BACK_MESSAGE =
    "קיבלתי ג׳ריקן חשמל חדש מרוני. איפה היינו?"

class ChatRepository(
    private val db: DbRepository,
    private val state: AppStateManager,
    private val agent: AgentRepository,
    private val scope: CoroutineScope,
) {

    // ============================================================
    //  בקשה לעוד חשמל
    //
    //  לא מגדילה שום תקציב. היא רק רושמת ב-DB שהיא ביקשה, ורוני
    //  מחליט ידנית. בכוונה: תקציב שמגדיל את עצמו לפי בקשה הוא
    //  לא תקציב.
    // ============================================================
    suspend fun requestMoreTokens(token: String): RequestTokensResult {
        val session = db.findByToken(Tokens.hash(token)) ?: return RequestTokensResult.InvalidSession

        // בלי בדיקת isLive: היא מבקשת בדיוק כשנגמר לה, וזה
        // הרגע שבו הסשן הכי קרוב להיסגר.
        db.requestMoreTokens(session.email)
        return RequestTokensResult.Recorded
    }

    // ============================================================
    //  שליחת הודעה
    // ============================================================
    suspend fun submit(token: String, text: String): SubmitResult {
        val session = db.findByToken(Tokens.hash(token))
        if (session == null || !session.isLive) return SubmitResult.InvalidSession

        // נפסלה (-1) — חסומה לנצח. ההודעה נבלעת בשקט: לא מפעילים
        // סוכן ולא שומרים כלום. הלקוח יראה בפינג ש-match שלילי,
        // ויפסיק לשלוח. הפסילה שורדת ב-DB גם אחרי כניסה מחדש.
        if (session.matchPercent < 0) return SubmitResult.Accepted

        val email = session.email
        val outbox = state.outboxFor(email)

        val remaining = (TOKEN_BUDGET - session.tokensSpent).coerceAtLeast(0)
        if (remaining <= 0) {
            // דרך תיבת היוצא ולא כשגיאת HTTP: היא צריכה לראות
            // בועה של ג׳ימיקלוד, לא פס אדום. פעם אחת בלבד —
            // בלי זה כל ניסיון נוסף היה מוסיף עוד עותק.
            if (outbox.lastText != POWER_OUT_MESSAGE) outbox.add(POWER_OUT_MESSAGE)

            return SubmitResult.BudgetExhausted
        }

        // ההודעה נכנסת לתור תמיד. אם כבר יש עובד — הוא יאסוף
        // אותה בסבב הבא ויידע שהיא התפרצה.
        outbox.enqueue(text)

        // רק מי שתפס את התפקיד מפעיל סוכן. compareAndSet ולא
        // בדיקה-ואז-קביעה: שתי הודעות באותו רגע היו מפעילות שני
        // סוכנים על אותה שיחה.
        if (outbox.tryStart()) {
            scope.launch {
                try {
                    // הנעילה נתפסת רק כאן, ברקע. הפינג לא עובר
                    // דרכה ולכן הוא ממשיך לענות בזמן שהסוכן חושב.
                    state.withConversation(email) { girl ->
                        agent.run(girl, outbox, remaining, session.tokensSpent, session.herName)
                    }
                } catch (e: Exception) {
                    log.error("הסוכן נכשל עבור {}", email, e)
                    // היא מחכה לתשובה. עדיף הודעה כנה מאשר שקט.
                    outbox.add("משהו השתבש אצלי. אפשר לנסות שוב?", isError = true)
                } finally {
                    outbox.finish()
                    // אם היא כתבה בין הריקון האחרון לשחרור הדגל,
                    // ההודעה הייתה נשארת תקועה בלי עובד.
                    if (outbox.hasInbound && outbox.tryStart()) {
                        scope.launch {
                            try {
                                state.withConversation(email) { agent.run(it, outbox, remaining, session.tokensSpent, session.herName) }
                            } finally {
                                outbox.finish()
                            }
                        }
                    }
                }
            }
        }

        return SubmitResult.Accepted
    }

    // ============================================================
    //  פינג
    //
    //  נקרא כל שנייה, ולכן חייב להיות זול: קריאה אחת ל-DB לאימות
    //  הטוקן, וקריאה ממבנה אטומי. בלי נעילות ובלי המתנה.
    // ============================================================
    suspend fun poll(token: String, after: Long): PollResult {
        val session = db.findByToken(Tokens.hash(token))
        if (session == null || !session.isLive) return PollResult.InvalidSession

        // סימן החיים. כל עוד היא מתשאלת, השיחה חיה.
        state.markSeen(session.email)

        val outbox = state.outboxFor(session.email)
        val remaining = (TOKEN_BUDGET - session.tokensSpent).coerceAtLeast(0)

        // תיבה שמעולם לא נשלח ממנה כלום = היא בדיוק נכנסה, והסוכן
        // עוד לא פתח. הטריגר יושב בפינג ולא בכניסה כדי שגם רענון
        // אחרי הפעלה מחדש של השרת יקבל פתיחה — שם ההיסטוריה
        // ממילא נמחקה, אז הסוכן באמת מתחיל מאפס.
        //
        // tryStart הוא מה שמונע שני פותחים: הפינג רץ כל שנייה,
        // והפתיחה לוקחת כמה.
        if (remaining > 0 && outbox.lastSeq == 0L && !outbox.pending && outbox.tryStart()) {
            scope.launch {
                try {
                    state.withConversation(session.email) { girl ->
                        agent.open(girl, outbox, session.herName)
                    }
                } catch (e: Exception) {
                    log.warn("פתיחת שיחה נכשלה עבור {}: {}", session.email, e.message)
                } finally {
                    outbox.finish()
                }
            }
        }

        // התקציב יכול להיגמר באמצע תור, בלי שהיא ניסתה לשלוח
        // כלום. אם נחכה לניסיון השליחה הבא, הפינג כבר ידווח
        // "לא פעיל", הלקוח יעצור, וההודעה תישאר בתיבה לנצח.
        // לכן היא נכנסת כאן — *לפני* האיסוף, כדי שהפינג הזה
        // עצמו יחזיר אותה.
        if (remaining <= 0 && outbox.lastText != POWER_OUT_MESSAGE) {
            outbox.add(POWER_OUT_MESSAGE)
        } else if (remaining > 0 && outbox.lastText == POWER_OUT_MESSAGE) {
            // ההודעה האחרונה שהיא קיבלה הייתה "נגמר החשמל",
            // ופתאום יש תקציב — כלומר רוני הטעין בין שני פינגים.
            // אין צורך בדגל נוסף: תיבת היוצא עצמה זוכרת איפה
            // עצרנו, וברגע שההודעה הזאת נכנסת התנאי כבר לא
            // מתקיים, אז היא נשלחת פעם אחת בלבד.
            outbox.add(POWER_BACK_MESSAGE)
        }

        return PollResult.Ok(
            messages = outbox.since(after),
            pending = outbox.pending,
            tokensRemaining = remaining,
            matchPercent = session.matchPercent,
            agentOnline = state.isAgentOnline,
        )
    }
}
