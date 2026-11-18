package com.dateroni.db

// ============================================================
//  כל ה-SQL של האפליקציה, במקום אחד.
//
//  אין מחרוזת SQL אחת מחוץ לקובץ הזה. כשמשנים סכימה, זו
//  הרשימה שעוברים עליה — לא חיפוש טקסט בכל הפרויקט.
// ============================================================

internal object Queries {

    /** חיפוש סשן לפי גיבוב הטוקן. */
    const val FIND_BY_TOKEN = """
        select email, her_name, session_active, tokens_spent, expires_at, match_percent
          from chicks
         where token_hash = ?
    """

    /**
     * קובע את אחוז ההתאמה של הבחורה. נכתב על ידי סוכן הניתוח בלבד.
     *
     * דורס: כל ניתוח חדש מחליף את הקודם, כי הוא מבוסס על יותר
     * ממה שידענו לפניו.
     *
     * התנאי match_percent >= 0 אוכף את "אין דרך חזרה" מפסילה:
     * מרגע שהערך -1, שום עדכון לא נתפס — גם לא ניסיון להעלות
     * אותה בחזרה. קביעת ה--1 עצמה כן נתפסת, כי לפניה הערך 0..100.
     */
    const val SET_MATCH_PERCENT = """
        update chicks
           set match_percent = ?
         where email = ?
           and match_percent >= 0
    """

    /** קורא את אחוז ההתאמה הנוכחי. שורה קיימת מחזירה 0 כברירת מחדל. */
    const val GET_MATCH_PERCENT = "select match_percent from chicks where email = ?"

    /**
     * כניסה. יוצר שורה אם אין, ואם יש — פותח בה סשן חדש.
     *
     * פקודה אחת ולא "בדוק ואז החלט": בין הבדיקה להחלטה יכולה
     * להיכנס בקשה מקבילה עם אותו מייל. Postgres מבטיח אטומיות.
     *
     * token_hash נדרס — וזה מה שהופך את הכניסה לדריסת הסשן הקודם.
     * מי שנכנסה מדפדפן שני מפילה את הראשון.
     *
     * tokens_spent לא מופיע כאן, ולכן לא מתאפס: התקרה היא לכל החיים.
     */
    const val LOGIN_UPSERT = """
        insert into chicks (email, google_sub, her_name, token_hash,
                            session_active, expires_at, verified, verified_at)
        values (?, ?, ?, ?, true, ?, true, now())
        on conflict (email) do update set
            google_sub     = excluded.google_sub,
            her_name       = coalesce(chicks.her_name, excluded.her_name),
            token_hash     = excluded.token_hash,
            session_active = true,
            expires_at     = excluded.expires_at,
            verified       = true,
            verified_at    = coalesce(chicks.verified_at, now())
        returning tokens_spent
    """

    /**
     * מסיים שיחה: מכבה את הסשן ומאפס את הטוקן.
     *
     * הטוקן מתאפס כי הוא אישור כניסה מת — אין סיבה שיישאר בטבלה.
     *
     * tokens_spent **לא** מופיע כאן, ובכוונה: התקציב מצטבר לאורך
     * חיי הכתובת. היא יכולה להיכנס שוב ולפתוח שיחה חדשה, אבל לא
     * לקבל תקציב טרי בכל פעם שהיא סוגרת לשונית.
     */
    /**
     * מה כבר הוצע לה.
     *
     * ב-DB ולא בזיכרון: "לא מציעים פעמיים" חייב לשרוד הפעלה
     * מחדש של השרת, אחרת כל פריסה הייתה מייצרת הצעה חוזרת —
     * וזה בדיוק מה שגורם להצעה להישמע נואשת.
     */
    const val OFFERS_MADE = """
        select whatsapp_offered_at is not null as whatsapp_offered,
               date_offered_at     is not null as date_offered
          from chicks
         where email = ?
    """

    /** coalesce: הפעם הראשונה היא זו שנשמרת. */
    const val MARK_WHATSAPP_OFFERED = """
        update chicks
           set whatsapp_offered_at = coalesce(whatsapp_offered_at, now())
         where email = ?
    """

    const val MARK_DATE_OFFERED = """
        update chicks
           set date_offered_at = coalesce(date_offered_at, now())
         where email = ?
    """

    const val TOKENS_SPENT = "select tokens_spent from chicks where email = ?"

    /**
     * מוסיף לחשבון הטוקנים.
     *
     * חיבור בתוך ה-SQL ולא קריאה-שינוי-כתיבה בקוטלין: שתי
     * לשוניות של אותה כתובת יכולות לכתוב יחד, ורק ככה אף
     * עדכון לא נדרס.
     */
    const val ADD_TOKENS = """
        update chicks
           set tokens_spent = tokens_spent + ?
         where email = ?
    """

    /**
     * מסמן שהיא ביקשה עוד חשמל.
     *
     * דורס ולא coalesce — בניגוד ל-got_whatsapp_at. שם מעניין
     * מתי זה קרה *בפעם הראשונה*; כאן מעניין מתי היא ביקשה
     * **לאחרונה**, כי רוני מאפס ידנית והבקשה הבאה חייבת לצוץ
     * שוב גם אם הוא שכח לנקות את הקודמת.
     */
    const val REQUEST_MORE_TOKENS = """
        update chicks
           set asked_for_more_at = now()
         where email = ?
    """

    /**
     * מסמן שהוואטסאפ נמסר.
     *
     * got_whatsapp_at נקבע רק בפעם הראשונה — coalesce שומר על
     * הערך הקיים. אם היא תבקש שוב, נדע מתי זה קרה *באמת* ולא
     * מתי היא ביקשה לאחרונה.
     */
    const val MARK_WHATSAPP = """
        update chicks
           set got_whatsapp    = true,
               got_whatsapp_at = coalesce(got_whatsapp_at, now())
         where email = ?
    """

    /**
     * קובע דייט.
     *
     * date_requested_at נקבע רק בפעם הראשונה. אם היא תשנה זמן
     * או מקום, נדע מתי היא ביקשה *לראשונה* ולא מתי עדכנה.
     */
    const val SET_DATE = """
        update chicks
           set date_scheduled    = true,
               date_when_text    = ?,
               date_place        = ?,
               date_at           = ?,
               date_requested_at = coalesce(date_requested_at, now())
         where email = ?
    """

    /**
     * מבטל דייט — מנקה את השורה לגמרי.
     *
     * גם date_requested_at מתאפס: אם היא תקבע שוב, זה דייט חדש
     * ולא המשך של הישן.
     */
    const val CANCEL_DATE = """
        update chicks
           set date_scheduled    = false,
               date_when_text    = null,
               date_place        = null,
               date_at           = null,
               date_requested_at = null
         where email = ?
    """

    const val GET_DATE = """
        select date_scheduled, date_when_text, date_place, date_at, date_requested_at
          from chicks
         where email = ?
    """

    const val END_SESSION = """
        update chicks
           set session_active = false,
               token_hash     = null
         where email = ?
    """
}
