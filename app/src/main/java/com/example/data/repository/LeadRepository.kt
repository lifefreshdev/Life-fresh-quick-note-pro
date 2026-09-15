package com.example.data.repository

import com.example.data.database.LeadDao
import com.example.data.database.LeadEntity
import com.example.sync.LeadSyncMutationCoordinator
import com.example.sync.LeadWriteOrigin
import kotlinx.coroutines.flow.Flow

class LeadRepository(
    private val leadDao: LeadDao,
    private val mutationCoordinator: LeadSyncMutationCoordinator? = null
) {
    fun getAllLeads(ownerUid: String): Flow<List<LeadEntity>> = leadDao.getAllLeads(ownerUid)

    suspend fun getLeadById(id: String, ownerUid: String): LeadEntity? = leadDao.getLeadById(id, ownerUid)

    suspend fun getAllLeadsList(ownerUid: String): List<LeadEntity> = leadDao.getAllLeadsList(ownerUid)

    suspend fun claimUnownedLeads(ownerUid: String): Int = leadDao.claimUnownedLeads(ownerUid)

    suspend fun insertLead(lead: LeadEntity, origin: LeadWriteOrigin = LeadWriteOrigin.LOCAL_USER) {
        if (mutationCoordinator != null) {
            mutationCoordinator.upsertLead(lead, origin)
        } else {
            leadDao.insertLead(lead)
        }
    }

    suspend fun insertLeads(leads: List<LeadEntity>, origin: LeadWriteOrigin = LeadWriteOrigin.LOCAL_USER) {
        if (mutationCoordinator != null) {
            mutationCoordinator.upsertLeads(leads, origin)
        } else {
            leadDao.insertLeads(leads)
        }
    }

    suspend fun deleteLeadById(id: String, ownerUid: String, origin: LeadWriteOrigin = LeadWriteOrigin.LOCAL_USER) {
        if (mutationCoordinator != null) {
            mutationCoordinator.deleteLead(id, ownerUid, origin)
        } else {
            leadDao.deleteLeadById(id, ownerUid)
        }
    }

    suspend fun clearLeadsForUser(ownerUid: String) {
        leadDao.clearLeadsForUser(ownerUid)
    }

    suspend fun clearAllLeads() {
        leadDao.clearAllLeads()
    }
}
