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
 * 决策智能体（Decision Agent）
 * 使用 ZHIPU 大模型进行任务规划和决策
 */
@Singleton
class DecisionAgent @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val aiClient: AIClient
) {

    private val messages = mutableListOf<ChatMessage>()
    private var isAvailable = true

    /**
     * 检查 DecisionAgent 是否可用
     */
    fun checkAvailability(): Boolean {
        return try {
            val config = settingsRepository.loadProviderConfig(ApiProvider.ZHIPU)
            config.apiKey.isNotBlank() && config.baseUrl.isNotBlank()
        } catch (e: Exception) {
            Log.e("DecisionAgent", "可用性检查失败", e)
            false
        }
    }

    /**
     * 分析任务并生成执行计划
     */
    suspend fun analyzeTask(task: String): TaskPlan {
        return try {
            messages.clear()
            messages.add(ChatMessage("system", DECISION_SYSTEM_PROMPT))
            messages.add(ChatMessage("user", """
                请分析以下任务并生成执行计划：
                
                任务：$task
                
                返回 JSON 格式：
                {
                  "summary": "任务概要",
                  "steps": ["步骤1", "步骤2", ...],
                  "estimatedActions": 5
                }
            """.trimIndent()))

            // Get ZHIPU config
            val config = settingsRepository.loadProviderConfig(ApiProvider.ZHIPU)
            
            val response = aiClient.sendMessage(messages, overrideConfig = config)
            
            // Add assistance message to history (use text content)
            val contentStr = response.content ?: ""
            messages.add(ChatMessage("assistant", contentStr))

            parseTaskPlan(contentStr, task)
        } catch (e: Exception) {
            Log.e("DecisionAgent", "任务分析失败", e)
            isAvailable = false
            // 降级：返回简单计划
            TaskPlan(summary = task, steps = listOf(task), estimatedActions = 5)
        }
    }

    /**
     * 根据当前状态做出决策
     */
    suspend fun makeDecision(
        uiTree: String,
        currentApp: String,
        screenshot: String? = null
    ): Decision {
        return try {
            // 构建消息内容
            val contentParts = mutableListOf<com.autoglm.autoagent.data.api.ContentPart>()
            
            // 如果有截图，添加图片
            if (screenshot != null) {
                contentParts.add(com.autoglm.autoagent.data.api.ContentPart(
                    type = "image_url",
                    image_url = com.autoglm.autoagent.data.api.ImageUrl("data:image/png;base64,$screenshot")
                ))
            }
            
            // 添加文本内容（UI树 + 当前App）
            val textContent = buildString {
                append("当前状态：\n")
                append("App: $currentApp\n\n")
                append("UI树：\n```json\n$uiTree\n```\n\n")
                if (screenshot != null) {
                    append("（已提供截图辅助识别）\n\n")
                }
                append("请根据UI树决定下一步操作，返回 JSON 格式：\n")
                append("{\n")
                append("  \"action\": \"tap/type/back/home/finish\",\n")
                append("  \"target\": \"元素描述或坐标\",\n")
                append("  \"reasoning\": \"决策理由\",\n")
                append("  \"finished\": false\n")
                append("}")
            }
            contentParts.add(com.autoglm.autoagent.data.api.ContentPart(type = "text", text = textContent))
            
            // 添加消息
            messages.add(ChatMessage("user", contentParts))

            // Get ZHIPU config
            val config = settingsRepository.loadProviderConfig(ApiProvider.ZHIPU)

            val response = aiClient.sendMessage(messages, overrideConfig = config)
            
            val contentStr = response.content ?: ""
            messages.add(ChatMessage("assistant", contentStr))

            parseDecision(contentStr)
        } catch (e: Exception) {
            Log.e("DecisionAgent", "决策失败", e)
            isAvailable = false
            throw e // 让上层处理降级
        }
    }

    /**
     * 重新规划（异常恢复）
     */
    suspend fun replan(
        errorContext: String,
        executedActions: List<String>,
        uiTree: String
    ): Decision {
        return try {
            val replanPrompt = """
                ⚠️ 检测到异常，需要重新规划：
                
                异常信息：$errorContext
                已执行操作：${executedActions.joinToString(", ")}
                当前UI树：$uiTree
                
                请分析问题并决定下一步操作
            """.trimIndent()

            messages.add(ChatMessage("user", replanPrompt))

            // Get ZHIPU config
            val config = settingsRepository.loadProviderConfig(ApiProvider.ZHIPU)

            val response = aiClient.sendMessage(messages, overrideConfig = config)
            val contentStr = response.content ?: ""
            messages.add(ChatMessage("assistant", contentStr))

            parseDecision(contentStr)
        } catch (e: Exception) {
            Log.e("DecisionAgent", "重规划失败", e)
            throw e
        }
    }

    private fun parseTaskPlan(response: String, fallbackTask: String): TaskPlan {
        return try {
            val jsonStr = response
                .replace(Regex("```json\\s*"), "")
                .replace(Regex("```\\s*"), "")
                .trim()

            val json = JSONObject(jsonStr)
            TaskPlan(
                summary = json.optString("summary", fallbackTask),
                steps = parseJsonArray(json.optJSONArray("steps")),
                estimatedActions = json.optInt("estimatedActions", 5)
            )
        } catch (e: Exception) {
            Log.w("DecisionAgent", "TaskPlan 解析失败，使用降级", e)
            TaskPlan(summary = fallbackTask, steps = listOf(fallbackTask), estimatedActions = 5)
        }
    }

    private fun parseDecision(response: String): Decision {
        return try {
            val jsonStr = response
                .replace(Regex("```json\\s*"), "")
                .replace(Regex("```\\s*"), "")
                .trim()

            val json = JSONObject(jsonStr)
            Decision(
                action = json.optString("action", "tap"),
                target = json.optString("target", ""),
                reasoning = json.optString("reasoning", ""),
                finished = json.optBoolean("finished", false)
            )
        } catch (e: Exception) {
            Log.w("DecisionAgent", "Decision 解析失败", e)
            Decision(action = "unknown", target = "", reasoning = response, finished = false)
        }
    }

    private fun parseJsonArray(jsonArray: org.json.JSONArray?): List<String> {
        if (jsonArray == null) return emptyList()
        return (0 until jsonArray.length()).map { jsonArray.getString(it) }
    }

    companion object {
        private const val DECISION_SYSTEM_PROMPT = """
你是一个 Android 手机任务规划和决策专家。你的职责是：
1. 分析用户任务并制定执行计划
2. 根据当前屏幕状态决定下一步操作
3. 异常时重新规划

请始终返回结构化的 JSON 格式响应。
"""
    }
}

/**
 * 任务计划
 */
data class TaskPlan(
    val summary: String,
    val steps: List<String>,
    val estimatedActions: Int
)

/**
 * 决策结果
 */
data class Decision(
    val action: String,
    val target: String,
    val reasoning: String,
    val finished: Boolean
)
