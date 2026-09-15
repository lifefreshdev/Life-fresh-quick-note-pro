package com.example.account

import android.content.Context
import androidx.room.withTransaction
import com.example.audio.AlarmService
import com.example.audio.ReminderScheduler
import com.example.data.ActiveAccountStore
import com.example.data.database.AppDatabase
import com.example.sync.SyncPreferences
import com.example.sync.SyncScheduler
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Production Firebase implementation. The app currently stores cloud CRM data only under leads. */
class FirebaseAccountDeletionGateway(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : AccountDeletionGateway {

    override fun currentAccount(): DeletionAccount? {
        val user = auth.currentUser ?: return null
        return DeletionAccount(
            uid = user.uid,
            isAnonymous = user.isAnonymous,
            email = user.email,
            providerIds = user.providerData.map { it.providerId }.toSet()
        )
    }

    override suspend fun reauthenticate(
        expectedUid: String,
        credential: AccountDeletionCredential
    ) {
        val user = requireCurrentUser(expectedUid)
        val firebaseCredential = when (credential) {
            is AccountDeletionCredential.Password -> {
                val email = user.email?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("The account email address is unavailable.")
                EmailAuthProvider.getCredential(email, credential.value)
            }

            is AccountDeletionCredential.GoogleIdToken ->
                GoogleAuthProvider.getCredential(credential.value, null)
        }
        user.reauthenticate(firebaseCredential).awaitResult()
        requireCurrentUser(expectedUid)
    }

    override suspend fun deleteRemoteData(expectedUid: String) {
        withContext(Dispatchers.IO) {
            val userDocument = firestore.collection("users").document(expectedUid)
            val leads = userDocument.collection("leads")

            while (true) {
                requireCurrentUser(expectedUid)
                // Server-only reads prevent an offline cache from being mistaken for complete deletion.
                val snapshot = leads.limit(400).get(Source.SERVER).awaitResult()
                if (snapshot.isEmpty) break

                requireCurrentUser(expectedUid)
                val batch = firestore.batch()
                snapshot.documents.forEach { document -> batch.delete(document.reference) }
                batch.commit().awaitResult()
                firestore.waitForPendingWrites().awaitResult()
                requireCurrentUser(expectedUid)
            }

            requireCurrentUser(expectedUid)
            userDocument.delete().awaitResult()
            firestore.waitForPendingWrites().awaitResult()
            requireCurrentUser(expectedUid)
        }
    }

    override suspend fun deleteAuthenticationAccount(expectedUid: String) {
        requireCurrentUser(expectedUid).delete().awaitResult()
    }

    private fun requireCurrentUser(expectedUid: String) = auth.currentUser?.takeIf {
        !it.isAnonymous && it.uid == expectedUid
    } ?: throw IllegalStateException("The signed-in account changed during deletion.")
}

/** Production local cleanup; all Room deletes are committed as one owner-scoped transaction. */
class RoomAccountDeletionLocalStore(
    context: Context,
    private val database: AppDatabase = AppDatabase.getDatabase(context.applicationContext)
) : AccountDeletionLocalStore {
    private val appContext = context.applicationContext

    override fun activeUid(): String = ActiveAccountStore.getActiveUid(appContext)

    override suspend fun prepareForDeletion(expectedUid: String) {
        requireExpectedActiveUid(expectedUid)
        SyncPreferences(appContext).setAutomaticSyncEnabled(false)
        SyncScheduler.cancelAllForUser(appContext, expectedUid)

        val leads = database.leadDao.getAllLeadsList(expectedUid)
        leads.forEach { ReminderScheduler.cancelReminder(appContext, expectedUid, it.id) }
        AlarmService.stopService(appContext, expectedUid)
        ReminderScheduler.clearNotificationsForUser(appContext, expectedUid)
        ReminderScheduler.activeRingingLead.value = null
        ReminderScheduler.stopRingingSound()
    }

    override suspend fun clearUserData(expectedUid: String) {
        requireExpectedActiveUid(expectedUid)
        database.withTransaction {
            database.syncDao.clearOutboxForUser(expectedUid)
            database.syncDao.clearConflictsForUser(expectedUid)
            database.syncDao.clearCheckpointByScope("leads:$expectedUid")
            database.leadSyncMetadataDao.clearMetadataForUser(expectedUid)
            database.aiChatDao.clearMessagesForUser(expectedUid)
            database.aiChatDao.clearSessionsForUser(expectedUid)
            database.leadDao.clearLeadsForUser(expectedUid)
        }

        SyncPreferences(appContext).clearAccountState(expectedUid)
        appContext.getSharedPreferences("lifefresh_prefs", Context.MODE_PRIVATE)
            .edit()
            .remove("cloud_restore_completed_$expectedUid")
            .remove("cloud_last_sync_time")
            .remove("cloud_total_customers")
            .apply()
    }

    override fun clearSession(expectedUid: String) {
        if (ActiveAccountStore.getActiveUid(appContext) == expectedUid) {
            ActiveAccountStore.clearActiveUid(appContext)
        }
    }

    private fun requireExpectedActiveUid(expectedUid: String) {
        require(expectedUid.isNotBlank() && activeUid() == expectedUid) {
            "The active workspace changed during account deletion."
        }
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        when {
            task.isSuccessful -> continuation.resume(task.result)
            else -> continuation.resumeWithException(
                task.exception ?: IllegalStateException("Firebase operation failed without an error detail.")
            )
        }
    }
}
