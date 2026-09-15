package com.example.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "leads",
    primaryKeys = ["ownerUid", "id"]
)
data class LeadEntity(
    val id: String,
    val name: String,
    val mobile: String,
    val diseases: String,       // JSON list format e.g., ["Diabetes", "High BP"]
    val otherDisease: String,
    val relation: String,
    val otherRelation: String,
    val status: String,         // "Pending" or "Complete"
    val reminderDate: String,   // "yyyy-MM-dd" or empty
    val reminderTime: String,   // "HH:mm" or empty
    val reminderNote: String,
    val reminderStatus: String, // "Pending", "Completed", "Dismissed" etc.
    val notes: String = "",
    val archived: Boolean = false,
    val lastCall: String? = null, // ISO-8601 string or null
    val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val notesUpdatedAt: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val reminderUpdatedAt: Long = 0L,
    val ownerUid: String = ""
)
