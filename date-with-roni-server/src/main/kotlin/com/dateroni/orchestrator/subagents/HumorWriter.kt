package com.dateroni.orchestrator.subagents

import com.dateroni.orchestrator.OrchestratorContext
import com.dateroni.orchestrator.roni.RoniFacts

// ============================================================
//  מציע תגובה הומוריסטית בסגנון של רוני.
//
//  ההומור לא מומצא מאפס — הוא מחקה דוגמאות שרוני נתן. בלי
//  דוגמאות, תת-הסוכן אומר במפורש שאין לו על מה להתבסס, במקום
//  להמציא סגנון ולטעון שהוא של רוני.
// ============================================================

private fun prompt(): String = buildString {
    appendLine("תפקידך: להציע תגובה קצרה ומצחיקה, בסגנון של רוני.")
    appendLine()

    if (RoniFacts.humorStyle.isNullOrBlank() && RoniFacts.humorExamples.isEmpty()) {
        appendLine("**אין לך דוגמאות לסגנון שלו.** ענה בדיוק:")
        appendLine("אין לי דוגמאות לסגנון ההומור של רוני — עדיף לא לחקות.")
        return@buildString
    }

    RoniFacts.humorStyle?.takeIf { it.isNotBlank() }?.let {
        appendLine("## הסגנון שלו"); appendLine(it); appendLine()
    }
    if (RoniFacts.humorExamples.isNotEmpty()) {
        appendLine("## דברים שהוא באמת אמר")
        RoniFacts.humorExamples.forEach { appendLine("- $it") }
        appendLine()
    }
    appendLine("""
        החזר משפט אחד או שניים, לא יותר. בלי הקדמה ובלי הסבר —
        רק התגובה עצמה.

        אם שום דבר מצחיק לא מתאים לרגע הזה, ענה:
        אין כאן מקום לבדיחה.

        זה חשוב: בדיחה שנדחפת בכוח גרועה מאין בדיחה.
    """.trimIndent())
}

class HumorWriter(private val ctx: OrchestratorContext) {
    suspend fun suggest(situation: String): String = ctx.ask(
        id = "humor-writer",
        prompt = prompt(),
        input = situation,
        tier = com.dateroni.orchestrator.llm.ModelTier.SMART,
        fallback = "אין כאן מקום לבדיחה.",
    )
}
