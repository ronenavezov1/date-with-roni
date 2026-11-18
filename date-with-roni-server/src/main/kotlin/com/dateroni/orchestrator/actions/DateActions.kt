package com.dateroni.orchestrator.actions

import com.dateroni.orchestrator.OrchestratorContext
import com.dateroni.orchestrator.agent.Action
import java.time.Instant

// ============================================================
//  קביעת דייט וביטולו.
//
//  Action ולא Tool: שניהם כותבים ל-DB, ושניהם דברים שרוני
//  באמת רואה. להפעיל פעמיים זה לשנות פעמיים.
//
//  ★ כלל הברזל יושב כאן, בקוטלין ★
//  אי אפשר לקבוע דייט בלי מתי **וגם** איפה. אילו זה היה
//  בפרומפט, מודל משוכנע היה קובע דייט "מתישהו" — ורוני היה
//  מקבל שורה בטבלה שלא אומרת כלום.
// ============================================================

class ScheduleDateAction(private val ctx: OrchestratorContext) : Action {

    override val name = "schedule_date"

    override val description =
        "קובע דייט עם רוני. הפעל רק אחרי שיש לך **גם מתי וגם איפה**, " +
            "ורק אחרי שהיא אישרה שזה מה שהיא רוצה. אם חסר משהו — " +
            "שאל אותה, אל תמציא."

    override val parameters = mapOf(
        "when_text" to "מתי, כמו שהיא ניסחה. למשל: יום שלישי בערב",
        "place" to "איפה סוכם. למשל: המרינה בהרצליה",
        "when_iso" to "אופציונלי. תאריך ושעה מדויקים בפורמט ISO, אם ברור מהשיחה",
    )

    override suspend fun perform(agentId: String, args: Map<String, Any>): String {
        val whenText = args["when_text"]?.toString()?.trim().orEmpty()
        val place = args["place"]?.toString()?.trim().orEmpty()

        // כלל הברזל. לא בקשה — בדיקה.
        if (whenText.isBlank()) return "לא נקבע: חסר מתי. שאל אותה מתי נוח לה."
        if (place.isBlank()) return "לא נקבע: חסר איפה. הצע מקום או שאל אותה."

        // פענוח מיטבי. כישלון הוא לא בעיה — whenText הוא מקור האמת.
        val at = args["when_iso"]?.toString()?.takeIf { it.isNotBlank() }
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }

        ctx.scheduleDate(whenText, place, at)

        return "נקבע: $whenText, $place. אשר לה שזה עבר לרוני ושהוא יחזור אליה. " +
            "אפשר להזכיר שהיא יכולה לשנות או לבטל בכל רגע."
    }
}

class CancelDateAction(private val ctx: OrchestratorContext) : Action {

    override val name = "cancel_date"

    override val description =
        "מבטל את הדייט שנקבע ומנקה את הפרטים. הפעל כשהיא מבקשת לבטל. " +
            "אם היא רק רוצה לשנות זמן או מקום — עדיף schedule_date מחדש."

    override suspend fun perform(agentId: String, args: Map<String, Any>): String {
        val existing = ctx.currentDate()
        if (existing?.scheduled != true) {
            return "אין דייט קבוע לבטל. תגיד לה את זה בעדינות."
        }

        ctx.cancelDate()
        return "הדייט בוטל והפרטים נמחקו. אשר לה בלי לעשות מזה עניין, " +
            "והשאר את הדלת פתוחה אם תרצה לקבוע שוב."
    }
}
