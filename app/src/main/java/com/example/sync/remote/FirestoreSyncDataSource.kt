package com.example.sync.remote

import com.example.sync.model.*
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FirestoreSyncDataSource(
    private val firestoreProvider: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() }
) : RemoteSyncDataSource {

    override suspend fun push(
        uid: String,
        mutations: List<RemoteMutation>
    ): PushBatchResult {
        if (uid.isBlank() || mutations.isEmpty()) {
            return PushBatchResult(emptyList())
        }

        val results = mutableListOf<PushItemResult>()
        val db = firestoreProvider()
        val collectionRef = db.collection("users").document(uid).collection("leads")

        for (mutation in mutations) {
            val docRef = collectionRef.document(mutation.entityId)
            try {
                val remoteSnapshot = awaitTask(docRef.get())
                val remoteData = remoteSnapshot.data
                val remoteRecord = if (remoteSnapshot.exists()) {
                    RemoteLeadMapper.mapToRemoteLeadRecord(remoteSnapshot.id, remoteData)
                } else null

                val remoteServerVersion = remoteRecord?.serverVersion ?: 0L
                val baseServerVersion = mutation.serverVersion ?: 0L

                // Detect push conflict: remote has advanced beyond our base version
                if (remoteRecord != null && remoteServerVersion > baseServerVersion) {
                    results.add(
                        PushItemResult(
                            mutationId = mutation.mutationId,
                            entityId = mutation.entityId,
                            status = PushItemStatus.CONFLICT,
                            newServerVersion = remoteServerVersion,
                            remoteUpdatedAt = remoteRecord.updatedAt,
                            conflictRecord = remoteRecord,
                            errorMessage = "Remote document has higher server version"
                        )
                    )
                    continue
                }

                val nextServerVersion = remoteServerVersion + 1L
                val nowUtc = System.currentTimeMillis()

                val finalPayload = mutation.payload.toMutableMap()
                finalPayload["serverVersion"] = nextServerVersion
                finalPayload["updatedAt"] = finalPayload["updatedAt"] ?: nowUtc

                awaitTask(docRef.set(finalPayload))

                results.add(
                    PushItemResult(
                        mutationId = mutation.mutationId,
                        entityId = mutation.entityId,
                        status = PushItemStatus.ACKNOWLEDGED,
                        newServerVersion = nextServerVersion,
                        remoteUpdatedAt = (finalPayload["updatedAt"] as? Long) ?: nowUtc
                    )
                )
            } catch (e: Exception) {
                val classification = classifyFirestoreException(e)
                val status = if (classification.errorClass == SyncErrorClass.AUTH ||
                    classification.errorClass == SyncErrorClass.MALFORMED_DATA ||
                    !classification.isRetryable
                ) {
                    PushItemStatus.PERMANENT_FAILURE
                } else {
                    PushItemStatus.RETRYABLE_FAILURE
                }

                results.add(
                    PushItemResult(
                        mutationId = mutation.mutationId,
                        entityId = mutation.entityId,
                        status = status,
                        errorMessage = classification.message
                    )
                )
            }
        }

        return PushBatchResult(results)
    }

    override suspend fun pull(
        uid: String,
        cursor: String?,
        pageSize: Int
    ): PullPage {
        if (uid.isBlank()) {
            return PullPage(emptyList(), null, false)
        }

        val db = firestoreProvider()
        var query = db.collection("users")
            .document(uid)
            .collection("leads")
            .orderBy(FieldPath.documentId())
            .limit(pageSize.toLong())

        if (!cursor.isNullOrBlank()) {
            query = query.startAfter(cursor)
        }

        val snapshot = awaitTask(query.get())
        val docs = snapshot.documents

        val records = mutableListOf<RemoteLeadRecord>()
        for (doc in docs) {
            try {
                val record = RemoteLeadMapper.mapToRemoteLeadRecord(doc.id, doc.data)
                records.add(record)
            } catch (e: Exception) {
                // Return safe malformed fallback so single record failure doesn't crash pull
                records.add(
                    RemoteLeadRecord(
                        id = doc.id,
                        name = "",
                        mobile = "",
                        schemaVersion = 999 // schema mismatch marker for malformed doc
                    )
                )
            }
        }

        val nextCursor = if (docs.size == pageSize && docs.isNotEmpty()) {
            docs.last().id
        } else {
            null
        }

        return PullPage(
            records = records,
            nextCursor = nextCursor,
            hasMore = nextCursor != null
        )
    }

    private suspend fun <T> awaitTask(task: Task<T>): T = suspendCancellableCoroutine { continuation ->
        task.addOnSuccessListener { result ->
            if (continuation.isActive) continuation.resume(result)
        }.addOnFailureListener { exception ->
            if (continuation.isActive) continuation.resumeWithException(exception)
        }
    }

    companion object {
        fun classifyFirestoreException(e: Exception): RetryClassification {
            if (e is FirebaseFirestoreException) {
                return when (e.code) {
                    FirebaseFirestoreException.Code.UNAVAILABLE,
                    FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
                    FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED,
                    FirebaseFirestoreException.Code.ABORTED -> {
                        RetryClassification(
                            isRetryable = true,
                            errorClass = SyncErrorClass.NETWORK,
                            message = "Temporary network error: ${e.code}"
                        )
                    }
                    FirebaseFirestoreException.Code.UNAUTHENTICATED,
                    FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                        RetryClassification(
                            isRetryable = false,
                            errorClass = SyncErrorClass.AUTH,
                            message = "Authentication or permission denied: ${e.code}"
                        )
                    }
                    FirebaseFirestoreException.Code.INVALID_ARGUMENT -> {
                        RetryClassification(
                            isRetryable = false,
                            errorClass = SyncErrorClass.MALFORMED_DATA,
                            message = "Invalid arguments"
                        )
                    }
                    else -> {
                        RetryClassification(
                            isRetryable = true,
                            errorClass = SyncErrorClass.SERVER,
                            message = "Server error: ${e.code}"
                        )
                    }
                }
            }

            val msg = e.localizedMessage ?: "Unknown network error"
            val isIoOrTimeout = e is java.io.IOException ||
                    msg.contains("timeout", ignoreCase = true) ||
                    msg.contains("connect", ignoreCase = true) ||
                    msg.contains("network", ignoreCase = true)

            return RetryClassification(
                isRetryable = isIoOrTimeout,
                errorClass = if (isIoOrTimeout) SyncErrorClass.NETWORK else SyncErrorClass.UNKNOWN,
                message = msg
            )
        }
    }
}
