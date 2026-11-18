package com.dateroni

import com.dateroni.repository.AppRepository
import com.dateroni.api.ApiError
import com.dateroni.servlets.loginServlet
import com.dateroni.servlets.messageServlet
import com.dateroni.workers.LoginWorker
import com.dateroni.workers.MessageWorker
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing

// ============================================================
//  נקודת הכניסה.
//
//  השכבות, מלמעלה למטה:
//
//    servlets/     View.  HTTP בלבד. תקינות בקשה ומיפוי לקודים
//    workers/      Intent. ניתוב טיפוסי, אפס לוגיקה
//    repository/   Model. הלב — המצב, ה-Mutex, והתיאום
//    objects/      אובייקטי-תחום: מופע חי אחד לכל אישה (שיחה, תיבה, דייט)
//    state/        ניהול המצב: המנהל, ה-Mutex לכל שיחה, וזרם האירועים
//    orchestrator/ הסוכן: agent · llm · debug
//
//  הקובץ הזה מכיר **רק את AppRepository**. הוא לא יודע שיש
//  Postgres, בריכת חיבורים, לקוח של גוגל או סוכן — כל אלה
//  נבנים בתוך הרפוזיטורי, ב-fromEnv().
//
//  זה API של JSON ותו לא, גם 404 ו-500. HTML שייך לקונטיינר
//  של הפרונט.
// ============================================================

const val VERSION = "0.1.0"

// 0.0.0.0 בכוונה: בתוך קונטיינר, האזנה על 127.0.0.1 אומרת ששום דבר
// מחוץ לקונטיינר לא יגיע לשרת — גם לא קונטיינר אחר באותה רשת.
// הבידוד נעשה בשכבת דוקר, ע"י פרסום הפורט רק ל-127.0.0.1 של המארח.
private val HOST: String = Env.get("HOST", "0.0.0.0")
private val PORT: Int = Env.get("PORT")?.toIntOrNull() ?: 8080

fun main() {
    embeddedServer(Netty, host = HOST, port = PORT) { module() }.start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json()
    }

    install(StatusPages) {
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respond(HttpStatusCode.NotFound, ApiError("אין נתיב כזה."))
        }
        status(HttpStatusCode.MethodNotAllowed) { call, _ ->
            call.respond(HttpStatusCode.MethodNotAllowed, ApiError("שיטה לא נתמכת בנתיב הזה."))
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("בקשה נכשלה", cause)
            call.respond(HttpStatusCode.InternalServerError, ApiError("משהו השתבש אצלנו."))
        }
    }

    // null = אין DATABASE_URL. השרת עולה, והמסלולים שדורשים
    // מסד נתונים מחזירים 503 מפורש.
    val app = AppRepository.fromEnv()
    if (app == null) log.warn("אין בסיס נתונים — /api/login ו-/api/message יחזירו 503")

    monitor.subscribe(ApplicationStopped) { app?.close() }

    routing {
        // נרשמים תמיד, גם בלי DB. צורת ה-API לא צריכה להשתנות
        // לפי ההגדרות — 503 אומר מה הבעיה, 404 נראה כמו באג ניתוב.
        loginServlet(app?.let { LoginWorker(it.sessions) })
        messageServlet(app?.let { MessageWorker(it.chat) })
    }
}
