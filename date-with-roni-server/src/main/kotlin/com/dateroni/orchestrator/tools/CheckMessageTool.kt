package com.dateroni.orchestrator.tools

import com.dateroni.orchestrator.OrchestratorContext
import com.dateroni.orchestrator.agent.Tool
import com.dateroni.orchestrator.subagents.SpamDetector
import com.dateroni.orchestrator.subagents.Verdict

// ============================================================
//  הפעלת מסנן הג׳יבריש, כ-Tool.
//
//  למה Tool ולא Action: תת-הסוכן רק שופט. הוא לא כותב ל-DB, לא
//  שולח מייל, ולא משנה כלום. להריץ אותו פעמיים מחזיר את אותה
//  תשובה ולא קורה שום דבר בעולם.
//
//  ככה תת-סוכנים נחשפים לסוכן הראשי: כיכולת שהוא בוחר אם
//  להפעיל, בדיוק כמו כל כלי אחר.
// ============================================================

class CheckMessageTool(private val ctx: OrchestratorContext) : Tool {

    override val name = "check_message"

    override val description =
        "בודק אם ההודעה שקיבלת היא טקסט אנושי קריא או רעש. " +
            "השתמש כשההודעה נראית לך חסרת פשר, אקראית, או כמו ניסיון להטעות."

    override val parameters = mapOf(
        "text" to "ההודעה שצריך לבדוק, כפי שהיא",
    )

    override suspend fun execute(args: Map<String, Any>): String {
        val text = args["text"]?.toString().orEmpty()
        if (text.isBlank()) return "לא קיבלתי טקסט לבדוק."

        return when (SpamDetector(ctx).check(text)) {
            Verdict.REAL -> "ההודעה נראית אנושית. אפשר להמשיך כרגיל."
            Verdict.JUNK -> "ההודעה נראית כמו רעש. אל תנסה לפרש אותה — " +
                "בקש ממנה בעדינות לנסח מחדש."
        }
    }
}
