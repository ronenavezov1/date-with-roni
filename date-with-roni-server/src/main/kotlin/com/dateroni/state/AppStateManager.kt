package com.dateroni.state

import com.dateroni.objects.Girl
import com.dateroni.objects.LiveConversation
import com.dateroni.objects.Outbox
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

// ============================================================
//  מנהל המצב — facade דק מעל המצב שחי ב-RAM.
//
//  אחריות אחת: להחזיק את המצבים ולסדר גישה אליהם. הוא לא
//  מכיר לוגיקה של שיחה, של דייט או של טוקנים — רק מחזיק ומגן.
//
//  המצב מפורק לשני חלקים, וזה כל מה שיש כאן:
//    • conversations — מפה אחת של LiveConversation. מופע חי לכל
//      אישה: היסטוריה, תיבה, סימן-חיים, מנעול. ממופתח לפי email.
//    • provider      — מצב הספק, כלל-שרת.
//
//  כל מצב שחי ב-RAM נולד כאן ומת כאן. אף שכבה אחרת לא מחזיקה
//  שדה מצב — הרפוזיטורי מתאם, הסוכן חסר מצב, והשרבלטים לא
//  יודעים שקיים מצב בכלל.
//
//  ה-Mutex הוא פר-שיחה (בתוך LiveConversation) ולא גלובלי: שתי
//  הודעות מאותה אישה מסתדרות בתור, ונשים שונות לא מחכות זו לזו.
// ============================================================

class AppStateManager {

    private val conversations = ConcurrentHashMap<String, LiveConversation>()

    private val provider = ProviderStatus()

    // ------------------------------------------------------------
    //  מצב הספק (כלל-שרת)
    // ------------------------------------------------------------
    val isAgentOnline: Boolean get() = provider.isOnline

    fun markAgentOffline(reason: String) = provider.markOffline(reason)

    fun markAgentOnline() = provider.markOnline()

    // ------------------------------------------------------------
    //  גישה לשיחה
    //
    //  computeIfAbsent הוא אטומי ובלי נעילה — לכן outboxFor ו-
    //  markSeen (שרצים בכל פינג) לא נתקעים מאחורי סוכן שחושב.
    //  הנעילה נתפסת רק ב-withConversation, סביב ה-Girl עצמה.
    // ------------------------------------------------------------

    private fun liveFor(email: String): LiveConversation =
        conversations.computeIfAbsent(email) { LiveConversation(it) }

    /**
     * מריץ את הבלוק כשהשיחה נעולה. זו הדרך היחידה לגעת ב-
     * Girl — הוא לא נחשף החוצה בשום צורה אחרת.
     */
    suspend fun <T> withConversation(email: String, block: suspend (Girl) -> T): T {
        val live = liveFor(email)
        return live.lock.withLock { block(live.girl) }
    }

    /** תיבת היוצא של השיחה. נגישה בלי נעילה, בכוונה. */
    fun outboxFor(email: String): Outbox = liveFor(email).outbox

    /** נקרא בכל פינג. לא נועל כלום — הוא רץ פעם בשנייה לכל שיחה. */
    fun markSeen(email: String) {
        liveFor(email).lastSeen = Instant.now()
    }

    /**
     * כניסה חדשה מתחילה שיחה נקייה. מחכה לסוכן אם הוא באמצע תור
     * (הנעילה), מסיר את הרשומה הישנה, ומכין טרייה עם lastSeen=now
     * כדי שהמנקה לא יראה אותה נטושה מיד.
     */
    suspend fun resetConversation(email: String) {
        conversations[email]?.let { old -> old.lock.withLock { conversations.remove(email, old) } }
        liveFor(email)
    }

    val liveConversations: Int get() = conversations.size

    // ------------------------------------------------------------
    //  שיחות נטושות
    //
    //  הזיהוי כאן, השחרור בשני שלבים: מי שקורא צריך גם לסגור את
    //  הסשן ב-DB, וזה לא באחריות מנהל המצב.
    // ------------------------------------------------------------

    /** מי לא תישאל מאז [cutoff]. */
    fun idleSince(cutoff: Instant): List<String> =
        conversations.filterValues { it.lastSeen.isBefore(cutoff) }.keys.toList()

    /** משחרר את כל מה שהשיחה מחזיקה בזיכרון. */
    suspend fun release(email: String) {
        conversations[email]?.let { live -> live.lock.withLock { conversations.remove(email, live) } }
    }
}
