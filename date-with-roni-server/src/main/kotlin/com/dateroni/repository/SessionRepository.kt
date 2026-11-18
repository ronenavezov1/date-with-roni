package com.dateroni.repository

import com.dateroni.Tokens
import com.dateroni.google.GoogleAuth
import com.dateroni.state.AppStateManager
import java.time.Duration
import java.time.Instant

// ============================================================
//  כניסה וזהות.
//
//  מקבל credential של גוגל, מאמת אותו דרך הכלי GoogleAuth,
//  פותח (או מרענן) סשן ב-DB, ומאפס את השיחה בזיכרון.
//
//  הטוקן הגולמי חוזר לדפדפן; ב-DB נשמר רק הגיבוב שלו.
// ============================================================

/** כמה זמן סשן חי. */
private val SESSION_TTL: Duration = Duration.ofHours(2)

class SessionRepository(
    private val db: DbRepository,
    private val state: AppStateManager,
) {
    suspend fun login(credential: String): LoginResult {
        if (!GoogleAuth.isConfigured) return LoginResult.NotConfigured

        val identity = GoogleAuth.verify(credential)
            ?: return LoginResult.NotAuthenticated
        if (!identity.emailVerified) return LoginResult.EmailNotVerified

        // הטוקן הגולמי חוזר לדפדפן; ב-DB נשמר רק הגיבוב שלו.
        val sessionToken = Tokens.generate()

        val tokensSpent = db.login(
            email = identity.email,
            googleSub = identity.sub,
            name = identity.name,
            tokenHash = Tokens.hash(sessionToken),
            expiresAt = Instant.now().plus(SESSION_TTL),
        )

        // כניסה חדשה = שיחה חדשה. אותה כתובת שנכנסת מדפדפן שני
        // מפילה את הסשן הראשון, ואין טעם שההיסטוריה תמשיך.
        state.resetConversation(identity.email)

        return LoginResult.Ok(
            token = sessionToken,
            email = identity.email,
            name = identity.name,
            tokensSpent = tokensSpent,
        )
    }
}
