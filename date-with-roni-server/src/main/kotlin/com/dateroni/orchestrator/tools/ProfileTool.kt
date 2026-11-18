package com.dateroni.orchestrator.tools

import com.dateroni.orchestrator.OrchestratorContext
import com.dateroni.orchestrator.agent.Tool
import com.dateroni.orchestrator.subagents.CuriosityAdvisor

// ============================================================
//  מה לשאול כדי להכיר אותה.
//
//  Tool (קריאה): שואל את סוכן ההיכרות מה כדאי לגלות עכשיו ואיך,
//  והמתכנן מחליט אם ואיך לשזור את זה. הוא ידע גם לומר "לא עכשיו".
//
//  (החילוץ של הנתונים עצמם והניתוח קורים אוטומטית בקוטלין אחרי
//  כל הודעה — לא ככלי כאן.)
// ============================================================

class WhatToAskTool(private val ctx: OrchestratorContext) : Tool {

    override val name = "what_to_ask"

    override val description =
        "מציע מה הכי שווה לגלות עליה עכשיו ואיך לשאול בקלילות — לפי מה " +
            "שחשוב לרוני ומה שעדיין לא ידוע. השתמש כשהשיחה זורמת ורוצים " +
            "להכיר אותה. הוא ידע גם לומר מתי לא הרגע לשאול — אז אל תדחוף."

    override suspend fun execute(args: Map<String, Any>): String = CuriosityAdvisor(ctx).suggest()
}
