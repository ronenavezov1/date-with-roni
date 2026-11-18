package com.dateroni.repository

import com.dateroni.db.Database
import com.dateroni.db.Queries
import com.dateroni.objects.DateDetails
import com.dateroni.objects.Session
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

class DbRepository private constructor(private val db: Database) {

    suspend fun findByToken(tokenHash: String): Session? = db.query { c ->
        c.prepareStatement(Queries.FIND_BY_TOKEN).use { st ->
            st.setString(1, tokenHash)
            st.executeQuery().use { rs ->
                if (!rs.next()) return@use null
                Session(
                    email = rs.getString("email"),
                    herName = rs.getString("her_name"),
                    sessionActive = rs.getBoolean("session_active"),
                    tokensSpent = rs.getInt("tokens_spent"),
                    expiresAt = rs.getObject("expires_at", OffsetDateTime::class.java)?.toInstant(),
                    matchPercent = rs.getInt("match_percent"),
                )
            }
        }
    }

    /** מחזיר כמה טוקנים כבר נשרפו בחשבון הזה. שורה חדשה מחזירה 0. */
    suspend fun login(
        email: String,
        googleSub: String,
        name: String?,
        tokenHash: String,
        expiresAt: Instant,
    ): Int = db.query { c ->
        c.prepareStatement(Queries.LOGIN_UPSERT).use { st ->
            st.setString(1, email)
            st.setString(2, googleSub)
            st.setString(3, name)
            st.setString(4, tokenHash)
            st.setObject(5, OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC))
            st.executeQuery().use { rs ->
                rs.next()
                rs.getInt("tokens_spent")
            }
        }
    }

    /** כמה טוקנים נשרפו בכתובת הזו אי פעם. null = אין שורה. */
    suspend fun tokensSpent(email: String): Int? = db.query { c ->
        c.prepareStatement(Queries.TOKENS_SPENT).use { st ->
            st.setString(1, email)
            st.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else null }
        }
    }

    /** מוסיף לחשבון הטוקנים של הכתובת. אפס ומטה — מתעלמים. */
    suspend fun addTokens(email: String, amount: Int) {
        if (amount <= 0) return
        db.query { c ->
            c.prepareStatement(Queries.ADD_TOKENS).use { st ->
                st.setInt(1, amount)
                st.setString(2, email)
                st.executeUpdate()
            }
        }
    }

    /** רושמת שהיא ביקשה עוד טוקנים. רוני מאפס ידנית. */
    suspend fun requestMoreTokens(email: String): Unit = db.query { c ->
        c.prepareStatement(Queries.REQUEST_MORE_TOKENS).use { st ->
            st.setString(1, email)
            st.executeUpdate()
        }
        Unit
    }

    /** אחוז ההתאמה הנוכחי של הבחורה. שורה קיימת = 0 כברירת מחדל. */
    suspend fun matchPercent(email: String): Int = db.query { c ->
        c.prepareStatement(Queries.GET_MATCH_PERCENT).use { st ->
            st.setString(1, email)
            st.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }
    }

    /** קובע את אחוז ההתאמה של הבחורה. נכתב על ידי סוכן הניתוח. */
    suspend fun setMatchPercent(email: String, percent: Int): Unit = db.query { c ->
        c.prepareStatement(Queries.SET_MATCH_PERCENT).use { st ->
            st.setInt(1, percent)
            st.setString(2, email)
            st.executeUpdate()
        }
    }

    /** מה כבר הוצע לה. */
    data class Offers(val whatsapp: Boolean, val date: Boolean)

    suspend fun offersMade(email: String): Offers = db.query { c ->
        c.prepareStatement(Queries.OFFERS_MADE).use { st ->
            st.setString(1, email)
            st.executeQuery().use { rs ->
                if (rs.next()) Offers(rs.getBoolean(1), rs.getBoolean(2))
                else Offers(false, false)
            }
        }
    }

    suspend fun markWhatsappOffered(email: String): Unit = db.query { c ->
        c.prepareStatement(Queries.MARK_WHATSAPP_OFFERED).use { st ->
            st.setString(1, email); st.executeUpdate()
        }
        Unit
    }

    suspend fun markDateOffered(email: String): Unit = db.query { c ->
        c.prepareStatement(Queries.MARK_DATE_OFFERED).use { st ->
            st.setString(1, email); st.executeUpdate()
        }
        Unit
    }

    /** מסמן שהוואטסאפ נמסר לה. */
    suspend fun markWhatsappGiven(email: String): Unit = db.query { c ->
        c.prepareStatement(Queries.MARK_WHATSAPP).use { st ->
            st.setString(1, email)
            st.executeUpdate()
        }
    }

    // ------------------------------------------------------------
    //  דייטים
    // ------------------------------------------------------------

    suspend fun setDate(
        email: String,
        whenText: String,
        place: String,
        at: Instant?,
    ): Unit = db.query { c ->
        c.prepareStatement(Queries.SET_DATE).use { st ->
            st.setString(1, whenText)
            st.setString(2, place)
            st.setObject(3, at?.let { OffsetDateTime.ofInstant(it, ZoneOffset.UTC) })
            st.setString(4, email)
            st.executeUpdate()
        }
    }

    suspend fun cancelDate(email: String): Unit = db.query { c ->
        c.prepareStatement(Queries.CANCEL_DATE).use { st ->
            st.setString(1, email)
            st.executeUpdate()
        }
    }

    suspend fun getDate(email: String): DateDetails? = db.query { c ->
        c.prepareStatement(Queries.GET_DATE).use { st ->
            st.setString(1, email)
            st.executeQuery().use { rs ->
                if (!rs.next()) return@use null
                DateDetails(
                    scheduled = rs.getBoolean("date_scheduled"),
                    whenText = rs.getString("date_when_text"),
                    place = rs.getString("date_place"),
                    at = rs.getObject("date_at", OffsetDateTime::class.java)?.toInstant(),
                    requestedAt = rs.getObject("date_requested_at", OffsetDateTime::class.java)?.toInstant(),
                )
            }
        }
    }

    /** מסיים שיחה. לא נוגע ב-tokens_spent. */
    suspend fun endSession(email: String): Unit = db.query { c ->
        c.prepareStatement(Queries.END_SESSION).use { st ->
            st.setString(1, email)
            st.executeUpdate()
        }
    }

    fun close() = db.close()

    companion object {
        /**
         * בונה את הרפוזיטורי יחד עם בריכת החיבורים שהוא הבעלים היחיד שלה.
         *
         * null = אין DATABASE_URL. הבנייה של Database קורית אך ורק כאן,
         * ולכן אף שכבה אחרת לא מחזיקה בבריכה — הגישה למסד עוברת רק דרך
         * המתודות של הרפוזיטורי, וכל אחת מהן מפעילה שאילתה מוכנה מ-Queries.
         */
        fun fromEnv(): DbRepository? = Database.fromEnv()?.let { DbRepository(it) }
    }
}