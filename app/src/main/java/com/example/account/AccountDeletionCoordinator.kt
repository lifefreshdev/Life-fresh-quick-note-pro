package com.example.account


/** Credentials supplied only for the destructive account-deletion operation. */
sealed interface AccountDeletionCredential {
    data class Password(val value: String) : AccountDeletionCredential
    data class GoogleIdToken(val value: String) : AccountDeletionCredential
}

data class DeletionAccount(
    val uid: String,
    val isAnonymous: Boolean,
    val email: String?,
    val providerIds: Set<String>
)

enum class AccountDeletionStage {
    VALIDATION,
    REAUTHENTICATION,
    PREPARATION,
    REMOTE_DATA,
    LOCAL_DATA,
    AUTH_ACCOUNT
}

sealed interface AccountDeletionResult {
    data object Success : AccountDeletionResult

    data class Failure(
        val stage: AccountDeletionStage,
        val message: String,
        val cloudDataDeleted: Boolean = false,
        val localDataDeleted: Boolean = false
    ) : AccountDeletionResult
}

/** Firebase-facing operations kept behind an interface so the orchestration is unit-testable. */
interface AccountDeletionGateway {
    fun currentAccount(): DeletionAccount?
    suspend fun reauthenticate(expectedUid: String, credential: AccountDeletionCredential)
    suspend fun deleteRemoteData(expectedUid: String)
    suspend fun deleteAuthenticationAccount(expectedUid: String)
}

/** Device-facing operations for the active account only. */
interface AccountDeletionLocalStore {
    fun activeUid(): String
    suspend fun prepareForDeletion(expectedUid: String)
    suspend fun clearUserData(expectedUid: String)
    fun clearSession(expectedUid: String)
}

/**
 * Executes account deletion in a guarded order. Every irreversible stage is preceded by an
 * expected-UID check so an account switch cannot redirect deletion to another workspace.
 */
class AccountDeletionCoordinator(
    private val gateway: AccountDeletionGateway,
    private val localStore: AccountDeletionLocalStore
) {
    suspend fun delete(credential: AccountDeletionCredential): AccountDeletionResult {
        val account = gateway.currentAccount()
            ?: return failure(AccountDeletionStage.VALIDATION, "No signed-in account was found.")

        if (account.isAnonymous) {
            return failure(
                AccountDeletionStage.VALIDATION,
                "Guest profiles do not have a cloud account. Use Delete Local Data instead."
            )
        }
        if (account.uid.isBlank()) {
            return failure(AccountDeletionStage.VALIDATION, "The active account identifier is invalid.")
        }
        if (localStore.activeUid() != account.uid) {
            return failure(
                AccountDeletionStage.VALIDATION,
                "The active workspace changed. Sign in again and retry account deletion."
            )
        }

        val credentialError = validateCredential(account, credential)
        if (credentialError != null) {
            return failure(AccountDeletionStage.REAUTHENTICATION, credentialError)
        }

        try {
            gateway.reauthenticate(account.uid, credential)
        } catch (t: Throwable) {
            return failure(
                AccountDeletionStage.REAUTHENTICATION,
                t.safeMessage("Identity verification failed. Check your credentials and try again.")
            )
        }

        if (!isSameActiveAccount(account.uid)) {
            return accountChangedFailure()
        }

        try {
            localStore.prepareForDeletion(account.uid)
        } catch (t: Throwable) {
            return failure(
                AccountDeletionStage.PREPARATION,
                t.safeMessage("Could not safely stop synchronization and reminders.")
            )
        }

        if (!isSameActiveAccount(account.uid)) {
            return accountChangedFailure()
        }

        try {
            gateway.deleteRemoteData(account.uid)
        } catch (t: Throwable) {
            return failure(
                AccountDeletionStage.REMOTE_DATA,
                t.safeMessage("Cloud data could not be deleted. No local data or account was removed.")
            )
        }

        if (!isSameActiveAccount(account.uid)) {
            return failure(
                AccountDeletionStage.VALIDATION,
                "The account changed after cloud cleanup. Local data and authentication were not deleted.",
                cloudDataDeleted = true
            )
        }

        try {
            localStore.clearUserData(account.uid)
        } catch (t: Throwable) {
            return failure(
                AccountDeletionStage.LOCAL_DATA,
                t.safeMessage("Cloud data was deleted, but local cleanup failed. Retry before using another account."),
                cloudDataDeleted = true
            )
        }

        if (!isSameActiveAccount(account.uid)) {
            return failure(
                AccountDeletionStage.VALIDATION,
                "The account changed during cleanup. Authentication was not deleted.",
                cloudDataDeleted = true,
                localDataDeleted = true
            )
        }

        try {
            gateway.deleteAuthenticationAccount(account.uid)
        } catch (t: Throwable) {
            return failure(
                AccountDeletionStage.AUTH_ACCOUNT,
                t.safeMessage(
                    "Cloud and local data were deleted, but the sign-in account could not be removed. " +
                        "Sign in again and retry Delete Account."
                ),
                cloudDataDeleted = true,
                localDataDeleted = true
            )
        }

        localStore.clearSession(account.uid)
        return AccountDeletionResult.Success
    }

    private fun validateCredential(
        account: DeletionAccount,
        credential: AccountDeletionCredential
    ): String? {
        return when (credential) {
            is AccountDeletionCredential.Password -> when {
                "password" !in account.providerIds -> "This account does not use password sign-in."
                account.email.isNullOrBlank() -> "The account email address is unavailable."
                credential.value.isBlank() -> "Enter your current password to continue."
                else -> null
            }

            is AccountDeletionCredential.GoogleIdToken -> when {
                "google.com" !in account.providerIds -> "This account is not connected to Google Sign-In."
                credential.value.isBlank() -> "Google identity verification did not return a valid token."
                else -> null
            }
        }
    }

    private fun isSameActiveAccount(expectedUid: String): Boolean {
        val current = gateway.currentAccount()
        return current != null &&
            !current.isAnonymous &&
            current.uid == expectedUid &&
            localStore.activeUid() == expectedUid
    }

    private fun accountChangedFailure() = failure(
        AccountDeletionStage.VALIDATION,
        "The signed-in account changed. Deletion was stopped without touching the new account."
    )

    private fun failure(
        stage: AccountDeletionStage,
        message: String,
        cloudDataDeleted: Boolean = false,
        localDataDeleted: Boolean = false
    ) = AccountDeletionResult.Failure(stage, message, cloudDataDeleted, localDataDeleted)

    private fun Throwable.safeMessage(fallback: String): String {
        val detail = localizedMessage?.trim().orEmpty()
        return if (detail.isBlank()) fallback else "$fallback\n$detail"
    }
}
