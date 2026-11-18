package com.dateroni.repository

import com.dateroni.Env
import com.dateroni.state.AppStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

private val log = LoggerFactory.getLogger("AppRepository")

// ============================================================
//  שורש ההרכבה.
//
//  המצב היחיד שהוא מחזיק הוא AppStateManager — הזיכרון של השרת.
//  מעבר לזה הוא חסר-מצב: הוא בונה, מחזיק וסוגר את שאר
//  הרפוזיטורים, ומריץ ברקע את משימות מחזור-החיים.
//
//      AppRepository (שורש)
//        ├── state     AppStateManager    הזיכרון (RAM)
//        ├── db        DbRepository       מצב מתמיד (Postgres)
//        ├── agent     AgentRepository    הסוכן, חסר מצב
//        ├── sessions  SessionRepository  כניסה
//        └── chat      ChatRepository     שליחה · פינג · חשמל
//
//  שני סוגי מצב, ובכוונה מופרדים:
//
//    Girl          תוכן ההתכתבות, בזיכרון בלבד. נמחק כשהתהליך
//                  יורד, ולא מגיע לדיסק לעולם.
//    Session       ב-Postgres. מי היא, כמה נשרף, מה יצא מהשיחה.
//                  בלי מילה מהתוכן.
//
//  ההפרדה הזו היא ההבטחה שכתובה בתחתית האתר.
// ============================================================

/** כל כמה זמן בודקים מי הפסיקה לתשאל. */
private val SWEEP_EVERY: Duration = Duration.ofMinutes(1)

/**
 * כמה זמן בלי פינג עד שמסיימים את השיחה.
 *
 * הלקוח מתשאל כל שנייה. חמש דקות זה מרווח שמכסה לשונית מוסתרת —
 * דפדפנים מאטים טיימרים ברקע לפעם בדקה בערך — ועדיין משחרר
 * מהר מספיק לשונית שנסגרה.
 */
private val IDLE_TTL: Duration =
    Duration.ofMinutes(Env.get("SESSION_IDLE_MINUTES", "5").toLongOrNull() ?: 5)


class AppRepository private constructor(
    private val state: AppStateManager,
    private val db: DbRepository,
    private val agent: AgentRepository,
) : AutoCloseable {

    // הסקופ שבו הסוכן והמשימות ברקע רצים אחרי שהבקשה כבר חזרה.
    // SupervisorJob: תור אחד שנכשל לא מפיל את השאר. השורש הוא
    // הבעלים של הסקופ — הוא יוצר אותו, מזריק אותו למי שצריך,
    // וסוגר אותו ב-close.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ------------------------------------------------------------
    //  הרפוזיטורים שהשורש מחזיק. כל worker מקבל בדיוק את זה
    //  שהוא צריך — לא את השורש כולו.
    // ------------------------------------------------------------
    val sessions: SessionRepository = SessionRepository(db, state)

    val chat: ChatRepository = ChatRepository(db, state, agent, scope)

    // ============================================================
    //  מחזור חיים
    // ============================================================

    /**
     * מסיים שיחות שהלקוח הפסיק לתשאל עליהן.
     *
     * שני צעדים, בסדר הזה: קודם סוגרים את הסשן ב-DB, ורק אחר כך
     * משחררים את הזיכרון. אם ה-DB ייפול באמצע, השיחה תישאר
     * בזיכרון וננסה שוב בסבב הבא — עדיף מלשחרר אותה ולהשאיר
     * ב-DB סשן שנראה פתוח עם טוקן חי.
     */
    private fun startJanitor() = scope.launch {
        while (isActive) {
            delay(SWEEP_EVERY.toMillis())
            val cutoff = Instant.now().minus(IDLE_TTL)
            for (email in state.idleSince(cutoff)) {
                try {
                    db.endSession(email)
                    state.release(email)
                    log.info("השיחה של {} הסתיימה — אין פינג {} דקות", email, IDLE_TTL.toMinutes())
                } catch (e: Exception) {
                    log.warn("סגירת שיחה נכשלה עבור {}: {}", email, e.message)
                }
            }
        }
    }

    /**
     * בודק שהספק עונה, ברקע.
     *
     * ברקע ולא בעלייה עצמה: קריאת רשת שנתקעת לא צריכה לעכב
     * את פתיחת הפורט. עד שהתשובה חוזרת, ברירת המחדל היא
     * "מחובר" — הנחה אופטימית שנמשכת שנייה או שתיים.
     */
    private fun probeAgent() = scope.launch {
        try {
            agent.probeProvider()
        } catch (e: Exception) {
            log.warn("בדיקת הספק בעלייה נכשלה: {}", e.message)
        }
    }

    override fun close() {
        scope.cancel()
        db.close()
    }

    companion object {
        /**
         * בונה ומחבר את כל הרפוזיטורים.
         *
         * זו הסיבה שהקונסטרקטור פרטי: מי שמחוץ למחלקה הזו לא צריך
         * לדעת שיש Postgres, בריכת חיבורים או סוכן. הוא מקבל
         * AppRepository או null, ומושך ממנו את הרפוזיטורי שהוא צריך.
         *
         * null = אין DATABASE_URL. השרת עולה בלי מסד נתונים, וכל
         * מסלול שדורש אותו מחזיר 503 מפורש.
         */
        fun fromEnv(): AppRepository? {
            // null = אין DATABASE_URL. DbRepository הוא הבעלים היחיד של
            // בריכת החיבורים — הוא בונה אותה, והוא היחיד שנוגע בה.
            val db = DbRepository.fromEnv() ?: return null

            // מופע אחד מכל דבר. הסוכן מקבל את *אותו* מנהל מצב.
            val state = AppStateManager()

            return AppRepository(
                state = state,
                db = db,
                agent = AgentRepository(db, state),
            ).also {
                it.startJanitor()
                it.probeAgent()
            }
        }
    }
}
