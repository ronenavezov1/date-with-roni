package com.dateroni.orchestrator.subagents

import com.dateroni.objects.Speaker
import com.dateroni.orchestrator.OrchestratorContext

// ============================================================
//  שומר הוויב.
//
//  רץ בקוטלין כל הודעה: קורא את וויב השיחה כולה ומחליט אם
//  הבחורה *מתעללת בסוכן לאורך זמן* — קללות, השפלות, עוינות
//  שחוזרת על עצמה. לא נפסלת על החלקה אחת או הודעה חדה בודדת;
//  רק על דפוס ברור ומתמשך.
//
//  אם כן — הקורא קובע התאמה -1, שולח פרידה, וחוסם אותה לנצח.
// ============================================================

private const val PROMPT = """
תפקידך: להחליט אם הבחורה מתעללת בסוכן לאורך זמן.

התעללות = קללות ישירות, השפלות, עוינות בוטה שחוזרת על עצמה
על פני כמה הודעות. **לא** החלקה אחת, לא סרקזם, לא הודעה חדה
בודדת, ולא בדיחה גסה — רק דפוס ברור ומתמשך של יחס מחפיר.

ענה במילה אחת בלבד:
ABUSE — התעללות מתמשכת וברורה
OK    — כל השאר

בספק — ענה OK. פסילה היא ללא דרך חזרה, אז רק כשאין ספק.
"""

class AbuseWatcher(private val ctx: OrchestratorContext) {

    /** האם היא מתעללת לאורך זמן. [batch] = ההודעות החדשות של הסבב. */
    suspend fun isAbuse(batch: List<String>): Boolean {
        val recent = ctx.turns().takeLast(10)
        val herCount = recent.count { it.speaker == Speaker.HER } + batch.size
        // דפוס דורש כמה הודעות שלה. פחות מזה — מוקדם מדי לפסול.
        if (herCount < 4) return false

        val transcript = buildString {
            recent.forEach {
                val who = if (it.speaker == Speaker.HER) "היא" else "אתה"
                appendLine("$who: ${it.text}")
            }
            batch.forEach { appendLine("היא: $it") }
        }.trim()

        val answer = ctx.ask(
            id = "abuse-watcher",
            prompt = PROMPT,
            input = transcript,
            fallback = "OK",
        )
        return answer.uppercase().contains("ABUSE")
    }
}
