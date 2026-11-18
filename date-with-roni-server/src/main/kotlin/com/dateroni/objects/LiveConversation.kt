package com.dateroni.objects

import kotlinx.coroutines.sync.Mutex
import java.time.Instant

// ============================================================
//  מופע חי אחד של אישה אחת, מאוגד.
//
//  כל מה ששיחה מחזיקה ב-RAM, במקום אחד: הבחורה עצמה (girl),
//  תיבת היוצא (outbox), סימן החיים (lastSeen), והמנעול שמסדר
//  גישה (lock). במקום ארבע מפות מקבילות ממופתחות לפי email —
//  מפה אחת של רשומות.
//
//  ה-lock הוא פר-רשומה: שתי הודעות של אותה אישה מסתדרות בתור,
//  ונשים שונות לא מחכות זו לזו.
//
//  ה-outbox נגיש **בלי** המנעול — הפינג קורא אותו כל שנייה,
//  ואסור לו להיתקע מאחורי סוכן שמחזיק את הנעילה בזמן שהוא חושב.
//  לכן הוא שדה גלוי, לא משהו שעוברים אליו דרך withLock.
// ============================================================

class LiveConversation(email: String) {

    val girl: Girl = Girl(email)

    val outbox: Outbox = Outbox()

    val lock: Mutex = Mutex()

    /** סימן החיים. מתעדכן בכל פינג, נקרא בלי נעילה. */
    @Volatile
    var lastSeen: Instant = Instant.now()
}
