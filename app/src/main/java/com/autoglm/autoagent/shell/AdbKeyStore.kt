package com.autoglm.autoagent.shell

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 管理 ADB 密钥和证书的持久化存储 (回退版 - 移除 Kadb)
 */
@Singleton
class AdbKeyStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val keyFile: File
        get() = File(context.filesDir, "adb_private_key")
    
    private val certFile: File
        get() = File(context.filesDir, "adb_certificate.pem")
    
    /**
     * 初始化 (占位)
     */
    fun initializeKadb() {
        Log.d(TAG, "AdbKeyStore.initializeKadb (stub)")
    }
    
    /**
     * 检查是否已有密钥
     */
    fun hasKeyPair(): Boolean {
        return keyFile.exists() && certFile.exists()
    }
    
    /**
     * 删除密钥
     */
    fun deleteKeyPair() {
        keyFile.delete()
        certFile.delete()
        Log.d(TAG, "Deleted ADB key files")
    }
    
    companion object {
        private const val TAG = "AdbKeyStore"
    }
}
