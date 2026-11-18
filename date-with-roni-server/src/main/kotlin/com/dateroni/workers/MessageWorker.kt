package com.dateroni.workers

import com.dateroni.api.ApiResponse
import com.dateroni.api.PollResponse
import com.dateroni.api.SessionEnded
import com.dateroni.api.SubmitResponse
import com.dateroni.api.toDto
import com.dateroni.repository.ChatRepository
import com.dateroni.repository.PollResult
import com.dateroni.repository.RequestTokensResult
import com.dateroni.repository.SubmitResult

// ============================================================
//  Intent. מתרגם תוצאות רפוזיטורי לתשובות API.
// ============================================================

class MessageWorker(private val chat: ChatRepository) {

    /** שולח הודעה. אין תוכן בתשובה — הוא יגיע בפינג. */
    suspend fun submit(token: String, text: String): ApiResponse =
        when (chat.submit(token, text)) {
            // 202 ולא 200: נקלטה, עוד לא טופלה.
            SubmitResult.Accepted ->
                ApiResponse.accepted(SubmitResponse())

            SubmitResult.InvalidSession ->
                ApiResponse.unauthorized("השיחה פגה. אפשר להיכנס שוב.")

            SubmitResult.BudgetExhausted ->
                ApiResponse.conflict(
                    SessionEnded("השיחה הסתיימה. אפשר לפתוח חדשה מהדף הראשי.", 0)
                )
        }

    /** רושמת בקשה לעוד טוקנים. לא מגדילה כלום. */
    suspend fun requestTokens(token: String): ApiResponse =
        when (chat.requestMoreTokens(token)) {
            RequestTokensResult.Recorded ->
                ApiResponse.ok(mapOf("message" to "העברתי לרוני. אם הוא ער, הוא יטעין אותי."))

            RequestTokensResult.InvalidSession ->
                ApiResponse.unauthorized("השיחה פגה. אפשר להיכנס שוב.")
        }

    /** אוסף מה שהצטבר מאז [after]. */
    suspend fun poll(token: String, after: Long): ApiResponse =
        when (val result = chat.poll(token, after)) {
            is PollResult.Ok -> ApiResponse.ok(
                PollResponse(
                    messages = result.messages.map { it.toDto() },
                    pending = result.pending,
                    tokensRemaining = result.tokensRemaining,
                    matchPercent = result.matchPercent,
                    active = result.tokensRemaining > 0,
                    outOfPower = result.tokensRemaining <= 0,
                    agentOnline = result.agentOnline,
                )
            )

            PollResult.InvalidSession ->
                ApiResponse.unauthorized("השיחה פגה. אפשר להיכנס שוב.")
        }
}
