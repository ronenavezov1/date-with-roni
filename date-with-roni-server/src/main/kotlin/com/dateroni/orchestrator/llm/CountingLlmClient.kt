package com.dateroni.orchestrator.llm

import com.dateroni.orchestrator.agent.AgentCapability
import com.dateroni.orchestrator.agent.AgentHistory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

// ============================================================
//  לקוח שסופר.
//
//  עוטף לקוח אמיתי ומחזיק מונה. הוא לא יודע כלום על מודלים,
//  על תמחור או על מי קרא לו — רק מחבר.
//
//  למה כאן ולא בתוך המנצח: המנצח הוא לא הצרכן היחיד. כל
//  תת-סוכן — בודק הזבל, קורא מצב הרוח, יועץ התמונות — מקבל
//  את *אותו* ctx.llmClient ומוציא טוקנים דרכו. ספירה במנצח
//  בלבד הייתה מדווחת על חלק קטן מהחשבון.
//
//  מופע אחד לכל סבב, נולד ומת עם ההקשר. הלקוח האמיתי שמתחתיו
//  משותף לכל השיחות ולכן אסור לו להחזיק מונה משלו.
// ============================================================

class CountingLlmClient(private val delegate: LlmClient) : LlmClient {

    private val counter = AtomicInteger(0)
    private val outOfService = AtomicBoolean(false)

    /** כמה טוקנים נצרכו מאז שנוצר. */
    val tokensUsed: Int get() = counter.get()

    /**
     * הספק עצמו מסרב לשרת אותנו — נגמרו הקרדיטים או שהמפתח פסול.
     *
     * זה שונה מ"אנטרופיק עמוסה" (529) או מנפילת רשת: אלה חולפות
     * ו-BaseLlmClient מנסה שוב. כאן שום ניסיון חוזר לא יעזור, וכל
     * תת-סוכן ייכשל בדיוק אותו דבר — אין טעם להעמיד פנים שהסוכן
     * עובד.
     */
    val providerOutOfService: Boolean get() = outOfService.get()

    override val providerName: String get() = delegate.providerName

    override fun modelNameFor(tier: ModelTier): String = delegate.modelNameFor(tier)

    override suspend fun sendMessage(
        agentId: String,
        systemPrompt: String,
        history: List<AgentHistory>,
        availableCapabilities: List<AgentCapability>,
        tier: ModelTier,
    ): LlmResponse {
        val response = try {
            delegate.sendMessage(agentId, systemPrompt, history, availableCapabilities, tier)
        } catch (e: Exception) {
            if (isOutOfService(e)) outOfService.set(true)
            throw e
        }

        // תשובה שהגיעה מוכיחה שהספק חי, גם אם קודם נכשלנו.
        outOfService.set(false)
        counter.addAndGet(response.totalTokens)
        return response
    }

    /**
     * מה נחשב "הספק סגור בפנינו".
     *
     * לפי הטקסט ולא לפי טיפוס, כי ה-SDK עוטף את שגיאת ה-HTTP
     * ואנחנו מקבלים אותה כמחרוזת. שתי המשפחות שמעניינות:
     * יתרת קרדיט שנגמרה (400) ומפתח שנדחה (401).
     */
    private fun isOutOfService(e: Exception): Boolean {
        val m = (e.message ?: "").lowercase()
        return m.contains("credit balance") ||
            m.contains("billing") ||
            m.contains("authentication_error") ||
            m.contains("invalid x-api-key") ||
            m.contains("invalid_api_key") ||
            Regex("\\b401\\b").containsMatchIn(m)
    }
}
