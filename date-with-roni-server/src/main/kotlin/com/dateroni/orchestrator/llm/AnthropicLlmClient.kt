package com.dateroni.orchestrator.llm

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.ThinkingConfigAdaptive
import com.anthropic.models.messages.ThinkingConfigParam
import com.anthropic.models.messages.Tool
import com.anthropic.models.messages.ToolResultBlockParam
import com.anthropic.models.messages.ToolUseBlockParam
import com.dateroni.Env
import com.dateroni.orchestrator.agent.AgentCapability
import com.dateroni.orchestrator.agent.AgentHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * מימוש Anthropic של [LlmClient].
 *
 * שתי הערות על הבדלים מ-Gemini:
 *
 * 1. **אין שני מודלים.** אצל Gemini הדרגה בוחרת מודל אחר. כאן שני
 *    הדרגות רצות על אותו מודל, והדרגה מתורגמת ל-`effort` — עומק
 *    החשיבה. זה הכלי המקביל בדור הזה, והוא זול יותר משמירת שני
 *    מודלים בסנכרון. אפשר עדיין להפריד מודלים דרך הפרמטרים.
 *
 * 2. **מזהי כלים.** Anthropic דורשת שכל `tool_result` יצביע ל-
 *    `tool_use_id` של הקריאה שלו. [AgentHistory.ToolExecutionResult]
 *    מחזיק רק שם כלי, ולכן ההצמדה כאן היא לפי סדר: קריאה ותוצאה
 *    מאותו שם מזדווגות לפי התור שבו הן הופיעו.
 */
class AnthropicLlmClient(
    apiKey: String,
    /**
     * מזהה סביבת העבודה. נדרש כשהמפתח מקושר-זהות — במקרה כזה
     * בקשה בלעדיו נדחית ב-400 עם "anthropic-workspace-id is
     * required". מפתח רגיל לא צריך אותו, ואז אפשר להשאיר ריק.
     */
    workspaceId: String? = null,
    /**
     * הדרגה המהירה — החלטות בינאריות, טקסט פשוט.
     *
     * ברירת המחדל זהה ל-SMART, ובכוונה: בדור הזה הדרגה מתורגמת
     * ל-effort ולא לשם מודל. כשיהיו סוכני עזר זולים — מסנן,
     * בודק תשובה — שווה להצביע כאן על מודל קטן יותר:
     *
     *     ANTHROPIC_MODEL_FAST=claude-haiku-4-5
     */
    val fastModelName: String = Env.get("ANTHROPIC_MODEL_FAST", "claude-opus-5"),
    /** הדרגה החזקה — ניתוח רב-תורי והחלטות כבדות. */
    val smartModelName: String = Env.get("ANTHROPIC_MODEL_SMART", "claude-opus-5"),
    private val maxTokens: Long = 4096,
) : BaseLlmClient(
    providerName = "Anthropic",
    apiKey = apiKey,
    modelName = "$fastModelName / $smartModelName",
) {

    private val client = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .apply { workspaceId?.takeIf { it.isNotBlank() }?.let { putHeader("anthropic-workspace-id", it) } }
        .build()

    override fun modelNameFor(tier: ModelTier): String =
        if (tier == ModelTier.SMART) smartModelName else fastModelName

    override fun buildRequestPayload(
        system: String,
        history: List<AgentHistory>,
        capabilities: List<AgentCapability>,
        tier: ModelTier,
    ): Any = AnthropicPayload(system, history, capabilities, tier)

    @Throws(LlmSendException::class)
    override suspend fun executeNetworkCall(agentId: String, payload: Any): LlmResponse {
        val req = payload as AnthropicPayload

        // ה-SDK חוסם, ולכן חייבים לצאת מתהליכוני Netty לפני הקריאה.
        return withContext(Dispatchers.IO) {
            val params = MessageCreateParams.builder()
                .model(modelNameFor(req.tier))
                .maxTokens(maxTokens)
                .system(req.system)
                .messages(toMessages(req.history))
                // חשיבה אדפטיבית: המודל מחליט לבד כמה להשקיע.
                .thinking(ThinkingConfigParam.ofAdaptive(ThinkingConfigAdaptive.builder().build()))
                .outputConfig(
                    OutputConfig.builder()
                        .effort(
                            if (req.tier == ModelTier.SMART) OutputConfig.Effort.HIGH
                            else OutputConfig.Effort.LOW
                        )
                        .build()
                )
                .apply { toTools(req.capabilities).forEach { addTool(it) } }
                .build()

            val response = client.messages().create(params)

            val funcCalls = response.content().mapNotNull { block ->
                block.toolUse().orElse(null)?.let { use ->
                    @Suppress("UNCHECKED_CAST")
                    FunctionCall(
                        functionName = use.name(),
                        arguments = use._input().convert(Map::class.java) as? Map<String, Any> ?: emptyMap(),
                    )
                }
            }

            val textReply = if (funcCalls.isEmpty()) {
                response.content()
                    .mapNotNull { it.text().orElse(null)?.text() }
                    .joinToString("\n")
                    .takeIf { it.isNotBlank() }
            } else null

            val usage = response.usage()

            LlmResponse(
                textReply = textReply,
                functionCalls = funcCalls,
                inputTokens = usage.inputTokens().toInt(),
                outputTokens = usage.outputTokens().toInt(),
            )
        }
    }

    // ------------------------------------------------------------
    //  היסטוריה → הודעות
    //
    //  כמו ב-Gemini, קריאות כלי רצופות חייבות להתמזג להודעה אחת,
    //  וכך גם התוצאות. Anthropic לא מקבלת שני תורים רצופים של אותו
    //  תפקיד, ודורשת שכל tool_result יגיע בהודעת user אחת.
    // ------------------------------------------------------------
    private fun toMessages(history: List<AgentHistory>): List<MessageParam> {
        val result = mutableListOf<MessageParam>()

        // תור מזהים לכל שם כלי: הקריאה מייצרת מזהה, והתוצאה
        // הבאה עם אותו שם שולפת אותו.
        val pendingIds = mutableMapOf<String, ArrayDeque<String>>()

        fun text(role: MessageParam.Role, value: String) {
            result += MessageParam.builder()
                .role(role)
                .contentOfBlockParams(
                    listOf(ContentBlockParam.ofText(TextBlockParam.builder().text(value).build()))
                )
                .build()
        }

        var i = 0
        while (i < history.size) {
            when (val action = history[i]) {

                is AgentHistory.UserInput -> {
                    text(MessageParam.Role.USER, action.text); i++
                }

                is AgentHistory.AiResponse -> {
                    text(MessageParam.Role.ASSISTANT, action.text); i++
                }

                is AgentHistory.ToolCallRequest -> {
                    val blocks = mutableListOf<ContentBlockParam>()
                    while (i < history.size && history[i] is AgentHistory.ToolCallRequest) {
                        val call = history[i] as AgentHistory.ToolCallRequest
                        val id = "call_${call.id}"
                        pendingIds.getOrPut(call.toolName) { ArrayDeque() }.addLast(id)

                        val input = ToolUseBlockParam.Input.builder().apply {
                            call.arguments.forEach { (k, v) -> putAdditionalProperty(k, JsonValue.from(v)) }
                        }.build()

                        blocks += ContentBlockParam.ofToolUse(
                            ToolUseBlockParam.builder()
                                .id(id)
                                .name(call.toolName)
                                .input(input)
                                .build()
                        )
                        i++
                    }
                    result += MessageParam.builder()
                        .role(MessageParam.Role.ASSISTANT)
                        .contentOfBlockParams(blocks)
                        .build()
                }

                is AgentHistory.ToolExecutionResult -> {
                    val blocks = mutableListOf<ContentBlockParam>()
                    while (i < history.size && history[i] is AgentHistory.ToolExecutionResult) {
                        val res = history[i] as AgentHistory.ToolExecutionResult
                        val id = pendingIds[res.toolName]?.removeFirstOrNull() ?: "call_${res.id}"
                        blocks += ContentBlockParam.ofToolResult(
                            ToolResultBlockParam.builder()
                                .toolUseId(id)
                                .content(res.resultData)
                                .isError(!res.isSuccess)
                                .build()
                        )
                        i++
                    }
                    result += MessageParam.builder()
                        .role(MessageParam.Role.USER)
                        .contentOfBlockParams(blocks)
                        .build()
                }

                // אירוע מערכת — פנימי, לא נשלח למודל.
                is AgentHistory.SystemEvent -> i++

                // סיכום — נשלח כתור user, כדי שהמודל יבין שזה הקשר
                // דחוס ולא שאלה חיה.
                is AgentHistory.Summary -> {
                    text(MessageParam.Role.USER, "סיכום השיחה עד כה (הקשר דחוס):\n${action.text}")
                    i++
                }
            }
        }
        return result
    }

    // ------------------------------------------------------------
    //  יכולות → כלים
    // ------------------------------------------------------------
    private fun toTools(capabilities: List<AgentCapability>): List<Tool> =
        capabilities.map { cap ->
            val properties = Tool.InputSchema.Properties.builder().apply {
                cap.parameters.forEach { (name, description) ->
                    putAdditionalProperty(
                        name,
                        JsonValue.from(mapOf("type" to "string", "description" to description)),
                    )
                }
            }.build()

            Tool.builder()
                .name(cap.name)
                .description(cap.description)
                .inputSchema(
                    Tool.InputSchema.builder()
                        .properties(properties)
                        .required(cap.parameters.keys.toList())
                        .build()
                )
                .build()
        }

    private data class AnthropicPayload(
        val system: String,
        val history: List<AgentHistory>,
        val capabilities: List<AgentCapability>,
        val tier: ModelTier,
    )
}
