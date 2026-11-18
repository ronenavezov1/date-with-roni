package com.dateroni.orchestrator.subagents

import com.dateroni.objects.HerProfile
import com.dateroni.objects.Speaker
import com.dateroni.orchestrator.OrchestratorContext

// ============================================================
//  חולץ הפרופיל.
//
//  רץ בקוטלין אחרי כל הודעה — לא ככלי של המתכנן: קורא את השיחה
//  ומחלץ נתונים מובנים על הבחורה לתוך HerProfile. מחזיר האם
//  נכנס מידע חדש, ואם כן הקורא מפעיל ניתוח התאמה מחדש.
//
//  הכתיבה היחידה היא ל-profile שב-RAM — שום דבר לא יוצא לעולם.
// ============================================================

private const val PROMPT = """
תפקידך: לחלץ עובדות על הבחורה מהשיחה, לתוך שדות.

פלט — רק שדות שאתה יכול למלא ממה ש**היא** אמרה, שורה לכל אחד:
key: value

המפתחות (באנגלית), הערכים בעברית:
  age, location, occupation, personality, humor, lifestyle,
  relationship_goals, values, interests, notes

- interests ו-notes: אפשר כמה, מופרדים בפסיקים.
- personality / humor / lifestyle / values: משפט קצר.
- אל תמציא. רק מה שעולה מהשיחה בבירור. בספק — אל תכתוב שורה.
- אם אין שום דבר חדש עליה — כתוב NONE ותו לא.
"""

class ProfileExtractor(private val ctx: OrchestratorContext) {

    /** מחזיר true אם נכנס מידע חדש לפרופיל. */
    suspend fun extract(): Boolean {
        val recent = ctx.turns().takeLast(12)
        if (recent.none { it.speaker == Speaker.HER }) return false

        val profile = ctx.profile()
        val transcript = recent.joinToString("\n") {
            val who = if (it.speaker == Speaker.HER) "היא" else "אתה"
            "$who: ${it.text}"
        }
        val input = buildString {
            appendLine("## מה שכבר ידוע עליה")
            appendLine(profile.render().ifBlank { "(עדיין כלום)" })
            appendLine()
            appendLine("## השיחה")
            append(transcript)
        }

        val answer = ctx.ask(
            id = "profile-extractor",
            prompt = PROMPT,
            input = input,
            fallback = "NONE",
        )

        return apply(profile, answer)
    }

    /** ממזג את פלט החילוץ לפרופיל. מחזיר האם משהו השתנה. */
    private fun apply(profile: HerProfile, answer: String): Boolean {
        if (answer.trim().equals("NONE", ignoreCase = true)) return false
        var changed = false

        for (line in answer.lines()) {
            val idx = line.indexOf(':')
            if (idx <= 0) continue
            val key = line.substring(0, idx).trim().lowercase()
            val value = line.substring(idx + 1).trim()
            if (value.isEmpty() || value.equals("NONE", ignoreCase = true)) continue

            fun scalar(current: String?, set: (String) -> Unit): Boolean {
                if (value == current) return false
                set(value); return true
            }

            val lineChanged = when (key) {
                "age" -> value.filter { it.isDigit() }.toIntOrNull()?.let {
                    if (profile.age != it) { profile.age = it; true } else false
                } ?: false
                "location" -> scalar(profile.location) { profile.location = it }
                "occupation" -> scalar(profile.occupation) { profile.occupation = it }
                "personality" -> scalar(profile.personality) { profile.personality = it }
                "humor" -> scalar(profile.humor) { profile.humor = it }
                "lifestyle" -> scalar(profile.lifestyle) { profile.lifestyle = it }
                "relationship_goals", "relationshipgoals", "goals" ->
                    scalar(profile.relationshipGoals) { profile.relationshipGoals = it }
                "values" -> scalar(profile.values) { profile.values = it }
                "interests" -> value.split(",", ";").fold(false) { acc, i -> profile.addInterest(i) || acc }
                "notes" -> value.split(",", ";").fold(false) { acc, i -> profile.addNote(i) || acc }
                else -> false
            }
            changed = changed || lineChanged
        }
        return changed
    }
}
