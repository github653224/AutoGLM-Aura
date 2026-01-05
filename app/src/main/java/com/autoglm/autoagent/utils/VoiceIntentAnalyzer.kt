package com.autoglm.autoagent.utils

import android.util.Log
import com.autoglm.autoagent.data.api.AIClient
import com.autoglm.autoagent.data.api.ChatMessage
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 语音意图分析器（AI 驱动）
 * 判断用户语音回复是同意还是拒绝
 */
@Singleton
class VoiceIntentAnalyzer @Inject constructor(
    private val aiClient: AIClient
) {

    private val intentPrompt = """
你是一个意图识别专家。用户会给你一段语音转文字的内容，你需要判断用户是"同意执行"还是"拒绝执行"某个任务。

**同意的表达**（示例）：
- "好"、"行"、"可以"、"开始吧"、"执行"、"搞起"、"没问题"
- "确认"、"同意"、"就这样"、"开始"

**拒绝的表达**（示例）：
- "不"、"不行"、"取消"、"算了"、"不要"、"停"
- "不对"、"不是"、"错了"

**输出格式**（严格 JSON）：
{
  "intent": "confirm" 或 "reject",
  "confidence": 0.0-1.0
}

现在分析以下语音内容：
""".trimIndent()

    /**
     * 分析用户语音意图
     * @return true = 同意，false = 拒绝
     */
    suspend fun analyzeIntent(voiceText: String): VoiceIntent {
        return try {
            withTimeout(2000L) {
                val messages = listOf(
                    ChatMessage("system", intentPrompt),
                    ChatMessage("user", voiceText)
                )
                
                val response = aiClient.sendMessage(messages)
                val content = response.content ?: ""
                
                Log.d("VoiceIntent", "AI 分析结果: $content")
                
                parseIntentResult(content)
            }
        } catch (e: Exception) {
            Log.e("VoiceIntent", "意图分析失败", e)
            // 降级策略：无法判断则拒绝（安全优先）
            VoiceIntent(isConfirm = false, confidence = 0.0f)
        }
    }

    private fun parseIntentResult(content: String): VoiceIntent {
        return try {
            val jsonStr = content
                .replace(Regex("```json\\s*"), "")
                .replace(Regex("```\\s*"), "")
                .trim()
            
            val json = JSONObject(jsonStr)
            val intent = json.optString("intent", "reject")
            val confidence = json.optDouble("confidence", 0.0).toFloat()
            
            VoiceIntent(
                isConfirm = intent == "confirm",
                confidence = confidence
            )
        } catch (e: Exception) {
            Log.e("VoiceIntent", "JSON 解析失败: $content", e)
            VoiceIntent(isConfirm = false, confidence = 0.0f)
        }
    }
}

/**
 * 语音意图分析结果
 */
data class VoiceIntent(
    val isConfirm: Boolean,
    val confidence: Float
)
