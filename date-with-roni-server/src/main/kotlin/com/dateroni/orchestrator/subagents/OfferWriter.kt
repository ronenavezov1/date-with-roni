package com.dateroni.orchestrator.subagents

import com.dateroni.orchestrator.OrchestratorContext
import com.dateroni.objects.Speaker
import com.dateroni.orchestrator.llm.ModelTier

// ============================================================
//  מי שמציע — וואטסאפ או דייט.
//
//  ההחלטה **מתי** להציע היא לא שלו: היא נגזרת מאחוז ההתאמה
//  בקוטלין, ומה-DB שזוכר מה כבר הוצע. תפקידו היחיד הוא לנסח.
//
//  החלוקה הזאת מכוונת. אם המתכנן היה מחליט מתי להציע, הוא היה
//  מציע שוב ושוב לפי מצב הרוח של המודל — וזה בדיוק מה שגורם
//  להצעה להישמע נואשת. ככה היא נאמרת פעם אחת, ברגע שנקבע
//  מראש, ובניסוח שנכתב לרגע הזה.
// ============================================================

enum class Offer { WHATSAPP, DATE }

private fun prompt(offer: Offer): String = """
תפקידך: לנסח **הודעה אחת** של ג׳ימיקלוד, הסוכן של רוני, לאישה
שהוא מדבר איתה. השיחה הולכת טוב, וזה הרגע להציע לה ${
    if (offer == Offer.WHATSAPP) "לעבור לוואטסאפ עם רוני"
    else "לקבוע דייט עם רוני"
}.

## איך זה צריך להישמע
- **כייפי וזורם.** כאילו זה עלה לך עכשיו בראש, לא כאילו חיכית לזה
- שנון. משפט אחד טוב עדיף על שלושה נחמדים
- קצר: שורה, שתיים לכל היותר
- הצעה, לא בקשה. היא יכולה לומר לא וזה בסדר גמור

## אסור
- להתחנן, ללחוץ, או להישמע נואש
- "אם בא לך אולי אפשר אולי" — היסוס כפול נשמע גרוע יותר מישירות
- לנמק למה היא מתאימה, ובטח לא להזכיר ציונים, אחוזים או ניתוח
- לפתוח ב"אז" או ב"האמת ש"
- לסיים בשאלה מנומסת כמו "מה דעתך?" — תן לזה לעמוד

${
    if (offer == Offer.WHATSAPP)
        "אתה מציע להעביר לה את הוואטסאפ שלו כדי שידברו ישירות. אל " +
            "תשלח לינק ואל תמציא מספר — רק תציע."
    else
        "אתה מציע להיפגש. אל תקבע תאריך, שעה או מקום — רק תזרוק את הרעיון."
}

ענה בהודעה עצמה בלבד. בלי הקדמה ובלי מרכאות.
"""

class OfferWriter(private val ctx: OrchestratorContext) {

    /** מנסח את ההצעה, או null אם לא יצא משהו שראוי לשלוח. */
    suspend fun write(offer: Offer): String? {
        val recent = ctx.turns().takeLast(8).joinToString("\n") {
            val who = if (it.speaker == Speaker.HER) "היא" else "אתה"
            "$who: ${it.text}"
        }

        val input = buildString {
            ctx.herName?.let { appendLine("קוראים לה $it.") }
            appendLine("סוף השיחה עד עכשיו:")
            append(recent)
        }

        return ctx.ask(
            id = "offer-writer",
            prompt = prompt(offer),
            input = input,
            tier = ModelTier.SMART,
            maxDepth = 1,
            timeoutMs = 30_000L,
            fallback = "",
        ).takeIf { it.isNotBlank() }
    }
}
