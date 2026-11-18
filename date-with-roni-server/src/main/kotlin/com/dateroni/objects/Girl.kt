package com.dateroni.objects

import java.time.Instant

// ============================================================
//  הבחורה — הזיכרון החי שלה, בזיכרון בלבד.
//
//  כל מה שאנחנו יודעים עליה מתוך השיחה: ההיסטוריה, התמונות
//  שנשלחו, התקציר. לא נכתב ל-Postgres, לא לקובץ ולא ללוג,
//  ונמחק כשהתהליך יורד. זו לא החלטה טכנית אלא ההבטחה שכתובה
//  בתחתית האתר: "רוני לא מודע להתכתבות". ברגע שהזיכרון הזה
//  יגיע לדיסק, המשפט הזה מפסיק להיות נכון.
//
//  אין כאן מתודות ריצה — נגישה רק דרך AppStateManager, תחת
//  ה-Mutex שב-LiveConversation.
// ============================================================

/** תפקיד ההודעה בשיחה, כפי שהמודל רואה אותה. */
enum class Speaker { HER, AGENT }

data class Turn(
    val speaker: Speaker,
    val text: String,
)

class Girl(
    val email: String,
) {
    val turns: MutableList<Turn> = mutableListOf()

    var startedAt: Instant = Instant.now()
        private set

    var lastActivity: Instant = Instant.now()

    /**
     * אינדקסים של תמונות שכבר נשלחו בשיחה הזו.
     *
     * חי בזיכרון כמו כל השאר, ולכן נמחק איתה. אם היא תחזור
     * מחר — הוא יוכל להראות שוב את אותה תמונה, וזה בסדר: הוא
     * ממילא לא זוכר שכבר הראה.
     */
    val sentPhotos: MutableSet<Int> = mutableSetOf()

    /**
     * מספר התור שבו נשלחה התמונה האחרונה.
     *
     * זה מה שמאפשר לאכוף "לא תמונה בכל הודעה" בקוטלין ולא
     * בפרומפט. -1 = עוד לא נשלחה תמונה בשיחה הזו.
     */
    var lastPhotoTurn: Int = -1

    /**
     * תקציר של מה שכבר נמחק מההיסטוריה.
     *
     * מוחלף (ולא מצטבר) בכל תמצות: התמצות הבא מקבל את התקציר
     * הזה ואת מה שנאמר מאז, ומייצר אחד חדש שמכיל את שניהם.
     */
    var summary: String? = null

    /**
     * באיזה "חלון" של 25,000 טוקנים היינו בתמצות האחרון.
     *
     * מספר החלון ולא ספירת טוקנים, כי זה מה שמאפשר לזהות גם
     * *ירידה*: כשרוני מאפס את המכסה החלון קופץ מ-4 ל-0, וזה
     * שינוי — אז השיחה נכנסת לסבב הבא עם היסטוריה כבר מקוצרת.
     */
    var compactionWindow: Int = 0

    // ------------------------------------------------------------
    //  הפרופיל המובנה — מה שאנחנו יודעים עליה, בשדות.
    //  RAM כמו כל השאר. חולץ הפרופיל ממלא אותו כל הודעה, וסוכן
    //  הניתוח משקלל אותו. רק ה-match_percent המזוקק שורד ל-DB.
    // ------------------------------------------------------------

    val profile: HerProfile = HerProfile()

    /** מחליף את ההיסטוריה הישנה בתקציר, ומשאיר את הסוף כלשונו. */
    fun compact(newSummary: String, keepRecent: Int = 6) {
        summary = newSummary
        if (turns.size > keepRecent) {
            turns.subList(0, turns.size - keepRecent).clear()
        }
    }

    /** תקרת אורך להיסטוריה. מעבר לזה חותכים את הישן. */
    fun append(turn: Turn, maxTurns: Int = 90) {
        turns += turn
        lastActivity = Instant.now()
        if (turns.size > maxTurns) {
            turns.subList(0, turns.size - maxTurns).clear()
        }
    }

    fun clear() {
        turns.clear()
        sentPhotos.clear()
        lastPhotoTurn = -1
        summary = null
        compactionWindow = 0
        profile.clear()
        startedAt = Instant.now()
        lastActivity = startedAt
    }
}

/**
 * תמונה שהסוכן בחר להציג. data הוא base64 נקי, בלי הקידומת.
 *
 * המודל אף פעם לא רואה את השדה הזה — הוא בוחר אינדקס, וקוטלין
 * טוענת את הקובץ. זה בדיוק "המודל מייצר טקסט, קוטלין מחליטה".
 */
data class Image(
    val id: String,
    val mime: String,
    val data: String,
    val alt: String,
)
