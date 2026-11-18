package com.dateroni.servlets

import com.dateroni.api.ApiResponse
import com.dateroni.api.LoginRequest
import com.dateroni.workers.LoginWorker
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

// ============================================================
//  POST /api/login
//
//  POST ולא GET: ה-JWT הוא אישור כניסה. בשורת שאילתה הוא היה
//  נוחת ב-access.log של nginx, בהיסטוריית הדפדפן ובכותרת Referrer.
//
//  השרבלט עושה שני דברים בלבד: מוודא שהבקשה תקינה מבחינת צורה,
//  ומוציא החוצה את מה שה-worker הרכיב. הוא לא מכיר אף טיפוס
//  של הרפוזיטורי, ולא מחליט אף קוד תשובה בעצמו.
// ============================================================

suspend fun io.ktor.server.application.ApplicationCall.send(result: ApiResponse) =
    respond(HttpStatusCode.fromValue(result.status), result.body)

fun Route.loginServlet(worker: LoginWorker?) {
    post("/api/login") {
        if (worker == null) {
            return@post call.send(ApiResponse.unavailable("השרת עוד לא מחובר לבסיס הנתונים."))
        }

        val credential = try {
            call.receive<LoginRequest>().credential.trim()
        } catch (_: Exception) {
            ""
        }
        if (credential.isEmpty()) {
            return@post call.send(ApiResponse.badRequest("חסר טוקן הכניסה."))
        }

        call.send(worker.run(credential))
    }
}
