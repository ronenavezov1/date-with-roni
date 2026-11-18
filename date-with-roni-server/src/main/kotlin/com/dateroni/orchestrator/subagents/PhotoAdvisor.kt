package com.dateroni.orchestrator.subagents

import com.dateroni.orchestrator.OrchestratorContext
import com.dateroni.orchestrator.roni.RoniPhotos

// ============================================================
//  מחליט אם לצרף תמונה, ואיזו.
//
//  שתי השאלות — "האם" ו"איזו" — נשאלות יחד ולא בשני תת-סוכנים
//  נפרדים, כי אי אפשר לענות על הראשונה בלי לדעת מה קיים.
//  "האם שווה תמונה עכשיו" תלוי לגמרי בשאלה אם יש תמונה שמתאימה.
//
//  שתי מגבלות קשיחות, ושתיהן בקוטלין ולא בפרומפט:
//    • תמונה שכבר נשלחה לא נשלחת שוב
//    • אינדקס לא תקין מבוטל
//
//  ברירת המחדל היא NONE. עדיף שיחה בלי תמונה מאשר תמונה בכל
//  הודעה — זה הופך אותן מרגע נחמד לרעש.
// ============================================================

private fun prompt(available: String): String = """
תפקידך: להחליט אם מתאים לצרף תמונה לתשובה הנוכחית, ואיזו.

## התמונות הזמינות
$available

## מתי כן
- **היא שאלה על משהו שיש לו תמונה** (הכלב, המילואים, האופנוע, העבודה,
  הקעקוע) — זה המקרה הנפוץ, ובו התשובה היא כן. תמונה עונה על
  "איזה אופנוע?" טוב יותר מכל תיאור
- היא ביקשה תמונה במפורש
- יש רגע הומוריסטי שתמונה תחזק

## מתי לא — וזה הרוב
- השיחה עניינית ותמונה תקטע אותה
- כבר שלחת תמונה לפני הודעה-שתיים
- אין תמונה שבאמת מתאימה למה שנאמר

הסעיף הזה כולו לא חל כשהיא ביקשה תמונה. מי שביקשה — מקבלת.

כשהיא מבקשת "סתם תמונה" בלי הקשר — עדיף אחת מהיומיום.

ענה במספר של התמונה בלבד, או במילה NONE.
בלי הסבר.

NONE שמור למקרה שבאמת אין תמונה שמתאימה למה שנאמר, או ששלחת
אחת ממש עכשיו. אם הנושא על השולחן הוא נושא שיש לו תמונה —
התשובה היא המספר, לא NONE.
"""

class PhotoAdvisor(private val ctx: OrchestratorContext) {

    /** מחזיר אינדקס תמונה מתאימה, או null. */
    suspend fun advise(herMessage: String, asked: Boolean = false): Int? {
        if (RoniPhotos.isEmpty) return null

        val alreadySent = ctx.sentPhotos()
        val available = RoniPhotos.indexExcluding(alreadySent)
        // כולן נשלחו כבר — אין מה להציע.
        if (available.isBlank()) return null

        val recent = ctx.turns().takeLast(6).joinToString("\n") {
            val who = if (it.speaker == com.dateroni.objects.Speaker.HER) "היא" else "אתה"
            "$who: ${it.text}"
        }

        val answer = ctx.ask(
            id = "photo-advisor",
            prompt = prompt(available),
            input = buildString {
                if (asked) {
                    appendLine("[היא ביקשה תמונה במפורש. NONE הוא לא מענה — בחר את המתאימה ביותר.]")
                }
                appendLine("ההודעה האחרונה שלה: $herMessage")
                appendLine()
                appendLine("השיחה:")
                append(recent)
            },
            fallback = "NONE",
        )

        val index = answer.filter { it.isDigit() }.toIntOrNull() ?: return null

        // הכללים נאכפים כאן, לא בפרומפט. מודל שיחליט לשלוח שוב
        // את אותה תמונה פשוט לא יצליח.
        if (index in alreadySent) return null
        if (!RoniPhotos.isValidIndex(index)) return null
        return index
    }
}
