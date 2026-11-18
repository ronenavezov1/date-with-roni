package com.dateroni.orchestrator.subagents

import com.dateroni.orchestrator.OrchestratorContext

// ============================================================
//  קורא את מצב הרוח של השיחה.
//
//  לא מה נאמר — איך זה מרגיש. זורמת? נעצרת? היא משועממת,
//  זהירה, משחקית? המתכנן משתמש בזה כדי לכוון טון, לא תוכן.
// ============================================================

private const val PROMPT = """
תפקידך: לקרוא את מצב הרוח של השיחה ולהמליץ על טון.

ענה בשורה אחת קצרה, בפורמט:
<מצב> · <המלצה>

מצבים אפשריים: זורמת · מנומסת · נעצרת · משועממת · זהירה · משחקית · מתוחה

דוגמאות:
זורמת · אפשר להעז יותר, היא משחקת איתך
נעצרת · תשובות של מילה. שאל שאלה פתוחה במקום להצהיר
זהירה · פחות הומור, יותר כנות. היא בודקת אם אתה אמיתי
משחקית · תן בדיחה, היא תקבל אותה

בלי הסבר, בלי מירכאות. שורה אחת.
"""

class MoodReader(private val ctx: OrchestratorContext) {
    suspend fun read(): String {
        val recent = ctx.turns().takeLast(10)
        if (recent.size < 2) return "התחלה · עדיין אין ממה לקרוא. תהיה טבעי."

        val transcript = recent.joinToString("\n") {
            val who = if (it.speaker == com.dateroni.objects.Speaker.HER) "היא" else "אתה"
            "$who: ${it.text}"
        }
        return ctx.ask(
            id = "mood-reader",
            prompt = PROMPT,
            input = transcript,
            fallback = "לא ברור · תהיה טבעי.",
        )
    }
}
