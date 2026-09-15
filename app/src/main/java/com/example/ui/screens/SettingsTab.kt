package com.example.ui.screens

import java.util.Locale

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.example.R
import com.example.ui.viewmodel.CRMViewModel
import com.example.ui.viewmodel.AuthViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import android.os.Build
import android.os.PowerManager
import android.content.ContentValues
import android.provider.MediaStore
import android.os.Environment
import java.text.SimpleDateFormat
import java.util.Date

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke
import com.example.ui.theme.LifeFreshGreen
import com.example.ui.theme.SoftLeafGreen
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.sync.SyncPreferences
import com.example.sync.SyncScheduler
import com.example.sync.SyncRuntimeFactory
import com.example.sync.model.SyncTrigger
import com.example.sync.model.SyncRunResult
import com.example.sync.SyncState
import com.example.data.database.AppDatabase
import androidx.work.WorkManager
import androidx.compose.ui.text.style.TextOverflow
import com.example.ui.viewmodel.AccountDeletionUiState
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.example.data.AppLanguage
import com.example.data.AppStrings
import com.example.data.AppLanguageManager
import com.example.data.LanguagePackMetadata
import com.example.data.LanguagePackStatus
import com.example.data.PlayLanguageInstallState
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.CheckCircle
import com.example.BuildConfig
import com.example.data.security.AIQuotaManager
import com.example.ui.screens.settings.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(
    viewModel: CRMViewModel,
    authViewModel: com.example.ui.viewmodel.AuthViewModel,
    onOpenPolicy: (String) -> Unit,
    onOpenAIDesignPreview: () -> Unit = {},
    onOpenAISettings: () -> Unit = {},
    onRequestAuthentication: () -> Unit = {},
    activeSubScreenParam: String? = null,
    onActiveSubScreenChange: (String?) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val darkModeEnabled by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val activeLanguageMeta by viewModel.activeLanguageMetadata.collectAsStateWithLifecycle()
    val languagePacks by viewModel.languagePacks.collectAsStateWithLifecycle()
    val playInstallState by viewModel.playLanguageInstallState.collectAsStateWithLifecycle()
    var showLanguageDialog by remember { mutableStateOf(false) }
    // Backup actions dialog states
    var showResetConfirmAlert by remember { mutableStateOf(false) }
    var showBackupHelpDialog by remember { mutableStateOf(false) }

    // Premium Backup & Restore State Properties
    var progressStatus by remember { mutableStateOf<String?>(null) }
    var backupProgressStatus by remember { mutableStateOf<String?>(null) }
    var restoreProgressStatus by remember { mutableStateOf<String?>(null) }
    var localExportProgressStatus by remember { mutableStateOf<String?>(null) }
    var localImportProgressStatus by remember { mutableStateOf<String?>(null) }
    val activeOperationProgress = backupProgressStatus ?: restoreProgressStatus ?: localExportProgressStatus ?: localImportProgressStatus ?: progressStatus

    var successDialogMsg by remember { mutableStateOf<String?>(null) }
    var successDialogTimestamp by remember { mutableStateOf<String?>(null) }
    var errorDialogMsg by remember { mutableStateOf<String?>(null) }
    var errorDialogGuidance by remember { mutableStateOf<String?>(null) }

    var showCloudBackupConfirm by remember { mutableStateOf(false) }
    var showDeleteCloudBackupConfirm by remember { mutableStateOf(false) }
    var showCloudRestoreConfirm by remember { mutableStateOf(false) }
    var showLocalExportConfirm by remember { mutableStateOf(false) }
    var showLocalImportConfirm by remember { mutableStateOf(false) }
    var showBackupSuccessCard by remember { mutableStateOf(false) }

    LaunchedEffect(showBackupSuccessCard) {
        if (showBackupSuccessCard) {
            kotlinx.coroutines.delay(3500)
            showBackupSuccessCard = false
        }
    }

    val syncPreferences = remember { SyncPreferences(context) }
    var autoSyncEnabled by remember { mutableStateOf(syncPreferences.isAutomaticSyncEnabled()) }
    var showSyncDetailsDialog by remember { mutableStateOf(false) }
    var isSyncingNow by remember { mutableStateOf(false) }
    val isOperationRunning = activeOperationProgress != null || isSyncingNow
    val pendingUploadsCount = remember { mutableStateOf(0) }
    val unresolvedConflictsCount = remember { mutableStateOf(0) }
    var lastSyncError by remember { mutableStateOf(syncPreferences.getLastSyncError()) }
    
    fun isNetworkAvailable(ctx: Context): Boolean {
        val connectivityManager = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val network = connectivityManager?.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    
    var isNetworkOnline by remember { mutableStateOf(isNetworkAvailable(context)) }
    LaunchedEffect(Unit) {
        while(true) {
            isNetworkOnline = isNetworkAvailable(context)
            lastSyncError = syncPreferences.getLastSyncError()
            try {
                val db = AppDatabase.getDatabase(context)
                val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
                pendingUploadsCount.value = db.syncDao.getPendingCount(currentUid)
                unresolvedConflictsCount.value = db.syncDao.countUnresolvedConflicts(currentUid)
            } catch(e: Exception) {
                // Ignore
            }
            kotlinx.coroutines.delay(2000)
        }
    }

    // JSON file backup launcher
    val backupFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                localImportProgressStatus = "Preparing..."
                kotlinx.coroutines.delay(600)
                localImportProgressStatus = "Restoring..."
                kotlinx.coroutines.delay(1000)
                try {
                    val inputStr = context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader().use { it.readText() }
                    } ?: ""
                    
                    val summary = viewModel.importBackupJson(inputStr)
                    localImportProgressStatus = "Finalizing..."
                    kotlinx.coroutines.delay(500)
                    localImportProgressStatus = null
                    
                    if (summary.contains("Error") || summary.contains("Failed")) {
                        errorDialogMsg = "Local Import Failed"
                        errorDialogGuidance = "We were unable to parse the backup JSON payload. Please verify that this is a valid LifeFresh backup file."
                    } else {
                        successDialogMsg = "Local Backup Restored"
                        val countLabel = summary.replace("Import Complete!", "").trim()
                        successDialogTimestamp = "$countLabel\nTime: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                    }
                } catch (e: Exception) {
                    localImportProgressStatus = null
                    errorDialogMsg = "Import Error"
                    errorDialogGuidance = "Failed to read chosen backup file. Please verify file access permissions and try again."
                }
            }
        }
    }


    var activeSubScreen by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(activeSubScreen) {
        onActiveSubScreenChange(activeSubScreen)
    }

    LaunchedEffect(activeSubScreenParam) {
        if (activeSubScreen != activeSubScreenParam) {
            activeSubScreen = activeSubScreenParam
        }
    }

    BackHandler(enabled = activeSubScreen != null) {
        activeSubScreen = when (activeSubScreen) {
            "whats_new", "privacy", "terms", "support" -> "about"
            else -> null
        }
    }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var editDisplayName by remember { mutableStateOf("") }
    var editEmailAddress by remember { mutableStateOf("") }
    var isSavingProfile by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var currentProfilePassword by rememberSaveable { mutableStateOf("") }
    var currentProfilePasswordVisible by remember { mutableStateOf(false) }
    var newProfilePassword by rememberSaveable { mutableStateOf("") }
    var newProfilePasswordVisible by remember { mutableStateOf(false) }
    var confirmProfilePassword by rememberSaveable { mutableStateOf("") }
    var confirmProfilePasswordVisible by remember { mutableStateOf(false) }
    var isChangingPassword by remember { mutableStateOf(false) }
    var isSendingPasswordReset by remember { mutableStateOf(false) }
    var showRequestDeletionConfirmDialog by remember { mutableStateOf(false) }
    var infoDialogTitle by remember { mutableStateOf<String?>(null) }
    var infoDialogMessage by remember { mutableStateOf<String?>(null) }
    val deletionNotice by authViewModel.deletionNotice.collectAsStateWithLifecycle()
    LaunchedEffect(deletionNotice) {
        val notice = deletionNotice
        if (!notice.isNullOrBlank()) {
            infoDialogTitle = "Account Update"
            infoDialogMessage = notice
            authViewModel.clearDeletionNotice()
        }
    }
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var deleteAccountPassword by rememberSaveable { mutableStateOf("") }
    var deleteAccountPasswordVisible by remember { mutableStateOf(false) }
    var deleteAccountLocalError by remember { mutableStateOf<String?>(null) }

    val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()
    val accountDeletionState by authViewModel.accountDeletionState.collectAsStateWithLifecycle()
    val isGoogle = currentUser?.providerData?.any { it.providerId == "google.com" } == true
    val usesPasswordProvider = currentUser?.providerData?.any { it.providerId == "password" } == true
    val isAnonymous = currentUser?.isAnonymous == true
    val isGuest = currentUser == null || isAnonymous

    val accountDeletionGoogleOptions = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
    }
    val accountDeletionGoogleClient = remember {
        GoogleSignIn.getClient(context, accountDeletionGoogleOptions)
    }
    val accountDeletionGoogleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)
                val token = account?.idToken
                if (token.isNullOrBlank()) {
                    deleteAccountLocalError = "Google identity verification did not return a valid token."
                } else {
                    deleteAccountLocalError = null
                    authViewModel.deleteAccountWithGoogleCredential(token)
                }
            } catch (e: Exception) {
                deleteAccountLocalError = e.localizedMessage ?: "Google identity verification failed."
            }
        } else {
            deleteAccountLocalError = "Google identity verification was cancelled."
        }
    }

    val accountTypeStr = when {
        isAnonymous -> "Guest Account"
        isGoogle -> "Google Account"
        else -> "Email Account"
    }
    val loginMethodStr = when {
        isAnonymous -> "Anonymous Authentication"
        isGoogle -> "Google Sign-In Provider"
        else -> "Secure Email & Password"
    }
    val displayNameStr = when {
        isAnonymous -> "Guest User"
        !currentUser?.displayName.isNullOrBlank() -> currentUser?.displayName ?: "User"
        !currentUser?.email.isNullOrBlank() -> currentUser?.email ?: "User"
        else -> "Pro Member"
    }
    val emailStr = currentUser?.email ?: "No Email Associated"

    LaunchedEffect(showEditProfileDialog) {
        if (showEditProfileDialog) {
            editDisplayName = displayNameStr
            editEmailAddress = currentUser?.email ?: ""
        }
    }

    LaunchedEffect(accountDeletionState) {
        if (accountDeletionState is AccountDeletionUiState.Success) {
            showDeleteAccountDialog = false
            deleteAccountPassword = ""
            deleteAccountLocalError = null
            viewModel.clearInMemoryStateOnSignOut()
            viewModel.resetCloudRestoreCheck()
            accountDeletionGoogleClient.signOut()
            Toast.makeText(context, "Account and associated data deleted successfully.", Toast.LENGTH_LONG).show()
            authViewModel.clearAccountDeletionState()
        }
    }

    val creationTimestamp = currentUser?.metadata?.creationTimestamp ?: 0L
    val creationDateStr = if (creationTimestamp != 0L) {
        val sdf = java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.getDefault())
        sdf.format(java.util.Date(creationTimestamp))
    } else {
        "Not available"
    }

    val topScrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (activeSubScreen == null) {
                    Modifier.verticalScroll(topScrollState)
                } else {
                    Modifier
                }
            )
            .padding(16.dp)
            .testTag("settings_tab"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (activeSubScreen) {
            null -> {
                // Header Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.settings_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Settings Menu Items
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // My Profile
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SettingsMenuItem(
                            icon = Icons.Default.Person,
                            title = stringResource(R.string.settings_my_profile),
                            subtitle = stringResource(R.string.settings_my_profile_sub),
                            onClick = { activeSubScreen = "profile" },
                            testTag = "menu_my_profile"
                        )
                    }

                    // Dark Theme
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SettingsSwitchRow(
                            icon = if (darkModeEnabled) Icons.Default.DarkMode else Icons.Default.LightMode,
                            title = stringResource(R.string.settings_dark_theme),
                            subtitle = stringResource(R.string.settings_dark_theme_sub),
                            checked = darkModeEnabled,
                            onCheckedChange = { viewModel.toggleTheme(it) },
                            testTag = "dark_mode_switch_row"
                        )
                    }

                    // App Language
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SettingsMenuItem(
                            icon = Icons.Default.Language,
                            title = stringResource(R.string.settings_app_language),
                            subtitle = "${appLanguage.nativeName} (${appLanguage.displayName})",
                            onClick = { showLanguageDialog = true },
                            testTag = "menu_app_language"
                        )
                    }

                    // Alarm Settings
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SettingsMenuItem(
                            icon = Icons.Default.Alarm,
                            title = stringResource(R.string.settings_alarm_settings),
                            subtitle = stringResource(R.string.settings_alarm_settings_sub),
                            onClick = { activeSubScreen = "alarm" },
                            testTag = "menu_alarm_settings"
                        )
                    }

                    // Cloud Backup
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SettingsMenuItem(
                            icon = Icons.Default.Cloud,
                            title = stringResource(R.string.settings_cloud_backup),
                            subtitle = stringResource(R.string.settings_cloud_backup_sub),
                            onClick = { activeSubScreen = "cloud" },
                            testTag = "menu_cloud_backup"
                        )
                    }

                    // AI Assistant Configuration (BYOK & Quota Indicator)
                    AIAssistantConfigCard()

                    // Account & Data
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SettingsMenuItem(
                            icon = Icons.Default.ManageAccounts,
                            title = stringResource(R.string.settings_account_data),
                            subtitle = stringResource(R.string.settings_account_data_sub),
                            onClick = { activeSubScreen = "account_data" },
                            testTag = "menu_account_data"
                        )
                    }

                    // About & Support
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SettingsMenuItem(
                            icon = Icons.Default.Info,
                            title = stringResource(R.string.settings_about_support),
                            subtitle = stringResource(R.string.settings_about_support_sub),
                            onClick = { activeSubScreen = "about" },
                            testTag = "menu_about_support"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Logout / Exit Guest Mode Button
                OutlinedButton(
                    onClick = {
                        showLogoutConfirmDialog = true
                    },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("btn_logout")
                ) {
                    Icon(
                        imageVector = if (isGuest) Icons.Default.ExitToApp else Icons.Default.Logout,
                        contentDescription = if (isGuest) "Exit Guest Mode Icon" else "Sign Out Icon",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isGuest) stringResource(R.string.settings_exit_guest) else stringResource(R.string.settings_sign_out),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            "profile" -> {
                SettingsProfileScreen(
                    currentUser = currentUser,
                    isAnonymous = isAnonymous,
                    isGoogle = isGoogle,
                    usesPasswordProvider = usesPasswordProvider,
                    displayNameStr = displayNameStr,
                    emailStr = emailStr,
                    loginMethodStr = loginMethodStr,
                    darkModeEnabled = darkModeEnabled,
                    onBack = { activeSubScreen = null },
                    onEditProfileClick = { showEditProfileDialog = true },
                    onChangePasswordClick = {
                        currentProfilePassword = ""
                        newProfilePassword = ""
                        confirmProfilePassword = ""
                        showChangePasswordDialog = true
                    }
                )
            }

            "alarm" -> {
                SettingsAlarmScreen(
                    viewModel = viewModel,
                    onBack = { activeSubScreen = null }
                )
            }

            "cloud", "backup_tools", "backup_restore" -> {
                // Unified Backup & Restore Screen
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    IconButton(
                        onClick = { activeSubScreen = null },
                        modifier = Modifier.testTag("btn_back_to_settings")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back to Settings",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Backup & Restore",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                val lastSyncTimeVal by viewModel.lastSyncTime.collectAsStateWithLifecycle()
                val totalCloudCustomersVal by viewModel.totalCloudCustomers.collectAsStateWithLifecycle()
                val isChecking = remember { mutableStateOf(false) }
                val allLeadsList by viewModel.allLeadsList.collectAsStateWithLifecycle()
                val isGuest = currentUser == null || isAnonymous

                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // --- SECTION 1: CLOUD PROTECTION & SYNC ---
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cloud,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CLOUD PROTECTION & SYNC",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            if (isChecking.value) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        if (isGuest) {
                            // Guest Cloud Card (Features Locked)
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth().testTag("guest_cloud_locked_card")
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Locked",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "Cloud Features Locked",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            text = "Sign in with Google or Email to securely protect and sync your workspace.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.align(Alignment.Start)
                                    ) {
                                        listOf(
                                            "Automatic Cloud Sync",
                                            "Cloud Backup",
                                            "Cloud Restore",
                                            "Multi-device Sync",
                                            "Secure Recovery"
                                        ).forEach { benefit ->
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Included",
                                                    tint = LifeFreshGreen,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = benefit,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }

                                    Button(
                                        onClick = onRequestAuthentication,
                                        shape = RoundedCornerShape(24.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = LifeFreshGreen),
                                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_sign_in_continue")
                                    ) {
                                        Text("Sign In to Continue", fontWeight = FontWeight.Bold, color = Color.White)
                                    }

                                    Text(
                                        text = "Local backup tools are still available below.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            // Signed-In Cloud Experience
                            val lastSuccessTime = syncPreferences.getLastSuccessfulSyncAt()
                            val lastBackupTimeStr = if (lastSuccessTime > 0L) {
                                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                                sdf.format(java.util.Date(lastSuccessTime))
                            } else "Never Synced"

                            val automaticSyncStatusText = when {
                                !autoSyncEnabled -> "Automatic sync is off"
                                !isNetworkOnline && pendingUploadsCount.value > 0 -> {
                                    val count = pendingUploadsCount.value
                                    "$count ${if (count == 1) "change" else "changes"} waiting for internet"
                                }
                                !isNetworkOnline -> "Waiting for internet"
                                pendingUploadsCount.value > 0 -> {
                                    val count = pendingUploadsCount.value
                                    "$count ${if (count == 1) "change" else "changes"} waiting to sync"
                                }
                                lastSyncError != null -> "Sync needs attention"
                                else -> "Up to date"
                            }
                            val automaticSyncStatusColor = when {
                                !autoSyncEnabled -> MaterialTheme.colorScheme.onSurfaceVariant
                                !isNetworkOnline || lastSyncError != null -> MaterialTheme.colorScheme.error
                                pendingUploadsCount.value > 0 -> Color(0xFFFB8C00)
                                else -> LifeFreshGreen
                            }

                            // SECTION 4: BACKUP SUCCESS FEEDBACK OVERLAY (within hierarchy)
                            if (showBackupSuccessCard) {
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = LifeFreshGreen.copy(alpha = 0.15f)),
                                    border = BorderStroke(1.dp, LifeFreshGreen),
                                    modifier = Modifier.fillMaxWidth().testTag("backup_success_card")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = LifeFreshGreen,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Backup Complete",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = LifeFreshGreen
                                            )
                                            Text(
                                                text = "${allLeadsList.size} Leads secured successfully.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        IconButton(
                                            onClick = { showBackupSuccessCard = false },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Dismiss",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Cloud Protection Hero Card (Section 1)
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth().testTag("cloud_protection_hero_card")
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Cloud Protection",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = LifeFreshGreen.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "Connected",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = LifeFreshGreen,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).testTag("cloud_protection_status_badge")
                                            )
                                        }
                                    }

                                    Text(
                                        text = "Your CRM workspace is securely connected to the cloud vault.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Account",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = currentUser?.email ?: "None",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                }
                            }

                            // Automatic Cloud Sync (Section 2)
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth().testTag("automatic_sync_card")
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CloudQueue,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Automatic Cloud Sync",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.weight(1f))
                                        Switch(
                                            checked = autoSyncEnabled,
                                            onCheckedChange = { checked ->
                                                if (isGuest) {
                                                    errorDialogMsg = "Authentication Required"
                                                    errorDialogGuidance = "Guests cannot execute cloud actions. Please sign in to continue."
                                                    return@Switch
                                                }
                                                autoSyncEnabled = checked
                                                SyncScheduler.setEnabled(context, checked)
                                                if (checked) {
                                                    SyncScheduler.enqueueMutationSync(context)
                                                } else {
                                                    WorkManager.getInstance(context).cancelUniqueWork(com.example.sync.AutoSyncWorker.WORK_NAME_MUTATION)
                                                }
                                            },
                                            modifier = Modifier.testTag("automatic_sync_toggle")
                                        )
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                                    Text(
                                        text = if (autoSyncEnabled) "Your changes are protected automatically." else "Automatic sync is off. Manual Backup Now remains available.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            "Sync status",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            automaticSyncStatusText,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                            color = automaticSyncStatusColor,
                                            modifier = Modifier.testTag("automatic_sync_status_text")
                                        )
                                    }
                                }
                            }

                            // Cloud Backup (Section 3)
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth().testTag("cloud_backup_card")
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CloudUpload,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Cloud Backup",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                                    if (backupProgressStatus != null) {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "Uploading Backup...",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )

                                            LinearProgressIndicator(
                                                modifier = Modifier.fillMaxWidth(),
                                                color = MaterialTheme.colorScheme.primary,
                                                trackColor = MaterialTheme.colorScheme.primaryContainer
                                            )

                                            val stageText = when (backupProgressStatus) {
                                                "Preparing..." -> "Stage: Preparing data..."
                                                "Uploading..." -> "Stage: Uploading records..."
                                                "Deleting..." -> "Stage: Deleting cloud backup..."
                                                "Finalizing..." -> "Stage: Finalizing..."
                                                else -> "Stage: Processing..."
                                            }

                                            Text(
                                                text = stageText,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )

                                            Text(
                                                text = "Please keep the app open while the backup is being prepared.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("Last backup", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(lastBackupTimeStr, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface)
                                            }

                                            Row(
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("Database size", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("${allLeadsList.size} Leads included", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))

                                            Button(
                                                onClick = {
                                                    if (isGuest) {
                                                        errorDialogMsg = "Authentication Required"
                                                        errorDialogGuidance = "Guests cannot execute cloud actions. Please sign in to continue."
                                                        return@Button
                                                    }
                                                    showCloudBackupConfirm = true
                                                },
                                                shape = RoundedCornerShape(20.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = LifeFreshGreen),
                                                enabled = !isOperationRunning,
                                                modifier = Modifier.fillMaxWidth().height(40.dp).testTag("btn_backup_now_card")
                                            ) {
                                                Text("Backup Now", fontWeight = FontWeight.Bold, color = Color.White)
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    if (isGuest) {
                                                        errorDialogMsg = "Authentication Required"
                                                        errorDialogGuidance = "Guests cannot execute cloud actions. Please sign in to continue."
                                                        return@OutlinedButton
                                                    }
                                                    showDeleteCloudBackupConfirm = true
                                                },
                                                shape = RoundedCornerShape(20.dp),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = MaterialTheme.colorScheme.error
                                                ),
                                                enabled = !isOperationRunning,
                                                modifier = Modifier.fillMaxWidth().height(40.dp).testTag("btn_delete_cloud_backup")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Delete Cloud Backup", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }

                            // Cloud Restore (Section 5)
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth().testTag("cloud_restore_card")
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CloudDownload,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Cloud Restore",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                                    if (restoreProgressStatus != null) {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "Restoring from Cloud...",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )

                                            LinearProgressIndicator(
                                                modifier = Modifier.fillMaxWidth(),
                                                color = MaterialTheme.colorScheme.primary,
                                                trackColor = MaterialTheme.colorScheme.primaryContainer
                                            )

                                            val stageText = when (restoreProgressStatus) {
                                                "Checking cloud backup..." -> "Stage: Checking cloud backup..."
                                                "Preparing..." -> "Stage: Preparing restore..."
                                                "Restoring..." -> "Stage: Restoring records..."
                                                "Finalizing..." -> "Stage: Finalizing database..."
                                                else -> "Stage: Processing..."
                                            }

                                            Text(
                                                text = stageText,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )

                                            Text(
                                                text = "Please keep the app open while records are being restored.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("Latest cloud backup", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(lastSyncTimeVal ?: "Never Synced", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface)
                                            }

                                            Row(
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("Cloud records", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                val countText = totalCloudCustomersVal?.let { "$it Leads" } ?: "Not available"
                                                Text(countText, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))

                                            Button(
                                                onClick = {
                                                    if (isGuest) {
                                                        errorDialogMsg = "Authentication Required"
                                                        errorDialogGuidance = "Guests cannot execute cloud actions. Please sign in to continue."
                                                        return@Button
                                                    }
                                                    restoreProgressStatus = "Checking cloud backup..."
                                                    viewModel.queryCloudBackupStatus { success, message, count ->
                                                        restoreProgressStatus = null
                                                        if (!success) {
                                                            errorDialogMsg = "Cloud Check Failed"
                                                            errorDialogGuidance = message
                                                        } else if ((count ?: 0) <= 0) {
                                                            infoDialogTitle = "No Cloud Backup Available"
                                                            infoDialogMessage = "You haven't backed up any leads yet. Create a cloud backup first, then you can restore it here."
                                                        } else {
                                                            showCloudRestoreConfirm = true
                                                        }
                                                    }
                                                },
                                                shape = RoundedCornerShape(20.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = LifeFreshGreen),
                                                enabled = !isOperationRunning,
                                                modifier = Modifier.fillMaxWidth().height(40.dp).testTag("btn_restore_from_cloud")
                                            ) {
                                                Text("Restore", fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }

                            // View Sync Details action
                            OutlinedButton(
                                onClick = {
                                    if (isGuest) {
                                        errorDialogMsg = "Authentication Required"
                                        errorDialogGuidance = "Guests cannot view sync details. Please sign in to continue."
                                        return@OutlinedButton
                                    }
                                    showSyncDetailsDialog = true
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth().testTag("view_details_button")
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text("View Sync Details", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // --- SECTION 2: LOCAL BACKUP & FILE TOOLS ---
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "LOCAL BACKUP & FILES",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            TextButton(
                                onClick = { showBackupHelpDialog = true },
                                modifier = Modifier.testTag("btn_backup_help_guide"),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text("Guide", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        // Local Action 1: Export JSON
                        PremiumActionCard(
                            title = "Export JSON Backup",
                            subtitle = "Save a secure backup file into phone Downloads",
                            icon = Icons.Default.FileUpload,
                            testTag = "btn_export_backup",
                            onClick = {
                                if (allLeadsList.isEmpty()) {
                                    infoDialogTitle = "Nothing to Back Up"
                                    infoDialogMessage = "You don't have any client data yet. Add at least one client before creating a local backup."
                                } else {
                                    showLocalExportConfirm = true
                                }
                            }
                        )

                        // Local Action 2: Import JSON
                        PremiumActionCard(
                            title = "Import JSON Backup",
                            subtitle = "Merge and restore clients from a chosen JSON file",
                            icon = Icons.Default.FileDownload,
                            testTag = "btn_import_backup",
                            onClick = { showLocalImportConfirm = true }
                        )

                        // Local Action 3: Share Backup
                        PremiumActionCard(
                            title = "Share Backup File",
                            subtitle = "Directly share database file via WhatsApp, Email, or Drive",
                            icon = Icons.Default.Share,
                            testTag = "btn_share_backup",
                            onClick = {
                                val data = viewModel.exportBackupJson()
                                if (data == "[]") {
                                    infoDialogTitle = "Nothing to Share"
                                    infoDialogMessage = "There is currently no local backup data available to share. Add at least one client first."
                                    return@PremiumActionCard
                                }
                                try {
                                    val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
                                    val displayName = "LifeFresh_Backup_$timestamp.json"
                                    
                                    val tempFile = File(context.cacheDir, displayName)
                                    FileOutputStream(tempFile).use { fos ->
                                        fos.write(data.toByteArray())
                                    }

                                    val authority = "${context.packageName}.fileprovider"
                                    val fileUri = FileProvider.getUriForFile(context, authority, tempFile)

                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/json"
                                        putExtra(Intent.EXTRA_STREAM, fileUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Backup"))
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    errorDialogMsg = "Share Error"
                                    errorDialogGuidance = "An error occurred while building the share intent: ${e.localizedMessage}"
                                }
                            }
                        )
                    }

                    // --- SECTION 3: DANGER ZONE ---
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "DANGER ZONE",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            ),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.08f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteForever,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Delete Local Data",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                val dangerText = if (isGuest) {
                                    "This action will permanently erase your local CRM database and delete all pending reminders. This cannot be undone."
                                } else {
                                    "This action will permanently erase your local CRM database and delete all pending reminders. This cannot be undone. Your cloud backup will not be deleted."
                                }
                                Text(
                                    text = dangerText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = { showResetConfirmAlert = true },
                                    shape = RoundedCornerShape(24.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_reset_all_data")
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null, tint = Color.White)
                                        Text("Delete Local Data", fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "account_data" -> {
                SettingsAccountDataScreen(
                    isGuest = isGuest,
                    displayNameStr = displayNameStr,
                    emailStr = emailStr,
                    onBack = { activeSubScreen = null },
                    onRequestAccountDeletion = { showRequestDeletionConfirmDialog = true },
                    onGuestNotice = {
                        errorDialogMsg = "Guest Mode"
                        errorDialogGuidance = "Guest profiles do not have cloud account data. To clear local guest records from this device, use 'Delete Local Data' under Cloud Backup."
                    }
                )
            }

            "about" -> {
                SettingsAboutScreen(
                    onBack = { activeSubScreen = null },
                    onNavigate = { activeSubScreen = it }
                )
            }

            "whats_new" -> {
                SettingsWhatsNewScreen(
                    onBack = { activeSubScreen = "about" }
                )
            }

            "privacy" -> {
                SettingsPrivacyScreen(
                    onBack = { activeSubScreen = "about" }
                )
            }

            "terms" -> {
                SettingsTermsScreen(
                    onBack = { activeSubScreen = "about" }
                )
            }

            "support" -> {
                SettingsSupportScreen(
                    onBack = { activeSubScreen = "about" }
                )
            }
        }
    }

    // Sign Out / Exit Guest Mode confirmation dialog
    if (showLogoutConfirmDialog) {
        Dialog(onDismissRequest = { showLogoutConfirmDialog = false }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isGuest) Icons.Default.ExitToApp else Icons.Default.Logout,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Text(
                        text = if (isGuest) "Exit Guest Mode?" else "Sign out?",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = if (isGuest) {
                            "You will return to the welcome screen. Your local data will remain saved on this device."
                        } else {
                            "Choose how you want to sign out."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // PRIMARY BUTTON: Sign Out
                    Button(
                        onClick = {
                            showLogoutConfirmDialog = false
                            authViewModel.logout()
                            viewModel.clearInMemoryStateOnSignOut()
                            viewModel.resetCloudRestoreCheck()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("dialog_logout_confirm_btn")
                    ) {
                        Text(
                            text = if (isGuest) "Exit Guest Mode" else "Sign Out",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // SECONDARY BUTTON: Forget This Account
                    if (isGoogle && !isGuest) {
                        OutlinedButton(
                            onClick = {
                                showLogoutConfirmDialog = false
                                accountDeletionGoogleClient.signOut().addOnCompleteListener { task ->
                                    authViewModel.logout()
                                    viewModel.clearInMemoryStateOnSignOut()
                                    viewModel.resetCloudRestoreCheck()
                                    if (!task.isSuccessful) {
                                        Toast.makeText(
                                            context,
                                            "Signed out, but the saved Google account could not be cleared.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.25.dp, MaterialTheme.colorScheme.error),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("dialog_logout_forget_account_btn")
                        ) {
                            Text(
                                text = "Forget This Account",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Text(
                            text = "Next Google sign-in will show account chooser.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                        )
                    }

                    // CANCEL BUTTON
                    TextButton(
                        onClick = { showLogoutConfirmDialog = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("dialog_logout_cancel_btn")
                    ) {
                        Text(
                            text = "Cancel",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showDeleteAccountDialog) {
        val isDeletingAccount = accountDeletionState is AccountDeletionUiState.Deleting
        val deletionFailure = accountDeletionState as? AccountDeletionUiState.Error
        AlertDialog(
            onDismissRequest = {
                if (!isDeletingAccount) {
                    showDeleteAccountDialog = false
                    deleteAccountPassword = ""
                    deleteAccountLocalError = null
                    authViewModel.clearAccountDeletionState()
                }
            },
            shape = RoundedCornerShape(24.dp),
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(30.dp)
                )
            },
            title = {
                Text(
                    text = "Permanently delete account?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "This permanently removes your cloud CRM records, local CRM database, AI chats, sync history, reminders and Firebase sign-in account. Exported files already saved or shared outside the app cannot be removed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (usesPasswordProvider) {
                        OutlinedTextField(
                            value = deleteAccountPassword,
                            onValueChange = {
                                deleteAccountPassword = it
                                deleteAccountLocalError = null
                            },
                            label = { Text("Current password") },
                            singleLine = true,
                            enabled = !isDeletingAccount,
                            visualTransformation = if (deleteAccountPasswordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                IconButton(onClick = { deleteAccountPasswordVisible = !deleteAccountPasswordVisible }) {
                                    Icon(
                                        imageVector = if (deleteAccountPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (deleteAccountPasswordVisible) "Hide password" else "Show password"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("delete_account_password_input")
                        )
                    } else if (isGoogle) {
                        Text(
                            text = "Google will ask you to verify the same account before deletion begins.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isDeletingAccount) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Deleting account and associated data…", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    val errorText = deleteAccountLocalError ?: deletionFailure?.message
                    if (!errorText.isNullOrBlank()) {
                        Text(
                            text = errorText,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("delete_account_error")
                        )
                    }

                    if (deletionFailure?.cloudDataDeleted == true) {
                        Text(
                            text = if (deletionFailure.localDataDeleted) {
                                "Cloud and local records were removed, but authentication cleanup needs another attempt."
                            } else {
                                "Cloud records were removed, but local/account cleanup needs another attempt."
                            },
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when {
                            usesPasswordProvider && deleteAccountPassword.isBlank() -> {
                                deleteAccountLocalError = "Enter your current password."
                            }
                            usesPasswordProvider -> {
                                deleteAccountLocalError = null
                                authViewModel.deleteAccountWithPassword(deleteAccountPassword)
                                deleteAccountPassword = ""
                            }
                            isGoogle -> {
                                deleteAccountLocalError = null
                                accountDeletionGoogleClient.signOut().addOnCompleteListener {
                                    accountDeletionGoogleLauncher.launch(accountDeletionGoogleClient.signInIntent)
                                }
                            }
                            else -> {
                                deleteAccountLocalError = "This sign-in provider is not supported for in-app deletion."
                            }
                        }
                    },
                    enabled = !isDeletingAccount,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("dialog_delete_account_confirm_btn")
                ) {
                    Text(if (isGoogle && !usesPasswordProvider) "Verify & Delete" else "Delete Permanently", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showDeleteAccountDialog = false
                        deleteAccountPassword = ""
                        deleteAccountLocalError = null
                        authViewModel.clearAccountDeletionState()
                    },
                    enabled = !isDeletingAccount,
                    modifier = Modifier.testTag("dialog_delete_account_cancel_btn")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSyncDetailsDialog) {
        var workerStateText by remember { mutableStateOf("Querying...") }
        var isCheckingConn by remember { mutableStateOf(false) }
        val isGuestUser = currentUser == null || isAnonymous
        LaunchedEffect(Unit) {
            try {
                val workManager = WorkManager.getInstance(context)
                val infos = workManager.getWorkInfosForUniqueWork(com.example.sync.AutoSyncWorker.WORK_NAME_PERIODIC).get()
                workerStateText = if (infos.isNotEmpty()) {
                    infos.first().state.name
                } else {
                    "Not Scheduled"
                }
            } catch(e: Exception) {
                workerStateText = "Unknown"
            }
        }

        AlertDialog(
            onDismissRequest = { showSyncDetailsDialog = false },
            shape = RoundedCornerShape(28.dp),
            icon = {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Sync Engine Details",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Pending mutations", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${pendingUploadsCount.value}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.testTag("dialog_pending_mutations"))
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Conflict count", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${unresolvedConflictsCount.value}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.testTag("dialog_conflict_count"))
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Last sync", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val lastSuccessTime = syncPreferences.getLastSuccessfulSyncAt()
                        val lastSuccessStr = if (lastSuccessTime > 0L) {
                            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                            sdf.format(java.util.Date(lastSuccessTime))
                        } else "Never Synced"
                        Text(lastSuccessStr, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Last error", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(lastSyncError ?: "None", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Database version", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("9", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Sync engine version", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("v1.0", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Worker state", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(workerStateText, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedButton(
                        onClick = {
                            if (isGuestUser) {
                                errorDialogMsg = "Authentication Required"
                                errorDialogGuidance = "Guests cannot execute cloud actions. Please sign in to continue."
                                showSyncDetailsDialog = false
                                return@OutlinedButton
                            }
                            isCheckingConn = true
                            viewModel.queryCloudBackupStatus { success, message, count ->
                                isCheckingConn = false
                                if (success) {
                                    successDialogMsg = "Cloud Connection Verified"
                                    successDialogTimestamp = "Found: ${count ?: 0} clients safely stored in cloud storage."
                                } else {
                                    errorDialogMsg = "Connection Error"
                                    errorDialogGuidance = message
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isCheckingConn && !isOperationRunning,
                        modifier = Modifier.fillMaxWidth().testTag("btn_check_connection")
                    ) {
                        if (isCheckingConn) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Check Connection", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showSyncDetailsDialog = false },
                    modifier = Modifier.testTag("dialog_done_button")
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // 1. Premium Progress Dialog Overlay
    if (activeOperationProgress != null) {
        AlertDialog(
            onDismissRequest = {},
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            ),
            shape = RoundedCornerShape(24.dp),
            confirmButton = {},
            title = null,
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = activeOperationProgress,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Updating secure database systems. Please do not close or exit the application.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        )
    }

    // Informational dialog for valid empty-state outcomes
    if (infoDialogTitle != null) {
        AlertDialog(
            onDismissRequest = {
                infoDialogTitle = null
                infoDialogMessage = null
            },
            shape = RoundedCornerShape(24.dp),
            icon = {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = infoDialogTitle ?: "Information",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = infoDialogMessage ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        infoDialogTitle = null
                        infoDialogMessage = null
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LifeFreshGreen)
                ) {
                    Text("OK", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        )
    }

    // 2. Premium Success Dialog
    if (successDialogMsg != null) {
        AlertDialog(
            onDismissRequest = { successDialogMsg = null },
            shape = RoundedCornerShape(24.dp),
            icon = {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(LifeFreshGreen.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = LifeFreshGreen,
                        modifier = Modifier.size(32.dp)
                    )
                }
            },
            title = {
                Text(
                    text = successDialogMsg ?: "Action Successful",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Operational changes have been synchronized successfully.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (successDialogTimestamp != null) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = successDialogTimestamp ?: "",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { successDialogMsg = null },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LifeFreshGreen),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text("Done", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        )
    }

    // 3. Premium Error Dialog
    if (errorDialogMsg != null) {
        AlertDialog(
            onDismissRequest = { errorDialogMsg = null },
            shape = RoundedCornerShape(24.dp),
            icon = {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                }
            },
            title = {
                Text(
                    text = errorDialogMsg ?: "Sync Warning",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = errorDialogGuidance ?: "Operational error during cloud communication.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.05f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Retry guidance: Make sure you are signed in, have a functional internet connection, and try performing the operation again.",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(12.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { errorDialogMsg = null },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text("Acknowledge", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        )
    }

    // 4. Cloud Backup Confirmation Dialog
    if (showCloudBackupConfirm) {
        AlertDialog(
            onDismissRequest = { showCloudBackupConfirm = false },
            shape = RoundedCornerShape(28.dp),
            icon = {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Confirm Cloud Backup",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to backup your entire local CRM database to the secure cloud vault? This will overwrite your existing cloud backup files.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCloudBackupConfirm = false
                        coroutineScope.launch {
                            backupProgressStatus = "Preparing..."
                            kotlinx.coroutines.delay(600)
                            backupProgressStatus = "Uploading..."
                            kotlinx.coroutines.delay(1000)
                            viewModel.backupAllToCloud { success, message ->
                                coroutineScope.launch {
                                    backupProgressStatus = "Finalizing..."
                                    kotlinx.coroutines.delay(500)
                                    backupProgressStatus = null
                                    if (success) {
                                        successDialogMsg = "Cloud Sync Completed"
                                        successDialogTimestamp = "Message: $message\nTime: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                                        showBackupSuccessCard = true
                                    } else {
                                        errorDialogMsg = "Sync Upload Failed"
                                        errorDialogGuidance = message
                                    }
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LifeFreshGreen),
                    modifier = Modifier.height(44.dp)
                ) {
                    Text("Confirm Backup", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showCloudBackupConfirm = false },
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.height(44.dp)
                ) {
                    Text("Cancel", fontWeight = FontWeight.Medium)
                }
            }
        )
    }

    // 4b. Delete Cloud Backup Confirmation Dialog
    if (showDeleteCloudBackupConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteCloudBackupConfirm = false },
            shape = RoundedCornerShape(28.dp),
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Delete Cloud Backup?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "All cloud backup data stored in the cloud for this account will be permanently deleted and cannot be recovered. Your local device records will not be deleted.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteCloudBackupConfirm = false
                        coroutineScope.launch {
                            backupProgressStatus = "Preparing..."
                            kotlinx.coroutines.delay(400)
                            backupProgressStatus = "Deleting..."
                            viewModel.deleteCloudBackup { success, message ->
                                coroutineScope.launch {
                                    backupProgressStatus = "Finalizing..."
                                    kotlinx.coroutines.delay(400)
                                    backupProgressStatus = null
                                    if (success) {
                                        successDialogMsg = "Cloud Backup Deleted"
                                        successDialogTimestamp = "$message\nTime: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                                    } else {
                                        errorDialogMsg = "Delete Cloud Backup Failed"
                                        errorDialogGuidance = message
                                    }
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.height(44.dp).testTag("btn_confirm_delete_cloud_backup")
                ) {
                    Text("Delete Cloud Backup", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteCloudBackupConfirm = false },
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.height(44.dp)
                ) {
                    Text("Cancel", fontWeight = FontWeight.Medium)
                }
            }
        )
    }

    // 5. Cloud Restore Confirmation Dialog
    if (showCloudRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showCloudRestoreConfirm = false },
            shape = RoundedCornerShape(28.dp),
            icon = {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Confirm Cloud Restore",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "You are about to restore your workspace from the secure cloud backup. Please note:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("•", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("Cloud records will be merged and restored locally.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("•", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("Existing newer local changes should remain protected according to sync rules.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("•", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("The operation cannot be interrupted casually.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCloudRestoreConfirm = false
                        coroutineScope.launch {
                            restoreProgressStatus = "Preparing..."
                            kotlinx.coroutines.delay(600)
                            restoreProgressStatus = "Restoring..."
                            kotlinx.coroutines.delay(1000)
                            viewModel.manualRestoreFromCloud { success, message ->
                                coroutineScope.launch {
                                    restoreProgressStatus = "Finalizing..."
                                    kotlinx.coroutines.delay(500)
                                    restoreProgressStatus = null
                                    if (success) {
                                        successDialogMsg = "Restore Complete"
                                        successDialogTimestamp = message
                                    } else {
                                        errorDialogMsg = "Cloud Sync Error"
                                        errorDialogGuidance = message
                                    }
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LifeFreshGreen),
                    modifier = Modifier.height(44.dp)
                ) {
                    Text("Confirm Restore", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showCloudRestoreConfirm = false },
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.height(44.dp)
                ) {
                    Text("Cancel", fontWeight = FontWeight.Medium)
                }
            }
        )
    }

    // 6. Local Export Confirmation Dialog
    if (showLocalExportConfirm) {
        AlertDialog(
            onDismissRequest = { showLocalExportConfirm = false },
            shape = RoundedCornerShape(28.dp),
            icon = {
                Icon(
                    imageVector = Icons.Default.FileUpload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Confirm Local Export",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "Generate a secure JSON backup of your current database. The file will be exported directly to your Downloads directory.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLocalExportConfirm = false
                        coroutineScope.launch {
                            localExportProgressStatus = "Preparing..."
                            kotlinx.coroutines.delay(500)
                            localExportProgressStatus = "Uploading..."
                            kotlinx.coroutines.delay(800)
                            val data = viewModel.exportBackupJson()
                            if (data == "[]") {
                                localExportProgressStatus = null
                                infoDialogTitle = "Nothing to Back Up"
                                infoDialogMessage = "You don't have any client data yet. Add at least one client before creating a local backup."
                                return@launch
                            }
                            
                            try {
                                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
                                val displayName = "LifeFresh_Backup_$timestamp.json"
                                val savedUri = saveBackupToDownloads(context, data, displayName)
                                localExportProgressStatus = "Finalizing..."
                                kotlinx.coroutines.delay(500)
                                localExportProgressStatus = null
                                if (savedUri != null) {
                                    val pathText = if (savedUri.scheme == "content") {
                                        "Downloads/$displayName"
                                    } else {
                                        savedUri.path ?: "Downloads/$displayName"
                                    }
                                    successDialogMsg = "Database Backup Exported"
                                    successDialogTimestamp = "Saved to Downloads: $displayName\nTimestamp: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                                } else {
                                    errorDialogMsg = "Export Failed"
                                    errorDialogGuidance = "We were unable to write the backup file to your local storage. Please check storage space."
                                }
                            } catch (e: Exception) {
                                localExportProgressStatus = null
                                errorDialogMsg = "Export Error"
                                errorDialogGuidance = "An unexpected error occurred: ${e.localizedMessage}"
                            }
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LifeFreshGreen),
                    modifier = Modifier.height(44.dp)
                ) {
                    Text("Confirm Export", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showLocalExportConfirm = false },
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.height(44.dp)
                ) {
                    Text("Cancel", fontWeight = FontWeight.Medium)
                }
            }
        )
    }

    // 7. Local Import Confirmation Dialog
    if (showLocalImportConfirm) {
        AlertDialog(
            onDismissRequest = { showLocalImportConfirm = false },
            shape = RoundedCornerShape(28.dp),
            icon = {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Confirm Local Import",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "Importing will merge the client information inside a chosen JSON backup file into your local CRM database. Duplicate mobile numbers will be skipped.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLocalImportConfirm = false
                        backupFilePickerLauncher.launch("application/json")
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LifeFreshGreen),
                    modifier = Modifier.height(44.dp)
                ) {
                    Text("Browse Backup", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showLocalImportConfirm = false },
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.height(44.dp)
                ) {
                    Text("Cancel", fontWeight = FontWeight.Medium)
                }
            }
        )
    }

    // 8. Wipe All CRM Data confirm dialog (replaces old showResetConfirmAlert)
    if (showResetConfirmAlert) {
        AlertDialog(
            onDismissRequest = { showResetConfirmAlert = false },
            shape = RoundedCornerShape(28.dp),
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Delete Local Data?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "This action is PERMANENT and IRREVERSIBLE. It will wipe all local client profiles, call histories, reminders, and custom settings. Please ensure you have backed up your database first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmAlert = false
                        coroutineScope.launch {
                            progressStatus = "Preparing..."
                            kotlinx.coroutines.delay(400)
                            viewModel.resetAllData { success, resultMsg ->
                                progressStatus = null
                                if (success) {
                                    successDialogMsg = "CRM Database Reset"
                                    successDialogTimestamp = "$resultMsg\nTimestamp: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                                } else {
                                    successDialogMsg = "Action Blocked"
                                    successDialogTimestamp = "$resultMsg\nTimestamp: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.height(44.dp).testTag("reset_confirm_confirm")
                ) {
                    Text("Delete Local Data", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showResetConfirmAlert = false },
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.height(44.dp)
                ) {
                    Text("Cancel", fontWeight = FontWeight.Medium)
                }
            }
        )
    }

    // Help & Backup guide dialog
    if (showBackupHelpDialog) {
        AlertDialog(
            onDismissRequest = { showBackupHelpDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("📦 Backup Guide", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Export JSON Backup",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "• Creates a .json backup file\n• Save it in phone storage, Drive, or another safe location",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Import JSON Backup",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "• Select a previously exported .json file\n• Restores CRM data and reminders",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Share Backup",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "• Send the backup file using WhatsApp, Email, Telegram, Drive, etc.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showBackupHelpDialog = false },
                    modifier = Modifier.testTag("backup_help_close")
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showEditProfileDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSavingProfile) showEditProfileDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(if (isAnonymous) "Edit Guest Profile" else "Edit Profile")
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = when {
                            isAnonymous -> "Update the display name used for this local guest workspace."
                            isGoogle -> "Update your display name. Your email is managed by your Google Account."
                            else -> "Update your display name or account email. Password changes are handled separately from My Profile."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = editDisplayName,
                        onValueChange = { editDisplayName = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("edit_profile_name_input"),
                        enabled = !isSavingProfile
                    )
                    if (!isAnonymous && usesPasswordProvider) {
                        OutlinedTextField(
                            value = editEmailAddress,
                            onValueChange = { editEmailAddress = it },
                            label = { Text("Email Address") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("edit_profile_email_input"),
                            enabled = !isSavingProfile
                        )
                    } else if (isGoogle) {
                        OutlinedTextField(
                            value = emailStr,
                            onValueChange = {},
                            label = { Text("Google Account Email") },
                            singleLine = true,
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth().testTag("edit_profile_email_readonly")
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editDisplayName.isBlank()) {
                            Toast.makeText(context, "Display name cannot be empty", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (usesPasswordProvider && editEmailAddress.isBlank()) {
                            Toast.makeText(context, "Email address cannot be empty", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (usesPasswordProvider && editEmailAddress.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(editEmailAddress.trim()).matches()) {
                            Toast.makeText(context, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val user = currentUser ?: return@Button
                        val nameChanged = editDisplayName.trim() != displayNameStr
                        val emailChanged = usesPasswordProvider && editEmailAddress.trim() != (user.email ?: "")
                        if (!nameChanged && !emailChanged) {
                            showEditProfileDialog = false
                            return@Button
                        }

                        isSavingProfile = true
                        fun finishEmailUpdate() {
                            if (emailChanged) {
                                authViewModel.updateEmail(editEmailAddress.trim()) { success, msg ->
                                    isSavingProfile = false
                                    if (success) {
                                        Toast.makeText(context, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                                        showEditProfileDialog = false
                                    } else {
                                        Toast.makeText(context, msg ?: "Failed to update email", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                isSavingProfile = false
                                Toast.makeText(context, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                                showEditProfileDialog = false
                            }
                        }

                        if (nameChanged) {
                            authViewModel.updateProfileName(editDisplayName.trim()) { success, msg ->
                                if (success) {
                                    finishEmailUpdate()
                                } else {
                                    isSavingProfile = false
                                    Toast.makeText(context, msg ?: "Failed to update display name", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            finishEmailUpdate()
                        }
                    },
                    enabled = !isSavingProfile,
                    modifier = Modifier.testTag("btn_save_profile")
                ) {
                    if (isSavingProfile) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Save")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEditProfileDialog = false },
                    enabled = !isSavingProfile
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showChangePasswordDialog && usesPasswordProvider && !isAnonymous) {
        AlertDialog(
            onDismissRequest = {
                if (!isChangingPassword && !isSendingPasswordReset) {
                    showChangePasswordDialog = false
                }
            },
            title = { Text("Change Password", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Verify your current password, then choose a new password for your LifeFresh email account.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = currentProfilePassword,
                        onValueChange = { currentProfilePassword = it },
                        label = { Text("Current Password") },
                        singleLine = true,
                        enabled = !isChangingPassword && !isSendingPasswordReset,
                        visualTransformation = if (currentProfilePasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { currentProfilePasswordVisible = !currentProfilePasswordVisible }) {
                                Icon(
                                    imageVector = if (currentProfilePasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (currentProfilePasswordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("current_password_input")
                    )
                    OutlinedTextField(
                        value = newProfilePassword,
                        onValueChange = { newProfilePassword = it },
                        label = { Text("New Password") },
                        singleLine = true,
                        enabled = !isChangingPassword && !isSendingPasswordReset,
                        visualTransformation = if (newProfilePasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { newProfilePasswordVisible = !newProfilePasswordVisible }) {
                                Icon(
                                    imageVector = if (newProfilePasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (newProfilePasswordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("change_password_input")
                    )
                    OutlinedTextField(
                        value = confirmProfilePassword,
                        onValueChange = { confirmProfilePassword = it },
                        label = { Text("Confirm New Password") },
                        singleLine = true,
                        enabled = !isChangingPassword && !isSendingPasswordReset,
                        visualTransformation = if (confirmProfilePasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { confirmProfilePasswordVisible = !confirmProfilePasswordVisible }) {
                                Icon(
                                    imageVector = if (confirmProfilePasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (confirmProfilePasswordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("confirm_new_password_input")
                    )
                    TextButton(
                        onClick = {
                            isSendingPasswordReset = true
                            authViewModel.sendPasswordResetEmailForCurrentUser { success, message ->
                                isSendingPasswordReset = false
                                Toast.makeText(
                                    context,
                                    message ?: if (success) "Password reset email sent" else "Failed to send password reset email",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        enabled = !isChangingPassword && !isSendingPasswordReset,
                        modifier = Modifier.align(Alignment.End).testTag("btn_profile_forgot_password")
                    ) {
                        Text(if (isSendingPasswordReset) "Sending..." else "Forgot Password?")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (currentProfilePassword.isBlank()) {
                            Toast.makeText(context, "Enter your current password", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (newProfilePassword.length < 6) {
                            Toast.makeText(context, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (newProfilePassword != confirmProfilePassword) {
                            Toast.makeText(context, "New passwords do not match", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isChangingPassword = true
                        authViewModel.reauthenticateAndUpdatePassword(
                            currentPassword = currentProfilePassword,
                            newPassword = newProfilePassword
                        ) { success, msg ->
                            isChangingPassword = false
                            if (success) {
                                currentProfilePassword = ""
                                newProfilePassword = ""
                                confirmProfilePassword = ""
                                showChangePasswordDialog = false
                                Toast.makeText(context, "Password updated successfully", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, msg ?: "Failed to update password", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !isChangingPassword && !isSendingPasswordReset,
                    modifier = Modifier.testTag("btn_save_new_password")
                ) {
                    if (isChangingPassword) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Update Password")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showChangePasswordDialog = false },
                    enabled = !isChangingPassword && !isSendingPasswordReset
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showRequestDeletionConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRequestDeletionConfirmDialog = false },
            shape = RoundedCornerShape(28.dp),
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Request Account Deletion?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to request deletion of your account (${currentUser?.email ?: "your account"})?\n\nYour account will enter a 7-day grace period. If you sign in again within the next 7 days, your deletion request will be cancelled and your account and data will remain intact.\n\nAfter 7 days, your account and cloud data will be permanently deleted.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRequestDeletionConfirmDialog = false
                        authViewModel.requestAccountDeletionGracePeriod { success, message, scheduledAt ->
                            if (success) {
                                val scheduledDate = if (scheduledAt != null) {
                                    SimpleDateFormat("MMMM d, yyyy 'at' HH:mm", Locale.getDefault()).format(Date(scheduledAt))
                                } else "in 7 days"
                                infoDialogTitle = "Deletion Request Submitted"
                                infoDialogMessage = "Your account is scheduled for permanent deletion on $scheduledDate.\n\nYou have been signed out. To cancel this request and restore your account, simply sign in again within 7 days."
                            } else {
                                errorDialogMsg = "Request Failed"
                                errorDialogGuidance = message
                            }
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.height(44.dp).testTag("btn_confirm_request_account_deletion")
                ) {
                    Text("Request Account Deletion", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showRequestDeletionConfirmDialog = false },
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.height(44.dp)
                ) {
                    Text("Cancel", fontWeight = FontWeight.Medium)
                }
            }
        )
    }

    if (showLanguageDialog) {
        LanguageSelectionDialog(
            currentMetadata = activeLanguageMeta,
            languagePacks = languagePacks,
            playInstallState = playInstallState,
            onSelectLanguage = { lang ->
                viewModel.selectOrInstallLanguage(lang)
                if (viewModel.playLanguageDeliveryManager.isLanguageAvailable(lang)) {
                    showLanguageDialog = false
                    Toast.makeText(
                        context,
                        AppStrings.getString("settings_language_changed", lang.code),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onSelectCustomPack = { meta ->
                viewModel.setAppLanguage(meta)
                showLanguageDialog = false
                Toast.makeText(
                    context,
                    AppStrings.getString("settings_language_changed", meta.code),
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDownloadPack = { meta ->
                viewModel.downloadLanguagePack(meta) { success, error ->
                    if (!success && error != null) {
                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onRemovePack = { meta ->
                val success = viewModel.removeLanguagePack(meta)
                if (success) {
                    Toast.makeText(
                        context,
                        "Language pack removed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onCancelPlayInstall = { sessionId ->
                viewModel.cancelLanguageInstall(sessionId)
            },
            onResetPlayInstall = {
                viewModel.resetPlayLanguageInstallState()
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

}

@Composable
fun LanguageSelectionDialog(
    currentMetadata: LanguagePackMetadata,
    languagePacks: List<LanguagePackMetadata>,
    playInstallState: PlayLanguageInstallState = PlayLanguageInstallState.Idle,
    onSelectLanguage: (AppLanguage) -> Unit,
    onSelectCustomPack: (LanguagePackMetadata) -> Unit = {},
    onDownloadPack: (LanguagePackMetadata) -> Unit,
    onRemovePack: (LanguagePackMetadata) -> Unit,
    onCancelPlayInstall: (Int) -> Unit = {},
    onResetPlayInstall: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var packToRemove by remember { mutableStateOf<LanguagePackMetadata?>(null) }
    val scrollState = androidx.compose.foundation.rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        icon = {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.settings_language_manager),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Play Language Delivery Active State Card (Real progress, confirmation, errors)
                when (val state = playInstallState) {
                    is PlayLanguageInstallState.Pending -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.5.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Preparing download...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = state.language.nativeName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (state.sessionId > 0) {
                                    TextButton(onClick = { onCancelPlayInstall(state.sessionId) }) {
                                        Text("Cancel", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                    is PlayLanguageInstallState.Downloading -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Downloading ${state.language.nativeName}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        val progressText = if (state.totalBytesToDownload > 0L) {
                                            val mbDownloaded = state.bytesDownloaded / (1024.0 * 1024.0)
                                            val mbTotal = state.totalBytesToDownload / (1024.0 * 1024.0)
                                            String.format(Locale.US, "Downloaded %.2f MB / %.2f MB", mbDownloaded, mbTotal)
                                        } else {
                                            "Downloading..."
                                        }
                                        Text(
                                            text = progressText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (state.sessionId > 0) {
                                        TextButton(onClick = { onCancelPlayInstall(state.sessionId) }) {
                                            Text("Cancel", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }

                                if (state.totalBytesToDownload > 0L) {
                                    val progressFraction = (state.bytesDownloaded.toFloat() / state.totalBytesToDownload.toFloat()).coerceIn(0f, 1f)
                                    LinearProgressIndicator(
                                        progress = { progressFraction },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                    is PlayLanguageInstallState.Installing -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.5.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Installing ${state.language.nativeName}...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Please wait a moment",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    is PlayLanguageInstallState.RequiresUserConfirmation -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Confirmation Required",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    text = "Google Play requires confirmation to download ${state.language.nativeName} over your network connection.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    if (state.sessionId > 0) {
                                        TextButton(onClick = { onCancelPlayInstall(state.sessionId) }) {
                                            Text("Cancel")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    is PlayLanguageInstallState.Failed -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Download Failed",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = state.errorMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = { onResetPlayInstall() }) {
                                        Text("Dismiss", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (state.language != null) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        FilledTonalButton(
                                            onClick = {
                                                onResetPlayInstall()
                                                onSelectLanguage(state.language)
                                            }
                                        ) {
                                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Retry")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        // Idle, Downloaded, Installed, Canceled - no banner needed
                    }
                }

                // Section 1: Bundled Languages (Core 4)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_bundled_languages),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    AppLanguage.ALL.forEach { lang ->
                        val isSelected = (lang.code.equals(currentMetadata.code, ignoreCase = true))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            border = BorderStroke(
                                if (isSelected) 1.5.dp else 1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectLanguage(lang) }
                                .testTag("lang_option_${lang.code}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = lang.nativeName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.settings_lang_installed),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = when (lang) {
                                            AppLanguage.ENGLISH -> "English (Default)"
                                            AppLanguage.HINDI -> "Hindi (Devanagari)"
                                            AppLanguage.URDU -> "Urdu (RTL)"
                                            AppLanguage.TAMIL -> "Tamil"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { onSelectLanguage(lang) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }
                    }
                }

                // Section 2: Downloadable / Installed Language Packs (Dynamic)
                val dynamicPacks = languagePacks.filter { !it.isBundled }
                if (dynamicPacks.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_downloadable_languages),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        dynamicPacks.forEach { pack ->
                            val isSelected = pack.code.equals(currentMetadata.code, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                border = BorderStroke(
                                    if (isSelected) 1.5.dp else 1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (pack.status == LanguagePackStatus.INSTALLED) {
                                            Modifier.clickable { onSelectCustomPack(pack) }
                                        } else Modifier
                                    )
                                    .testTag("lang_pack_${pack.code}")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = pack.nativeName,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = pack.displayName,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        when (pack.status) {
                                            LanguagePackStatus.INSTALLED -> {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    IconButton(
                                                        onClick = { packToRemove = pack },
                                                        modifier = Modifier.size(36.dp).testTag("btn_remove_pack_${pack.code}")
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.DeleteOutline,
                                                            contentDescription = "Remove ${pack.displayName}",
                                                            tint = MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                    RadioButton(
                                                        selected = isSelected,
                                                        onClick = { onSelectCustomPack(pack) },
                                                        colors = RadioButtonDefaults.colors(
                                                            selectedColor = MaterialTheme.colorScheme.primary
                                                        )
                                                    )
                                                }
                                            }
                                            LanguagePackStatus.AVAILABLE -> {
                                                FilledTonalButton(
                                                    onClick = { onDownloadPack(pack) },
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.testTag("btn_download_pack_${pack.code}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Download,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(stringResource(R.string.settings_lang_download), style = MaterialTheme.typography.labelMedium)
                                                }
                                            }
                                            LanguagePackStatus.DOWNLOADING -> {
                                                Text(
                                                    text = "${(pack.downloadProgress * 100).toInt()}%",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            LanguagePackStatus.FAILED -> {
                                                IconButton(
                                                    onClick = { onDownloadPack(pack) },
                                                    modifier = Modifier.testTag("btn_retry_pack_${pack.code}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Refresh,
                                                        contentDescription = "Retry download",
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (pack.status == LanguagePackStatus.DOWNLOADING) {
                                        androidx.compose.material3.LinearProgressIndicator(
                                            progress = { pack.downloadProgress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(2.dp)),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    if (pack.status == LanguagePackStatus.FAILED && !pack.errorMessage.isNullOrBlank()) {
                                        Text(
                                            text = pack.errorMessage,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_close_lang_dialog")
            ) {
                Text(stringResource(R.string.common_close))
            }
        }
    )

    // Remove Confirmation Dialog
    if (packToRemove != null) {
        val pack = packToRemove!!
        AlertDialog(
            onDismissRequest = { packToRemove = null },
            shape = RoundedCornerShape(24.dp),
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.settings_lang_remove_confirm),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "${pack.displayName} (${pack.nativeName})\n\n${stringResource(R.string.settings_lang_remove_desc)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = pack
                        packToRemove = null
                        onRemovePack(target)
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("btn_confirm_remove_pack")
                ) {
                    Text(stringResource(R.string.settings_lang_remove))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { packToRemove = null },
                    modifier = Modifier.testTag("btn_cancel_remove_pack")
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}
