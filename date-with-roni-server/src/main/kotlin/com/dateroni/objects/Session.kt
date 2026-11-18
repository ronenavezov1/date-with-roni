package com.dateroni.objects

import java.time.Instant

// ============================================================
//  מצב הסשן כפי שהוא נקרא מ-Postgres.
//
//  data class רגיל, בלי שום טיפוס של JDBC — זה מה שמאפשר לכל
//  שאר השכבות לעבוד בלי לדעת שיש מסד נתונים מאחורה.
// ============================================================

data class Session(
    val email: String,
    val herName: String?,
    val sessionActive: Boolean,
    val tokensSpent: Int,
    val expiresAt: Instant?,
    /**
     * אחוז ההתאמה לרוני, כפי שסוכן הניתוח קבע.
     * מתחיל ב-0 ונע 0..100 תוך כדי השיחה. הערך -1 = פסילה.
     */
    val matchPercent: Int = 0,
) {
    /** חי = הסשן פתוח ולא פג. */
    val isLive: Boolean
        get() = sessionActive && (expiresAt == null || expiresAt.isAfter(Instant.now()))

    /** נפסלה. -1 הוא סנטינל נעול — אין דרך חזרה. */
    val isDisqualified: Boolean get() = matchPercent < 0
}
