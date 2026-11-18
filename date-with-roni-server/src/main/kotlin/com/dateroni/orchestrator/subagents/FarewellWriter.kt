package com.dateroni.orchestrator.subagents

import com.dateroni.objects.Speaker
import com.dateroni.orchestrator.OrchestratorContext

// ============================================================
//  כותב הפרידה.
//
//  כשהבחורה נפסלת (-1), הסוכן שולח הודעת פרידה קצרה אחת —
//  מתאימה לקונטקסט השיחה — ואז מתנתק לנצח. זה המשפט האחרון
//  שהיא מקבלת ממנו.
// ============================================================

private const val PROMPT = """
זו ההודעה האחרונה שאתה שולח לה — פרידה. משהו נגמר: או שהיא
התעללה, או שהתברר שאין התאמה. אתה מתנתק אחריה ולא עונה יותר.

כתוב פרידה **אחת קצרה** (שורה, אולי שתיים), בקול שלך, מתאימה
לקונטקסט השיחה. יבש, מכובד, בלי דרמה ובלי הטפות. אתה סוגר את
הדלת בשקט — לא מסביר, לא מתנצל, ולא מזמין לחזור.

רק ההודעה עצמה, בלי הסבר.
"""

class FarewellWriter(private val ctx: OrchestratorContext) {

    suspend fun write(): String {
        val recent = ctx.turns().takeLast(8)
        val transcript = recent.joinToString("\n") {
            val who = if (it.speaker == Speaker.HER) "היא" else "אתה"
            "$who: ${it.text}"
        }
        return ctx.ask(
            id = "farewell-writer",
            prompt = PROMPT,
            input = transcript.ifBlank { "(כמעט ולא הייתה שיחה)" },
            fallback = "נראה לי שזה לא הולך לעבוד. שיהיה בהצלחה.",
        )
    }
}
