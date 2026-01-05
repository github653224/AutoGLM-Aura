package com.autoglm.autoagent.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shizuku 管理器
 * 负责检查和管理 Shizuku 状态
 */
@Singleton
class ShizukuManager @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) {
    
    /**
     * 检查 Shizuku 是否已安装
     */
    fun isShizukuInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(
                "moe.shizuku.privileged.api",
                0
            )
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 检查 Shizuku 服务是否正在运行
     */
    fun isShizukuRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            Log.e("ShizukuManager", "Shizuku not running", e)
            false
        }
    }
    
    /**
     * 检查是否已授权
     */
    fun hasPermission(): Boolean {
        if (!isShizukuRunning()) return false
        
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.e("ShizukuManager", "Permission check failed", e)
            false
        }
    }
    
    /**
     * 请求 Shizuku 权限
     */
    fun requestPermission() {
        if (!isShizukuRunning()) {
            Log.w("ShizukuManager", "Cannot request permission: Shizuku not running")
            return
        }
        
        if (hasPermission()) {
            Log.d("ShizukuManager", "Already has permission")
            return
        }
        
        try {
            Shizuku.requestPermission(REQUEST_CODE_SHIZUKU)
            Log.d("ShizukuManager", "Permission requested")
        } catch (e: Exception) {
            Log.e("ShizukuManager", "Failed to request permission", e)
        }
    }
    
    /**
     * 获取激活状态
     */
    fun getActivationStatus(): ActivationStatus {
        return when {
            !isShizukuInstalled() -> ActivationStatus.NOT_INSTALLED
            !isShizukuRunning() -> ActivationStatus.NOT_RUNNING
            !hasPermission() -> ActivationStatus.NO_PERMISSION
            else -> ActivationStatus.ACTIVATED
        }
    }
    
    /**
     * 通过 Shizuku 执行 Shell 命令
     */
    fun runCommand(command: String): Boolean {
        if (!hasPermission()) {
            Log.e("ShizukuManager", "Cannot run command: No permission")
            return false
        }
        
        return try {
            // 使用 sh -c 执行多条命令
            // 使用反射调用隐藏的 newProcess 方法
            // Shizuku.newProcess(String[] cmd, String[] env, String dir)
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProcessMethod.isAccessible = true
            val process = newProcessMethod.invoke(
                null, 
                arrayOf("sh", "-c", command), 
                null, 
                null
            ) as Process
            val exitCode = process.waitFor()
            Log.d("ShizukuManager", "Command executed via Shizuku, exit code: $exitCode")
            exitCode == 0
        } catch (e: Exception) {
            Log.e("ShizukuManager", "Failed to run command via Shizuku", e)
            false
        }
    }
    
    companion object {
        private const val REQUEST_CODE_SHIZUKU = 1001
    }
}

/**
 * Shizuku 激活状态
 */
enum class ActivationStatus {
    NOT_INSTALLED,  // 未安装 Shizuku
    NOT_RUNNING,    // Shizuku 未运行
    NO_PERMISSION,  // 未授权
    ACTIVATED       // 已激活
}
