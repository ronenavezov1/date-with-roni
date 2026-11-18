package com.dateroni.api

import com.dateroni.objects.Image
import com.dateroni.objects.OutboundMessage
import kotlinx.serialization.Serializable

// ============================================================
//  צורות ה-JSON שנכנסות ויוצאות.
//
//  הן חיות כאן ולא ב-workers/ בכוונה: worker לא אמור לדעת
//  שיש HTTP בעולם. הוא מחזיר טיפוסי תחום, והשרבלט ממפה.
// ============================================================

@Serializable
data class ApiError(val error: String)

@Serializable
data class LoginRequest(
    /** ה-ID token שגוגל נתנה לדפדפן. */
    val credential: String,
)

@Serializable
data class LoginResponse(
    /** טוקן הסשן שלנו. הדפדפן שומר אותו ומצרף לכל בקשה. */
    val token: String,
    val email: String,
    val name: String? = null,
    val tokensSpent: Int,
)

@Serializable
data class MessageRequest(val token: String, val text: String)

/** בקשה שדורשת רק זהות: "עוד חשמל, בבקשה". */
@Serializable
data class TokenRequest(val token: String)

@Serializable
data class PhotoDto(val id: String, val mime: String, val data: String, val alt: String)

/** תשובה לשליחה. אין כאן תוכן — רק שההודעה נקלטה. */
@Serializable
data class SubmitResponse(
    val accepted: Boolean = true,
    val tokensRemaining: Int? = null,
)

@Serializable
data class PollRequest(
    val token: String,
    /** ה-seq האחרון שהלקוח כבר ראה. 0 בפתיחה. */
    val after: Long = 0,
)

@Serializable
data class OutboundMessageDto(
    val seq: Long,
    val text: String,
    val images: List<PhotoDto> = emptyList(),
    val isError: Boolean = false,
)

@Serializable
data class PollResponse(
    val messages: List<OutboundMessageDto>,
    /** הסוכן כותב כרגע. */
    val pending: Boolean,
    val tokensRemaining: Int,
    /** אחוז ההתאמה של הבחורה. -1 = נפסלה. */
    val matchPercent: Int,
    val active: Boolean,
    /** נסגר כי נגמר החשמל, להבדיל משיחה שפגה. */
    val outOfPower: Boolean = false,
    /** הסוכן מחובר לספק. false = הוא לא יכול לענות לאף אחת. */
    val agentOnline: Boolean = true,
)

@Serializable
data class SessionEnded(
    val error: String,
    val tokensRemaining: Int,
    val active: Boolean = false,
)

internal fun Image.toDto() = PhotoDto(id = id, mime = mime, data = data, alt = alt)

internal fun OutboundMessage.toDto() = OutboundMessageDto(
    seq = seq,
    text = text,
    images = images.map { it.toDto() },
    isError = isError,
)
