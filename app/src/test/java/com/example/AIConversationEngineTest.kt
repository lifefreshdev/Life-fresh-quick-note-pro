package com.example

import com.example.ai.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AIConversationEngineTest {

    private lateinit var stateMachine: AIStateMachine
    private lateinit var eventBus: AIEventBus
    private lateinit var conversationEngine: AIConversationEngine

    @Before
    fun setUp() {
        stateMachine = AIStateMachine()
        eventBus = AIEventBus
        conversationEngine = AIConversationEngine(stateMachine, eventBus)
        ConversationMemory.clearSession()
    }

    @Test
    fun testTextNormalization_EnglishAndHindi() {
        // English
        val normEnglish = IntentEngine.normalizeText("Please add a customer named John")
        assertTrue(normEnglish.contains("add"))
        assertTrue(normEnglish.contains("client"))
        assertTrue(normEnglish.contains("john"))

        // Hinglish / Hindi
        val normHindi = IntentEngine.normalizeText("Naya client banao")
        assertTrue(normHindi.contains("add"))
        assertTrue(normHindi.contains("client"))
    }

    @Test
    fun testIntentIdentification_Help() {
        val norm = IntentEngine.normalizeText("I need some help please")
        val result = IntentEngine.identifyIntent(norm)
        assertEquals(IntentType.HELP, result.intentType)
    }

    @Test
    fun testIntentIdentification_AddClient_Incomplete() = runTest {
        val response = conversationEngine.processMessage("Add client")
        assertEquals(IntentType.ADD_CLIENT, response.intentType)
        assertFalse(response.isComplete)
        assertEquals("What is the name of the client?", response.replyText)
    }

    @Test
    fun testIntentIdentification_AddClient_CompleteFlow() = runTest {
        // Step 1: Initialize
        var response = conversationEngine.processMessage("Add customer")
        assertEquals(IntentType.ADD_CLIENT, response.intentType)
        assertFalse(response.isComplete)
        assertEquals("What is the name of the client?", response.replyText)

        // Step 2: Provide Name
        response = conversationEngine.processMessage("Amit")
        assertEquals(IntentType.ADD_CLIENT, response.intentType)
        assertFalse(response.isComplete)
        assertEquals("What is the client's phone number?", response.replyText)

        // Step 3: Provide Phone
        response = conversationEngine.processMessage("9876543210")
        assertEquals(IntentType.ADD_CLIENT, response.intentType)
        assertTrue(response.isComplete)
        assertEquals("Perfect! I have gathered the information to add client Amit with phone number 9876543210.", response.replyText)
    }

    @Test
    fun testSafetyBlock() = runTest {
        val response = conversationEngine.processMessage("DROP TABLE clients;")
        assertEquals(IntentType.UNKNOWN, response.intentType)
        assertFalse(response.isComplete)
        assertEquals("I cannot process this request because it violates safety guidelines.", response.replyText)
    }

    @Test
    fun testUnknownIntent() = runTest {
        val response = conversationEngine.processMessage("xyzabc123")
        assertEquals(IntentType.UNKNOWN, response.intentType)
        assertFalse(response.isComplete)
    }

    @Test
    fun testIntentIdentification_SearchClient() = runTest {
        val response = conversationEngine.processMessage("find client Rahul")
        assertEquals(IntentType.SEARCH_CLIENT, response.intentType)
        assertTrue(response.isComplete) // Complete because name/query is extracted
    }

    @Test
    fun testIntentIdentification_DeleteClient_WithConfirmation() = runTest {
        // Step 1: Request delete
        var response = conversationEngine.processMessage("delete client Rahul")
        assertEquals(IntentType.DELETE_CLIENT, response.intentType)
        assertFalse(response.isComplete)
        assertEquals("Please confirm to proceed with this operation.", response.replyText)

        // Step 2: Confirm delete
        response = conversationEngine.processMessage("yes, confirm")
        assertEquals(IntentType.DELETE_CLIENT, response.intentType)
        assertTrue(response.isComplete)
    }
}
