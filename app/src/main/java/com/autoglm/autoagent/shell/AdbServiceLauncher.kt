package com.autoglm.autoagent.shell

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ADB Service Launcher (回退版 - 移除 Kadb)
 */
@Singleton
class AdbServiceLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyStore: AdbKeyStore,
    private val deployer: ShellServiceDeployer
) {
    /**
     * 配对 (Stub)
     */
    suspend fun pair(pairingPort: Int, pairingCode: String): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "pair method is now a stub (Kadb removed)")
        false
    }

    /**
     * 连接 (Stub)
     */
    suspend fun connect(connectPort: Int): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "connect method is now a stub (Kadb removed)")
        false
    }
    
    /**
     * 启动服务 (Stub)
     */
    suspend fun launchService(connectPort: Int): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "launchService via ADB is now a stub (Kadb removed)")
        false
    }
    
    fun disconnect() {
        Log.d(TAG, "disconnect (stub)")
    }
    
    fun hasPairedKey(): Boolean = false
    
    companion object {
        private const val TAG = "AdbServiceLauncher"
    }
}
