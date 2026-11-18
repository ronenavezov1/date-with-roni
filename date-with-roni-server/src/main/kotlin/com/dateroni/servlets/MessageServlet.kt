package com.dateroni.servlets

import com.dateroni.api.ApiResponse
import com.dateroni.api.MessageRequest
import com.dateroni.api.PollRequest
import com.dateroni.api.TokenRequest
import com.dateroni.workers.MessageWorker
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

// ============================================================
//  POST /api/message   שולח הודעה. 202, בלי תוכן תשובה.
//  POST /api/poll      אוסף את מה שהסוכן הספיק לייצר.
//  POST /api/request-tokens  רושמת שהיא מבקשת עוד חשמל.
//
//  שניהם POST ולא GET, כולל הפינג: הטוקן הוא אישור כניסה, ובשורת
//  שאילתה הוא היה נוחת ב-access.log — פעם בשנייה, לכל אורך כל
//  שיחה.
// ============================================================

/** תקרת אורך להודעה אחת. */
private const val MAX_MESSAGE_CHARS = 1000

fun Route.messageServlet(worker: MessageWorker?) {

    post("/api/message") {
        if (worker == null) {
            return@post call.send(ApiResponse.unavailable("השרת עוד לא מחובר לבסיס הנתונים."))
        }

        val body = try {
            call.receive<MessageRequest>()
        } catch (_: Exception) {
            null
        }

        val token = body?.token?.trim().orEmpty()
        if (body == null || token.isEmpty()) {
            return@post call.send(ApiResponse.unauthorized("חסר טוקן."))
        }

        val text = body.text.trim()
        if (text.isEmpty()) {
            return@post call.send(ApiResponse.badRequest("הודעה ריקה."))
        }
        if (text.length > MAX_MESSAGE_CHARS) {
            return@post call.send(ApiResponse.badRequest("ההודעה ארוכה מדי."))
        }

        call.send(worker.submit(token, text))
    }

    post("/api/request-tokens") {
        if (worker == null) {
            return@post call.send(ApiResponse.unavailable("השרת עוד לא מחובר לבסיס הנתונים."))
        }

        val body = try {
            call.receive<TokenRequest>()
        } catch (_: Exception) {
            null
        }

        val token = body?.token?.trim().orEmpty()
        if (token.isEmpty()) {
            return@post call.send(ApiResponse.unauthorized("חסר טוקן."))
        }

        call.send(worker.requestTokens(token))
    }

    post("/api/poll") {
        if (worker == null) {
            return@post call.send(ApiResponse.unavailable("השרת עוד לא מחובר לבסיס הנתונים."))
        }

        val body = try {
            call.receive<PollRequest>()
        } catch (_: Exception) {
            null
        }

        val token = body?.token?.trim().orEmpty()
        if (body == null || token.isEmpty()) {
            return@post call.send(ApiResponse.unauthorized("חסר טוקן."))
        }

        call.send(worker.poll(token, body.after.coerceAtLeast(0)))
    }
}
