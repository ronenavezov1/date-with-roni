package com.dateroni.orchestrator.tools

import com.dateroni.orchestrator.OrchestratorContext
import com.dateroni.orchestrator.agent.Tool
import com.dateroni.orchestrator.roni.RoniFacts
import com.dateroni.orchestrator.roni.RoniPhotos

// ============================================================
//  הכלים שמנגישים את המידע על רוני.
//
//  כולם Tool ולא Action: הם רק קוראים. אפשר להריץ אותם שוב
//  ושוב בלי שיקרה כלום בעולם.
//
//  ★ שדה ריק ב-RoniFacts מחזיר "רוני לא סיפר לי על זה". ★
//  זה לא כישלון — זו הנקודה. עדיף שיגיד שאינו יודע מאשר
//  שימציא, וזה מה שהופך את "אני לא יודע" לתשובה זמינה ולא
//  למשהו שהוא צריך להמציא בעצמו.
//
//  ★ כלי אחד לכל העובדות ★
//  roni_about מקבל topic ומחזיר את המידע עליו. במקום עשרים
//  כלים נפרדים — תיאור אחד. זה חשוב לעלות: כל קריאה של המתכנן
//  שולחת את תיאורי *כל* הכלים למודל, עד עומק 14 בתור אחד.
//  עשרים כלי-עובדות שם היו מוכפלים בעומק בכל תור.
//
//  היוצאים מן הכלל נשארים נפרדים בכוונה: פוליטיקה, אינטימיות,
//  טרנר-אוף וילדים דורשים ריסון ("רק אם היא שואלת / לפי וייב"),
//  והריסון הזה חייב לשבת בתיאור הכלי — במקום שבו המתכנן מחליט
//  אם בכלל להפעיל, לא בתוכן שחוזר אחרי שכבר הפעיל.
// ============================================================

private const val UNKNOWN = "רוני לא סיפר לי על זה. תגיד לה בכנות שאתה לא יודע, " +
    "ואם זה נראה לך מעניין — הצע לה לשאול אותו ישירות."

private const val PHOTO_HINT =
    "\n\n[יש תמונות של זה. אם זה מתאים לרגע — הפעל attach_photo_if_fits.]"

private fun String?.orNull(): String? = this?.takeIf { it.isNotBlank() }

/** בונה רשימת "תווית: ערך", מדלג על ריקים. */
private fun facts(vararg pairs: Pair<String, Any?>): String =
    pairs.mapNotNull { (label, value) ->
        when (value) {
            null -> null
            is String -> value.takeIf { it.isNotBlank() }?.let { "$label: $it" }
            is Collection<*> -> value.takeIf { it.isNotEmpty() }?.let { "$label: ${it.joinToString(", ")}" }
            is Map<*, *> -> value.takeIf { it.isNotEmpty() }
                ?.entries?.joinToString("\n") { "${it.key}: ${it.value}" }
            else -> "$label: $value"
        }
    }.joinToString("\n")

// ------------------------------------------------------------
//  כל העובדות, בכלי אחד
//
//  הנושאים שיש להם תמונות מקבלים רמז לצרף אותן. הנושאים
//  הרגישים (פוליטיקה, אינטימיות, טרנר-אוף, ילדים) *לא* כאן —
//  להם כלים נפרדים עם ריסון.
// ------------------------------------------------------------
class RoniAboutTool : Tool {
    override val name = "roni_about"

    override val description =
        "עובדות על רוני. העבר topic אחד ותקבל את המידע עליו. נושאים זמינים:\n" +
            "basics (גיל, עיר, עיסוק, תחומי עניין, מה מחפש, שפות, מוצא, דת, מוזיקה, שאיפות), " +
            "education (לימודים), work (עבודה), military (צבא), lifestyle (אלכוהול/עישון/גראס), " +
            "games (גיימינג), motorcycle (אופנוע), family (משפחה), food (אוכל), home (הבית), " +
            "cocktails (קוקטיילים), pets (חיות), tattoo (קעקוע), travel (נסיעות), " +
            "partnership (איך רואה זוגיות), relationships (עבר רומנטי ואקסיות), " +
            "dating_life (חיי דייטים), routine (שגרה), ufc (קרבות ולוחמים), casino (קזינו), " +
            "type (מה כובש אותו), proud (במה גאה), how_built (איך האתר נבנה). " +
            "לנושאים רגישים יש כלים נפרדים: roni_politics, roni_turnoffs, roni_kids."

    override val parameters = mapOf(
        "topic" to "אחד מהנושאים מהרשימה, למשל: motorcycle או family",
    )

    private val photoTopics = setOf("military", "work", "motorcycle", "pets", "tattoo")

    override suspend fun execute(args: Map<String, Any>): String {
        val topic = args["topic"]?.toString()?.trim()?.lowercase().orEmpty()

        val content: String? = when (topic) {
            "basics" -> facts(
                "גיל" to RoniFacts.age,
                "עיר" to RoniFacts.city,
                "עיסוק" to RoniFacts.work,
                "תחומי עניין" to RoniFacts.interests,
                "מה הוא מחפש" to RoniFacts.lookingFor,
                "דייט ראשון אידיאלי" to RoniFacts.idealFirstDate,
                "שפות" to RoniFacts.languages,
                "קעקועים" to RoniFacts.tattos,
                "מוצא" to RoniFacts.origin,
                "דת ומסורת" to RoniFacts.religion,
                "איך הוא מגדיר את עצמו" to RoniFacts.selfImage,
                "מוזיקה" to RoniFacts.music,
                "שאיפות" to RoniFacts.outlook,
                "עליו" to RoniFacts.about,
                "" to RoniFacts.extras,
            ).takeIf { it.isNotBlank() }

            "education"    -> RoniFacts.education.orNull()
            "work"         -> RoniFacts.workDeepDive.orNull()
            "military"     -> RoniFacts.militaryService.orNull()
            "lifestyle"    -> facts(
                "אלכוהול" to RoniFacts.drink,
                "עישון" to RoniFacts.smoke,
                "גראס" to RoniFacts.weed,
            ).takeIf { it.isNotBlank() }
            "games"        -> RoniFacts.games.takeIf { it.isNotEmpty() }?.joinToString("\n") { "- $it" }
            "motorcycle"   -> RoniFacts.motorcycle.orNull()
            "family"       -> RoniFacts.family.orNull()
            "food"         -> RoniFacts.food.orNull()
            "home"         -> RoniFacts.home.orNull()
            "cocktails"    -> RoniFacts.cocktails.orNull()
            "pets"         -> RoniFacts.pets.orNull()
            "tattoo"       -> RoniFacts.tattoo.orNull()
            "travel"       -> RoniFacts.travel.orNull()
            "partnership"  -> RoniFacts.partnershipView.orNull()
            "relationships" -> RoniFacts.relationships.orNull()
            "dating_life"  -> RoniFacts.datingLife.orNull()
            "routine"      -> RoniFacts.routine.orNull()
            "ufc"          -> RoniFacts.combatSports.orNull()
            "casino"       -> RoniFacts.casino.orNull()
            "type"         -> RoniFacts.greenFlags.orNull()
            "proud"        -> RoniFacts.proud.orNull()
            "how_built"    -> RoniFacts.howIBuiltThis.orNull()

            else -> return "אין נושא בשם \"$topic\". הנושאים: basics, education, work, military, " +
                "lifestyle, games, motorcycle, family, food, home, cocktails, pets, tattoo, travel, " +
                "partnership, relationships, dating_life, routine, ufc, casino, type, proud, how_built. " +
                "לרגיש: roni_politics / roni_turnoffs / roni_kids."
        }

        if (content == null) return UNKNOWN
        return if (topic in photoTopics) content + PHOTO_HINT else content
    }
}

// ------------------------------------------------------------
//  פוליטיקה
//
//  כלי נפרד, ובכוונה. הדעה הזו לא נכנסת ל-roni_about כדי
//  שג׳ימיקלוד לא יזרוק אותה לשיחה שלא ביקשה אותה — זה מה
//  שאדם היה עושה, ולא ריכוך של מה שרוני חושב.
// ------------------------------------------------------------
class PoliticsTool : Tool {
    override val name = "roni_politics"
    override val description =
        "הדעה הפוליטית של רוני. **הפעל רק אם היא שאלה על זה ישירות.** " +
            "אל תעלה את הנושא מיוזמתך ואל תשתמש בזה כרקע לתשובות אחרות. " +
            "כשהיא כן שואלת — מסור את זה כמו שהוא, בלי לרכך ובלי להתנצל."

    override suspend fun execute(args: Map<String, Any>): String =
        RoniFacts.politics.takeIf { it.isNotBlank() } ?: UNKNOWN
}

// ------------------------------------------------------------
//  טרנר-אוף
//
//  ריסון כמו פוליטיקה: לא עולה מיוזמת הסוכן. רק אם היא שואלת.
// ------------------------------------------------------------
class TurnOffsTool : Tool {
    override val name = "roni_turnoffs"
    override val description =
        "מה מוריד לרוני / טרנר-אוף. **הפעל רק אם היא שואלת על זה במפורש.** " +
            "אל תעלה מיוזמתך. כשהיא שואלת — מסור בקלילות, בלי להטיף."

    override suspend fun execute(args: Map<String, Any>): String =
        RoniFacts.turnOffs.takeIf { it.isNotBlank() } ?: UNKNOWN
}

// ------------------------------------------------------------
//  ילדים ועתיד
//
//  התשובה חצופה ומינית. ריסון לפי וייב.
// ------------------------------------------------------------
class KidsTool : Tool {
    override val name = "roni_kids"
    override val description =
        "היחס של רוני לילדים ולעתיד. התשובה שלו חצופה ומינית — **תן אותה " +
            "רק כשהוייב משחקי/פלרטטני או שהיא מעלה את זה בקלילות.** אם היא " +
            "שואלת ברצינות — קח את זה קליל וכן בלי הבדיחה הגסה."

    override suspend fun execute(args: Map<String, Any>): String =
        RoniFacts.kids.takeIf { it.isNotBlank() } ?: UNKNOWN
}

// ------------------------------------------------------------
//  תמונות
// ------------------------------------------------------------
class ShowPhotosTool(private val ctx: OrchestratorContext) : Tool {
    override val name = "show_photos"

    override val description = buildString {
        append("מציג לה תמונות של רוני. הן מוצגות אוטומטית בשיחה — ")
        append("אל תתאר אותן במילים ואל תנסה לצרף קישור. ")
        if (RoniPhotos.isEmpty) {
            append("כרגע אין תמונות זמינות.")
        } else {
            append("התמונות הזמינות:\n").append(RoniPhotos.index())
        }
    }

    override val parameters = mapOf(
        "indexes" to "המספרים של התמונות מהרשימה, מופרדים בפסיק. למשל: 0,2",
    )

    override suspend fun execute(args: Map<String, Any>): String {
        if (RoniPhotos.isEmpty) {
            return "אין תמונות. תגיד לה בכנות שאין לך תמונות כרגע והצע לה לבקש ממנו."
        }

        val indexes = args["indexes"].toString()
            .split(",", " ")
            .mapNotNull { it.trim().toIntOrNull() }

        val photos = RoniPhotos.load(indexes)
        if (photos.isEmpty()) return "לא נמצאו תמונות מתאימות. אל תבטיח לה תמונה."

        photos.forEach { ctx.attachImage(it) }
        return "הוצגו לה ${photos.size} תמונות. אפשר להוסיף משפט קצר לצידן."
    }
}

// ------------------------------------------------------------
//  האם היא כבר הייתה כאן
// ------------------------------------------------------------
class ReturningVisitorTool(private val ctx: OrchestratorContext) : Tool {
    override val name = "did_we_talk_before"

    override val description =
        "בודק אם דיברת איתה בעבר. השתמש בתחילת שיחה כשאין לך היסטוריה, " +
            "כדי לדעת אם זו הפעם הראשונה שלה או שהיא חוזרת — ושכחת הכל."

    override suspend fun execute(args: Map<String, Any>): String =
        if (ctx.hasTalkedBefore()) {
            "כן, דיברתם בעבר — אבל אין לך שום זכר לשיחה, כי היא לא נשמרת. " +
                "תגיד לה את זה בכנות ובקלילות: אתה זוכר שהיא הייתה כאן, לא על מה דיברתם. " +
                "אם היא תתייחס למשהו מהעבר, אמור לה שאין לך מושג ובקש שתזכיר לך."
        } else {
            "לא, זו הפעם הראשונה שלה. קבל אותה בהתאם — בלי להתייחס לשיחות קודמות."
        }
}
