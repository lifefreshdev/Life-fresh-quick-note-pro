package com.example.sync.remote

import com.example.data.database.LeadEntity
import com.example.data.database.LeadSyncMetadataEntity
import com.example.sync.model.RemoteLeadRecord
import org.json.JSONArray
import org.json.JSONObject

object RemoteLeadMapper {

    fun leadToRemoteMap(
        lead: LeadEntity,
        metadata: LeadSyncMetadataEntity?,
        deviceId: String,
        schemaVersion: Int = 1
    ): Map<String, Any?> {
        val now = System.currentTimeMillis()
        val updatedAt = metadata?.localUpdatedAt ?: lead.timestamp
        val serverVersion = metadata?.serverVersion ?: 0L

        return mapOf(
            "id" to lead.id,
            "name" to lead.name,
            "mobile" to lead.mobile,
            "diseases" to lead.diseases,
            "otherDisease" to lead.otherDisease,
            "relation" to lead.relation,
            "otherRelation" to lead.otherRelation,
            "status" to lead.status,
            "reminderDate" to lead.reminderDate,
            "reminderTime" to lead.reminderTime,
            "reminderNote" to lead.reminderNote,
            "reminderStatus" to lead.reminderStatus,
            "notes" to lead.notes,
            "archived" to lead.archived,
            "lastCall" to lead.lastCall,
            "timestamp" to lead.timestamp,
            "notesUpdatedAt" to lead.notesUpdatedAt,
            "reminderUpdatedAt" to lead.reminderUpdatedAt,
            "updatedAt" to updatedAt,
            "deleted" to false,
            "deletedAt" to null,
            "schemaVersion" to schemaVersion,
            "deviceId" to deviceId,
            "serverVersion" to serverVersion
        )
    }

    fun deleteTombstoneMap(
        leadId: String,
        deletedAtUtc: Long,
        updatedAtUtc: Long,
        deviceId: String,
        serverVersion: Long?,
        schemaVersion: Int = 1
    ): Map<String, Any?> {
        return mapOf(
            "id" to leadId,
            "deleted" to true,
            "deletedAt" to deletedAtUtc,
            "updatedAt" to updatedAtUtc,
            "schemaVersion" to schemaVersion,
            "deviceId" to deviceId,
            "serverVersion" to (serverVersion ?: 0L)
        )
    }

    fun mapToRemoteLeadRecord(docId: String, data: Map<String, Any?>?): RemoteLeadRecord {
        if (data == null) {
            return RemoteLeadRecord(
                id = docId,
                name = "",
                mobile = "",
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
                lastCall = null,
                timestamp = 0L,
                notesUpdatedAt = 0L,
                reminderUpdatedAt = 0L,
                updatedAt = 0L,
                deleted = false,
                deletedAt = null,
                schemaVersion = 0,
                deviceId = "",
                serverVersion = 0L
            )
        }

        val id = parseString(data["id"], docId)
        val name = parseString(data["name"], "")
        val mobile = parseString(data["mobile"], "")
        val rawDiseases = parseString(data["diseases"], "[]")
        val diseases = parseDiseasesJson(rawDiseases)
        val otherDisease = parseString(data["otherDisease"], "")
        val relation = parseString(data["relation"], "")
        val otherRelation = parseString(data["otherRelation"], "")
        val status = parseString(data["status"], "Pending")
        val reminderDate = parseString(data["reminderDate"], "")
        val reminderTime = parseString(data["reminderTime"], "")
        val reminderNote = parseString(data["reminderNote"], "")
        val reminderStatus = parseString(data["reminderStatus"], "Pending")
        val notes = parseString(data["notes"], "")
        val archived = parseBoolean(data["archived"], false)
        val lastCall = parseNullableString(data["lastCall"])

        val timestamp = parseLong(data["timestamp"], 0L)
        val notesUpdatedAt = parseLong(data["notesUpdatedAt"], 0L)
        val reminderUpdatedAt = parseLong(data["reminderUpdatedAt"], 0L)
        val updatedAt = parseLong(data["updatedAt"], timestamp)
        val deleted = parseBoolean(data["deleted"], false)
        val deletedAt = parseNullableLong(data["deletedAt"])
        val schemaVersion = parseInt(data["schemaVersion"], 0)
        val deviceId = parseString(data["deviceId"], "")
        val serverVersion = parseLong(data["serverVersion"], 0L)

        return RemoteLeadRecord(
            id = id,
            name = name,
            mobile = mobile,
            diseases = diseases,
            otherDisease = otherDisease,
            relation = relation,
            otherRelation = otherRelation,
            status = if (status.isBlank()) "Pending" else status,
            reminderDate = reminderDate,
            reminderTime = reminderTime,
            reminderNote = reminderNote,
            reminderStatus = if (reminderStatus.isBlank()) "Pending" else reminderStatus,
            notes = notes,
            archived = archived,
            lastCall = lastCall,
            timestamp = timestamp,
            notesUpdatedAt = notesUpdatedAt,
            reminderUpdatedAt = reminderUpdatedAt,
            updatedAt = updatedAt,
            deleted = deleted,
            deletedAt = deletedAt,
            schemaVersion = schemaVersion,
            deviceId = deviceId,
            serverVersion = serverVersion
        )
    }

    fun remoteRecordToLeadEntity(record: RemoteLeadRecord, ownerUid: String = ""): LeadEntity {
        return LeadEntity(
            id = record.id,
            name = record.name,
            mobile = record.mobile,
            diseases = record.diseases,
            otherDisease = record.otherDisease,
            relation = record.relation,
            otherRelation = record.otherRelation,
            status = record.status,
            reminderDate = record.reminderDate,
            reminderTime = record.reminderTime,
            reminderNote = record.reminderNote,
            reminderStatus = record.reminderStatus,
            notes = record.notes,
            archived = record.archived,
            lastCall = record.lastCall,
            timestamp = if (record.timestamp > 0L) record.timestamp else record.updatedAt,
            notesUpdatedAt = record.notesUpdatedAt,
            reminderUpdatedAt = record.reminderUpdatedAt,
            ownerUid = ownerUid
        )
    }

    fun toConflictJson(record: RemoteLeadRecord): String {
        val json = JSONObject()
        json.put("id", record.id)
        json.put("name", record.name)
        json.put("mobile", record.mobile)
        json.put("diseases", record.diseases)
        json.put("otherDisease", record.otherDisease)
        json.put("relation", record.relation)
        json.put("otherRelation", record.otherRelation)
        json.put("status", record.status)
        json.put("reminderDate", record.reminderDate)
        json.put("reminderTime", record.reminderTime)
        json.put("reminderNote", record.reminderNote)
        json.put("reminderStatus", record.reminderStatus)
        json.put("notes", record.notes)
        json.put("archived", record.archived)
        json.put("lastCall", record.lastCall ?: JSONObject.NULL)
        json.put("timestamp", record.timestamp)
        json.put("notesUpdatedAt", record.notesUpdatedAt)
        json.put("reminderUpdatedAt", record.reminderUpdatedAt)
        json.put("updatedAt", record.updatedAt)
        json.put("deleted", record.deleted)
        json.put("deletedAt", record.deletedAt ?: JSONObject.NULL)
        json.put("schemaVersion", record.schemaVersion)
        json.put("deviceId", record.deviceId)
        json.put("serverVersion", record.serverVersion)
        return json.toString()
    }

    fun leadToJson(lead: LeadEntity, metadata: LeadSyncMetadataEntity? = null): String {
        val json = JSONObject()
        json.put("id", lead.id)
        json.put("name", lead.name)
        json.put("mobile", lead.mobile)
        json.put("diseases", lead.diseases)
        json.put("otherDisease", lead.otherDisease)
        json.put("relation", lead.relation)
        json.put("otherRelation", lead.otherRelation)
        json.put("status", lead.status)
        json.put("reminderDate", lead.reminderDate)
        json.put("reminderTime", lead.reminderTime)
        json.put("reminderNote", lead.reminderNote)
        json.put("reminderStatus", lead.reminderStatus)
        json.put("notes", lead.notes)
        json.put("archived", lead.archived)
        json.put("lastCall", lead.lastCall ?: JSONObject.NULL)
        json.put("timestamp", lead.timestamp)
        json.put("notesUpdatedAt", lead.notesUpdatedAt)
        json.put("reminderUpdatedAt", lead.reminderUpdatedAt)
        if (metadata != null) {
            json.put("localUpdatedAt", metadata.localUpdatedAt)
            json.put("serverVersion", metadata.serverVersion ?: JSONObject.NULL)
            json.put("localVersion", metadata.localVersion)
        }
        return json.toString()
    }

    private fun parseString(value: Any?, default: String): String {
        return when (value) {
            null -> default
            is String -> value
            else -> value.toString()
        }
    }

    private fun parseNullableString(value: Any?): String? {
        return when (value) {
            null -> null
            is String -> if (value.isBlank() || value.lowercase() == "null") null else value
            else -> value.toString()
        }
    }

    private fun parseBoolean(value: Any?, default: Boolean): Boolean {
        return when (value) {
            null -> default
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> value.lowercase() == "true" || value == "1"
            else -> default
        }
    }

    private fun parseInt(value: Any?, default: Int): Int {
        return when (value) {
            null -> default
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: default
            else -> default
        }
    }

    private fun parseLong(value: Any?, default: Long): Long {
        return when (value) {
            null -> default
            is Number -> value.toLong()
            is String -> value.toLongOrNull() ?: default
            else -> {
                val className = value.javaClass.name
                if (className.contains("Timestamp") || className.contains("Date")) {
                    try {
                        val method = value.javaClass.getMethod("toDate")
                        val date = method.invoke(value) as? java.util.Date
                        date?.time ?: default
                    } catch (e: Exception) {
                        try {
                            val getTime = value.javaClass.getMethod("getTime")
                            (getTime.invoke(value) as? Long) ?: default
                        } catch (e2: Exception) {
                            default
                        }
                    }
                } else {
                    default
                }
            }
        }
    }

    private fun parseNullableLong(value: Any?): Long? {
        if (value == null) return null
        return parseLong(value, -1L).takeIf { it >= 0L }
    }

    private fun parseDiseasesJson(raw: String): String {
        if (raw.isBlank()) return "[]"
        return try {
            JSONArray(raw)
            raw
        } catch (e: Exception) {
            "[]"
        }
    }
}
