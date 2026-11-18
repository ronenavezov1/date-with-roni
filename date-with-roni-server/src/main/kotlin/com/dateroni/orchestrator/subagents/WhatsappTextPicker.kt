package com.dateroni.orchestrator.subagents

import com.dateroni.orchestrator.OrchestratorContext
import com.dateroni.orchestrator.roni.RoniFacts
import com.dateroni.orchestrator.tools.WhatsappTextsTool
import com.dateroni.objects.Speaker

// ============================================================
//  מחבר את ההודעה שתחכה לה בשדה הוואטסאפ.
//
//  הוא כותב אותה, לא בוחר מרשימה — כדי שהיא תתאים למה שבאמת
//  קרה בשיחה. אם דיברתם על האופנוע, זה יופיע שם.
//
//  הרשימה ב-RoniFacts.whatsappTexts היא ההשראה והרשת: היא
//  מראה לו את הטון, והיא מה שנשלח אם הוא נכשל או חורג.
//
//  ★ המגבלות נאכפות בקוטלין, לא בפרומפט ★
//  זו ההודעה הראשונה שרוני יקבל ממנה. מודל שמחליט לכתוב שם
//  משפט מוזר, קישור, או פסקה שלמה — פשוט לא יצליח.
// ============================================================

/** תקרת אורך. מעבר לזה זו כבר לא פתיחה. */
private const val MAX_CHARS = 70

private val PROMPT = """
תפקידך: לחבר את ההודעה שתחכה לה מוכנה בשדה הוואטסאפ, כשהיא
תלחץ על הקישור לרוני.

זו ההודעה שהיא תשלח לו — כתובה בגוף ראשון, מנקודת המבט שלה.

כללים:
- משפט אחד קצר. עד $MAX_CHARS תווים.
- טבעי, כמו שהיא הייתה כותבת בעצמה. לא רשמי ולא מתחנף.
- אם עלה בשיחה משהו ספציפי — אופנוע, הכלב, המילואים — אפשר
  לגעת בו במילה. זה מה שהופך את זה לאישי.
- בלי קישורים, בלי אימוג׳ים, בלי מירכאות.

הפעל את whatsapp_text_options כדי לראות את הטון שרוני אוהב.

ענה בטקסט ההודעה בלבד. בלי הקדמה, בלי הסבר, בלי מירכאות.
""".trimIndent()

class WhatsappTextPicker(private val ctx: OrchestratorContext) {

    suspend fun pick(conversationHint: String): String {
        val fallback = RoniFacts.whatsappTexts.firstOrNull() ?: return ""

        val recent = ctx.turns().takeLast(8).joinToString("\n") {
            val who = if (it.speaker == Speaker.HER) "היא" else "ג׳ימיקלוד"
            "$who: ${it.text}"
        }

        val raw = ctx.ask(
            id = "whatsapp-text-writer",
            prompt = PROMPT,
            input = "השיחה:\n$recent\n\nהערה מהמתכנן: $conversationHint",
            tools = listOf(WhatsappTextsTool()),
            maxDepth = 3,
            fallback = fallback,
        )

        return sanitize(raw) ?: fallback
    }

    /** null = לא עבר. אז נשלח מה שרוני אישר מראש. */
    private fun sanitize(text: String): String? {
        val clean = text
            .trim()
            .trim('"', '\'', '״', '”', '“')
            .replace(Regex("\\s+"), " ")

        if (clean.isBlank()) return null
        if (clean.length > MAX_CHARS) return null
        if (clean.contains("http", ignoreCase = true)) return null
        if (clean.contains("\n")) return null
        // תשובה שהיא בעצם הסבר ולא הודעה.
        if (clean.startsWith("ההודעה") || clean.startsWith("הנה")) return null

        return clean
    }
}
