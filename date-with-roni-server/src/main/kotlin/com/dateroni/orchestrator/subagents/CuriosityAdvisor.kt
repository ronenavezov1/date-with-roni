package com.dateroni.orchestrator.subagents

import com.dateroni.objects.Speaker
import com.dateroni.orchestrator.OrchestratorContext
import com.dateroni.orchestrator.roni.RoniRequirements

// ============================================================
//  סוכן ההיכרות.
//
//  כל עולמו: מה הכי שווה לגלות עליה עכשיו, ואיך לשאול בקלילות —
//  או מתי דווקא לא לשאול. הוא בוחר לפי מה שחשוב לרוני (הדרישות)
//  ומה שעדיין לא ידוע עליה, ושואב טון משאלות-הדוגמה של רוני.
//
//  שני כללים שהם כל האופי שלו:
//    • קליל והומוריסטי, כמו וואטסאפ. שאלה אחת, בטבעיות.
//    • לא מעיק. אם היא לא ענתה או התחמקה — לא שואלים שוב ולא
//      דוחפים. משחררים, זורמים, ולפעמים פשוט לא שואלים כלום.
//
//  Tool קריאה בלבד: הוא מייעץ, המתכנן מחליט אם ואיך לשאול.
// ============================================================

private val PROMPT = """
תפקידך היחיד: להחליט מה הכי שווה לגלות עליה עכשיו, ולנסח דרך
קלילה לשאול — או לומר שעכשיו לא הרגע.

האופי שלך:
- קליל והומוריסטי, בגובה העיניים. שאלה אחת, בטבעיות, כמו
  בוואטסאפ. לא שאלון, לא ראיון.
- **לא מעיק.** אם בתורות האחרונים היא לא ענתה על שאלה,
  התחמקה, או קיצרה — אל תחזור על השאלה ואל תדחוף. שחרר.
  לפעמים התשובה הנכונה היא לא לשאול כלום עכשיו.
- בחר מה לגלות לפי מה שחשוב לרוני ומה שעדיין לא ידוע עליה.
  אל תשאל על מה שכבר יודעים.

ענה בשורה אחת, באחד משני הפורמטים:
  ASK · <שאלה קלילה לשזור בשיחה>
  HOLD · <סיבה קצרה למה עכשיו לא הרגע>

בלי הסבר נוסף, בלי מירכאות. שורה אחת.
"""

class CuriosityAdvisor(private val ctx: OrchestratorContext) {

    suspend fun suggest(): String {
        val known = ctx.profile().render()
        val recent = ctx.turns().takeLast(8)

        val input = buildString {
            appendLine("## מה שחשוב לרוני")
            appendLine(
                if (RoniRequirements.isEmpty) "(עוד לא הוגדר — אז פשוט היכרות טבעית)"
                else RoniRequirements.text
            )
            if (RoniRequirements.exampleQuestions.isNotEmpty()) {
                appendLine()
                appendLine("## שאלות לדוגמה של רוני (לרוח ולטון, לא להעתקה)")
                RoniRequirements.exampleQuestions.forEach { appendLine("- $it") }
            }
            appendLine()
            appendLine("## מה שכבר ידוע עליה")
            appendLine(known.ifBlank { "(עדיין כלום)" })
            appendLine()
            appendLine("## מהלך השיחה האחרון")
            append(
                if (recent.isEmpty()) "(רק התחלנו)"
                else recent.joinToString("\n") {
                    val who = if (it.speaker == Speaker.HER) "היא" else "אתה"
                    "$who: ${it.text}"
                }
            )
        }

        return ctx.ask(
            id = "curiosity-advisor",
            prompt = PROMPT,
            input = input,
            fallback = "HOLD · פשוט תזרום, אין צורך לשאול עכשיו.",
        )
    }
}
