package com.dateroni.orchestrator.subagents

import com.dateroni.orchestrator.OrchestratorContext
import com.dateroni.orchestrator.roni.RoniRequirements

// ============================================================
//  סוכן ניתוח-ההתאמה.
//
//  כל עולמו: כמה הבחורה מתאימה לרוני, לפי הפרופיל המובנה שלה
//  מול הדרישות של רוני. מחזיר אחוז חדש, -1 לפסילה, או null
//  כשאין על מה לנתח (הדרישות ריקות או הפרופיל ריק).
//
//  קורא ושופט בלבד. הכתיבה ל-DB נעשית בקוטלין (AgentRepository)
//  אחרי חילוץ שהכניס מידע חדש — כאן אין תופעת לוואי.
// ============================================================

private const val PROMPT = """
אתה סוכן ניתוח-התאמה. תפקידך היחיד: לקבוע כמה הבחורה מתאימה
לרוני, לפי האישיות שלה ומה שנלמד עליה — מול הדרישות שלו.

ענה במספר יחיד ותו לא:
  0..100 — אחוז ההתאמה המעודכן
  -1     — פסילה מוחלטת. דיל-ברייקר ברור וחד-משמעי. אין דרך
           חזרה, אז רק כשאתה בטוח לגמרי.

כללים:
- האחוז מתחיל ב-0 ונע לפי מה שמתגלה. עדכן ביחס לאחוז הנוכחי,
  בלי קפיצות קיצוניות בלי בסיס.
- כשאין מספיק מידע — הישאר קרוב לאחוז הנוכחי.
- בלי מילים, בלי הסבר, בלי סימנים. מספר אחד בלבד.
"""

class MatchAnalyzer(private val ctx: OrchestratorContext) {

    /** אחוז חדש 0..100, ‎-1‎ לפסילה, או null אם אין קריטריונים. */
    suspend fun analyze(current: Int): Int? {
        if (RoniRequirements.isEmpty) return null

        val profile = ctx.profile()
        if (profile.isEmpty) return null

        val input = buildString {
            appendLine("## הדרישות של רוני")
            appendLine(RoniRequirements.text)
            appendLine()
            appendLine("## הפרופיל שלה")
            appendLine(profile.render())
            appendLine()
            append("## האחוז הנוכחי: $current")
        }

        val answer = ctx.ask(
            id = "match-analyzer",
            prompt = PROMPT,
            input = input,
            fallback = "",
        )

        // המספר הראשון בתשובה. נכשל בשקט → null, ואז לא נוגעים באחוז.
        val n = Regex("-?\\d+").find(answer)?.value?.toIntOrNull() ?: return null
        return n.coerceIn(-1, 100)
    }
}
