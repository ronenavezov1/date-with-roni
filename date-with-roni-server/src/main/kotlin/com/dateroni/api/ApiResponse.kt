package com.dateroni.api

// ============================================================
//  תשובה מוכנה לשליחה.
//
//  ה-worker מרכיב אותה, והשרבלט רק מוציא אותה החוצה. ככה
//  אף אחד לא חוצה גבול:
//
//    השרבלט  לא מכיר טיפוסים של הרפוזיטורי
//    ה-worker לא מכיר את Ktor
//
//  status הוא Int ולא HttpStatusCode בדיוק בגלל השורה השנייה —
//  מספר הוא ידע משותף, HttpStatusCode הוא טיפוס של שרת HTTP.
// ============================================================

data class ApiResponse(
    val status: Int,
    /** גוף התשובה. תמיד אחד מטיפוסי ה-DTO שבחבילה הזו. */
    val body: Any,
) {
    companion object {
        fun ok(body: Any) = ApiResponse(200, body)
        fun accepted(body: Any) = ApiResponse(202, body)
        fun badRequest(message: String) = ApiResponse(400, ApiError(message))
        fun unauthorized(message: String) = ApiResponse(401, ApiError(message))
        fun forbidden(message: String) = ApiResponse(403, ApiError(message))
        fun conflict(body: Any) = ApiResponse(409, body)
        fun unavailable(message: String) = ApiResponse(503, ApiError(message))
    }
}
