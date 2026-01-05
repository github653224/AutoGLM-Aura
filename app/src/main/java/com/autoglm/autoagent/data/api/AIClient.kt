package com.autoglm.autoagent.data.api

import android.util.Log
import com.autoglm.autoagent.data.ApiConfig
import com.autoglm.autoagent.data.SettingsRepository
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// Data Models
data class ChatMessage(val role: String, val content: Any, val tool_calls: List<ToolCall>? = null, val tool_call_id: String? = null)
data class ContentPart(val type: String, val text: String? = null, val image_url: ImageUrl? = null)
data class ImageUrl(val url: String)

data class ToolCall(val id: String, val type: String, val function: ToolFunction)
data class ToolFunction(val name: String, val arguments: String)

data class ChatCompletionResponse(val choices: List<Choice>)
data class Choice(val message: MessageContent)
data class MessageContent(val content: String?, val tool_calls: List<ToolCall>?)


@Singleton
class AIClient @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun sendMessage(
        messages: List<ChatMessage>,
        overrideConfig: ApiConfig? = null
    ): MessageContent {
        val config = overrideConfig ?: settingsRepository.config.first()
        val baseUrl = config.baseUrl.trimEnd('/')
        val url = "$baseUrl/chat/completions"
        
        Log.d("AIClient", "Sending to $url [Model: ${config.model}]")


        val requestBodyMap = mutableMapOf<String, Any>(
            "model" to config.model,
            "messages" to messages,
            "temperature" to 0.0,      // 严格模式：确保输出格式一致
            "top_p" to 0.85,            // Ruto-GLM: 0.85
            "frequency_penalty" to 0.2, // Ruto-GLM: 0.2
            "max_tokens" to 3000,       // Ruto-GLM: 3000
            "stream" to false
        )
        
        // Pure Text Prompt Mode (AutoGLM Style) - No Tools Array
        // if (tools != null) {
        //    requestBodyMap["tools"] = tools
        //    requestBodyMap["tool_choice"] = "auto"
        // }

        val jsonBody = gson.toJson(requestBodyMap)
        val body = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())

        val requestBuilder = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Content-Type", "application/json")
        
        if (config.apiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer ${config.apiKey}")
        }

        val request = requestBuilder.build()
        
        return withContext(Dispatchers.IO) {
            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (!response.isSuccessful) {
                    val errMsg = "API Error: ${response.code}\nBody: ${responseBody ?: "Empty"}"
                    Log.e("AIClient", errMsg)
                    throw Exception(errMsg)
                }

                val chatResponse = gson.fromJson(responseBody, ChatCompletionResponse::class.java)
                chatResponse.choices.firstOrNull()?.message 
                    ?: throw Exception("Invalid API response: No choices found")
            } catch (e: IOException) {
                Log.e("AIClient", "Network Error: ${e.message}", e)
                throw Exception("网络连接失败，请检查手机网络: ${e.message}")
            } catch (e: Exception) {
                Log.e("AIClient", "AI Error: ${e.message}", e)
                throw e
            }
        }
    }
}
