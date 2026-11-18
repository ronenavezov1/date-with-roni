package com.dateroni.orchestrator.subagents

import com.dateroni.orchestrator.OrchestratorContext
import com.dateroni.orchestrator.llm.ModelTier
import com.dateroni.objects.Speaker
import com.dateroni.objects.Turn

// ============================================================
//  מתמצת את השיחה.
//
//  הבעיה שהוא פותר: ההיסטוריה נשלחת מחדש בכל קריאה של הסוכן,
//  ולכן היא מוכפלת בעומק. שיחה של ארבעים תורות בעומק ארבעה-עשר
//  פירושה שאותן ארבעים תורות עלו על הקו ארבעה-עשר פעמים בסבב
//  אחד. זה מה שאוכל את התקציב, לא התשובות עצמן.
//
//  התמצות מחליף את הגוף הארוך בכמה שורות ומשאיר את התורות
//  האחרונות כלשונן — כי ניואנס של הרגע הנוכחי הולך לאיבוד
//  בתקציר, וניואנס של לפני חצי שעה כבר לא משנה.
//
//  הוא מתומצת שוב על גבי עצמו: הפעם הבאה מקבלת את התקציר הקודם
//  ואת מה שהצטבר מאז, כדי שדברים שנאמרו בהתחלה לא ייפלו.
// ============================================================

private val PROMPT = """
תפקידך: לתמצת שיחה בין ג׳ימיקלוד (סוכן דיגיטלי של רוני) לבין
אישה שנחתה על אתר ההיכרויות שלו.

התקציר נכנס בחזרה לג׳ימיקלוד כזיכרון. הוא צריך שיזכור **אותה**,
לא את עצמו.

## מה חייב להישאר
- מה היא סיפרה על עצמה: שם, גיל, עבודה, עיר, מצב, מה שמעניין אותה
- מה היא שאלה ומה כבר נענה — כדי שלא יחזור על אותה תשובה
- מה היא אהבה ומה פחות, ואיך היא מדברת
- דברים מעשיים: אם נמסר וואטסאפ, אם דובר על דייט, מה סוכם
- על אילו תמונות דובר

## מה לזרוק
- ניסוחים, בדיחות ומחמאות שכבר עשו את שלהן
- חזרות
- כל מה שג׳ימיקלוד אמר על עצמו ואפשר להסיק מחדש

## צורה
נקודות קצרות, עד 200 מילים. עברית. בגוף שלישי ("היא סיפרה ש...").
בלי הקדמה ובלי סיכום — רק הנקודות.
"""

class HistorySummarizer(private val ctx: OrchestratorContext) {

    /**
     * מחזיר תקציר מעודכן, או null אם אין מה לתמצת או שהתמצות
     * נכשל. null פירושו "השאר הכל כמו שהוא" — עדיף היסטוריה
     * ארוכה מהיסטוריה שנמחקה בלי תחליף.
     */
    suspend fun summarize(previous: String?, turns: List<Turn>): String? {
        if (turns.isEmpty()) return null

        val body = turns.joinToString("\n") {
            val who = if (it.speaker == Speaker.HER) "היא" else "ג׳ימיקלוד"
            "$who: ${it.text}"
        }

        val input = buildString {
            if (!previous.isNullOrBlank()) {
                appendLine("## התקציר עד עכשיו")
                appendLine(previous)
                appendLine()
                appendLine("## מה שנאמר מאז")
            } else {
                appendLine("## השיחה")
            }
            append(body)
        }

        return ctx.ask(
            id = "history-summarizer",
            prompt = PROMPT,
            input = input,
            tier = ModelTier.FAST,
            maxDepth = 1,
            timeoutMs = 45_000L,
            fallback = "",
        ).takeIf { it.isNotBlank() }
    }
}
