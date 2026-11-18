package com.dateroni

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

// ============================================================
//  יצירת סודות והגיבוב שלהם.
//
//  מה שנשמר ב-DB הוא תמיד ה-hash, אף פעם לא הסוד עצמו.
//  הסוד הגולמי קיים רק בדרך אל הדפדפן או אל המייל.
// ============================================================

object Tokens {

    // SecureRandom ולא Random. Random הוא מחזורי וניתן לחיזוי
    // מתוך כמה פלטים — בשביל סוד שמהווה אישור כניסה זה קטלני.
    private val rng = SecureRandom()

    private val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()

    /** 24 בתים אקראיים, מקודדים לטקסט בטוח ל-URL. */
    fun generate(): String {
        val bytes = ByteArray(24)
        rng.nextBytes(bytes)
        return encoder.encodeToString(bytes)
    }

    fun hash(raw: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
