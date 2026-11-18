package com.dateroni.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.dateroni.Env
import org.slf4j.LoggerFactory
import java.sql.Connection

// ============================================================
//  בריכת חיבורים ל-Postgres.
//
//  למה בכלל בריכה: פתיחת חיבור ל-Postgres היא פעולה יקרה —
//  TCP, אימות, ותהליך חדש בצד השרת. עשרות אלפיות שנייה, לכל בקשה.
//  הבריכה פותחת מראש כמה חיבורים ומשאילה אותם, וכל בקשה משלמת
//  רק על ההשאלה עצמה.
//
//  Ktor לא מביא בריכה משלו. HikariCP היא ברירת המחדל בעולם ה-JVM,
//  והיא גם מה ש-Spring Boot משתמש בו מתחת לפני השטח.
// ============================================================

private val log = LoggerFactory.getLogger("Database")

class Database private constructor(private val ds: HikariDataSource) : AutoCloseable {

    // ------------------------------------------------------------
    //  הכלל הכי חשוב בקובץ הזה.
    //
    //  JDBC הוא API חוסם. המסלולים ב-Ktor רצים על התהליכונים של
    //  Netty, ומספרם קטן. אם שאילתה תיחסם על אחד מהם, הוא לא יטפל
    //  בשום בקשה אחרת עד שהיא תחזור — וכמה שאילתות איטיות
    //  במקביל משתקות את כל השרת.
    //
    //  withContext(Dispatchers.IO) מעביר את החסימה לבריכת תהליכונים
    //  שנועדה בדיוק לזה. כל גישה ל-DB חייבת לעבור דרך כאן.
    // ------------------------------------------------------------
    suspend fun <T> query(block: (Connection) -> T): T = withContext(Dispatchers.IO) {
        ds.connection.use(block)
    }

    override fun close() {
        log.info("סוגר את בריכת החיבורים")
        ds.close()
    }

    companion object {
        /**
         * מחזיר null אם אין DATABASE_URL — כדי שאפשר יהיה להריץ את
         * השרת מקומית בלי Postgres, בדיוק כמו שגרסת ה-Node עולה בלי
         * SMTP. בקונטיינר המשתנה תמיד מוגדר.
         */
        fun fromEnv(): Database? {
            val url = Env.get("DATABASE_URL")
            if (url.isNullOrBlank()) {
                log.warn("אין DATABASE_URL — השרת עולה בלי בסיס נתונים")
                return null
            }

            val config = HikariConfig().apply {
                jdbcUrl = url
                username = Env.get("DATABASE_USER")
                password = Env.get("DATABASE_PASSWORD")
                driverClassName = "org.postgresql.Driver"
                poolName = "date-with-roni"

                // בריכות קטנות מהירות יותר מגדולות, וזו לא אינטואיציה
                // אלא מה ש-HikariCP עצמה ממליצה. כל חיבור הוא תהליך
                // בצד Postgres; יותר חיבורים מקבילים מליבות פנויות רק
                // מוסיפים החלפות הקשר. לאתר הזה חמישה זה בשפע.
                maximumPoolSize = Env.get("DB_POOL_SIZE")?.toIntOrNull() ?: 5
                minimumIdle = maximumPoolSize

                connectionTimeout = 10_000   // כמה לחכות לחיבור פנוי
                validationTimeout = 3_000
                idleTimeout = 600_000
                maxLifetime = 1_800_000      // ממחזר חיבורים כל חצי שעה

                isAutoCommit = true
                transactionIsolation = "TRANSACTION_READ_COMMITTED"
            }

            // Hikari נכשלת כאן ומיד אם אי אפשר להתחבר, וזה רצוי:
            // עדיף שהקונטיינר יפול על הגדרה שגויה מאשר יעלה ויתחיל
            // להחזיר 500 לכל בקשה. ב-compose יש depends_on עם
            // service_healthy, אז ה-DB כבר מוכן בשלב הזה.
            val ds = HikariDataSource(config)
            log.info("בריכת חיבורים מוכנה ({} חיבורים)", config.maximumPoolSize)
            return Database(ds)
        }
    }
}
