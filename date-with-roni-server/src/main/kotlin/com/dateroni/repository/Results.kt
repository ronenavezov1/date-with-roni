package com.dateroni.repository

import com.dateroni.objects.OutboundMessage

// ============================================================
//  מה ש-AppRepository מחזיר החוצה.
//
//  sealed: הקמפיילר כופה על השרבלט לטפל בכל מקרה. אם ייווסף
//  מצב חדש — למשל RateLimited — הבנייה תישבר בכל מקום ששכח
//  אותו, במקום שהוא ייפול בשקט ל-else.
//
//  אין כאן שום דבר מ-HTTP: הרפוזיטורי לא יודע שהוא קיים.
// ============================================================

sealed interface LoginResult {
    data class Ok(
        val token: String,
        val email: String,
        val name: String?,
        val tokensSpent: Int,
    ) : LoginResult

    /** גוגל לא אישרה: חתימה, aud או תפוגה. */
    data object NotAuthenticated : LoginResult

    /** נדיר, אבל קיים בחשבונות ארגוניים ומאוחדים. */
    data object EmailNotVerified : LoginResult

    /** חסר GOOGLE_CLIENT_ID בהגדרות השרת. */
    data object NotConfigured : LoginResult
}

/**
 * תשובה לשליחת הודעה. שים לב שאין כאן טקסט של תשובה —
 * ההודעה רק *נקלטה*. התשובה תגיע דרך הפינג.
 */
sealed interface SubmitResult {
    data object Accepted : SubmitResult

    /** הטוקן לא נמצא, הסשן כבוי, או שהוא פג. */
    data object InvalidSession : SubmitResult

    /** התקציב נגמר. השיחה נסגרת. */
    data object BudgetExhausted : SubmitResult
}

/** תשובה לבקשת עוד טוקנים. */
sealed interface RequestTokensResult {
    data object Recorded : RequestTokensResult
    data object InvalidSession : RequestTokensResult
}

/** תשובה לפינג. */
sealed interface PollResult {
    data class Ok(
        val messages: List<OutboundMessage>,
        /** הסוכן עובד כרגע. מזין את "מקליד..." אצל הלקוח. */
        val pending: Boolean,
        val tokensRemaining: Int,
        /** אחוז ההתאמה של הבחורה. -1 = נפסלה. */
        val matchPercent: Int,
        /** הספק עונה בכלל. false = קרדיטים או מפתח. */
        val agentOnline: Boolean,
    ) : PollResult

    data object InvalidSession : PollResult
}
