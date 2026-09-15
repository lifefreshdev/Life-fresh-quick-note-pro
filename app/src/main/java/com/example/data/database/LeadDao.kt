package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LeadDao {
    @Query("SELECT * FROM leads WHERE ownerUid = :ownerUid ORDER BY timestamp DESC")
    fun getAllLeads(ownerUid: String): Flow<List<LeadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLead(lead: LeadEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertLeadSync(lead: LeadEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLeads(leads: List<LeadEntity>)

    @Query("DELETE FROM leads WHERE id = :id AND ownerUid = :ownerUid")
    suspend fun deleteLeadById(id: String, ownerUid: String)

    @Query("SELECT * FROM leads WHERE id = :id AND ownerUid = :ownerUid LIMIT 1")
    suspend fun getLeadById(id: String, ownerUid: String): LeadEntity?

    @Query("SELECT * FROM leads WHERE ownerUid = :ownerUid")
    suspend fun getAllLeadsList(ownerUid: String): List<LeadEntity>

    @Query("UPDATE leads SET ownerUid = :ownerUid WHERE ownerUid = '' OR ownerUid IS NULL")
    suspend fun claimUnownedLeads(ownerUid: String): Int

    @Query("DELETE FROM leads WHERE ownerUid = :ownerUid")
    suspend fun clearLeadsForUser(ownerUid: String)

    @Query("DELETE FROM leads")
    suspend fun clearAllLeads()
}
