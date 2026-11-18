package com.dateroni.orchestrator.debug

import com.dateroni.Env
import com.dateroni.orchestrator.agent.AgentHistory
import org.slf4j.LoggerFactory

// ============================================================
//  לוג מפורט של מה שהסוכן ראה והחליט.
//
//  כבוי כברירת מחדל. עם RONI_DEBUG=true מדפיס את הקלט המלא,
//  את ההיסטוריה ואת הפלט של כל קריאה — מה שהופך "למה הוא ענה
//  את זה" לשאלה שאפשר לענות עליה.
//
//  הוא לא מדפיס לעולם בפרודקשן בלי הדגל, כי ההיסטוריה מכילה
//  את תוכן ההתכתבות — וזה בדיוק מה שהבטחנו שלא נשמר בשום מקום.
// ============================================================

private val log = LoggerFactory.getLogger("Agent")

object DebugLogger {

    val enabled: Boolean = Env.get("RONI_DEBUG", "false").equals("true", ignoreCase = true)

    fun agentStart(agentId: String, workOnId: String, modelName: String, input: String) {
        if (!enabled) return
        log.info("▶ {} [{}] מודל={} \n  קלט: {}", agentId, workOnId, modelName, input)
    }

    fun agentDone(
        agentId: String,
        workOnId: String,
        modelName: String,
        history: List<AgentHistory>,
        result: String,
    ) {
        if (!enabled) return
        log.info(
            "◀ {} [{}] מודל={} · {} פריטי היסטוריה\n  פלט: {}",
            agentId, workOnId, modelName, history.size, result,
        )
    }

    fun historySummarize(agentId: String, size: Int) {
        if (!enabled) return
        log.info("⇲ {} מדחיס היסטוריה של {} פריטים", agentId, size)
    }

    fun historySummarizeError(agentId: String, e: Exception) {
        log.warn("דחיסת היסטוריה נכשלה עבור {}: {}", agentId, e.message)
    }

    fun llmRetry(provider: String, message: String, delayMs: Long, attempt: Int, maxRetries: Int) {
        log.warn("{} נכשל (ניסיון {}/{}), ממתין {}ms: {}", provider, attempt, maxRetries, delayMs, message)
    }

    fun llmClientFailure(provider: String, message: String) {
        log.error("{}: {}", provider, message)
    }
}
