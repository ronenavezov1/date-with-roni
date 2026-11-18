package com.dateroni.orchestrator.tools

import com.dateroni.orchestrator.OrchestratorContext
import com.dateroni.orchestrator.agent.Tool
import com.dateroni.orchestrator.subagents.DateNegotiator

// ============================================================
//  צפייה בדייט, ובדיקה אם אפשר לקבוע. שניהם קריאה בלבד.
// ============================================================

class CurrentDateTool(private val ctx: OrchestratorContext) : Tool {

    override val name = "current_date_plan"

    override val description =
        "מציג את הדייט שנקבע — מתי, איפה, ומתי היא ביקשה. השתמש כשהיא " +
            "שואלת מה סוכם, מתי זה, או לפני שאתה מציע לקבוע — כדי לא " +
            "להציע משהו שכבר קיים."

    override suspend fun execute(args: Map<String, Any>): String {
        val plan = ctx.currentDate()
        if (plan?.scheduled != true) return "אין דייט קבוע."

        return buildString {
            append("יש דייט קבוע. ")
            plan.whenText?.let { append("מתי: $it. ") }
            plan.place?.let { append("איפה: $it. ") }
            append("מסור לה את זה כמו שהוא — אלה המילים שלה.")
        }
    }
}

class DateReadinessTool(private val ctx: OrchestratorContext) : Tool {

    override val name = "can_we_schedule"

    override val description =
        "בודק אם יש בשיחה מספיק כדי לקבוע דייט, ומחלץ מתי ואיפה. " +
            "השתמש לפני schedule_date. מחזיר READY עם הפרטים, או MISSING " +
            "עם מה שחסר."

    override suspend fun execute(args: Map<String, Any>): String =
        DateNegotiator(ctx).assess()
}
