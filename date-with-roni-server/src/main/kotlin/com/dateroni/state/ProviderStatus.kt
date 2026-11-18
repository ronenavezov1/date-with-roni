package com.dateroni.state

import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

// ============================================================
//  מצב הספק — כלל-שרת, לא פר-שיחה.
//
//  אם נגמרו הקרדיטים באנטרופיק או שהמפתח פסול, אף שיחה לא
//  תקבל תשובה. עדיף לומר לה את זה מראש מאשר להשאיר אותה מול
//  "מקליד..." שלא נגמר.
//
//  compareAndSet ולא בדיקה-ואז-קביעה: המעבר נרשם (וזה גם מתי
//  שכותבים ללוג) פעם אחת בלבד, גם אם כמה סבבים מדווחים על אותו
//  מצב ברצף.
// ============================================================

class ProviderStatus {

    private val log = LoggerFactory.getLogger("ProviderStatus")

    private val offline = AtomicBoolean(false)

    val isOnline: Boolean get() = !offline.get()

    fun markOffline(reason: String) {
        if (offline.compareAndSet(false, true)) log.error("הסוכן ירד: {}", reason)
    }

    fun markOnline() {
        if (offline.compareAndSet(true, false)) log.info("הסוכן חזר")
    }
}
