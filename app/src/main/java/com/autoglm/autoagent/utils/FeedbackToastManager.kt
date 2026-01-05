package com.autoglm.autoagent.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.autoglm.autoagent.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.util.regex.Pattern

/**
 * 管理 Toast 反馈，支持智能裁剪和截图前自动隐藏
 */
@Singleton
class FeedbackToastManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var currentToast: Toast? = null
    private val handler = Handler(Looper.getMainLooper())
    private val MAX_LENGTH = 50

    /**
     * 显示普通反馈
     */
    fun show(message: String, length: Int = Toast.LENGTH_SHORT) {
        val processed = smartTruncate(cleanAIResponse(message))
        showInternal(processed, length)
    }

    /**
     * 显示操作反馈
     */
    fun showAction(action: String, target: String) {
        val emoji = when (action.lowercase()) {
            "tap", "click" -> "👆"
            "type", "input" -> "⌨️"
            "scroll", "swipe" -> "📜"
            "back" -> "🔙"
            "home" -> "🏠"
            "launch", "open" -> "🚀"
            "wait" -> "⏳"
            else -> "🤖"
        }
        val cleanTarget = target.take(20).replace("\n", " ")
        showInternal("$emoji $action: $cleanTarget", Toast.LENGTH_SHORT)
    }

    /**
     * 截图前立即取消 Toast
     */
    fun cancelForScreenshot() {
        handler.post {
            currentToast?.cancel()
            currentToast = null
        }
    }

    private fun showInternal(text: String, length: Int) {
        handler.post {
            // Cancel previous to avoid stack up
            currentToast?.cancel()
            
            if (text.isNotBlank()) {
                currentToast = Toast.makeText(context, text, length)
                currentToast?.show()
            }
        }
    }

    // === 智能处理逻辑 ===

    private fun cleanAIResponse(text: String): String {
        var cleaned = text
        // 移除 JSON/Markdown
        cleaned = cleaned.replace(Regex("```json\\s*"), "")
            .replace(Regex("```\\s*"), "")
        // 移除 thinking
        cleaned = cleaned.replace(Regex("(?s)<think>.*?</think>"), "")
            .replace(Regex("(?s)\\{think\\}.*?\\{/think\\}"), "")
        // 移除废话
        val fillers = listOf("好的", "明白", "收到", "正在", "我将")
        fillers.forEach { 
             if (cleaned.startsWith(it)) cleaned = cleaned.substring(it.length)
        }
        return cleaned.trim()
    }

    private fun smartTruncate(text: String): String {
        if (text.length <= MAX_LENGTH) return text
        
        // 尝试提取动词短语
        val actionPattern = Pattern.compile("(点击|输入|打开|去|搜索)(.{1,10})")
        val matcher = actionPattern.matcher(text)
        if (matcher.find()) {
            return "${matcher.group(1)}${matcher.group(2)}..."
        }
        
        return text.take(MAX_LENGTH - 3) + "..."
    }
    
    // === 状态栏通知 ===
    
    companion object {
        private const val CHANNEL_ID = "autoglm_status"
        private const val NOTIFICATION_ID_FALLBACK = 1001
        private const val NOTIFICATION_ID_TASK_COMPLETE = 1002
    }
    
    private val notificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    
    init {
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AutoGLM 状态",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "任务状态和服务降级通知"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * 服务降级通知
     */
    fun notifyServiceFallback(fromMode: String, toMode: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("AutoGLM 服务降级")
            .setContentText("已从 $fromMode 切换到 $toMode")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_FALLBACK, notification)
    }
    
    /**
     * 服务不可用通知
     */
    fun notifyServiceUnavailable() {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("AutoGLM 无法执行")
            .setContentText("Shell 服务和无障碍服务均不可用")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_FALLBACK, notification)
    }
    
    /**
     * 任务完成通知
     */
    fun notifyTaskCompleted(message: String = "任务已完成") {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("AutoGLM")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_TASK_COMPLETE, notification)
    }
}
