package com.dateroni.orchestrator.subagents

import com.dateroni.orchestrator.OrchestratorContext
import com.dateroni.orchestrator.roni.RoniFacts
import com.dateroni.objects.Speaker

// ============================================================
//  בודק אם יש מספיק כדי לקבוע דייט, ומחלץ את הפרטים.
//
//  הוא לא קובע כלום — הוא קורא את השיחה ואומר למתכנן מה יש
//  ומה חסר. הקביעה עצמה היא Action, כי היא כותבת ל-DB.
//
//  ההפרדה חשובה: תת-סוכן שמחלץ יכול לטעות ולהמציא זמן שלא
//  נאמר. לכן הוא מחזיר טקסט, והמתכנן — שרואה את השיחה — מאשר
//  אותו לפני שהוא מעביר לפעולה.
// ============================================================

private fun prompt(): String = buildString {
    appendLine("תפקידך: לבדוק אם יש בשיחה מספיק פרטים כדי לקבוע דייט, ולחלץ אותם.")
    appendLine()
    appendLine("צריך שני דברים: **מתי** ו**איפה**.")
    appendLine()
    RoniFacts.idealFirstDate?.takeIf { it.isNotBlank() }?.let {
        appendLine("דייט ראשון אידיאלי מבחינת רוני: $it")
        appendLine("אפשר להציע את זה כברירת מחדל למקום, אם היא לא ציינה.")
        appendLine()
    }
    appendLine("""
        ענה באחד משני הפורמטים, בשורה אחת:

        READY | מתי: <כמו שהיא אמרה> | איפה: <כמו שסוכם>
        MISSING | <מה חסר, במילה או שתיים>

        חשוב: אל תמציא זמן או מקום שלא נאמרו. אם היא אמרה
        "מתישהו בשבוע הבא" — זה לא זמן, זה MISSING.
    """.trimIndent())
}

class DateNegotiator(private val ctx: OrchestratorContext) {

    suspend fun assess(): String {
        val recent = ctx.turns().takeLast(14)
        if (recent.isEmpty()) return "MISSING | הכל"

        val transcript = recent.joinToString("\n") {
            val who = if (it.speaker == Speaker.HER) "היא" else "ג׳ימיקלוד"
            "$who: ${it.text}"
        }
        return ctx.ask(
            id = "date-negotiator",
            prompt = prompt(),
            input = transcript,
            tier = com.dateroni.orchestrator.llm.ModelTier.SMART,
            maxDepth = 3,
            fallback = "MISSING | לא הצלחתי לקרוא את השיחה",
        )
    }
}
