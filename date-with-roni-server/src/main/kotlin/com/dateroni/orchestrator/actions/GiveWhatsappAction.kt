package com.dateroni.orchestrator.actions

import com.dateroni.Env
import com.dateroni.orchestrator.OrchestratorContext
import com.dateroni.orchestrator.agent.Action
import com.dateroni.orchestrator.subagents.WhatsappTextPicker
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

// ============================================================
//  מסירת הוואטסאפ של רוני.
//
//  Action ולא Tool משתי סיבות, ושתיהן מהותיות:
//
//    1. זה נרשם ב-DB. got_whatsapp הוא אחד משני הדברים שרוני
//       באמת רואה מהשיחה, וזה חייב להיות מדויק.
//    2. אי אפשר לבטל. ברגע שהמספר אצלה, הוא אצלה.
//
//  הטקסט שנכנס מראש להודעה נבחר על ידי תת-סוכן, מתוך רשימה
//  סגורה שרוני אישר — לא נכתב חופשי.
// ============================================================

class GiveWhatsappAction(private val ctx: OrchestratorContext) : Action {

    override val name = "give_whatsapp"

    override val description =
        "מוסר לה את הוואטסאפ של רוני, עם הודעה שכבר כתובה לה בשדה. " +
            "השתמש כשהיא מבקשת לדבר איתו ישירות, שואלת אם יש לו וואטסאפ, " +
            "או שהשיחה הגיעה לנקודה שבה טבעי להציע את זה."

    override val parameters = mapOf(
        "context" to "משפט קצר על איך השיחה הלכה, כדי לבחור הודעה מתאימה",
    )

    override suspend fun perform(agentId: String, args: Map<String, Any>): String {
        val number = Env.get("RONI_WHATSAPP_NUMBER")?.filter { it.isDigit() }
        if (number.isNullOrBlank()) {
            return "לא הוגדר מספר וואטסאפ. תגיד לה שהאפשרות הזו לא זמינה כרגע, " +
                "והצע לה לקבוע דרכי במקום."
        }

        val prefill = WhatsappTextPicker(ctx).pick(args["context"]?.toString().orEmpty())
        val link = buildString {
            append("https://wa.me/").append(number)
            if (prefill.isNotBlank()) {
                append("?text=").append(URLEncoder.encode(prefill, StandardCharsets.UTF_8))
            }
        }

        // רק אחרי שהקישור נבנה בהצלחה. אין טעם לרשום שנמסר משהו
        // שלא נמסר.
        ctx.markWhatsappGiven()

        return "הקישור: $link\n" +
            "הצג לה אותו כקישור לחיץ, עם משפט קצר משלך. " +
            "ההודעה \"$prefill\" כבר תחכה לה בשדה — אפשר לציין את זה."
    }
}
