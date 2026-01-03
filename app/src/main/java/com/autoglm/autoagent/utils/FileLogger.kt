package com.autoglm.autoagent.utils

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileLogger @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val fileDateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    
    private val logDir = File(context.filesDir, "logs").apply { mkdirs() }
    private val maxFileSize = 5 * 1024 * 1024L // 5MB
    private val maxLogAge = 7 // 保留7天
    
    init {
        // 启动时清理旧日志
        cleanOldLogs()
    }
    
    fun log(tag: String, level: LogLevel, message: String) {
        // 同时输出到 Logcat（方便开发调试）
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, message)
            LogLevel.INFO -> Log.i(tag, message)
            LogLevel.WARN -> Log.w(tag, message)
            LogLevel.ERROR -> Log.e(tag, message)
        }
        
        // 异步写入文件
        scope.launch {
            writeToFile(tag, level, message)
        }
    }
    
    @Synchronized
    private fun writeToFile(tag: String, level: LogLevel, message: String) {
        try {
            val logFile = getCurrentLogFile()
            
            // 检查文件大小，超过限制则滚动
            if (logFile.exists() && logFile.length() > maxFileSize) {
                rotateLogFile(logFile)
            }
            
            val timestamp = dateFormat.format(Date())
            val logEntry = "[$timestamp] [${level.name}] [$tag] $message\n"
            
            BufferedWriter(FileWriter(logFile, true)).use { writer ->
                writer.write(logEntry)
            }
        } catch (e: Exception) {
            Log.e("FileLogger", "Failed to write log", e)
        }
    }
    
    private fun getCurrentLogFile(): File {
        val today = fileDateFormat.format(Date())
        return File(logDir, "app_log_$today.txt")
    }
    
    private fun rotateLogFile(file: File) {
        // 重命名为带时间戳的备份文件
        val timestamp = System.currentTimeMillis()
        val backupFile = File(file.parent, "${file.nameWithoutExtension}_$timestamp.txt")
        file.renameTo(backupFile)
    }
    
    private fun cleanOldLogs() {
        scope.launch {
            try {
                val cutoffTime = System.currentTimeMillis() - (maxLogAge * 24 * 60 * 60 * 1000L)
                logDir.listFiles()?.forEach { file ->
                    if (file.lastModified() < cutoffTime) {
                        file.delete()
                        Log.d("FileLogger", "Deleted old log: ${file.name}")
                    }
                }
            } catch (e: Exception) {
                Log.e("FileLogger", "Failed to clean old logs", e)
            }
        }
    }
    
    fun getAllLogs(): List<File> {
        return logDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
    
    fun getLogsByDate(date: String): File? {
        val fileName = "app_log_$date.txt"
        val file = File(logDir, fileName)
        return if (file.exists()) file else null
    }
    
    fun clearAllLogs() {
        scope.launch {
            try {
                logDir.listFiles()?.forEach { it.delete() }
                Log.i("FileLogger", "All logs cleared")
            } catch (e: Exception) {
                Log.e("FileLogger", "Failed to clear logs", e)
            }
        }
    }
    
    enum class LogLevel {
        DEBUG, INFO, WARN, ERROR
    }
}

// 扩展函数，方便调用
fun FileLogger.d(tag: String, message: String) = log(tag, FileLogger.LogLevel.DEBUG, message)
fun FileLogger.i(tag: String, message: String) = log(tag, FileLogger.LogLevel.INFO, message)
fun FileLogger.w(tag: String, message: String) = log(tag, FileLogger.LogLevel.WARN, message)
fun FileLogger.e(tag: String, message: String) = log(tag, FileLogger.LogLevel.ERROR, message)
