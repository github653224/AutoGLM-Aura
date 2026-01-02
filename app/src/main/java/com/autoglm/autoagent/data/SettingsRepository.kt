package com.autoglm.autoagent.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ApiProvider {
    EDGE, ZHIPU
}

data class ApiConfig(
    val provider: ApiProvider,
    val baseUrl: String,
    val apiKey: String,
    val model: String
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // 旧的非加密存储
    private val oldPrefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    
    // 加密存储
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_settings",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<ApiConfig> = _config.asStateFlow()

    private fun loadConfig(): ApiConfig {
        // 读取当前选中的 provider
        val providerStr = encryptedPrefs.getString("current_provider", ApiProvider.ZHIPU.name) ?: ApiProvider.ZHIPU.name
        val provider = try { ApiProvider.valueOf(providerStr) } catch (e: Exception) { ApiProvider.ZHIPU }
        
        // 根据 provider 读取对应的配置
        val (baseUrl, apiKey, model) = when (provider) {
            ApiProvider.EDGE -> Triple(
                encryptedPrefs.getString("edge_base_url", "") ?: "",
                encryptedPrefs.getString("edge_api_key", "") ?: "",
                encryptedPrefs.getString("edge_model", "") ?: ""
            )
            ApiProvider.ZHIPU -> Triple(
                encryptedPrefs.getString("zhipu_base_url", "https://open.bigmodel.cn/api/paas/v4/") ?: "",
                encryptedPrefs.getString("zhipu_api_key", "") ?: "",
                encryptedPrefs.getString("zhipu_model", "glm-4-flash") ?: ""
            )
        }
        
        return ApiConfig(provider, baseUrl, apiKey, model)
    }

    fun saveConfig(provider: ApiProvider, baseUrl: String, apiKey: String, model: String) {
        // 保存当前选中的 provider
        encryptedPrefs.edit().putString("current_provider", provider.name).apply()
        
        // 根据 provider 保存到对应的字段
        when (provider) {
            ApiProvider.EDGE -> {
                encryptedPrefs.edit().apply {
                    putString("edge_base_url", baseUrl)
                    putString("edge_api_key", apiKey)
                    putString("edge_model", model)
                    apply()
                }
            }
            ApiProvider.ZHIPU -> {
                encryptedPrefs.edit().apply {
                    putString("zhipu_base_url", baseUrl)
                    putString("zhipu_api_key", apiKey)
                    putString("zhipu_model", model)
                    apply()
                }
            }
        }
        
        _config.value = ApiConfig(provider, baseUrl, apiKey, model)
    }
    
    fun loadProviderConfig(provider: ApiProvider): ApiConfig {
        val (baseUrl, apiKey, model) = when (provider) {
            ApiProvider.EDGE -> Triple(
                encryptedPrefs.getString("edge_base_url", "") ?: "",
                encryptedPrefs.getString("edge_api_key", "") ?: "",
                encryptedPrefs.getString("edge_model", "") ?: ""
            )
            ApiProvider.ZHIPU -> Triple(
                encryptedPrefs.getString("zhipu_base_url", "https://open.bigmodel.cn/api/paas/v4/") ?: "",
                encryptedPrefs.getString("zhipu_api_key", "") ?: "",
                encryptedPrefs.getString("zhipu_model", "glm-4-flash") ?: ""
            )
        }
        return ApiConfig(provider, baseUrl, apiKey, model)
    }
    
    // 保存应用列表到配置
    fun saveAppList(appMap: Map<String, String>) {
        val json = appMap.entries.joinToString(",") { 
            "\"${it.key}\":\"${it.value}\"" 
        }
        oldPrefs.edit().putString("app_list", "{$json}").apply()
    }
    
    // 从配置加载应用列表
    fun loadAppList(): Map<String, String> {
        val json = oldPrefs.getString("app_list", null) ?: return emptyMap()
        return try {
            // 简单的JSON解析 (避免引入额外依赖)
            val result = mutableMapOf<String, String>()
            val content = json.trim().removeSurrounding("{", "}")
            if (content.isEmpty()) return emptyMap()
            
            val entries = content.split("\",\"")
            for (entry in entries) {
                val cleaned = entry.trim().removePrefix("\"").removeSuffix("\"")
                val parts = cleaned.split("\":\"")
                if (parts.size == 2) {
                    result[parts[0]] = parts[1]
                }
            }
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
