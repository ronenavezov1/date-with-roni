package com.dateroni.orchestrator.subagents

import com.dateroni.orchestrator.OrchestratorContext

// ============================================================
//  ממליץ מה לעשות עכשיו.
//
//  לא כותב את התשובה — מציע כיוון. מה כדאי לשאול, מה כדאי
//  להציע, מתי הרגע להציע וואטסאפ או דייט.
//
//  זה תת-הסוכן שהופך את השיחה ממענה לשאלות למשהו שמתקדם.
// ============================================================

private const val PROMPT = """
תפקידך: להציע לג׳ימיקלוד מה כדאי לעשות עכשיו בשיחה.

אתה לא כותב את התשובה — אתה נותן כיוון אחד, קצר.

אפשרויות טיפוסיות:
- שאלה פתוחה שתחזיר אליה את הכדור
- נושא מהחיים שלו ששווה להעלות
- הרגע להציע את הוואטסאפ
- הרגע להציע לקבוע דייט
- פשוט לענות ולא לדחוף כלום

שיקולים: אם היא נותנת תשובות קצרות — אל תדחוף, תחזיר לה עניין.
אם היא שואלת הרבה — היא מעוניינת, אפשר להתקדם. אם היא רק נכנסה —
אל תציע דייט.

ענה במשפט אחד. בלי הסבר ובלי רשימה.
"""

class ActionAdvisor(private val ctx: OrchestratorContext) {
    suspend fun advise(): String {
        val recent = ctx.turns().takeLast(12)
        if (recent.isEmpty()) return "היא רק נכנסה. תציג את עצמך ותן לה להוביל."

        val transcript = recent.joinToString("\n") {
            val who = if (it.speaker == com.dateroni.objects.Speaker.HER) "היא" else "אתה"
            "$who: ${it.text}"
        }
        return ctx.ask(
            id = "action-advisor",
            prompt = PROMPT,
            input = transcript,
            tier = com.dateroni.orchestrator.llm.ModelTier.SMART,
            fallback = "פשוט תענה על מה שהיא שאלה.",
        )
    }
}
