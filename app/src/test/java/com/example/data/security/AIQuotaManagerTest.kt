package com.example.data.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.network.AICandidate
import com.example.data.network.AIContent
import com.example.data.network.AIPart
import com.example.data.network.AIProxyRequest
import com.example.data.network.AIProxyResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AIQuotaManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Clear prefs before each test
        context.getSharedPreferences("daily_ai_usage_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `initial state has full 20 free daily quota`() {
        assertEquals(20, AIQuotaManager.getRemainingQuota(context))
        assertTrue(AIQuotaManager.canExecuteAI(context))
        assertFalse(AIQuotaManager.isUnlimited(context))
        assertNull(AIQuotaManager.getCustomGeminiKey(context))
    }

    @Test
    fun `incrementUsage decreases remaining quota correctly`() {
        assertEquals(20, AIQuotaManager.getRemainingQuota(context))

        AIQuotaManager.incrementUsage(context)
        assertEquals(19, AIQuotaManager.getRemainingQuota(context))
        assertTrue(AIQuotaManager.canExecuteAI(context))

        repeat(18) {
            AIQuotaManager.incrementUsage(context)
        }
        assertEquals(1, AIQuotaManager.getRemainingQuota(context))
        assertTrue(AIQuotaManager.canExecuteAI(context))

        AIQuotaManager.incrementUsage(context)
        assertEquals(0, AIQuotaManager.getRemainingQuota(context))
        assertFalse(AIQuotaManager.canExecuteAI(context))
    }

    @Test
    fun `custom BYOK Gemini key enables unlimited queries even when quota is exhausted`() {
        // Exhaust quota
        repeat(20) {
            AIQuotaManager.incrementUsage(context)
        }
        assertFalse(AIQuotaManager.canExecuteAI(context))
        assertEquals(0, AIQuotaManager.getRemainingQuota(context))

        // Set custom BYOK key
        AIQuotaManager.saveCustomGeminiKey(context, "AIzaSyTestKey123456789")
        assertTrue(AIQuotaManager.isUnlimited(context))
        assertEquals("AIzaSyTestKey123456789", AIQuotaManager.getCustomGeminiKey(context))
        assertTrue(AIQuotaManager.canExecuteAI(context))

        // Clear custom key -> returns to limited mode
        AIQuotaManager.saveCustomGeminiKey(context, null)
        assertFalse(AIQuotaManager.isUnlimited(context))
        assertNull(AIQuotaManager.getCustomGeminiKey(context))
        assertFalse(AIQuotaManager.canExecuteAI(context))
    }

    @Test
    fun `deviceId retrieval returns a non-blank string without throwing`() {
        val deviceId = AIQuotaManager.getDeviceId(context)
        assertNotNull(deviceId)
        assertTrue(deviceId.isNotBlank())
    }

    @Test
    fun `proxy models serialize and deserialize correctly with Moshi`() {
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

        // Test AIProxyRequest
        val requestAdapter = moshi.adapter(AIProxyRequest::class.java)
        val request = AIProxyRequest(prompt = "Hello AI", deviceId = "device-abc-123")
        val jsonRequest = requestAdapter.toJson(request)
        assertTrue(jsonRequest.contains("Hello AI"))
        assertTrue(jsonRequest.contains("device-abc-123"))

        val parsedRequest = requestAdapter.fromJson(jsonRequest)
        assertNotNull(parsedRequest)
        assertEquals("Hello AI", parsedRequest?.prompt)
        assertEquals("device-abc-123", parsedRequest?.deviceId)

        // Test AIProxyResponse
        val responseAdapter = moshi.adapter(AIProxyResponse::class.java)
        val response = AIProxyResponse(
            candidates = listOf(
                AICandidate(
                    content = AIContent(
                        parts = listOf(AIPart(text = "AI Response Text"))
                    )
                )
            ),
            error = null
        )
        val jsonResponse = responseAdapter.toJson(response)
        val parsedResponse = responseAdapter.fromJson(jsonResponse)
        assertNotNull(parsedResponse)
        assertEquals("AI Response Text", parsedResponse?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text)
    }

    @Test
    fun `testGeminiKey fails immediately with blank key`() = kotlinx.coroutines.runBlocking {
        val repo = com.example.data.repository.AIServiceRepository()
        val result = repo.testGeminiKey("   ")
        assertTrue(result.isFailure)
        assertEquals("API Key cannot be empty.", result.exceptionOrNull()?.message)
    }

    @Test
    fun `processQuery fails immediately with blank prompt`() = kotlinx.coroutines.runBlocking {
        val repo = com.example.data.repository.AIServiceRepository()
        val result = repo.processQuery(context, "   ")
        assertTrue(result.isFailure)
        assertEquals("Prompt cannot be blank", result.exceptionOrNull()?.message)
    }
}
