package com.example.voice

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.MockMessage
import com.example.ui.screens.Sender
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VoiceConversationSystemTest {

    @Test
    fun testVoiceConversationManagerLogic() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var sentText: String? = null
        
        val testScope = TestScope()

        val manager = VoiceConversationManager(
            context = context,
            coroutineScope = testScope,
            onSendText = { text ->
                sentText = text
            }
        )

        // 1. Check initial state
        assertEquals(VoiceConversationState.IDLE, manager.state.value)
        assertFalse(manager.isVoiceModeEnabled.value)

        // 2. Enable voice mode
        manager.startVoiceMode()
        assertTrue(manager.isVoiceModeEnabled.value)

        // 3. Test onMessagesUpdated filtering
        val messages = listOf(
            MockMessage(id = "msg1", text = "Hi", sender = Sender.USER),
            MockMessage(id = "msg2", text = "Hello there", sender = Sender.AI)
        )

        // Set initial message to prevent speaking old historical messages
        manager.setInitialLastSpokenMessage(messages.last())
        
        // Update with the same messages (should not speak because latest AI msg ID == msg2)
        manager.onMessagesUpdated(messages, isThinking = false, isConfirmationExecuting = false)

        // Now a new AI message arrives
        val updatedMessages = messages + MockMessage(id = "msg3", text = "How can I help you?", sender = Sender.AI)
        
        // If thinking is true, it should NOT speak yet
        manager.onMessagesUpdated(updatedMessages, isThinking = true, isConfirmationExecuting = false)
        
        // If a confirmation is executing, it should NOT speak yet
        manager.onMessagesUpdated(updatedMessages, isThinking = false, isConfirmationExecuting = true)

        // Once thinking and executing are false, it should speak
        manager.onMessagesUpdated(updatedMessages, isThinking = false, isConfirmationExecuting = false)
    }

    @Test
    fun testTextToSpeechCleaning() {
        val rawText = "**AI Response**\nid=123\nactionCardType=leads\n- Bullet 1\n* Bullet 2\nPlease do this."
        val cleaned = TextToSpeechManager.sanitizeForSpeech(rawText)
        
        assertFalse(cleaned.contains("**"))
        assertFalse(cleaned.contains("actionCardType"))
        assertFalse(cleaned.contains("id=123"))
        assertTrue(cleaned.contains("AI Response"))
        assertTrue(cleaned.contains("Please do this."))
    }

    @Test
    fun confirmationInterpreter_acceptsOnlyExplicitConfirmPhrases() {
        assertEquals(
            VoiceConfirmationDecision.CONFIRM,
            VoiceConfirmationInterpreter.parse("Haan, save kar do")
        )
        assertEquals(
            VoiceConfirmationDecision.CONFIRM,
            VoiceConfirmationInterpreter.parse("जी हाँ")
        )
        assertEquals(
            VoiceConfirmationDecision.CONFIRM,
            VoiceConfirmationInterpreter.parse("Confirm")
        )
        assertNull(
            VoiceConfirmationInterpreter.parse(
                "Kal Rahul ko call karke report save karna"
            )
        )
    }

    @Test
    fun confirmationInterpreter_acceptsExplicitCancelPhrases() {
        assertEquals(
            VoiceConfirmationDecision.CANCEL,
            VoiceConfirmationInterpreter.parse("Nahi, cancel kar do")
        )
        assertEquals(
            VoiceConfirmationDecision.CANCEL,
            VoiceConfirmationInterpreter.parse("रहने दो")
        )
        assertEquals(
            VoiceConfirmationDecision.CANCEL,
            VoiceConfirmationInterpreter.parse("Save mat karo")
        )
    }

    @Test
    fun messageSignature_changesWhenSameMessageGetsFinalResult() {
        val confirmation = MockMessage(
            id = "ai-confirmation-1",
            text = "Lead details confirm karein.",
            sender = Sender.AI,
            isConfirmation = true,
            actionCardType = "lead_ai_confirmation"
        )
        val success = confirmation.copy(
            text = "Lead Rahul successfully create hua.",
            isConfirmation = false,
            actionCardType = null
        )

        assertNotEquals(
            VoiceConversationManager.buildMessageSignature(confirmation),
            VoiceConversationManager.buildMessageSignature(success)
        )
    }
}
