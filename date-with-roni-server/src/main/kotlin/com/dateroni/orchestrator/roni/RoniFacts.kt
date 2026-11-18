package com.dateroni.orchestrator.roni

import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

// ============================================================
//  כל מה שג׳ימיקלוד יודע על רוני.
//
//  התוכן לא בקוד ולא בגיט. הוא יושב ב-resources/roni-facts.json,
//  שמוחרג מהגיט: יש בו פרטים אישיים, וגם על אנשים אחרים, והריפו
//  ציבורי. הקובץ נארז לתוך ה-jar בבנייה, אז דוקר והפריסה לא
//  צריכים לדעת שהוא קיים.
//
//  ★ roni-facts.json הוא הקובץ היחיד שצריך לערוך כדי לשנות
//    מה הוא מספר. ★ מה כל שדה אומר — ב-RoniFactsData.
//
//  בשכפול טרי, בלי הקובץ, נטען roni-facts.example.json במקומו
//  ואזהרה נכתבת ללוג: השרת עולה, רק עם רוני לדוגמה.
// ============================================================

private val log = LoggerFactory.getLogger("RoniFacts")

val RoniFacts: RoniFactsData by lazy { load() }

private fun load(): RoniFactsData {
    val real = RoniFactsData::class.java.getResource("/roni-facts.json")
    val source = real
        ?: RoniFactsData::class.java.getResource("/roni-facts.example.json")
        ?: error("חסרים ב-resources גם roni-facts.json וגם roni-facts.example.json")
    if (real == null) log.warn("roni-facts.json לא נמצא, נטען roni-facts.example.json")

    // Json קפדני בכוונה: שדה חסר או שם שדה עם שגיאת כתיב מפילים
    // את העלייה, במקום שהסוכן יגיד בשקט "רוני לא סיפר לי".
    return Json.decodeFromString(RoniFactsData.serializer(), source.readText())
}
