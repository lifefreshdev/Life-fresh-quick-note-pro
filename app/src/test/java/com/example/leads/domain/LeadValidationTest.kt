package com.example.leads.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class LeadValidationTest {

    private fun validDraft(
        name: String = "Salman",
        mobile: String = "9876543210",
        diseases: List<String> = listOf("General Wellness"),
        otherDisease: String = "",
        relation: String = "Customer",
        otherRelation: String = "",
        status: String = "Pending",
        reminderDate: String = "",
        reminderTime: String = ""
    ): LeadDraft {
        return LeadDraft(
            name = name,
            mobile = mobile,
            diseases = diseases,
            otherDisease = otherDisease,
            relation = relation,
            otherRelation = otherRelation,
            status = status,
            reminderDate = reminderDate,
            reminderTime = reminderTime
        )
    }

    private fun millis(dateTime: String): Long {
        return requireNotNull(
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
                isLenient = false
            }.parse(dateTime)
        ).time
    }

    @Test
    fun validDraft_hasNoValidationIssues() {
        val result = LeadValidator.validate(validDraft())

        assertTrue(result.isValid)
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun blankName_isRejected() {
        val result = LeadValidator.validate(validDraft(name = "   "))

        assertFalse(result.isValid)
        assertEquals(
            "Name is required.",
            result.firstIssueFor(LeadField.NAME)?.message
        )
    }

    @Test
    fun formattedIndianMobile_isNormalizedToDigits() {
        val result = LeadValidator.validate(
            validDraft(mobile = "+91 98765-43210")
        )

        assertTrue(result.isValid)
        assertEquals("919876543210", result.normalizedDraft.mobile)
    }

    @Test
    fun unsupportedMobileCharacters_areRejected() {
        val result = LeadValidator.validate(
            validDraft(mobile = "98765A43210")
        )

        assertFalse(result.isValid)
        assertEquals(
            "Please enter a valid mobile number (10 to 15 digits).",
            result.firstIssueFor(LeadField.MOBILE)?.message
        )
    }

    @Test
    fun missingCategory_isRejected() {
        val result = LeadValidator.validate(
            validDraft(diseases = emptyList())
        )

        assertFalse(result.isValid)
        assertEquals(
            "At least one category is required.",
            result.firstIssueFor(LeadField.DISEASES)?.message
        )
    }

    @Test
    fun otherCategory_requiresDetail() {
        val result = LeadValidator.validate(
            validDraft(
                diseases = listOf("Other"),
                otherDisease = ""
            )
        )

        assertFalse(result.isValid)
        assertEquals(
            "Please specify the category detail.",
            result.firstIssueFor(LeadField.OTHER_DISEASE)?.message
        )
    }

    @Test
    fun missingRelation_isRejected() {
        val result = LeadValidator.validate(
            validDraft(relation = " ")
        )

        assertFalse(result.isValid)
        assertEquals(
            "Relation is required.",
            result.firstIssueFor(LeadField.RELATION)?.message
        )
    }

    @Test
    fun otherRelation_requiresDetail() {
        val result = LeadValidator.validate(
            validDraft(
                relation = "Other",
                otherRelation = ""
            )
        )

        assertFalse(result.isValid)
        assertEquals(
            "Please specify the relation detail.",
            result.firstIssueFor(LeadField.OTHER_RELATION)?.message
        )
    }

    @Test
    fun completedStatus_isNormalizedToComplete() {
        val result = LeadValidator.validate(
            validDraft(status = " completed ")
        )

        assertTrue(result.isValid)
        assertEquals("Complete", result.normalizedDraft.status)
        assertNull(result.firstIssueFor(LeadField.STATUS))
    }

    @Test
    fun pastReminder_isRejected() {
        val now = millis("2030-01-02 10:00")

        val result = LeadValidator.validate(
            draft = validDraft(
                reminderDate = "2030-01-02",
                reminderTime = "09:59"
            ),
            nowMillis = now
        )

        assertFalse(result.isValid)
        assertEquals(
            "Error: Selected date/time must be in the future.",
            result.firstIssueFor(LeadField.REMINDER_DATE)?.message
        )
    }

    @Test
    fun futureReminder_isAccepted() {
        val now = millis("2030-01-02 10:00")

        val result = LeadValidator.validate(
            draft = validDraft(
                reminderDate = "2030-01-02",
                reminderTime = "10:01"
            ),
            nowMillis = now
        )

        assertTrue(result.isValid)
        assertNull(result.firstIssueFor(LeadField.REMINDER_DATE))
        assertNull(result.firstIssueFor(LeadField.REMINDER_TIME))
    }
}
