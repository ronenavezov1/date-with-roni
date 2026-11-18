package com.dateroni.objects

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

// ============================================================
//  תיבת היוצא של שיחה אחת.
//
//  תשובות הסוכן לא מגיעות בתשובה ל-POST — הן נוחתות כאן,
//  והלקוח אוסף אותן בפינגים. זה מה שמאפשר לסוכן לקחת את הזמן,
//  ובהמשך גם לשלוח יותר מהודעה אחת על תור אחד.
//
//  למה זה *לא* יושב תחת ה-Mutex של השיחה: הלקוח מתשאל כל
//  שנייה. אילו הקריאה הייתה דורשת את אותה נעילה שהסוכן מחזיק
//  בזמן שהוא חושב, כל פינג היה נתקע עד שהוא מסיים — ובדיוק אז
//  הלקוח הכי צריך תשובה מהירה ("הוא עדיין כותב").
//
//  לכן: מבנים אטומיים, קריאה בלי נעילה, כתיבה מהרקע.
// ============================================================

data class OutboundMessage(
    /** מספר רץ בתוך השיחה. הלקוח שולח את האחרון שראה. */
    val seq: Long,
    val text: String,
    val images: List<Image> = emptyList(),
    /** תשובה רגילה, או הודעת שגיאה שנוצרה אצלנו. */
    val isError: Boolean = false,
)

class Outbox {

    private val messages = CopyOnWriteArrayList<OutboundMessage>()
    private val nextSeq = AtomicLong(1)

    // ------------------------------------------------------------
    //  תיבת הנכנס
    //
    //  מה שהיא כתבה ועדיין לא טופל. אם היא כותבת בזמן שהסוכן
    //  עובד, ההודעה נכנסת לכאן והסבב הנוכחי אוסף אותה — במקום
    //  לפתוח עבודה מקבילה שתילחם על אותה היסטוריה.
    // ------------------------------------------------------------
    private val inbound = ConcurrentLinkedQueue<String>()

    /**
     * דגל "יש עובד". compareAndSet ולא בדיקה-ואז-קביעה: שתי
     * הודעות שנשלחות באותו רגע היו שתיהן רואות "פנוי" ומפעילות
     * שני סוכנים על אותה שיחה.
     */
    private val running = AtomicBoolean(false)

    fun enqueue(text: String) {
        inbound += text
    }

    /** מוציא את כל מה שהצטבר. ריק = אין מה לעשות. */
    fun drainInbound(): List<String> {
        val out = mutableListOf<String>()
        while (true) out += inbound.poll() ?: break
        // סבב חדש מתחיל — היא שוב מחכה.
        if (out.isNotEmpty()) replied.set(false)
        return out
    }

    val hasInbound: Boolean get() = inbound.isNotEmpty()

    /** true רק למי שהצליח לתפוס את התפקיד. */
    fun tryStart(): Boolean = running.compareAndSet(false, true)

    fun finish() = running.set(false)

    private val replied = AtomicBoolean(false)

    /** התשובה לסבב הנוכחי יצאה. מה שרץ מכאן הוא כבר לא המתנה שלה. */
    fun markReplied() = replied.set(true)

    /**
     * "מקליד...".
     *
     * לא "העובד עסוק" אלא "היא עדיין מחכה". ההבדל חשוב: אחרי
     * שהתשובה יצאה עוד רצות עבודות ברקע — חילוץ פרופיל, ניתוח
     * התאמה — ושתיהן קריאות LLM של כמה שניות. אם הן היו נספרות,
     * ה"מקליד" היה ממשיך לרוץ מתחת להודעה שכבר הופיעה על המסך.
     *
     * הודעת ביניים ("רגע, בודק לך") לא מסמנת שהתשובה יצאה, ולכן
     * שם ה"מקליד" דווקא נשאר — וזה נכון, כי היא באמת עוד מחכה.
     */
    val pending: Boolean get() = inbound.isNotEmpty() || (running.get() && !replied.get())

    fun add(text: String, images: List<Image> = emptyList(), isError: Boolean = false): OutboundMessage {
        val message = OutboundMessage(nextSeq.getAndIncrement(), text, images, isError)
        messages += message
        trim()
        return message
    }

    /**
     * כל מה שנוסף אחרי [after].
     *
     * סמן רץ ולא "רוקן את התיבה": פינג שאבד ברשת, או שתי לשוניות
     * פתוחות, לא גורמים להודעה להיעלם. הלקוח מבקש שוב מאותה נקודה
     * ומקבל את אותו דבר.
     */
    fun since(after: Long): List<OutboundMessage> {
        // סמן מריצה קודמת של השרת. הרצף חי בזיכרון ומתאפס
        // בכל הפעלה מחדש, בזמן שהלשונית שלה עדיין מחזיקה
        // מספר גבוה מהחיים הקודמים. בלי השורה הזאת כל פריסה
        // משתיקה בשקט כל לשונית פתוחה: היא ממשיכה לשאול,
        // מקבלת 200 ריק, ולא מקבלת תשובה עד רענון.
        //
        // אין סכנה של כפילות — אחרי הפעלה מחדש התיבה מכילה
        // רק את מה שנוצר מאז, וזה בדיוק מה שהיא עוד לא ראתה.
        val effective = if (after > lastSeq) 0L else after
        return messages.filter { it.seq > effective }
    }

    val lastSeq: Long get() = messages.lastOrNull()?.seq ?: 0

    /** הטקסט האחרון שנשלח. משמש כדי לא לחזור על אותה הודעה. */
    val lastText: String? get() = messages.lastOrNull()?.text

    /** תקרה, כדי ששיחה ארוכה לא תצבור זיכרון בלי גבול. */
    private fun trim(max: Int = 200) {
        while (messages.size > max) messages.removeAt(0)
    }
}
