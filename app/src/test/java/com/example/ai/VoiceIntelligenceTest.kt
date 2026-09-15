package com.example.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.database.LeadEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VoiceIntelligenceTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var testProvider: TestSpeechInputProvider
    private lateinit var engine: VoiceIntelligenceEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AIManager.initialize(context)
        database = AppDatabase.getDatabase(context)
        ConversationMemory.clearSession()

        testProvider = TestSpeechInputProvider()
        engine = VoiceIntelligenceEngine(
            provider = testProvider,
            stateMachine = AIManager.stateMachine,
            eventBus = AIManager.eventBus,
            conversationEngine = AIManager.conversationEngine
        )
    }

    @After
    fun tearDown() {
        runTest(testDispatcher) {
            database.leadDao.clearAllLeads()
        }
        AIManager.shutdown()
    }

    @Test
    fun testVoiceNormalization() {
        // Hinglish/Multilingual mapping tests
        assertEquals("client", engine.normalizeVoiceText("grahak"))
        assertEquals("client", engine.normalizeVoiceText("sadasya"))
        assertEquals("add reminder", engine.normalizeVoiceText("laga do"))
        assertEquals("add reminder", engine.normalizeVoiceText("lagado"))
        assertEquals("delete", engine.normalizeVoiceText("hata do"))
        assertEquals("delete", engine.normalizeVoiceText("mitado"))
        assertEquals("add", engine.normalizeVoiceText("banao"))
        assertEquals("search", engine.normalizeVoiceText("dhoondo"))
        assertEquals("edit", engine.normalizeVoiceText("sudharo"))
        assertEquals("help", engine.normalizeVoiceText("madad"))

        // Sentence level normalization
        assertEquals("rahul ko add reminder tomorrow", engine.normalizeVoiceText("Rahul ko lagado tomorrow"))
    }

    @Test
    fun testEmptyInputHandling() {
        var errorTriggered = false
        var lastError: VoiceError? = null

        engine.startVoiceSession(object : VoiceSessionListener {
            override fun onSessionStarted() {}
            override fun onSpeechStart() {}
            override fun onSpeechProgress(rmsdB: Float) {}
            override fun onResult(rawText: String, normalizedText: String, response: ConversationResponse?) {}
            override fun onError(error: VoiceError, message: String) {
                errorTriggered = true
                lastError = error
            }
            override fun onTimeout() {}
            override fun onCancelled() {}
        })

        testProvider.simulateResult("")

        assertTrue(errorTriggered)
        assertEquals(VoiceError.EMPTY_RESULT, lastError)
        assertEquals(AIState.IDLE, AIManager.stateMachine.currentState.value)
    }

    @Test
    fun testVeryShortInputHandling() {
        var errorTriggered = false
        var lastError: VoiceError? = null

        engine.startVoiceSession(object : VoiceSessionListener {
            override fun onSessionStarted() {}
            override fun onSpeechStart() {}
            override fun onSpeechProgress(rmsdB: Float) {}
            override fun onResult(rawText: String, normalizedText: String, response: ConversationResponse?) {}
            override fun onError(error: VoiceError, message: String) {
                errorTriggered = true
                lastError = error
            }
            override fun onTimeout() {}
            override fun onCancelled() {}
        })

        testProvider.simulateResult("a")

        assertTrue(errorTriggered)
        assertEquals(VoiceError.EMPTY_RESULT, lastError)
        assertEquals(AIState.IDLE, AIManager.stateMachine.currentState.value)
    }

    @Test
    fun testBackgroundNoiseDetection() {
        var errorTriggered = false
        var lastError: VoiceError? = null

        engine.startVoiceSession(object : VoiceSessionListener {
            override fun onSessionStarted() {}
            override fun onSpeechStart() {}
            override fun onSpeechProgress(rmsdB: Float) {}
            override fun onResult(rawText: String, normalizedText: String, response: ConversationResponse?) {}
            override fun onError(error: VoiceError, message: String) {
                errorTriggered = true
                lastError = error
            }
            override fun onTimeout() {}
            override fun onCancelled() {}
        })

        testProvider.simulateResult("...")

        assertTrue(errorTriggered)
        assertEquals(VoiceError.EMPTY_RESULT, lastError)
        assertEquals(AIState.IDLE, AIManager.stateMachine.currentState.value)
    }

    @Test
    fun testRecognitionTimeout() {
        var timeoutTriggered = false

        engine.startVoiceSession(object : VoiceSessionListener {
            override fun onSessionStarted() {}
            override fun onSpeechStart() {}
            override fun onSpeechProgress(rmsdB: Float) {}
            override fun onResult(rawText: String, normalizedText: String, response: ConversationResponse?) {}
            override fun onError(error: VoiceError, message: String) {}
            override fun onTimeout() {
                timeoutTriggered = true
            }
            override fun onCancelled() {}
        })

        testProvider.simulateTimeout()

        assertTrue(timeoutTriggered)
        assertEquals(AIState.IDLE, AIManager.stateMachine.currentState.value)
    }

    @Test
    fun testConversationIntegration_SingleCommand() = runTest(testDispatcher) {
        // Pre-insert a lead for search/interaction verification
        val client = LeadEntity(
            id = UUID.randomUUID().toString(),
            name = "Ravi",
            mobile = "9876543210",
            diseases = "[]",
            otherDisease = "",
            relation = "Self",
            otherRelation = "",
            status = "Pending",
            reminderDate = "",
            reminderTime = "",
            reminderNote = "",
            reminderStatus = "Pending"
        )
        database.leadDao.insertLead(client)

        var resultText = ""
        var normalizedText = ""
        var response: ConversationResponse? = null

        engine.startVoiceSession(object : VoiceSessionListener {
            override fun onSessionStarted() {}
            override fun onSpeechStart() {}
            override fun onSpeechProgress(rmsdB: Float) {}
            override fun onResult(raw: String, norm: String, resp: ConversationResponse?) {
                resultText = raw
                normalizedText = norm
                response = resp
            }
            override fun onError(error: VoiceError, message: String) {}
            override fun onTimeout() {}
            override fun onCancelled() {}
        })

        // Speak "find customer Ravi"
        // Synonyms will map "customer" to "client", so "find client Ravi"
        testProvider.simulateResult("find customer Ravi")

        // Wait for coroutines
        testScheduler.advanceUntilIdle()

        assertEquals("find customer Ravi", resultText)
        assertEquals("find client ravi", normalizedText)
        assertNotNull(response)
        assertEquals(IntentType.SEARCH_CLIENT, response?.intentType)
    }

    @Test
    fun testConversationIntegration_MultiTurn() = runTest(testDispatcher) {
        // Turn 1: speak "add client"
        var response: ConversationResponse? = null

        engine.startVoiceSession(object : VoiceSessionListener {
            override fun onSessionStarted() {}
            override fun onSpeechStart() {}
            override fun onSpeechProgress(rmsdB: Float) {}
            override fun onResult(raw: String, norm: String, resp: ConversationResponse?) {
                response = resp
            }
            override fun onError(error: VoiceError, message: String) {}
            override fun onTimeout() {}
            override fun onCancelled() {}
        })

        testProvider.simulateResult("add client")
        testScheduler.advanceUntilIdle()

        assertNotNull(response)
        assertEquals(IntentType.ADD_CLIENT, response?.intentType)
        assertFalse(response!!.isComplete)
        assertEquals("What is the name of the client?", response?.replyText)

        // Turn 2: speak name "Kamal" using the same session
        var secondResponse: ConversationResponse? = null

        engine.startVoiceSession(object : VoiceSessionListener {
            override fun onSessionStarted() {}
            override fun onSpeechStart() {}
            override fun onSpeechProgress(rmsdB: Float) {}
            override fun onResult(raw: String, norm: String, resp: ConversationResponse?) {
                secondResponse = resp
            }
            override fun onError(error: VoiceError, message: String) {}
            override fun onTimeout() {}
            override fun onCancelled() {}
        })

        testProvider.simulateResult("Kamal")
        testScheduler.advanceUntilIdle()

        assertNotNull(secondResponse)
        assertEquals(IntentType.ADD_CLIENT, secondResponse?.intentType)
        // Since Kamal is added, next missing slot is "phone"
        assertEquals("What is the client's phone number?", secondResponse?.replyText)
    }

    @Test
    fun testSafetyRouting() = runTest(testDispatcher) {
        var response: ConversationResponse? = null

        engine.startVoiceSession(object : VoiceSessionListener {
            override fun onSessionStarted() {}
            override fun onSpeechStart() {}
            override fun onSpeechProgress(rmsdB: Float) {}
            override fun onResult(raw: String, norm: String, resp: ConversationResponse?) {
                response = resp
            }
            override fun onError(error: VoiceError, message: String) {}
            override fun onTimeout() {}
            override fun onCancelled() {}
        })

        // Speak an unsafe command containing SQL injection
        testProvider.simulateResult("delete client Ravi where drop table leads")
        testScheduler.advanceUntilIdle()

        assertNotNull(response)
        assertEquals(IntentType.UNKNOWN, response?.intentType)
        assertTrue(response!!.replyText.contains("violates safety guidelines"))
    }

    class TestSpeechInputProvider : SpeechInputProvider {
        var listener: SpeechInputListener? = null
        var startCount = 0
        var stopCount = 0
        var destroyCount = 0

        override fun init(context: Context) {}

        override fun startListening(listener: SpeechInputListener) {
            this.listener = listener
            startCount++
        }

        override fun stopListening() {
            stopCount++
        }

        override fun destroy() {
            destroyCount++
        }

        fun simulateSpeechStart() {
            listener?.onSpeechStart()
        }

        fun simulateResult(text: String) {
            listener?.onResult(text)
        }

        fun simulateError(error: VoiceError) {
            listener?.onError(error)
        }

        fun simulateTimeout() {
            listener?.onTimeout()
        }
    }
}
