-- ============================================================
--  מיגרציה 003 — אחוז התאמה לכל בחורה
--
--  להריץ ב-DataGrip (או psql) לפני פריסת קוד שקורא את העמודה.
--  idempotent — בטוח להריץ שוב, היא מתכנסת למצב הסופי.
--
--  match_percent הוא הפלט של סוכן ניתוח-ההתאמה: כמה היא מתאימה
--  לרוני, לפי האישיות שלה והדרישות שלו.
--
--    0..100  — אחוז ההתאמה. מתחיל ב-0 לכל בחורה, ועולה ויורד
--              תוך כדי השיחה.
--    -1      — פסילה. אין דרך חזרה: מרגע שנקבע -1 הוא נעול.
--              האכיפה בקוד — SET_MATCH_PERCENT מעדכן רק כשהערך
--              הנוכחי >= 0, כך שאחרי -1 שום עדכון לא נתפס.
-- ============================================================

begin;

alter table chicks add column if not exists match_percent int;

-- שורות שנוצרו לפני העמודה קיבלו null; אחוז ההתאמה מתחיל ב-0.
update chicks set match_percent = 0 where match_percent is null;

alter table chicks alter column match_percent set default 0;
alter table chicks alter column match_percent set not null;

-- הטווח כולל את -1 (פסילה). גרסה מוקדמת של המיגרציה הרשתה 0..100
-- בלבד — לכן מפילים אותה קודם, ואז מוסיפים את הטווח הנכון.
alter table chicks drop constraint if exists chicks_match_percent_check;
alter table chicks drop constraint if exists chicks_match_percent_range;
alter table chicks add constraint chicks_match_percent_range
    check (match_percent between -1 and 100);

commit;
