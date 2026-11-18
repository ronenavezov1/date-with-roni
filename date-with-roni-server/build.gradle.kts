import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
    application
}

group = "com.dateroni"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-netty:3.5.2")
    // API של JSON בלבד: ContentNegotiation מסדר את הסריאליזציה,
    // StatusPages דואג שגם שגיאות יחזרו כ-JSON ולא כטקסט חופשי.
    implementation("io.ktor:ktor-server-content-negotiation:3.5.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.2")
    implementation("io.ktor:ktor-server-status-pages:3.5.2")
    implementation("ch.qos.logback:logback-classic:1.6.3")

    // בסיס הנתונים: הדרייבר, ובריכת החיבורים מעליו.
    implementation("org.postgresql:postgresql:42.7.13")
    implementation("com.zaxxer:HikariCP:7.1.0")
    // מגיע ממילא דרך Ktor, אבל אנחנו מייבאים ממנו ישירות (Dispatchers.IO)
    // אז נכון להצהיר עליו במפורש ולא להישען על תלות עקיפה.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

    // אימות ה-ID token של גוגל. מטפל גם בהורדת המפתחות
    // הציבוריים ובהחלפה שלהם כשגוגל מסובבת אותם.
    implementation("com.google.api-client:google-api-client:2.9.0")

    // ה-SDK הרשמי של Anthropic לג'אווה. קוטלין משתמשת בו ישירות.
    implementation("com.anthropic:anthropic-java:2.34.0")
}

// ה-JDK המקומי הוא 26, אבל מקמפלים ל-21 — זה מה שרץ בקונטיינר.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

application {
    mainClass.set("com.dateroni.ApplicationKt")
    // Netty ניגש לספריות מקומיות. בלי זה JDK 26 מדפיס אזהרה בכל הרצה.
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

// ------------------------------------------------------------
//  ./gradlew run טוען את .env לבד, כדי שהרצה מקומית תשתמש
//  באותו קובץ שה-compose משתמש בו ולא ידרוש export ידני.
//  זו אותה נוחות ש-dotenv נותן לגרסת ה-Node.
// ------------------------------------------------------------
tasks.named<JavaExec>("run") {
    val envFile = rootProject.file(".env")
    if (envFile.exists()) {
        envFile.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
            .forEach { line ->
                val (key, value) = line.split("=", limit = 2)
                environment(key.trim(), value.trim())
            }
    }
}
