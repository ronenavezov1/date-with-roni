package com.dateroni.orchestrator.actions

import com.dateroni.orchestrator.OrchestratorContext
import com.dateroni.orchestrator.agent.Action

// ============================================================
//  שליחת הודעה ביניים.
//
//  Action ולא Tool, כי זה **מעשה**: אחרי שהיא נשלחה אי אפשר
//  לבטל אותה, ולהפעיל פעמיים זה לשלוח פעמיים. בדיוק ההבדל
//  שדיברנו עליו.
//
//  מיועד לרגעים שבהם התשובה תיקח זמן: "רגע, בודק לך" ואז
//  התשובה האמיתית. התשובה הסופית נשלחת אוטומטית בסוף הטיפול —
//  אין צורך לקרוא לכלי הזה בשבילה.
// ============================================================

class SendMessageAction(private val ctx: OrchestratorContext) : Action {

    override val name = "send_message"

    override val description =
        "שולח לה הודעה מיד, לפני שסיימת לחשוב. השתמש רק כשברור לך " +
            "שהתשובה המלאה תיקח זמן, כדי שהיא לא תמתין בשקט. " +
            "אל תשתמש בזה לתשובה הסופית — היא נשלחת לבד."

    override val parameters = mapOf(
        "text" to "ההודעה שתישלח אליה עכשיו. משפט קצר.",
    )

    override suspend fun perform(agentId: String, args: Map<String, Any>): String {
        val text = args["text"]?.toString()?.trim().orEmpty()
        if (text.isBlank()) return "לא שלחתי — ההודעה הייתה ריקה."

        ctx.sendMessage(text)
        return "נשלח אליה. אפשר להמשיך לעבוד על התשובה המלאה."
    }
}
