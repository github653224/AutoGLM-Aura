package com.autoglm.autoagent.agent

import android.util.Log
import com.autoglm.autoagent.data.SettingsRepository
import com.autoglm.autoagent.data.ApiProvider
import com.autoglm.autoagent.data.api.AIClient
import com.autoglm.autoagent.data.api.ChatMessage
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 执行智能体（Execution Agent）
 * 使用 EDGE 小模型进行元素定位和操作执行
 */
@Singleton
class ExecutionAgent @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val aiClient: AIClient
) {

    private var isAvailable = true

    /**
     * 检查 ExecutionAgent 是否可用
     */
    fun checkAvailability(): Boolean {
        return try {
            val config = settingsRepository.loadProviderConfig(ApiProvider.EDGE)
            config.apiKey.isNotBlank() && config.baseUrl.isNotBlank()
        } catch (e: Exception) {
            Log.e("ExecutionAgent", "可用性检查失败", e)
            false
        }
    }

    /**
     * 将 Decision 转换为可执行的 Action 字符串
     * 如果 Decision 不够具体，使用 EDGE 模型辅助定位
     */
    suspend fun resolveAction(
        decision: Decision,
        uiTree: String
    ): String {
        return try {
            // 如果 Decision 已经包含具体坐标，直接返回
            if (decision.target.contains("[") && decision.target.contains("]")) {
                return buildActionString(decision)
            }

            // 否则使用 EDGE 模型辅助定位
            val prompt = """
                UI树：
                $uiTree
                
                目标操作：${decision.action}
                目标元素：${decision.target}
                
                请返回具体的操作指令，格式：do(action="...", element=[x,y])
            """.trimIndent()

            // GLM-4V/Edge config
            val config = settingsRepository.loadProviderConfig(ApiProvider.EDGE)

            val response = aiClient.sendMessage(listOf(
                ChatMessage("system", EXECUTION_SYSTEM_PROMPT),
                ChatMessage("user", prompt)
            ), overrideConfig = config)

            val contentStr = response.content ?: ""
            parseActionString(contentStr)
        } catch (e: Exception) {
            Log.e("ExecutionAgent", "操作解析失败，使用降级策略", e)
            isAvailable = false
            // 降级：直接转换 Decision
            buildActionString(decision)
        }
    }

    /**
     * 独立执行模式（降级策略）
     * 当 DecisionAgent 不可用时，ExecutionAgent 可以独立运行
     */
    suspend fun executeIndependently(
        goal: String,
        uiTree: String,
        currentApp: String
    ): String {
        return try {
            val prompt = """
                任务：$goal
                当前App：$currentApp
                UI树：$uiTree
                
                请决定下一步操作并返回具体指令
            """.trimIndent()

            // GLM-4V/Edge config
            val config = settingsRepository.loadProviderConfig(ApiProvider.EDGE)

            val response = aiClient.sendMessage(listOf(
                ChatMessage("system", EXECUTION_SYSTEM_PROMPT),
                ChatMessage("user", prompt)
            ), overrideConfig = config)

            val contentStr = response.content ?: ""
            parseActionString(contentStr)
        } catch (e: Exception) {
            Log.e("ExecutionAgent", "独立执行失败", e)
            throw e
        }
    }

    private fun buildActionString(decision: Decision): String {
        return when (decision.action.lowercase()) {
            "tap", "click" -> "do(action=\"Tap\", element=${decision.target})"
            "type", "input" -> "do(action=\"Type\", text=\"${decision.target}\")"
            "swipe", "scroll" -> "do(action=\"Swipe\", start=[500,800], end=[500,200])"
            "back" -> "do(action=\"Back\")"
            "home" -> "do(action=\"Home\")"
            "launch" -> "do(action=\"Launch\", app=\"${decision.target}\")"
            "finish" -> "finish(message=\"完成\")"
            else -> "do(action=\"Tap\", element=[500,500])"
        }
    }

    private fun parseActionString(response: String): String {
        // 提取 do(...) 或 finish(...) 指令
        val pattern = Regex("(do|finish)\\s*\\([^)]+\\)")
        val match = pattern.find(response)
        return match?.value ?: buildFallbackAction(response)
    }

    private fun buildFallbackAction(response: String): String {
        Log.w("ExecutionAgent", "无法解析 action，使用降级")
        return "do(action=\"Tap\", element=[500,500])"
    }

    companion object {
        private const val EXECUTION_SYSTEM_PROMPT = """
你是一个 Android 操作执行专家。你的职责是：
1. 根据 UI树 精准定位元素
2. 将高层决策转换为具体操作指令
3. 返回标准格式的操作命令

操作格式示例：
- do(action="Tap", element=[x,y])
- do(action="Type", text="内容")
- do(action="Back")
- finish(message="完成")
"""
    }
}
