package com.dateroni.orchestrator.tools

import com.dateroni.orchestrator.agent.Tool
import com.dateroni.orchestrator.roni.RoniFacts

// ============================================================
//  הטקסטים האפשריים לוואטסאפ.
//
//  Tool ולא Action: הוא רק מציג רשימה. בוחר-הטקסט (תת-סוכן)
//  משתמש בו כדי לדעת מה עומד לרשותו.
//
//  הרשימה סגורה בכוונה. אילו תת-הסוכן היה כותב טקסט חופשי,
//  ההודעה הראשונה שרוני מקבל בוואטסאפ הייתה מה שמודל המציא
//  באותו רגע. ככה מה שנשלח בשמה תמיד מתוך מה שרוני אישר.
// ============================================================

class WhatsappTextsTool : Tool {

    override val name = "whatsapp_text_options"

    override val description =
        "מציג את הטקסטים שאפשר להכניס מראש להודעת הוואטסאפ שלה לרוני."

    override suspend fun execute(args: Map<String, Any>): String {
        if (RoniFacts.whatsappTexts.isEmpty()) return "אין טקסטים מוגדרים."
        return RoniFacts.whatsappTexts
            .mapIndexed { i, text -> "$i. $text" }
            .joinToString("\n")
    }
}
