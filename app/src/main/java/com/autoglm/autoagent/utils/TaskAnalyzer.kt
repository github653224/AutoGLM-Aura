package com.autoglm.autoagent.utils

import android.util.Log
import com.autoglm.autoagent.data.api.AIClient
import com.autoglm.autoagent.data.api.ChatMessage
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 任务复杂度分析器（AI 驱动）
 * 通过 LLM 判断用户任务是否涉及跨 App 操作
 */
@Singleton
class TaskAnalyzer @Inject constructor(
    private val aiClient: AIClient
) {

    private val analysisPrompt = """
你是一个任务分类专家。用户会给你一个手机操作任务，你需要判断它是否为"跨App任务"。

**跨App任务定义**：
- 需要在多个不同的 App 之间切换操作（如：比价、从微信复制到美团、先淘宝再京东）
- 示例：
  - ✅ 跨App："去京东和淘宝比价 iPhone 15"（涉及京东、淘宝两个App）
  - ✅ 跨App："把微信聊天记录里的地址复制到美团外卖"（涉及微信、美团）
  - ❌ 单App："打开微信发消息给张三"（仅涉及微信）
  - ❌ 单App："刷一会儿抖音"（仅涉及抖音）

**输出格式**（严格 JSON）：
{
  "isComplex": true/false,
  "reason": "简短原因（20字以内）",
  "apps": ["app1", "app2"]
}

现在分析以下任务：
""".trimIndent()

    /**
     * 分析任务复杂度
     * @return true = 复杂任务（跨App），false = 简单任务（单App）
     */
    suspend fun analyzeTask(taskText: String): TaskComplexity {
        return try {
            // 超时保护：最多等待 3 秒
            withTimeout(3000L) {
                val messages = listOf(
                    ChatMessage("system", analysisPrompt),
                    ChatMessage("user", taskText)
                )
                
                val response = aiClient.sendMessage(messages)
                val content = response.content ?: ""
                
                Log.d("TaskAnalyzer", "AI 分析结果: $content")
                
                // 解析 JSON 响应
                parseAnalysisResult(content)
            }
        } catch (e: Exception) {
            Log.e("TaskAnalyzer", "AI 分析失败，降级为简单模式", e)
            // 降级策略：分析失败时默认为简单任务
            TaskComplexity(isComplex = false, reason = "分析超时，默认简单模式", apps = emptyList())
        }
    }

    private fun parseAnalysisResult(content: String): TaskComplexity {
        return try {
            // 提取 JSON（处理可能的 markdown 包裹）
            val jsonStr = content
                .replace(Regex("```json\\s*"), "")
                .replace(Regex("```\\s*"), "")
                .trim()
            
            val json = JSONObject(jsonStr)
            val isComplex = json.optBoolean("isComplex", false)
            val reason = json.optString("reason", "未知")
            val appsArray = json.optJSONArray("apps")
            val apps = mutableListOf<String>()
            
            if (appsArray != null) {
                for (i in 0 until appsArray.length()) {
                    apps.add(appsArray.getString(i))
                }
            }
            
            TaskComplexity(isComplex, reason, apps)
        } catch (e: Exception) {
            Log.e("TaskAnalyzer", "JSON 解析失败: $content", e)
            TaskComplexity(isComplex = false, reason = "解析失败", apps = emptyList())
        }
    }
}

/**
 * 任务复杂度分析结果
 */
data class TaskComplexity(
    val isComplex: Boolean,
    val reason: String,
    val apps: List<String>
)
