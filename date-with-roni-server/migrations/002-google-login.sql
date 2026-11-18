-- ============================================================
--  מיגרציה 002 — מייל כמפתח ראשי + כניסה עם גוגל
--
--  להריץ ב-DataGrip. כל הפקודות idempotent.
-- ============================================================

begin;

-- ---------- מייל כמפתח ראשי, במקום id ----------
-- שורה אחת לאדם. זה מה שהופך את הכניסה ל-upsert יחיד
-- על on conflict (email), בלי חיפוש-ואז-החלטה.

alter table chicks drop constraint if exists chicks_pkey;
alter table chicks drop column if exists id;
alter table chicks alter column email set not null;
alter table chicks add primary key (email);

-- המפתח הראשי כבר יוצר אינדקס על email.
drop index if exists chicks_email_idx;

-- ---------- מזהה גוגל ----------
-- sub הוא המזהה היציב של החשבון. כתובת מייל *כן* יכולה
-- להשתנות בחשבון גוגל; sub לעולם לא. הוא לא המפתח הראשי
-- כאן, אבל שווה לשמור אותו — ביום שמייל ישתנה, הוא מה
-- שיאפשר לזהות שזו אותה אישה.
alter table chicks add column if not exists google_sub text;

create unique index if not exists chicks_google_sub_idx
    on chicks (google_sub)
    where google_sub is not null;

-- ---------- הסשן ----------
alter table chicks add column if not exists session_active boolean not null default false;
alter table chicks alter column expires_at drop not null;

create unique index if not exists chicks_token_idx
    on chicks (token_hash)
    where token_hash is not null;

commit;

-- ============================================================
--  מה *לא* נמצא כאן, כי גוגל מייתרת אותו
--
--    activation_code_hash    אין קישור במייל
--    activation_expires_at   אין קישור לפוג
--    mail_sent               אין מייל לשלוח
--    new_token_hash          אין טוקן ממתין לקידום
--
--  גוגל כבר הוכיחה שהכתובת שלה. זו בדיוק העבודה שהיא עושה,
--  ואין טעם לעשות אותה פעם שנייה.
-- ============================================================
