package com.example.ai.chat.provider

import android.util.Log
import com.example.ai.chat.config.AIConfig
import com.example.ai.chat.model.ChatMessage
import com.example.ai.chat.model.ChatRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class GeminiProvider(
    private val apiKeyProvider: () -> String = { AIConfig.geminiApiKey },
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val modelName: String = "gemini-2.0-flash"
) : AIProvider {

    override val name: String = "Gemini"

    override val isConfigured: Boolean
        get() = apiKeyProvider().isNotBlank()

    override suspend fun generateResponse(
        messages: List<ChatMessage>,
        systemInstruction: String
    ): AIProviderResult = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider()
        if (apiKey.isBlank()) {
            return@withContext AIProviderResult.Failure(
                providerName = name,
                errorMessage = "Gemini API key is not configured.",
                isRetryable = false,
                isRateLimitOrTimeout = false
            )
        }

        try {
            val jsonContents = JSONArray()

            // Filter recent valid messages (limit to 20 messages for context)
            val recentMessages = if (messages.size > 20) messages.takeLast(20) else messages
            for (msg in recentMessages) {
                if (msg.isError || msg.content.isBlank()) continue
                val roleStr = when (msg.role) {
                    ChatRole.USER -> "user"
                    ChatRole.ASSISTANT -> "model"
                    ChatRole.SYSTEM -> "user"
                }

                val contentObj = JSONObject().apply {
                    put("role", roleStr)
                    val partsArray = JSONArray().apply {
                        put(JSONObject().apply { put("text", msg.content) })
                    }
                    put("parts", partsArray)
                }
                jsonContents.put(contentObj)
            }

            if (jsonContents.length() == 0) {
                return@withContext AIProviderResult.Failure(
                    providerName = name,
                    errorMessage = "No message content to send.",
                    isRetryable = false
                )
            }

            val requestJson = JSONObject().apply {
                if (systemInstruction.isNotBlank()) {
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", systemInstruction) })
                        })
                    })
                }

                put("contents", jsonContents)

                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                    put("maxOutputTokens", 3072)
                })
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)

            val models = listOf(
                modelName,
                "gemini-2.0-flash",
                "gemini-1.5-flash",
                "gemini-flash-latest",
                "gemini-2.5-flash",
                "gemini-2.5-pro"
            ).distinct()

            var lastFailure: AIProviderResult.Failure? = null

            for (model in models) {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

                val failure: AIProviderResult.Failure = client.newCall(request).execute().use { response ->
                    val responseBodyString = response.body?.string().orEmpty()
                    val code = response.code

                    if (response.isSuccessful) {
                        val jsonResponse = JSONObject(responseBodyString)
                        val candidates = jsonResponse.optJSONArray("candidates")
                        if (candidates != null && candidates.length() > 0) {
                            val firstCandidate = candidates.getJSONObject(0)
                            val contentObj = firstCandidate.optJSONObject("content")
                            val partsArray = contentObj?.optJSONArray("parts")
                            if (partsArray != null && partsArray.length() > 0) {
                                val textBuilder = StringBuilder()
                                for (i in 0 until partsArray.length()) {
                                    val part = partsArray.getJSONObject(i)
                                    val text = part.optString("text", "")
                                    textBuilder.append(text)
                                }
                                val fullText = textBuilder.toString().trim()
                                if (fullText.isNotBlank()) {
                                    return@withContext AIProviderResult.Success(
                                        text = fullText,
                                        providerName = name
                                    )
                                }
                            }
                        }
                    }

                    val parsedError = try {
                        JSONObject(responseBodyString).optJSONObject("error")?.optString("message")
                    } catch (_: Throwable) {
                        null
                    }

                    val isRateLimit = code == 429
                    val isAuthError = code == 400 || code == 401 || code == 403

                    val errorMsg = if (isAuthError) {
                        "API Key Invalid: ${parsedError ?: "HTTP $code"}"
                    } else if (isRateLimit) {
                        "Gemini rate limit reached: ${parsedError ?: "Please try again later."}"
                    } else {
                        parsedError ?: "Gemini returned status $code"
                    }

                    // If auth error, return immediately without trying other models
                    if (isAuthError) {
                        return@withContext AIProviderResult.Failure(
                            providerName = name,
                            errorMessage = errorMsg,
                            isRetryable = false,
                            isRateLimitOrTimeout = false
                        )
                    }

                    // For any model-specific issue (404, 429, 503, etc.), log and try next model
                    Log.w(TAG, "Gemini model $model returned HTTP $code ($errorMsg). Trying next fallback model...")
                    AIProviderResult.Failure(
                        providerName = name,
                        errorMessage = errorMsg,
                        isRetryable = true,
                        isRateLimitOrTimeout = isRateLimit
                    )
                }

                lastFailure = failure
            }

            return@withContext lastFailure ?: AIProviderResult.Failure(
                providerName = name,
                errorMessage = "Unable to reach Gemini API after trying all fallback models.",
                isRetryable = true
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "Gemini request timed out", e)
            AIProviderResult.Failure(
                providerName = name,
                errorMessage = "Gemini request timed out.",
                isRetryable = true,
                isRateLimitOrTimeout = true,
                cause = e
            )
        } catch (e: IOException) {
            Log.w(TAG, "Gemini network error", e)
            AIProviderResult.Failure(
                providerName = name,
                errorMessage = "Network connection error reaching Gemini.",
                isRetryable = true,
                cause = e
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in GeminiProvider", e)
            AIProviderResult.Failure(
                providerName = name,
                errorMessage = "Failed to communicate with Gemini.",
                isRetryable = true,
                cause = e
            )
        }
    }

    companion object {
        private const val TAG = "GeminiProvider"
    }
}
