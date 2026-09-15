package com.example.leads.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LeadAIDraftManagerTest {

    private fun completeCreateDraft(
        manager: LeadAIDraftManager,
        reminderRequested: Boolean = false
    ): LeadAIDraft {
        manager.startCreateDraft(sourceMessageId = "message-1")

        manager.updateActiveDraft(
            name = "Salman",
            mobile = "9876543210",
            diseases = listOf("General Wellness"),
            relation = "Customer",
            status = "Pending",
            reminderRequested = reminderRequested
        )

        if (reminderRequested) {
            manager.updateActiveDraft(
                reminderDate = "2030-01-02",
                reminderTime = "10:30",
                reminderNote = "Follow-up call"
            )
        }

        manager.skipQuickNotes()
        return requireNotNull(manager.getActiveDraft())
    }

    @Test
    fun newManager_hasNoActiveDraft() {
        val manager = LeadAIDraftManager()

        assertFalse(manager.hasActiveDraft())
        assertNull(manager.getActiveDraft())
        assertNull(manager.nextRequiredStep())
        assertNull(manager.nextQuestion())
        assertFalse(manager.isReadyForConfirmation())
        assertNull(manager.buildLeadDraftForConfirmation())
    }

    @Test
    fun startCreateDraft_createsAndStoresCreateDraft() {
        val manager = LeadAIDraftManager()

        val draft = manager.startCreateDraft(sourceMessageId = "source-1")

        assertTrue(manager.hasActiveDraft())
        assertEquals(draft, manager.getActiveDraft())
        assertEquals(LeadAIDraftMode.CREATE, draft.mode)
        assertEquals("source-1", draft.sourceMessageId)
        assertNull(draft.targetLeadId)
        assertEquals(LeadAIDraftStep.NAME, manager.nextRequiredStep())
        assertEquals("Lead ka naam kya hai?", manager.nextQuestion())
    }

    @Test
    fun startCreateDraft_replacesPreviousDraft() {
        val manager = LeadAIDraftManager()

        val first = manager.startCreateDraft(sourceMessageId = "first")
        manager.updateActiveDraft(name = "Old Name")

        val second = manager.startCreateDraft(sourceMessageId = "second")

        assertNotEquals(first.draftId, second.draftId)
        assertEquals("second", second.sourceMessageId)
        assertEquals("", second.name)
        assertEquals(second, manager.getActiveDraft())
    }

    @Test
    fun startCreateDraft_withInitialDraft_preservesCollectedValuesButForcesCreateMode() {
        val manager = LeadAIDraftManager()
        val initial = LeadAIDraft(
            mode = LeadAIDraftMode.UPDATE,
            targetLeadId = "old-lead-id",
            name = "Idris",
            mobile = "7237645354",
            diseases = listOf("General Wellness"),
            relation = "Customer",
            status = "Pending",
            reminderRequested = false,
            quickNotesSkipped = true
        )

        val started = manager.startCreateDraft(
            sourceMessageId = "new-source",
            initialDraft = initial
        )

        assertEquals(LeadAIDraftMode.CREATE, started.mode)
        assertNull(started.targetLeadId)
        assertEquals("new-source", started.sourceMessageId)
        assertEquals("Idris", started.name)
        assertEquals("7237645354", started.mobile)
        assertTrue(started.quickNotesSkipped)
    }

    @Test
    fun startUpdateDraft_requiresNonBlankLeadId() {
        val manager = LeadAIDraftManager()

        var failed = false
        try {
            manager.startUpdateDraft(targetLeadId = "   ")
        } catch (_: IllegalArgumentException) {
            failed = true
        }

        assertTrue(failed)
        assertFalse(manager.hasActiveDraft())
    }

    @Test
    fun startUpdateDraft_setsTrimmedTargetIdAndUpdateMode() {
        val manager = LeadAIDraftManager()

        val draft = manager.startUpdateDraft(
            targetLeadId = "  lead-123  ",
            sourceMessageId = "source-update"
        )

        assertEquals(LeadAIDraftMode.UPDATE, draft.mode)
        assertEquals("lead-123", draft.targetLeadId)
        assertEquals("source-update", draft.sourceMessageId)
        assertEquals(draft, manager.getActiveDraft())
    }

    @Test
    fun updateActiveDraft_returnsNullWhenNoDraftExists() {
        val manager = LeadAIDraftManager()

        val result = manager.updateActiveDraft(name = "Salman")

        assertNull(result)
        assertFalse(manager.hasActiveDraft())
    }

    @Test
    fun updateActiveDraft_updatesImmutablyAndPreservesOriginal() {
        val manager = LeadAIDraftManager()
        val original = manager.startCreateDraft()

        val updated = manager.updateActiveDraft(
            name = "  Salman  ",
            mobile = " 9876543210 ",
            diseases = listOf("General Wellness", "General Wellness", " "),
            relation = " Customer "
        )

        assertNotNull(updated)
        assertNotEquals(original, updated)
        assertEquals("", original.name)
        assertEquals("Salman", updated?.name)
        assertEquals("9876543210", updated?.mobile)
        assertEquals(listOf("General Wellness"), updated?.diseases)
        assertEquals("Customer", updated?.relation)
        assertEquals(updated, manager.getActiveDraft())
    }

    @Test
    fun nextQuestion_followsExpectedMissingFieldOrder() {
        val manager = LeadAIDraftManager()
        manager.startCreateDraft()

        assertEquals("Lead ka naam kya hai?", manager.nextQuestion())

        manager.updateActiveDraft(name = "Salman")
        assertEquals("Mobile number kya hai?", manager.nextQuestion())

        manager.updateActiveDraft(mobile = "9876543210")
        assertEquals("Wellness category kya rakhni hai?", manager.nextQuestion())

        manager.updateActiveDraft(diseases = listOf("Other"))
        assertEquals("Other category ki detail kya hai?", manager.nextQuestion())

        manager.updateActiveDraft(otherDisease = "Nutrition")
        assertEquals("Client ke saath relation kya hai?", manager.nextQuestion())

        manager.updateActiveDraft(relation = "Other")
        assertEquals("Other relation ki detail kya hai?", manager.nextQuestion())

        manager.updateActiveDraft(otherRelation = "Referral")
        assertEquals("Status Pending rakhna hai ya Complete?", manager.nextQuestion())

        manager.updateActiveDraft(status = "Pending")
        assertEquals("Kya is lead ke liye reminder bhi lagana hai?", manager.nextQuestion())

        manager.updateActiveDraft(reminderRequested = true)
        assertEquals("Reminder kis date ko lagana hai?", manager.nextQuestion())

        manager.updateActiveDraft(reminderDate = "2030-01-02")
        assertEquals("Reminder kis time par lagana hai?", manager.nextQuestion())

        manager.updateActiveDraft(reminderTime = "10:30")
        assertEquals("Reminder kis kaam ke liye hai?", manager.nextQuestion())

        manager.updateActiveDraft(reminderNote = "Follow-up call")
        assertEquals(
            "Koi Quick Note add karna hai? Aap skip bhi bol sakte hain.",
            manager.nextQuestion()
        )

        manager.skipQuickNotes()
        assertNull(manager.nextQuestion())
        assertTrue(manager.isReadyForConfirmation())
    }

    @Test
    fun reminderFields_areSkippedWhenReminderIsNotRequested() {
        val manager = LeadAIDraftManager()

        val draft = completeCreateDraft(
            manager = manager,
            reminderRequested = false
        )

        assertTrue(draft.isReadyForConfirmation())
        assertEquals(LeadAIDraftStep.READY_FOR_CONFIRMATION, manager.nextRequiredStep())
        assertNull(manager.nextQuestion())
    }

    @Test
    fun buildLeadDraftForConfirmation_returnsNullUntilDraftIsComplete() {
        val manager = LeadAIDraftManager()
        manager.startCreateDraft()
        manager.updateActiveDraft(name = "Salman")

        assertNull(manager.buildLeadDraftForConfirmation())
        assertFalse(manager.isReadyForConfirmation())
    }

    @Test
    fun buildLeadDraftForConfirmation_returnsCollectedValuesWithoutClearingDraft() {
        val manager = LeadAIDraftManager()
        val active = completeCreateDraft(
            manager = manager,
            reminderRequested = true
        )

        val result = manager.buildLeadDraftForConfirmation()

        assertNotNull(result)
        assertEquals(active.targetLeadId, result?.id)
        assertEquals("Salman", result?.name)
        assertEquals("9876543210", result?.mobile)
        assertEquals(listOf("General Wellness"), result?.diseases)
        assertEquals("Customer", result?.relation)
        assertEquals("Pending", result?.status)
        assertEquals("2030-01-02", result?.reminderDate)
        assertEquals("10:30", result?.reminderTime)
        assertEquals("Follow-up call", result?.reminderNote)
        assertTrue(manager.hasActiveDraft())
        assertEquals(active.draftId, manager.getActiveDraft()?.draftId)
    }

    @Test
    fun cancelActiveDraft_withoutActiveDraft_returnsFalse() {
        val manager = LeadAIDraftManager()

        assertFalse(manager.cancelActiveDraft())
    }

    @Test
    fun cancelActiveDraft_withWrongExpectedId_doesNotClear() {
        val manager = LeadAIDraftManager()
        val draft = manager.startCreateDraft()

        val cancelled = manager.cancelActiveDraft(expectedDraftId = "wrong-id")

        assertFalse(cancelled)
        assertTrue(manager.hasActiveDraft())
        assertEquals(draft.draftId, manager.getActiveDraft()?.draftId)
    }

    @Test
    fun cancelActiveDraft_withMatchingId_clearsDraft() {
        val manager = LeadAIDraftManager()
        val draft = manager.startCreateDraft()

        val cancelled = manager.cancelActiveDraft(expectedDraftId = draft.draftId)

        assertTrue(cancelled)
        assertFalse(manager.hasActiveDraft())
        assertNull(manager.getActiveDraft())
    }

    @Test
    fun clearAfterSuccessfulExecution_requiresNonBlankDraftId() {
        val manager = LeadAIDraftManager()

        var failed = false
        try {
            manager.clearAfterSuccessfulExecution(" ")
        } catch (_: IllegalArgumentException) {
            failed = true
        }

        assertTrue(failed)
    }

    @Test
    fun clearAfterSuccessfulExecution_doesNotClearNewerDraftForOldResult() {
        val manager = LeadAIDraftManager()
        val oldDraft = manager.startCreateDraft(sourceMessageId = "old")
        val newDraft = manager.startCreateDraft(sourceMessageId = "new")

        val cleared = manager.clearAfterSuccessfulExecution(oldDraft.draftId)

        assertFalse(cleared)
        assertTrue(manager.hasActiveDraft())
        assertEquals(newDraft.draftId, manager.getActiveDraft()?.draftId)
    }

    @Test
    fun clearAfterSuccessfulExecution_clearsMatchingDraftOnly() {
        val manager = LeadAIDraftManager()
        val draft = manager.startCreateDraft()

        val cleared = manager.clearAfterSuccessfulExecution(draft.draftId)

        assertTrue(cleared)
        assertFalse(manager.hasActiveDraft())
    }

    @Test
    fun replaceActiveDraft_storesExactDraft() {
        val manager = LeadAIDraftManager()
        val supplied = LeadAIDraft(
            name = "Idris",
            mobile = "7237645354"
        )

        val returned = manager.replaceActiveDraft(supplied)

        assertEquals(supplied, returned)
        assertEquals(supplied, manager.getActiveDraft())
    }
}
