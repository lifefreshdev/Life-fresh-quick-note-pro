package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import com.example.data.ActiveAccountStore
import com.example.account.AccountDeletionCoordinator
import com.example.account.AccountDeletionCredential
import com.example.account.AccountDeletionResult
import com.example.account.FirebaseAccountDeletionGateway
import com.example.account.RoomAccountDeletionLocalStore
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AuthState {
    object Idle : AuthState
    object Loading : AuthState
    data class Success(val message: String) : AuthState
    data class Error(val message: String) : AuthState
}

sealed interface AccountDeletionUiState {
    data object Idle : AccountDeletionUiState
    data object Deleting : AccountDeletionUiState
    data object Success : AccountDeletionUiState
    data class Error(
        val message: String,
        val cloudDataDeleted: Boolean,
        val localDataDeleted: Boolean
    ) : AccountDeletionUiState
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val guestTransitionPrefs = application.getSharedPreferences(
        "lifefresh_guest_transition_prefs",
        android.content.Context.MODE_PRIVATE
    )
    private val _pendingGuestOwnerUid = MutableStateFlow<String?>(
        guestTransitionPrefs.getString("pending_guest_owner_uid", null)
    )
    val pendingGuestOwnerUid: StateFlow<String?> = _pendingGuestOwnerUid.asStateFlow()

    private val _accountDeletionState = MutableStateFlow<AccountDeletionUiState>(AccountDeletionUiState.Idle)
    val accountDeletionState: StateFlow<AccountDeletionUiState> = _accountDeletionState.asStateFlow()

    private val accountDeletionCoordinator by lazy {
        AccountDeletionCoordinator(
            gateway = FirebaseAccountDeletionGateway(auth = auth),
            localStore = RoomAccountDeletionLocalStore(getApplication())
        )
    }

    private val deletionPrefs = application.getSharedPreferences(
        "lifefresh_account_deletion_prefs",
        android.content.Context.MODE_PRIVATE
    )

    private val _deletionNotice = MutableStateFlow<String?>(null)
    val deletionNotice: StateFlow<String?> = _deletionNotice.asStateFlow()

    fun clearDeletionNotice() {
        _deletionNotice.value = null
    }

    init {
        val initialUser = auth.currentUser
        if (initialUser != null && initialUser.uid.isNotBlank()) {
            ActiveAccountStore.setActiveUid(getApplication(), initialUser.uid)
            if (!initialUser.isAnonymous) {
                checkAndHandlePendingDeletion(initialUser)
            }
        }
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            val prevUid = _currentUser.value?.uid
            _currentUser.value = user
            if (user != null && user.uid.isNotBlank()) {
                ActiveAccountStore.setActiveUid(getApplication(), user.uid)
                if (prevUid != user.uid) {
                    com.example.audio.ReminderScheduler.startCheckingForUser(getApplication(), user.uid)
                    if (!user.isAnonymous) {
                        checkAndHandlePendingDeletion(user)
                    }
                }
            } else if (user == null) {
                ActiveAccountStore.clearActiveUid(getApplication())
            }
        }
    }

    fun clearState() {
        _authState.value = AuthState.Idle
    }

    fun clearAccountDeletionState() {
        _accountDeletionState.value = AccountDeletionUiState.Idle
    }

    fun beginAccountSignInFromGuest() {
        val user = auth.currentUser
        if (user == null || !user.isAnonymous || user.uid.isBlank()) {
            _authState.value = AuthState.Error("Guest session is not active.")
            return
        }

        _pendingGuestOwnerUid.value = user.uid
        guestTransitionPrefs.edit().putString("pending_guest_owner_uid", user.uid).apply()
        performLogout(clearPendingGuestTransition = false)
    }

    fun clearPendingGuestTransition() {
        _pendingGuestOwnerUid.value = null
        guestTransitionPrefs.edit().remove("pending_guest_owner_uid").apply()
    }

    fun deleteAccountWithPassword(password: String) {
        deleteAccount(AccountDeletionCredential.Password(password))
    }

    fun deleteAccountWithGoogleCredential(idToken: String) {
        deleteAccount(AccountDeletionCredential.GoogleIdToken(idToken))
    }

    private fun deleteAccount(credential: AccountDeletionCredential) {
        if (_accountDeletionState.value is AccountDeletionUiState.Deleting) return
        _accountDeletionState.value = AccountDeletionUiState.Deleting
        viewModelScope.launch {
            when (val result = accountDeletionCoordinator.delete(credential)) {
                AccountDeletionResult.Success -> {
                    _currentUser.value = null
                    _accountDeletionState.value = AccountDeletionUiState.Success
                    kotlinx.coroutines.delay(1_500)
                    if (_accountDeletionState.value is AccountDeletionUiState.Success) {
                        _accountDeletionState.value = AccountDeletionUiState.Idle
                    }
                }

                is AccountDeletionResult.Failure -> {
                    _accountDeletionState.value = AccountDeletionUiState.Error(
                        message = result.message,
                        cloudDataDeleted = result.cloudDataDeleted,
                        localDataDeleted = result.localDataDeleted
                    )
                }
            }
        }
    }

    fun loginWithEmail(email: String, orgPassword: String) {
        if (email.isBlank() || orgPassword.isBlank()) {
            _authState.value = AuthState.Error("Email and Password cannot be blank")
            return
        }
        _authState.value = AuthState.Loading
        auth.signInWithEmailAndPassword(email, orgPassword)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    _authState.value = AuthState.Success("Logged in successfully")
                } else {
                    val errorMsg = task.exception?.localizedMessage ?: "Login failed. Please try again."
                    _authState.value = AuthState.Error(errorMsg)
                }
            }
    }

    fun registerWithEmail(name: String, email: String, orgPassword: String) {
        if (name.isBlank() || email.isBlank() || orgPassword.isBlank()) {
            _authState.value = AuthState.Error("All fields are required")
            return
        }
        _authState.value = AuthState.Loading
        auth.createUserWithEmailAndPassword(email, orgPassword)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                        .setDisplayName(name)
                        .build()
                    user?.updateProfile(profileUpdates)?.addOnCompleteListener { profileTask ->
                        _authState.value = AuthState.Success("Registered successfully!")
                    } ?: run {
                        _authState.value = AuthState.Success("Registered successfully!")
                    }
                } else {
                    val errorMsg = task.exception?.localizedMessage ?: "Registration failed. Please try again."
                    _authState.value = AuthState.Error(errorMsg)
                }
            }
    }

    fun sendPasswordResetEmail(email: String) {
        if (email.isBlank()) {
            _authState.value = AuthState.Error("Please enter your email address")
            return
        }
        _authState.value = AuthState.Loading
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    _authState.value = AuthState.Success("Password reset instructions sent to $email")
                } else {
                    val errorMsg = task.exception?.localizedMessage ?: "Failed to send reset email."
                    _authState.value = AuthState.Error(errorMsg)
                }
            }
    }

    fun sendPasswordResetEmailForCurrentUser(onComplete: (Boolean, String?) -> Unit) {
        val email = auth.currentUser?.email
        if (email.isNullOrBlank()) {
            onComplete(false, "No email address is available for this account.")
            return
        }
        auth.sendPasswordResetEmail(email).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                onComplete(true, "Password reset instructions sent to $email")
            } else {
                onComplete(false, task.exception?.localizedMessage ?: "Failed to send reset email.")
            }
        }
    }

    fun loginAnonymously() {
        _authState.value = AuthState.Loading
        auth.signInAnonymously()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    _authState.value = AuthState.Success("Logged in as Guest")
                } else {
                    val errorMsg = task.exception?.localizedMessage ?: "Guest login failed."
                    _authState.value = AuthState.Error(errorMsg)
                }
            }
    }

    fun loginWithGoogleCredential(idToken: String) {
        _authState.value = AuthState.Loading
        val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    _authState.value = AuthState.Success("Logged in with Google")
                } else {
                    val errorMsg = task.exception?.localizedMessage ?: "Google Sign-In failed."
                    _authState.value = AuthState.Error(errorMsg)
                }
            }
    }

    fun updateProfileName(newDisplayName: String, onComplete: (Boolean, String?) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            onComplete(false, "No user is signed in")
            return
        }
        val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
            .setDisplayName(newDisplayName)
            .build()
        user.updateProfile(profileUpdates).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                user.reload().addOnCompleteListener { reloadTask ->
                    _currentUser.value = auth.currentUser
                    onComplete(true, "Profile name updated successfully")
                }
            } else {
                val errorMsg = task.exception?.localizedMessage ?: "Failed to update profile name"
                onComplete(false, errorMsg)
            }
        }
    }

    fun updateEmail(newEmail: String, onComplete: (Boolean, String?) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            onComplete(false, "No user is signed in")
            return
        }
        user.updateEmail(newEmail).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                user.reload().addOnCompleteListener { reloadTask ->
                    _currentUser.value = auth.currentUser
                    onComplete(true, "Email updated successfully")
                }
            } else {
                val errorMsg = task.exception?.localizedMessage ?: "Failed to update email address"
                onComplete(false, errorMsg)
            }
        }
    }

    fun updatePassword(newPassword: String, onComplete: (Boolean, String?) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            onComplete(false, "No user is signed in")
            return
        }
        user.updatePassword(newPassword).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                user.reload().addOnCompleteListener { reloadTask ->
                    _currentUser.value = auth.currentUser
                    onComplete(true, "Password updated successfully")
                }
            } else {
                val errorMsg = task.exception?.localizedMessage ?: "Failed to update password"
                onComplete(false, errorMsg)
            }
        }
    }

    fun reauthenticateAndUpdatePassword(
        currentPassword: String,
        newPassword: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val user = auth.currentUser
        val email = user?.email
        if (user == null || email.isNullOrBlank()) {
            onComplete(false, "No email/password user is signed in.")
            return
        }

        val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, currentPassword)
        user.reauthenticate(credential).addOnCompleteListener { reauthTask ->
            if (!reauthTask.isSuccessful) {
                onComplete(
                    false,
                    reauthTask.exception?.localizedMessage ?: "Current password verification failed."
                )
                return@addOnCompleteListener
            }

            user.updatePassword(newPassword).addOnCompleteListener { updateTask ->
                if (updateTask.isSuccessful) {
                    user.reload().addOnCompleteListener {
                        _currentUser.value = auth.currentUser
                        onComplete(true, "Password updated successfully")
                    }
                } else {
                    onComplete(
                        false,
                        updateTask.exception?.localizedMessage ?: "Failed to update password"
                    )
                }
            }
        }
    }

    fun logout() {
        performLogout(clearPendingGuestTransition = true)
    }

    private fun performLogout(clearPendingGuestTransition: Boolean) {
        val appContext = getApplication<Application>()
        val outgoingUid = ActiveAccountStore.getActiveUid(appContext).ifBlank {
            auth.currentUser?.uid ?: ""
        }

        if (outgoingUid.isNotBlank()) {
            com.example.audio.ReminderScheduler.cancelAllRemindersForUser(appContext, outgoingUid)
            com.example.audio.AlarmService.stopService(appContext, outgoingUid)
            com.example.audio.ReminderScheduler.clearNotificationsForUser(appContext, outgoingUid)
        }

        com.example.audio.ReminderScheduler.activeRingingLead.value = null
        com.example.audio.ReminderScheduler.stopRingingSound()
        ActiveAccountStore.clearActiveUid(appContext)

        if (clearPendingGuestTransition) {
            clearPendingGuestTransition()
        }

        auth.signOut()
        _currentUser.value = null
        _authState.value = AuthState.Idle
    }

    fun requestAccountDeletionGracePeriod(onComplete: (Boolean, String, Long?) -> Unit) {
        val user = auth.currentUser
        if (user == null || user.isAnonymous || user.uid.isBlank()) {
            onComplete(false, "An authenticated account is required to request deletion.", null)
            return
        }
        val uid = user.uid
        val now = System.currentTimeMillis()
        val scheduledAt = now + (7L * 24 * 60 * 60 * 1000L) // 7 days in milliseconds

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = FirebaseFirestore.getInstance()
                val data = mapOf(
                    "deletionRequestedAt" to now,
                    "deletionScheduledAt" to scheduledAt,
                    "deletionStatus" to "PENDING_DELETION"
                )
                Tasks.await(db.collection("users").document(uid).set(data, SetOptions.merge()))
                Tasks.await(db.waitForPendingWrites())

                deletionPrefs.edit()
                    .putBoolean("deletion_pending_$uid", true)
                    .putLong("deletion_scheduled_$uid", scheduledAt)
                    .apply()

                withContext(Dispatchers.Main) {
                    performLogout(clearPendingGuestTransition = true)
                    onComplete(true, "Account deletion request submitted. 7-day grace period started.", scheduledAt)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onComplete(false, "Failed to submit deletion request: ${e.localizedMessage}", null)
                }
            }
        }
    }

    fun checkAndHandlePendingDeletion(user: FirebaseUser) {
        if (user.isAnonymous || user.uid.isBlank()) return
        val uid = user.uid
        val db = FirebaseFirestore.getInstance()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val doc = Tasks.await(db.collection("users").document(uid).get(Source.SERVER))
                if (doc.exists()) {
                    val status = doc.getString("deletionStatus")
                    val scheduledAt = doc.getLong("deletionScheduledAt") ?: 0L
                    if (status == "PENDING_DELETION" && scheduledAt > 0L) {
                        val now = System.currentTimeMillis()
                        if (now < scheduledAt) {
                            // Within 7 days -> Cancel deletion request and restore active status
                            val updates = mapOf(
                                "deletionRequestedAt" to FieldValue.delete(),
                                "deletionScheduledAt" to FieldValue.delete(),
                                "deletionStatus" to "ACTIVE"
                            )
                            Tasks.await(db.collection("users").document(uid).update(updates))
                            deletionPrefs.edit()
                                .remove("deletion_pending_$uid")
                                .remove("deletion_scheduled_$uid")
                                .apply()

                            withContext(Dispatchers.Main) {
                                _deletionNotice.value = "Welcome back! Your pending account deletion request has been cancelled, and your account and data remain intact."
                            }
                        } else {
                            // 7 days expired -> Permanently delete old cloud data & wipe old local state
                            val leadsCollection = db.collection("users").document(uid).collection("leads")
                            while (true) {
                                val snap = Tasks.await(leadsCollection.limit(400).get(Source.SERVER))
                                if (snap.isEmpty) break
                                val batch = db.batch()
                                snap.documents.forEach { batch.delete(it.reference) }
                                Tasks.await(batch.commit())
                                Tasks.await(db.waitForPendingWrites())
                            }
                            Tasks.await(db.collection("users").document(uid).delete())
                            RoomAccountDeletionLocalStore(getApplication()).clearUserData(uid)
                            deletionPrefs.edit()
                                .remove("deletion_pending_$uid")
                                .remove("deletion_scheduled_$uid")
                                .apply()

                            withContext(Dispatchers.Main) {
                                _deletionNotice.value = "The 7-day grace period expired. Previous cloud account data has been permanently cleared, and your session is initialized as a fresh account."
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Graceful fallback if offline
            }
        }
    }
}
