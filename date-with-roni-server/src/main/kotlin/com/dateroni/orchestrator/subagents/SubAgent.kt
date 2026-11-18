package com.dateroni.orchestrator.subagents

import com.dateroni.orchestrator.OrchestratorContext
import com.dateroni.orchestrator.agent.AgentConfig
import com.dateroni.orchestrator.agent.AiAgent
import com.dateroni.orchestrator.agent.Tool
import com.dateroni.orchestrator.llm.ModelTier
import org.slf4j.LoggerFactory

// ============================================================
//  העוזר שכל תת-הסוכנים משתמשים בו.
//
//  כולם באותה צורה: פרומפט צר, קלט אחד, תשובה קצרה. בלי זה
//  כל תת-סוכן היה חוזר על אותן שתים-עשרה שורות חיווט.
//
//  ההגנה החשובה כאן היא ה-fallback: תת-סוכן שנכשל, נתקע או
//  מחזיר זבל **לא מפיל את השיחה**. הוא מחזיר את ברירת המחדל,
//  והמתכנן ממשיך בלעדיו. עצה שלא הגיעה היא לא סוף העולם;
//  שיחה שנתקעה כן.
// ============================================================

private val log = LoggerFactory.getLogger("SubAgent")

internal suspend fun OrchestratorContext.ask(
    id: String,
    prompt: String,
    input: String,
    tools: List<Tool> = emptyList(),
    tier: ModelTier = ModelTier.FAST,
    maxDepth: Int = 4,
    timeoutMs: Long = 30_000L,
    fallback: String = "",
): String {
    val agent = AiAgent(
        AgentConfig(
            agentId = id,
            workOnId = herEmail,
            llmClient = llmClient,
            systemPrompt = prompt,
            tools = tools.associateBy { it.name },
            timeoutMs = timeoutMs,
            maxDepth = maxDepth,
            maxHistorySize = 16,
            tier = tier,
        )
    )

    return runCatching { agent.processInput(input).trim() }
        .onFailure { log.warn("תת-הסוכן {} נכשל: {}", id, it.message) }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: fallback
}
