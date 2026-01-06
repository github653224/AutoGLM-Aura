package com.autoglm.autoagent.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.autoglm.autoagent.shell.IAutoDroidShell
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shizuku 管理器
 * 负责检查和管理 Shizuku 状态，并管理与 AutoDroidUserService 的 Binder 连接。
 */
@Singleton
class ShizukuManager @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) {
    private var userService: IAutoDroidShell? = null
    private val _isServiceConnected = MutableStateFlow(false)
    val isServiceConnected = _isServiceConnected.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.d("ShizukuManager", "📡 Shizuku Binder received, attempting auto-bind...")
        tryAutoBind()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.w("ShizukuManager", "💀 Shizuku Binder dead, service disconnected")
        _isServiceConnected.value = false
        userService = null
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        if (result == PackageManager.PERMISSION_GRANTED) {
            Log.d("ShizukuManager", "✅ Permission granted via request result, binding...")
            bindService()
        }
    }

    /**
     * 初始化监听器 (由 MainActivity 或 AutoAgentService 调用)
     */
    fun initialize() {
        Log.d("ShizukuManager", "🔍 Initializing Shizuku listeners...")
        try {
            Shizuku.addBinderReceivedListener(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
            
            // 初次尝试
            tryAutoBind()
        } catch (e: Exception) {
            Log.e("ShizukuManager", "Failed to add Shizuku listeners", e)
        }
    }

    /**
     * 自动尝试绑定逻辑
     */
    private fun tryAutoBind() {
        if (isShizukuRunning() && hasPermission()) {
            bindService()
        }
    }

    /**
     * 确保服务已连接，若未连接则尝试静默重连
     */
    fun ensureConnected(): Boolean {
        if (userService != null && isShizukuRunning()) {
            try {
                if (userService?.ping() == true) return true
            } catch (e: Exception) {
                Log.w("ShizukuManager", "Service ping failed, attempting reconnect")
            }
        }
        return bindService()
    }
    
    /**
     * 在本地进程初始化 Binder 包装服务 (Ruto-GLM 模式)
     */
    fun bindService(): Boolean {
        if (!hasPermission()) {
            Log.e("ShizukuManager", "Cannot connect: No Shizuku permission")
            return false
        }
        
        return try {
            Log.d("ShizukuManager", "🚀 Initializing Direct Binder Shell...")
            // 直接在当前进程创建服务实例
            // 由于该实例内部使用了 ShizukuBinderWrapper，它发出的所有请求都将带有 Shizuku 权限
            userService = com.autoglm.autoagent.shell.AutoDroidUserService(context)
            _isServiceConnected.value = true
            Log.d("ShizukuManager", "✅ Direct Binder Shell initialized")
            true
        } catch (e: Exception) {
            Log.e("ShizukuManager", "Failed to init local shell service", e)
            _isServiceConnected.value = false
            false
        }
    }

    /**
     * 断开连接
     */
    fun unbindService() {
        userService = null
        _isServiceConnected.value = false
        Log.d("ShizukuManager", "Disconnected from local shell service")
    }

    /**
     * 获取当前服务接口
     */
    fun getService(): IAutoDroidShell? = userService

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
     * 通过 Shizuku 执行 Shell 命令 (保留作为通用用途)
     */
    fun runCommand(command: String): Boolean {
        // [Safety] 禁止自杀：防止命令误杀自己
        if (command.contains("force-stop") && command.contains("com.autoglm.autoagent")) {
            Log.e("ShizukuManager", "⛔ 拦截自杀命令: $command")
            return false
        }

        if (!hasPermission()) {
            Log.e("ShizukuManager", "Cannot run command: No permission")
            return false
        }
        
        Log.d("ShizukuManager", "📝 Executing command: $command")
        
        return try {
            val newProcessMethod = try {
                Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
            } catch (e: NoSuchMethodException) {
                Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java
                )
            }
            newProcessMethod.isAccessible = true
            
            val process = if (newProcessMethod.parameterCount == 3) {
                newProcessMethod.invoke(null, arrayOf("sh", "-c", command), null, null)
            } else {
                newProcessMethod.invoke(null, arrayOf("sh", "-c", command), null)
            } as Process
            
            val exitCode = process.waitFor()
            val success = exitCode == 0
            
            if (success) {
                Log.d("ShizukuManager", "✅ Command succeeded (exit=$exitCode): $command")
            } else {
                val error = process.errorStream.bufferedReader().readText()
                Log.w("ShizukuManager", "❌ Command failed (exit=$exitCode): $command\nError: $error")
            }
            
            success
        } catch (e: Exception) {
            Log.e("ShizukuManager", "❌ Failed to run command via Shizuku: ${e.message}", e)
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
