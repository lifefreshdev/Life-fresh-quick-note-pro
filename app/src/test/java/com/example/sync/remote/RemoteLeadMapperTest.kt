package com.example.sync.remote

import com.example.data.database.LeadEntity
import com.example.data.database.LeadSyncMetadataEntity
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RemoteLeadMapperTest {

    @Test
    fun leadToRemoteMap_containsAllLeadFieldsAndMetadata() {
        val lead = LeadEntity(
            id = "lead_123",
            name = "John Doe",
            mobile = "+1234567890",
            diseases = "[\"Diabetes\"]",
            otherDisease = "",
            relation = "Self",
            otherRelation = "",
            status = "Pending",
            reminderDate = "2026-08-01",
            reminderTime = "10:00",
            reminderNote = "Checkup",
            reminderStatus = "Pending",
            notes = "Patient note",
            archived = false,
            lastCall = null,
            timestamp = 1700000000000L
        )

        val metadata = LeadSyncMetadataEntity(
            leadId = "lead_123",
            localUpdatedAt = 1700000005000L,
            serverVersion = 2L
        )

        val map = RemoteLeadMapper.leadToRemoteMap(lead, metadata, deviceId = "dev_abc", schemaVersion = 1)

        assertEquals("lead_123", map["id"])
        assertEquals("John Doe", map["name"])
        assertEquals("+1234567890", map["mobile"])
        assertEquals("[\"Diabetes\"]", map["diseases"])
        assertEquals("Pending", map["status"])
        assertEquals(1700000005000L, map["updatedAt"])
        assertEquals(false, map["deleted"])
        assertEquals(1, map["schemaVersion"])
        assertEquals("dev_abc", map["deviceId"])
        assertEquals(2L, map["serverVersion"])
    }

    @Test
    fun deleteTombstoneMap_containsOnlyTombstoneFields() {
        val map = RemoteLeadMapper.deleteTombstoneMap(
            leadId = "lead_456",
            deletedAtUtc = 1700000100000L,
            updatedAtUtc = 1700000100000L,
            deviceId = "dev_xyz",
            serverVersion = 3L
        )

        assertEquals("lead_456", map["id"])
        assertEquals(true, map["deleted"])
        assertEquals(1700000100000L, map["deletedAt"])
        assertEquals(1700000100000L, map["updatedAt"])
        assertEquals(1, map["schemaVersion"])
        assertEquals("dev_xyz", map["deviceId"])
        assertEquals(3L, map["serverVersion"])
        assertNull(map["name"])
        assertNull(map["mobile"])
        assertNull(map["notes"])
    }

    @Test
    fun mapToRemoteLeadRecord_handlesMissingAndLegacyFieldsGracefully() {
        val rawData = mapOf<String, Any?>(
            "id" to "lead_789",
            "name" to "Jane Smith",
            "mobile" to "+1987654321",
            "status" to "Complete",
            "updatedAt" to 1700000200000L
        )

        val record = RemoteLeadMapper.mapToRemoteLeadRecord("lead_789", rawData)

        assertEquals("lead_789", record.id)
        assertEquals("Jane Smith", record.name)
        assertEquals("+1987654321", record.mobile)
        assertEquals("Complete", record.status)
        assertEquals("[]", record.diseases)
        assertEquals(false, record.deleted)
        assertEquals(0, record.schemaVersion)
        assertEquals(0L, record.serverVersion)
    }

    @Test
    fun remoteRecordToLeadEntity_convertsCorrectly() {
        val record = RemoteLeadMapper.mapToRemoteLeadRecord(
            "lead_111",
            mapOf(
                "id" to "lead_111",
                "name" to "Alice",
                "mobile" to "1234",
                "diseases" to "[\"BP\"]",
                "status" to "Pending",
                "updatedAt" to 1700000300000L
            )
        )

        val entity = RemoteLeadMapper.remoteRecordToLeadEntity(record)

        assertEquals("lead_111", entity.id)
        assertEquals("Alice", entity.name)
        assertEquals("1234", entity.mobile)
        assertEquals("[\"BP\"]", entity.diseases)
        assertEquals("Pending", entity.status)
        assertEquals(1700000300000L, entity.timestamp)
    }

    @Test
    fun toConflictJson_producesValidJson() {
        val record = RemoteLeadMapper.mapToRemoteLeadRecord(
            "lead_222",
            mapOf(
                "id" to "lead_222",
                "name" to "Bob",
                "mobile" to "5555"
            )
        )

        val jsonStr = RemoteLeadMapper.toConflictJson(record)
        val json = JSONObject(jsonStr)

        assertEquals("lead_222", json.getString("id"))
        assertEquals("Bob", json.getString("name"))
        assertEquals("5555", json.getString("mobile"))
    }
}
