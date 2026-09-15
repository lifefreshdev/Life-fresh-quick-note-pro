package com.example.account

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountDeletionCoordinatorTest {

    @Test
    fun passwordAccountDeletesInGuardedOrder() = runBlocking {
        val sharedEvents = mutableListOf<String>()
        val gateway = FakeGateway(passwordAccount(), sharedEvents)
        val local = FakeLocalStore("user-a", sharedEvents)
        val coordinator = AccountDeletionCoordinator(gateway, local)

        val result = coordinator.delete(AccountDeletionCredential.Password("correct-password"))

        assertTrue(result is AccountDeletionResult.Success)
        assertEquals(
            listOf("reauth:user-a", "prepare:user-a", "remote:user-a", "local:user-a", "auth:user-a", "session:user-a"),
            sharedEvents
        )
        assertTrue(gateway.remoteDeleted)
        assertTrue(gateway.authDeleted)
        assertTrue(local.localDeleted)
    }

    @Test
    fun anonymousAccountIsRejectedWithoutDeletion() = runBlocking {
        val gateway = FakeGateway(passwordAccount(isAnonymous = true))
        val local = FakeLocalStore("user-a")

        val result = AccountDeletionCoordinator(gateway, local)
            .delete(AccountDeletionCredential.Password("password"))

        assertFailureStage(result, AccountDeletionStage.VALIDATION)
        assertFalse(gateway.remoteDeleted)
        assertFalse(gateway.authDeleted)
        assertFalse(local.localDeleted)
    }

    @Test
    fun mismatchedActiveWorkspaceIsRejected() = runBlocking {
        val gateway = FakeGateway(passwordAccount())
        val local = FakeLocalStore("user-b")

        val result = AccountDeletionCoordinator(gateway, local)
            .delete(AccountDeletionCredential.Password("password"))

        assertFailureStage(result, AccountDeletionStage.VALIDATION)
        assertTrue(gateway.events.isEmpty())
        assertTrue(local.events.isEmpty())
    }

    @Test
    fun accountSwitchAfterReauthenticationStopsBeforeAnyDeletion() = runBlocking {
        val gateway = FakeGateway(passwordAccount()).apply {
            onReauthenticated = { account = passwordAccount(uid = "user-b") }
        }
        val local = FakeLocalStore("user-a")

        val result = AccountDeletionCoordinator(gateway, local)
            .delete(AccountDeletionCredential.Password("password"))

        assertFailureStage(result, AccountDeletionStage.VALIDATION)
        assertFalse(gateway.remoteDeleted)
        assertFalse(gateway.authDeleted)
        assertFalse(local.localDeleted)
        assertFalse(local.prepared)
    }


    @Test
    fun blankPasswordIsRejectedBeforeReauthentication() = runBlocking {
        val gateway = FakeGateway(passwordAccount())
        val local = FakeLocalStore("user-a")

        val result = AccountDeletionCoordinator(gateway, local)
            .delete(AccountDeletionCredential.Password(""))

        assertFailureStage(result, AccountDeletionStage.REAUTHENTICATION)
        assertTrue(gateway.events.isEmpty())
        assertTrue(local.events.isEmpty())
    }

    @Test
    fun accountSwitchAfterRemoteDeletionProtectsNewAccountsLocalDataAndAuthentication() = runBlocking {
        val gateway = FakeGateway(passwordAccount()).apply {
            onRemoteDeleted = { account = passwordAccount(uid = "user-b") }
        }
        val local = FakeLocalStore("user-a")

        val result = AccountDeletionCoordinator(gateway, local)
            .delete(AccountDeletionCredential.Password("password"))

        val failure = assertFailureStage(result, AccountDeletionStage.VALIDATION)
        assertTrue(failure.cloudDataDeleted)
        assertFalse(failure.localDataDeleted)
        assertTrue(gateway.remoteDeleted)
        assertFalse(local.localDeleted)
        assertFalse(gateway.authDeleted)
    }

    @Test
    fun remoteFailureDoesNotDeleteLocalDataOrAuthentication() = runBlocking {
        val gateway = FakeGateway(passwordAccount()).apply { remoteFailure = IllegalStateException("offline") }
        val local = FakeLocalStore("user-a")

        val result = AccountDeletionCoordinator(gateway, local)
            .delete(AccountDeletionCredential.Password("password"))

        val failure = assertFailureStage(result, AccountDeletionStage.REMOTE_DATA)
        assertFalse(failure.cloudDataDeleted)
        assertFalse(failure.localDataDeleted)
        assertFalse(local.localDeleted)
        assertFalse(gateway.authDeleted)
    }

    @Test
    fun localFailureReportsCloudOnlyPartialCompletion() = runBlocking {
        val gateway = FakeGateway(passwordAccount())
        val local = FakeLocalStore("user-a").apply { localFailure = IllegalStateException("database locked") }

        val result = AccountDeletionCoordinator(gateway, local)
            .delete(AccountDeletionCredential.Password("password"))

        val failure = assertFailureStage(result, AccountDeletionStage.LOCAL_DATA)
        assertTrue(failure.cloudDataDeleted)
        assertFalse(failure.localDataDeleted)
        assertTrue(gateway.remoteDeleted)
        assertFalse(gateway.authDeleted)
    }

    @Test
    fun authFailureReportsCloudAndLocalDataAlreadyDeleted() = runBlocking {
        val gateway = FakeGateway(passwordAccount()).apply { authFailure = IllegalStateException("recent login required") }
        val local = FakeLocalStore("user-a")

        val result = AccountDeletionCoordinator(gateway, local)
            .delete(AccountDeletionCredential.Password("password"))

        val failure = assertFailureStage(result, AccountDeletionStage.AUTH_ACCOUNT)
        assertTrue(failure.cloudDataDeleted)
        assertTrue(failure.localDataDeleted)
        assertTrue(gateway.remoteDeleted)
        assertTrue(local.localDeleted)
        assertFalse(gateway.authDeleted)
    }

    @Test
    fun googleAccountRequiresGoogleCredentialAndCanComplete() = runBlocking {
        val gateway = FakeGateway(
            DeletionAccount(
                uid = "google-user",
                isAnonymous = false,
                email = "google@example.com",
                providerIds = setOf("firebase", "google.com")
            )
        )
        val local = FakeLocalStore("google-user")
        val coordinator = AccountDeletionCoordinator(gateway, local)

        val wrongResult = coordinator.delete(AccountDeletionCredential.Password("password"))
        assertFailureStage(wrongResult, AccountDeletionStage.REAUTHENTICATION)

        val successResult = coordinator.delete(AccountDeletionCredential.GoogleIdToken("id-token"))
        assertTrue(successResult is AccountDeletionResult.Success)
    }

    private fun passwordAccount(uid: String = "user-a", isAnonymous: Boolean = false) = DeletionAccount(
        uid = uid,
        isAnonymous = isAnonymous,
        email = "user@example.com",
        providerIds = setOf("firebase", "password")
    )

    private fun assertFailureStage(
        result: AccountDeletionResult,
        expected: AccountDeletionStage
    ): AccountDeletionResult.Failure {
        val failure = result as AccountDeletionResult.Failure
        assertEquals(expected, failure.stage)
        return failure
    }

    private class FakeGateway(
        initialAccount: DeletionAccount?,
        private val sharedEvents: MutableList<String>? = null
    ) : AccountDeletionGateway {
        var account: DeletionAccount? = initialAccount
        var remoteDeleted = false
        var authDeleted = false
        var remoteFailure: Throwable? = null
        var authFailure: Throwable? = null
        var onReauthenticated: (() -> Unit)? = null
        var onRemoteDeleted: (() -> Unit)? = null
        val events = mutableListOf<String>()

        override fun currentAccount(): DeletionAccount? = account

        override suspend fun reauthenticate(expectedUid: String, credential: AccountDeletionCredential) {
            events += "reauth:$expectedUid"
            sharedEvents?.add("reauth:$expectedUid")
            onReauthenticated?.invoke()
        }

        override suspend fun deleteRemoteData(expectedUid: String) {
            events += "remote:$expectedUid"
            sharedEvents?.add("remote:$expectedUid")
            remoteFailure?.let { throw it }
            remoteDeleted = true
            onRemoteDeleted?.invoke()
        }

        override suspend fun deleteAuthenticationAccount(expectedUid: String) {
            events += "auth:$expectedUid"
            sharedEvents?.add("auth:$expectedUid")
            authFailure?.let { throw it }
            authDeleted = true
            account = null
        }
    }

    private class FakeLocalStore(
        private var uid: String,
        private val sharedEvents: MutableList<String>? = null
    ) : AccountDeletionLocalStore {
        var prepared = false
        var localDeleted = false
        var localFailure: Throwable? = null
        val events = mutableListOf<String>()

        override fun activeUid(): String = uid

        override suspend fun prepareForDeletion(expectedUid: String) {
            events += "prepare:$expectedUid"
            sharedEvents?.add("prepare:$expectedUid")
            prepared = true
        }

        override suspend fun clearUserData(expectedUid: String) {
            events += "local:$expectedUid"
            sharedEvents?.add("local:$expectedUid")
            localFailure?.let { throw it }
            localDeleted = true
        }

        override fun clearSession(expectedUid: String) {
            events += "session:$expectedUid"
            sharedEvents?.add("session:$expectedUid")
            if (uid == expectedUid) uid = ""
        }
    }
}
