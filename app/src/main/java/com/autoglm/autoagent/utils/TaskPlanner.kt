package com.autoglm.autoagent.utils

import android.util.Log
import com.autoglm.autoagent.data.api.AIClient
import com.autoglm.autoagent.data.api.ChatMessage
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 任务规划器（AI 驱动）
 * 为复杂任务生成多步骤执行计划
 */
@Singleton
class TaskPlanner @Inject constructor(
    private val aiClient: AIClient
) {

    private val planningPrompt = """
你是一个任务规划专家。用户会给你一个跨App的复杂任务，你需要生成一个清晰的多步骤执行计划。

**规划原则**：
1. 分解为可执行的原子步骤
2. 每一步明确指定需要操作的 App
3. 步骤顺序合理，逻辑清晰
4. 控制在 3-7 步之间（不要过于细碎）

**输出格式**（严格 JSON）：
{
  "task": "任务描述",
  "steps": [
    {"step": 1, "app": "淘宝", "action": "搜索 iPhone 15 并记录价格"},
    {"step": 2, "app": "京东", "action": "搜索 iPhone 15 并记录价格"},
    {"step": 3, "app": "微信", "action": "发送比价结果给用户"}
  ],
  "summary": "简短的计划总结（30字以内）"
}

现在为以下任务生成计划：
""".trimIndent()

    /**
     * 生成任务执行计划
     */
    suspend fun generatePlan(taskText: String): TaskPlan {
        return try {
            withTimeout(5000L) {
                val messages = listOf(
                    ChatMessage("system", planningPrompt),
                    ChatMessage("user", taskText)
                )
                
                val response = aiClient.sendMessage(messages)
                val content = response.content ?: ""
                
                Log.d("TaskPlanner", "AI 规划结果: $content")
                
                parsePlanResult(content, taskText)
            }
        } catch (e: Exception) {
            Log.e("TaskPlanner", "规划生成失败", e)
            // 降级策略：返回空计划
            TaskPlan(
                task = taskText,
                steps = emptyList(),
                summary = "规划失败，请重试"
            )
        }
    }

    private fun parsePlanResult(content: String, fallbackTask: String): TaskPlan {
        return try {
            val jsonStr = content
                .replace(Regex("```json\\s*"), "")
                .replace(Regex("```\\s*"), "")
                .trim()
            
            val json = JSONObject(jsonStr)
            val task = json.optString("task", fallbackTask)
            val summary = json.optString("summary", "")
            val stepsArray = json.optJSONArray("steps") ?: JSONArray()
            
            val steps = mutableListOf<PlanStep>()
            for (i in 0 until stepsArray.length()) {
                val stepObj = stepsArray.getJSONObject(i)
                steps.add(
                    PlanStep(
                        step = stepObj.optInt("step", i + 1),
                        app = stepObj.optString("app", ""),
                        action = stepObj.optString("action", "")
                    )
                )
            }
            
            TaskPlan(task, steps, summary)
        } catch (e: Exception) {
            Log.e("TaskPlanner", "JSON 解析失败: $content", e)
            TaskPlan(
                task = fallbackTask,
                steps = emptyList(),
                summary = "解析失败"
            )
        }
    }
}

/**
 * 任务执行计划
 */
data class TaskPlan(
    val task: String,
    val steps: List<PlanStep>,
    val summary: String
) {
    fun toDisplayString(): String {
        if (steps.isEmpty()) return summary
        
        val builder = StringBuilder()
        builder.append("📋 $task\n\n")
        steps.forEach { step ->
            builder.append("${step.step}. [${step.app}] ${step.action}\n")
        }
        builder.append("\n💡 $summary")
        return builder.toString()
    }
}

/**
 * 单个计划步骤
 */
data class PlanStep(
    val step: Int,
    val app: String,
    val action: String
)
