package com.example.data.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MultiAccountIsolationTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var leadDao: LeadDao

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        leadDao = db.leadDao
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testUserAAndUserBDataIsolation() = runBlocking {
        val leadA = LeadEntity(
            id = "lead_1",
            name = "User A Lead",
            mobile = "1111111111",
            diseases = "[]",
            otherDisease = "",
            relation = "Self",
            otherRelation = "",
            status = "Pending",
            reminderDate = "",
            reminderTime = "",
            reminderNote = "",
            reminderStatus = "Pending",
            notes = "",
            archived = false,
            timestamp = 1000L,
            ownerUid = "user_A"
        )

        val leadB = LeadEntity(
            id = "lead_2",
            name = "User B Lead",
            mobile = "2222222222",
            diseases = "[]",
            otherDisease = "",
            relation = "Self",
            otherRelation = "",
            status = "Pending",
            reminderDate = "",
            reminderTime = "",
            reminderNote = "",
            reminderStatus = "Pending",
            notes = "",
            archived = false,
            timestamp = 2000L,
            ownerUid = "user_B"
        )

        leadDao.insertLead(leadA)
        leadDao.insertLead(leadB)

        // Verify User A sees only leadA
        val leadsForA = leadDao.getAllLeads("user_A").first()
        assertEquals(1, leadsForA.size)
        assertEquals("lead_1", leadsForA[0].id)
        assertEquals("user_A", leadsForA[0].ownerUid)

        // Verify User B sees only leadB
        val leadsForB = leadDao.getAllLeads("user_B").first()
        assertEquals(1, leadsForB.size)
        assertEquals("lead_2", leadsForB[0].id)
        assertEquals("user_B", leadsForB[0].ownerUid)

        // Verify User C sees no leads
        val leadsForC = leadDao.getAllLeads("user_C").first()
        assertTrue(leadsForC.isEmpty())
    }

    @Test
    fun testLegacyUnownedLeadClaim() = runBlocking {
        val unownedLead = LeadEntity(
            id = "legacy_lead",
            name = "Legacy Lead",
            mobile = "9999999999",
            diseases = "[]",
            otherDisease = "",
            relation = "Self",
            otherRelation = "",
            status = "Pending",
            reminderDate = "",
            reminderTime = "",
            reminderNote = "",
            reminderStatus = "Pending",
            notes = "",
            archived = false,
            timestamp = 500L,
            ownerUid = ""
        )

        leadDao.insertLead(unownedLead)

        // Prior to claim, neither User A nor User B see unowned lead in their scoped queries
        assertTrue(leadDao.getAllLeads("user_A").first().isEmpty())
        assertTrue(leadDao.getAllLeads("user_B").first().isEmpty())

        // User A claims unowned leads
        val claimedCount = leadDao.claimUnownedLeads("user_A")
        assertEquals(1, claimedCount)

        // Now User A sees the claimed lead
        val userALeads = leadDao.getAllLeads("user_A").first()
        assertEquals(1, userALeads.size)
        assertEquals("legacy_lead", userALeads[0].id)
        assertEquals("user_A", userALeads[0].ownerUid)

        // User B still sees nothing
        assertTrue(leadDao.getAllLeads("user_B").first().isEmpty())
    }

    @Test
    fun testClearLeadsForUserOnlyDeletesSpecifiedUser() = runBlocking {
        val leadA = LeadEntity(
            id = "lead_A",
            name = "Lead A",
            mobile = "123",
            diseases = "[]",
            otherDisease = "",
            relation = "",
            otherRelation = "",
            status = "Pending",
            reminderDate = "",
            reminderTime = "",
            reminderNote = "",
            reminderStatus = "Pending",
            notes = "",
            archived = false,
            timestamp = 100L,
            ownerUid = "user_A"
        )
        val leadB = LeadEntity(
            id = "lead_B",
            name = "Lead B",
            mobile = "456",
            diseases = "[]",
            otherDisease = "",
            relation = "",
            otherRelation = "",
            status = "Pending",
            reminderDate = "",
            reminderTime = "",
            reminderNote = "",
            reminderStatus = "Pending",
            notes = "",
            archived = false,
            timestamp = 200L,
            ownerUid = "user_B"
        )

        leadDao.insertLead(leadA)
        leadDao.insertLead(leadB)

        leadDao.clearLeadsForUser("user_A")

        assertTrue(leadDao.getAllLeads("user_A").first().isEmpty())
        assertEquals(1, leadDao.getAllLeads("user_B").first().size)
    }
}
