package com.dateroni.objects

// ============================================================
//  הפרופיל המובנה של הבחורה — מה שלמדנו עליה, בשדות.
//
//  RAM בלבד, כמו כל מה ש-Girl מחזיקה. סוכן החילוץ
//  (ProfileExtractor) ממלא אותו כל הודעה, וסוכן הניתוח משקלל
//  אותו מול הדרישות של רוני. רק ה-match_percent המזוקק שורד
//  ל-DB — הפרופיל עצמו נדיף, בהתאם למדיניות הפרטיות.
// ============================================================

class HerProfile {

    var age: Int? = null
    var location: String? = null            // איפה היא גרה
    var occupation: String? = null          // עבודה / לימודים
    var personality: String? = null         // הוייב הכללי שלה
    var humor: String? = null               // חוש ההומור שלה
    var lifestyle: String? = null           // שתייה, בילויים, יציאות
    var relationshipGoals: String? = null   // מה היא מחפשת
    var values: String? = null

    /** תחביבים ודברים שהיא אוהבת. */
    val interests: MutableList<String> = mutableListOf()

    /** catch-all לכל עובדה שלא נכנסת לשדה אחר. */
    val notes: MutableList<String> = mutableListOf()

    val isEmpty: Boolean
        get() = age == null && location == null && occupation == null &&
            personality == null && humor == null && lifestyle == null &&
            relationshipGoals == null && values == null &&
            interests.isEmpty() && notes.isEmpty()

    /** תצוגה טקסטואלית — רק שדות מלאים. מוזן לפרומפט של המנתח. */
    fun render(): String = buildString {
        age?.let { appendLine("גיל: $it") }
        location?.let { appendLine("מקום: $it") }
        occupation?.let { appendLine("עיסוק: $it") }
        personality?.let { appendLine("אישיות: $it") }
        humor?.let { appendLine("הומור: $it") }
        lifestyle?.let { appendLine("אורח חיים: $it") }
        relationshipGoals?.let { appendLine("מה מחפשת: $it") }
        values?.let { appendLine("ערכים: $it") }
        if (interests.isNotEmpty()) appendLine("תחומי עניין: ${interests.joinToString(", ")}")
        if (notes.isNotEmpty()) appendLine("עוד: ${notes.joinToString("; ")}")
    }.trim()

    /** מוסיף פריט לרשימה בלי כפילות ועם תקרה. מחזיר האם באמת נוסף. */
    private fun addTo(list: MutableList<String>, item: String, max: Int = 30): Boolean {
        val v = item.trim()
        if (v.isEmpty() || list.any { it.equals(v, ignoreCase = true) }) return false
        list += v
        if (list.size > max) list.subList(0, list.size - max).clear()
        return true
    }

    fun addInterest(item: String) = addTo(interests, item)
    fun addNote(item: String) = addTo(notes, item)

    fun clear() {
        age = null; location = null; occupation = null; personality = null
        humor = null; lifestyle = null; relationshipGoals = null; values = null
        interests.clear(); notes.clear()
    }
}
