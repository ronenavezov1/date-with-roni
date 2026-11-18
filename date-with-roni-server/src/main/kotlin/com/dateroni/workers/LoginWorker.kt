package com.dateroni.workers

import com.dateroni.api.ApiResponse
import com.dateroni.api.LoginResponse
import com.dateroni.repository.LoginResult
import com.dateroni.repository.SessionRepository

// ============================================================
//  Intent.
//
//  מתרגם תוצאת רפוזיטורי לתשובת API. זה כל תפקידו — הוא לא
//  מחליט כלום, רק ממפה. וכיוון שהמיפוי יושב כאן, השרבלט לא
//  צריך להכיר אף טיפוס של הרפוזיטורי.
//
//  ה-when על טיפוס sealed: אם ייווסף מצב חדש ל-LoginResult,
//  הבנייה תישבר כאן — במקום שהוא ייפול בשקט ל-else.
// ============================================================

class LoginWorker(private val sessions: SessionRepository) {

    suspend fun run(credential: String): ApiResponse =
        when (val result = sessions.login(credential)) {
            is LoginResult.Ok -> ApiResponse.ok(
                LoginResponse(
                    token = result.token,
                    email = result.email,
                    name = result.name,
                    tokensSpent = result.tokensSpent,
                )
            )

            LoginResult.NotAuthenticated ->
                ApiResponse.unauthorized("הכניסה עם גוגל לא אומתה.")

            LoginResult.EmailNotVerified ->
                ApiResponse.forbidden("כתובת המייל בחשבון גוגל לא מאומתת.")

            LoginResult.NotConfigured ->
                ApiResponse.unavailable("הכניסה עם גוגל לא מוגדרת. חסר GOOGLE_CLIENT_ID.")
        }
}
