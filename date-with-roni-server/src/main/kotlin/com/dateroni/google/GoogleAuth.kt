package com.dateroni.google

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.dateroni.Env
import org.slf4j.LoggerFactory

// ============================================================
//  כלי אימות הזהות של גוגל — אימות ה-ID token.
//
//  לא רפוזיטורי: הוא חסר מצב ואף אחד לא "מחזיק" אותו. זה כלי
//  שקוראים לו — נותנים לו credential, מקבלים זהות מאומתת או null.
//
//  מה שמגיע מהדפדפן הוא JWT חתום בידי גוגל. האימות בודק:
//    • שהחתימה תקפה מול המפתחות הציבוריים של גוגל
//    • ש-aud הוא ה-Client ID *שלנו* — בלי זה אפשר להביא
//      טוקן תקף לגמרי שהונפק לאתר אחר לגמרי
//    • שהוא לא פג
//
//  הכלל: אף פעם לא לקרוא את תוכן ה-JWT בלי לאמת חתימה קודם.
//  JWT הוא base64, לא הצפנה — כל אחד יכול לכתוב אחד ולטעון
//  בו שהוא כל כתובת מייל שירצה.
// ============================================================

private val log = LoggerFactory.getLogger("GoogleAuth")

object GoogleAuth {

    private val clientId: String? = Env.get("GOOGLE_CLIENT_ID")

    val isConfigured: Boolean = !clientId.isNullOrBlank()

    data class Identity(
        val email: String,
        val emailVerified: Boolean,
        val sub: String,
        val name: String?,
    )

    private val verifier: GoogleIdTokenVerifier? by lazy {
        clientId?.takeIf { it.isNotBlank() }?.let { id ->
            GoogleIdTokenVerifier.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(listOf(id))
                .build()
        }
    }

    /** null אם הטוקן לא תקף מכל סיבה. */
    suspend fun verify(credential: String): Identity? = withContext(Dispatchers.IO) {
        val v = verifier ?: return@withContext null
        try {
            // הקריאה הזו מורידה את המפתחות של גוגל בפעם הראשונה
            // ושומרת אותם במטמון. חוסמת — ולכן Dispatchers.IO.
            val token = v.verify(credential) ?: return@withContext null
            val payload = token.payload
            Identity(
                email = payload.email.lowercase(),
                emailVerified = payload.emailVerified == true,
                sub = payload.subject,
                name = payload["name"] as? String,
            )
        } catch (e: Exception) {
            log.warn("אימות טוקן גוגל נכשל: {}", e.message)
            null
        }
    }
}
