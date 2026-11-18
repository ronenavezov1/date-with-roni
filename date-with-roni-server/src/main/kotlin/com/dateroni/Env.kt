package com.dateroni

import org.slf4j.LoggerFactory
import java.io.File

// ============================================================
//  קריאת הגדרות, עם נפילה ל-.env אם הן לא בסביבה.
//
//  למה זה קיים: משתני סביבה מגיעים אחרת בכל דרך הרצה —
//  ./gradlew run מזריק אותם, הכפתור הירוק של IntelliJ לא,
//  ובקונטיינר הם מגיעים מ-compose. בלי זה השרת מתנהג אחרת
//  לפי איך שהפעלת אותו, וזה מבלבל בדיוק כשלא צריך.
//
//  סדר העדיפות חשוב: משתנה סביבה אמיתי תמיד גובר על הקובץ.
//  ככה בקונטיינר, שבו אין .env בכלל, compose הוא המקור היחיד,
//  ואי אפשר בטעות לדרוס הגדרת פרודקשן בקובץ מקומי.
//
//  (ב-JVM אי אפשר לשנות את סביבת התהליך אחרי שהוא עלה,
//  ולכן זו פונקציית קריאה ולא הזרקה ל-System.getenv.)
// ============================================================

private val log = LoggerFactory.getLogger("Env")

object Env {

    private val fromFile: Map<String, String> by lazy {
        val file = File(".env")
        if (!file.exists()) return@lazy emptyMap()

        val values = file.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
            .associate { line ->
                val (key, value) = line.split("=", limit = 2)
                key.trim() to value.trim().trim('"', '\'')
            }

        log.info("נטענו {} הגדרות מ-.env", values.size)
        values
    }

    /** משתנה סביבה, ואם אין — מה שכתוב ב-.env. */
    fun get(key: String): String? =
        System.getenv(key)?.takeIf { it.isNotBlank() } ?: fromFile[key]?.takeIf { it.isNotBlank() }

    fun get(key: String, default: String): String = get(key) ?: default
}
