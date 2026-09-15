package com.example.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.viewmodel.CRMViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAlarmScreen(
    viewModel: CRMViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val alarmSourceUseCustom by viewModel.alarmUseCustom.collectAsStateWithLifecycle()
    val activeAlarmSound by viewModel.alarmSound.collectAsStateWithLifecycle()
    val activeAlarmVolume by viewModel.alarmVolume.collectAsStateWithLifecycle()
    val customAudioName by viewModel.customAudioFilename.collectAsStateWithLifecycle()
    val isTesting by viewModel.isTestingAlarm.collectAsStateWithLifecycle()
    val activeRingMode by viewModel.reminderRingMode.collectAsStateWithLifecycle()
    val isExactAlarmGranted by viewModel.isExactAlarmGrantedState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.updateExactAlarmStatus()
    }

    var sourceDropdownExpanded by remember { mutableStateOf(false) }
    var soundDropdownExpanded by remember { mutableStateOf(false) }
    var volumeDropdownExpanded by remember { mutableStateOf(false) }
    var ringModeDropdownExpanded by remember { mutableStateOf(false) }
    var alarmSaveFeedbackMessage by remember { mutableStateOf<String?>(null) }

    val customAudioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val success = viewModel.registerCustomAudioFile(context, uri)
            if (success) {
                alarmSaveFeedbackMessage = "Settings saved"
                Toast.makeText(context, "Custom audio registered successfully!", Toast.LENGTH_SHORT).show()
            } else {
                alarmSaveFeedbackMessage = "Could not save alarm settings"
                Toast.makeText(context, "Failed to copy chosen tone files.", Toast.LENGTH_SHORT).show()
            }
        }
    }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = { onBack() },
                        modifier = Modifier.testTag("btn_back_to_settings")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back to Settings"
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Alarm Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (alarmSaveFeedbackMessage != null) {
                    LaunchedEffect(alarmSaveFeedbackMessage) {
                        kotlinx.coroutines.delay(2500)
                        alarmSaveFeedbackMessage = null
                    }
                    Snackbar(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("alarm_settings_snackbar"),
                        containerColor = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface
                    ) {
                        Text(alarmSaveFeedbackMessage ?: "", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Permission Status Cards
                val isNotificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Notification Permission Status Row
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("notification_permission_status_row")
                            .clickable {
                                if (!isNotificationGranted) {
                                    try {
                                        val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        try {
                                            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = Uri.fromParts("package", context.packageName, null)
                                            }
                                            context.startActivity(intent)
                                        } catch (ex: Exception) {
                                            // Safely handled
                                        }
                                    }
                                }
                            },
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isNotificationGranted) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                                    contentDescription = null,
                                    tint = if (isNotificationGranted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Notification Permission",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = if (isNotificationGranted) "Allowed" else "Disabled (Tap to fix)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isNotificationGranted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("notification_permission_status_text")
                            )
                        }
                    }

                    // Exact Alarm Permission Status Row
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("exact_alarm_status_row")
                            .clickable {
                                if (!isExactAlarmGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    try {
                                        val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                            data = Uri.fromParts("package", context.packageName, null)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        try {
                                            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = Uri.fromParts("package", context.packageName, null)
                                            }
                                            context.startActivity(intent)
                                        } catch (ex: Exception) {
                                            // Safely handled
                                        }
                                    }
                                }
                            },
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isExactAlarmGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (isExactAlarmGranted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Exact Alarm Permission",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = if (isExactAlarmGranted) "Allowed" else "Denied (Tap to fix)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isExactAlarmGranted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("exact_alarm_status_text")
                            )
                        }
                    }

                    if (!isExactAlarmGranted) {
                        Text(
                            text = "Reminder alarms will not ring until Exact Alarm permission is enabled.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, start = 4.dp, end = 4.dp)
                                .testTag("exact_alarm_warning_text")
                        )
                    }

                    // Background Battery Optimization (Doze Mode Exemption) Row
                    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    val isIgnoringBatteryOptimizations = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
                    } else true

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("battery_optimization_status_row"),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (isIgnoringBatteryOptimizations) Icons.Default.CheckCircle else Icons.Default.BatteryAlert,
                                    contentDescription = null,
                                    tint = if (isIgnoringBatteryOptimizations) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "Background Battery Optimization",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Disable battery restrictions so reminder alarms trigger immediately even when the device is locked in deep sleep.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            if (isIgnoringBatteryOptimizations) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Battery optimization disabled",
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(24.dp).testTag("battery_optimization_exempt_icon")
                                )
                            } else {
                                Button(
                                    onClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                            try {
                                                val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                try {
                                                    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                        data = Uri.fromParts("package", context.packageName, null)
                                                    }
                                                    context.startActivity(intent)
                                                } catch (ex: Exception) {
                                                    // Safely handled
                                                }
                                            }
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("battery_optimization_exempt_button")
                                ) {
                                    Text("Exempt", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }

                // Alarm Settings Preferences Card
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Alarm Source selection Dropdown
                        ExposedDropdownMenuBox(
                            expanded = sourceDropdownExpanded,
                            onExpandedChange = { sourceDropdownExpanded = !sourceDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = if (alarmSourceUseCustom) "Use Custom Alarm Audio" else "Use Default Alarm",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Alarm Source") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceDropdownExpanded) },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                                    .testTag("alarm_source_dropdown")
                            )
                            ExposedDropdownMenu(
                                expanded = sourceDropdownExpanded,
                                onDismissRequest = { sourceDropdownExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Use Default Alarm") },
                                    onClick = {
                                        if (alarmSourceUseCustom) {
                                            viewModel.setAlarmUseCustom(false)
                                            alarmSaveFeedbackMessage = "Settings saved"
                                        }
                                        sourceDropdownExpanded = false
                                    },
                                    modifier = Modifier.testTag("source_default_item")
                                )
                                DropdownMenuItem(
                                    text = { Text("Use Custom Alarm Audio") },
                                    onClick = {
                                        if (!alarmSourceUseCustom) {
                                            viewModel.setAlarmUseCustom(true)
                                            alarmSaveFeedbackMessage = "Settings saved"
                                        }
                                        sourceDropdownExpanded = false
                                    },
                                    modifier = Modifier.testTag("source_custom_item")
                                )
                            }
                        }

                        // Default Alarm options grouping
                        if (!alarmSourceUseCustom) {
                            ExposedDropdownMenuBox(
                                expanded = soundDropdownExpanded,
                                onExpandedChange = { soundDropdownExpanded = !soundDropdownExpanded }
                            ) {
                                OutlinedTextField(
                                    value = when (activeAlarmSound) {
                                        "holiday" -> "Holiday"
                                        "morning_bell" -> "Morning Bell"
                                        "soft_chime" -> "Soft Chime"
                                        "medical_reminder" -> "Wellness Reminder"
                                        "fresh_alert" -> "Fresh Alert"
                                        "nature_bell" -> "Nature Bell"
                                        "peaceful_glow" -> "Peaceful Glow"
                                        "crystal_breeze" -> "Crystal Breeze"
                                        "extreme_siren" -> "Extreme Siren"
                                        "critical_alert" -> "Critical Alert"
                                        else -> "Holiday"
                                    },
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Alarm Sound Selection") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = soundDropdownExpanded) },
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor()
                                        .testTag("alarm_sound_dropdown")
                                )
                                ExposedDropdownMenu(
                                    expanded = soundDropdownExpanded,
                                    onDismissRequest = { soundDropdownExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Holiday") },
                                        onClick = {
                                            if (activeAlarmSound != "holiday") {
                                                viewModel.setAlarmSound("holiday")
                                                alarmSaveFeedbackMessage = "Settings saved"
                                            }
                                            soundDropdownExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Morning Bell") },
                                        onClick = {
                                            if (activeAlarmSound != "morning_bell") {
                                                viewModel.setAlarmSound("morning_bell")
                                                alarmSaveFeedbackMessage = "Settings saved"
                                            }
                                            soundDropdownExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Soft Chime") },
                                        onClick = {
                                            if (activeAlarmSound != "soft_chime") {
                                                viewModel.setAlarmSound("soft_chime")
                                                alarmSaveFeedbackMessage = "Settings saved"
                                            }
                                            soundDropdownExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Wellness Reminder") },
                                        onClick = {
                                            if (activeAlarmSound != "medical_reminder") {
                                                viewModel.setAlarmSound("medical_reminder")
                                                alarmSaveFeedbackMessage = "Settings saved"
                                            }
                                            soundDropdownExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Fresh Alert") },
                                        onClick = {
                                            if (activeAlarmSound != "fresh_alert") {
                                                viewModel.setAlarmSound("fresh_alert")
                                                alarmSaveFeedbackMessage = "Settings saved"
                                            }
                                            soundDropdownExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Nature Bell") },
                                        onClick = {
                                            if (activeAlarmSound != "nature_bell") {
                                                viewModel.setAlarmSound("nature_bell")
                                                alarmSaveFeedbackMessage = "Settings saved"
                                            }
                                            soundDropdownExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Peaceful Glow") },
                                        onClick = {
                                            if (activeAlarmSound != "peaceful_glow") {
                                                viewModel.setAlarmSound("peaceful_glow")
                                                alarmSaveFeedbackMessage = "Settings saved"
                                            }
                                            soundDropdownExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Crystal Breeze") },
                                        onClick = {
                                            if (activeAlarmSound != "crystal_breeze") {
                                                viewModel.setAlarmSound("crystal_breeze")
                                                alarmSaveFeedbackMessage = "Settings saved"
                                            }
                                            soundDropdownExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Extreme Siren") },
                                        onClick = {
                                            if (activeAlarmSound != "extreme_siren") {
                                                viewModel.setAlarmSound("extreme_siren")
                                                alarmSaveFeedbackMessage = "Settings saved"
                                            }
                                            soundDropdownExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Critical Alert") },
                                        onClick = {
                                            if (activeAlarmSound != "critical_alert") {
                                                viewModel.setAlarmSound("critical_alert")
                                                alarmSaveFeedbackMessage = "Settings saved"
                                            }
                                            soundDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        } else {
                            // Custom Alarm Audio file register card mapping
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Custom Audio File", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                    Text(
                                        text = "Current: $customAudioName",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { customAudioPickerLauncher.launch("audio/*") },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            shape = RoundedCornerShape(24.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(40.dp)
                                                .testTag("select_audio_file")
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Text("Select Audio", fontSize = 12.sp, color = Color.White)
                                            }
                                        }

                                        if (customAudioName != "No file selected") {
                                            Button(
                                                onClick = {
                                                    viewModel.removeCustomAudio(context)
                                                    alarmSaveFeedbackMessage = "Settings saved"
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                                shape = RoundedCornerShape(24.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(40.dp)
                                                    .testTag("remove_audio_file")
                                            ) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Text("Remove", fontSize = 12.sp, color = Color.White)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Alarm Volume selection Dropdown slider
                        ExposedDropdownMenuBox(
                            expanded = volumeDropdownExpanded,
                            onExpandedChange = { volumeDropdownExpanded = !volumeDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = activeAlarmVolume.capitalize(),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Alarm Volume") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = volumeDropdownExpanded) },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                                    .testTag("alarm_volume_dropdown")
                            )
                            ExposedDropdownMenu(
                                expanded = volumeDropdownExpanded,
                                onDismissRequest = { volumeDropdownExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Low") },
                                    onClick = {
                                        if (activeAlarmVolume != "low") {
                                            viewModel.setAlarmVolume("low")
                                            alarmSaveFeedbackMessage = "Settings saved"
                                        }
                                        volumeDropdownExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Medium") },
                                    onClick = {
                                        if (activeAlarmVolume != "medium") {
                                            viewModel.setAlarmVolume("medium")
                                            alarmSaveFeedbackMessage = "Settings saved"
                                        }
                                        volumeDropdownExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("High") },
                                    onClick = {
                                        if (activeAlarmVolume != "high") {
                                            viewModel.setAlarmVolume("high")
                                            alarmSaveFeedbackMessage = "Settings saved"
                                        }
                                        volumeDropdownExpanded = false
                                    }
                                )
                            }
                        }

                        // Reminder Ring Mode selection Dropdown
                        ExposedDropdownMenuBox(
                            expanded = ringModeDropdownExpanded,
                            onExpandedChange = { ringModeDropdownExpanded = !ringModeDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = when (activeRingMode) {
                                    "continuous" -> "Continuous Ringing"
                                    "auto_stop" -> "Auto Stop After 2 Minutes"
                                    else -> "Continuous Ringing"
                                },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Reminder Ring Mode") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = ringModeDropdownExpanded) },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                                    .testTag("reminder_ring_mode_dropdown")
                            )
                            ExposedDropdownMenu(
                                expanded = ringModeDropdownExpanded,
                                onDismissRequest = { ringModeDropdownExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Continuous Ringing") },
                                    onClick = {
                                        if (activeRingMode != "continuous") {
                                            viewModel.setReminderRingMode("continuous")
                                            alarmSaveFeedbackMessage = "Settings saved"
                                        }
                                        ringModeDropdownExpanded = false
                                    },
                                    modifier = Modifier.testTag("ring_mode_continuous_item")
                                )
                                DropdownMenuItem(
                                    text = { Text("Auto Stop After 2 Minutes") },
                                    onClick = {
                                        if (activeRingMode != "auto_stop") {
                                            viewModel.setReminderRingMode("auto_stop")
                                            alarmSaveFeedbackMessage = "Settings saved"
                                        }
                                        ringModeDropdownExpanded = false
                                    },
                                    modifier = Modifier.testTag("ring_mode_auto_stop_item")
                                )
                            }
                        }
                    }
                }

                // Play / Test selected Alarm Trigger button
                Button(
                    onClick = {
                        try {
                            viewModel.toggleTestAlarm(context)
                        } catch (e: Exception) {
                            alarmSaveFeedbackMessage = "Could not play alarm sound"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isTesting) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary,
                        contentColor = if (isTesting) MaterialTheme.colorScheme.onPrimaryContainer else Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("test_selected_alarm_trigger"),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isTesting) Icons.Default.Square else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Text(
                            text = if (isTesting) "Playing test sound..." else "Test Selected Alarm Sound",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                }

}
