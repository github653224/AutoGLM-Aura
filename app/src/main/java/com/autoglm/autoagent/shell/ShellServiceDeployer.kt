package com.autoglm.autoagent.shell

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shell Service Deployer
 * 
 * Responsible for:
 * 1. Deploying server.dex from assets to filesDir
 * 2. Generating security token (UUID)
 * 3. Generating activation command
 */
@Singleton
class ShellServiceDeployer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val TAG = "ShellServiceDeployer"
        // Increment this version when Server code changes to force re-deployment
        private const val SERVER_VERSION = 2
    }
    
    private val dexFile: File
        get() = File(context.filesDir, "server.dex")
    
    private val tokenFile: File
        get() = File(context.filesDir, "server_token.txt")

    private val versionFile: File
        get() = File(context.filesDir, "server_version.txt")
    
    /**
     * Deploy server.dex to private storage
     * Called once when user activates advanced mode
     */
    fun deployServerDex(): Boolean {
        return try {
            Log.d(TAG, "Deploying server.dex (v$SERVER_VERSION)...")
            
            // Copy from assets
            context.assets.open("server.dex").use { input ->
                dexFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            // Make it readable (chmod 644)
            dexFile.setReadable(true, false)
            
            // Save version
            versionFile.writeText(SERVER_VERSION.toString())
            
            Log.d(TAG, "✅ server.dex deployed: ${dexFile.absolutePath} (${dexFile.length()} bytes)")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to deploy server.dex", e)
            false
        }
    }
    
    /**
     * Generate or retrieve security token
     * Token is persisted to prevent changing across sessions
     */
    fun getOrGenerateToken(): String {
        // Check existing token
        if (tokenFile.exists()) {
            try {
                val token = tokenFile.readText().trim()
                if (token.isNotEmpty()) {
                    Log.d(TAG, "Using existing token")
                    return token
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read existing token", e)
            }
        }
        
        // Generate new token
        val newToken = UUID.randomUUID().toString()
        
        try {
            tokenFile.writeText(newToken)
            tokenFile.setReadable(true, true) // Only owner can read
            Log.d(TAG, "✅ Generated new token")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save token", e)
        }
        
        return newToken
    }
    
    /**
     * Generate activation command for user to execute
     * 
     * @return ADB command string
     */
    fun getActivationCommand(): String {
        val token = getOrGenerateToken()
        val pkgName = context.packageName
        
        // Create file first with proper permissions, then write content
        return """
rm -f /data/local/tmp/autoglm_server.dex 2>/dev/null
touch /data/local/tmp/autoglm_server.dex
chmod 666 /data/local/tmp/autoglm_server.dex
run-as $pkgName cat files/server.dex > /data/local/tmp/autoglm_server.dex
chmod 755 /data/local/tmp/autoglm_server.dex
export CLASSPATH=/data/local/tmp/autoglm_server.dex
app_process /system/bin com.autoglm.server.ServerMain $token
        """.trimIndent()
    }
    
    /**
     * Check if server.dex is deployed AND matches current version
     */
    fun isDeployed(): Boolean {
        if (!dexFile.exists() || dexFile.length() == 0L) return false
        
        // Check version
        if (!versionFile.exists()) return false
        
        return try {
            val savedVersion = versionFile.readText().trim().toInt()
            savedVersion >= SERVER_VERSION
        } catch (e: Exception) {
            false
        }
    }
}
