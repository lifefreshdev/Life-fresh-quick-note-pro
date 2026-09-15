package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.example.R
import com.example.data.database.LeadEntity
import com.example.ui.viewmodel.CRMViewModel
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientProfileDialog(
    lead: LeadEntity,
    viewModel: CRMViewModel,
    onDismiss: () -> Unit,
    onEditClick: () -> Unit
) {
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAddReminderDialog by remember { mutableStateOf(false) }
    var showAddNoteDialog by remember { mutableStateOf(false) }

    // Parse raw diseases for save operations
    val rawDiseasesList = remember(lead.diseases) {
        try {
            val arr = JSONArray(lead.diseases)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Parse diseases for display
    val parsedDiseasesList = remember(lead.diseases, lead.otherDisease) {
        try {
            val arr = JSONArray(lead.diseases)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val d = arr.getString(i)
                list.add(if (d == "Other" && lead.otherDisease.isNotEmpty()) lead.otherDisease else d)
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    val parsedDiseasesString = remember(parsedDiseasesList) {
        if (parsedDiseasesList.isEmpty()) "None" else parsedDiseasesList.joinToString(", ")
    }

    // Avatar configuration
    val initials = remember(lead.name) {
        val parts = lead.name.split(" ").filter { it.isNotEmpty() }
        if (parts.size >= 2) {
            "${parts[0].take(1)}${parts[1].take(1)}".uppercase()
        } else if (parts.isNotEmpty()) {
            parts[0].take(1).uppercase()
        } else {
            "CL"
        }
    }

    val avatarBg = remember(lead.id) {
        val colors = listOf(
            Color(0xFF2E7D32), // Organic Emerald Green
            Color(0xFF00796B), // Teal Accent
            Color(0xFF1976D2), // Calm Blue
            Color(0xFF6A1B9A), // Deep Purple
            Color(0xFFC2185B), // Soft Pink Accent
            Color(0xFFE65100)  // Calm Orange
        )
        colors[kotlin.math.abs(lead.id.hashCode() % colors.size)]
    }

    // Dynamic chronological timeline generation.
    // Only render events when the app has a real persisted event timestamp.
    // Legacy v12 rows intentionally keep 0L for note/reminder event times so
    // the UI never fabricates activity times from the client-created timestamp.
    val timelineEvents = remember(lead) {
        val events = mutableListOf<TimelineEventItem>()

        events.add(
            TimelineEventItem(
                title = "Client Created",
                description = "Added to CRM registry",
                date = formatDateStr(lead.timestamp),
                time = formatTimeStr(lead.timestamp),
                relativeTime = getRelativeTimeString(lead.timestamp),
                icon = Icons.Default.PersonAdd,
                color = Color(0xFF1E88E5),
                timestamp = lead.timestamp
            )
        )

        if (lead.notes.isNotEmpty() && lead.notesUpdatedAt > 0L) {
            events.add(
                TimelineEventItem(
                    title = "Notes Updated",
                    description = "Coaching notes updated",
                    date = formatDateStr(lead.notesUpdatedAt),
                    time = formatTimeStr(lead.notesUpdatedAt),
                    relativeTime = getRelativeTimeString(lead.notesUpdatedAt),
                    icon = Icons.Default.EditNote,
                    color = Color(0xFF8E24AA),
                    timestamp = lead.notesUpdatedAt
                )
            )
        }

        if (lead.reminderDate.isNotEmpty() && lead.reminderUpdatedAt > 0L) {
            events.add(
                TimelineEventItem(
                    title = "Reminder Scheduled",
                    description = "Follow-up set for ${formatDateStr(lead.reminderDate)}",
                    date = formatDateStr(lead.reminderUpdatedAt),
                    time = formatTimeStr(lead.reminderUpdatedAt),
                    relativeTime = getRelativeTimeString(lead.reminderUpdatedAt),
                    icon = Icons.Default.Alarm,
                    color = Color(0xFFFB8C00),
                    timestamp = lead.reminderUpdatedAt
                )
            )
        }

        if (!lead.lastCall.isNullOrEmpty()) {
            val callTime = try {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                    .parse(lead.lastCall)
                    ?.time
                    ?: 0L
            } catch (_: Exception) {
                0L
            }

            if (callTime > 0L) {
                events.add(
                    TimelineEventItem(
                        title = "Call Initiated",
                        description = "Coaching conversation started",
                        date = formatDateStr(callTime),
                        time = formatTimeStr(callTime),
                        relativeTime = getRelativeTimeString(callTime),
                        icon = Icons.Default.PhoneCallback,
                        color = Color(0xFF00ACC1),
                        timestamp = callTime
                    )
                )
            }
        }

        events.sortByDescending { it.timestamp }
        events
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        LaunchedEffect(window) {
            window?.let {
                WindowCompat.setDecorFitsSystemWindows(it, false)
                it.setStatusBarColor(android.graphics.Color.TRANSPARENT)
                it.setNavigationBarColor(android.graphics.Color.TRANSPARENT)
                it.setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .testTag("client_profile_dialog"),
            color = MaterialTheme.colorScheme.background
        ) {
            var animateIn by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                animateIn = true
            }

            AnimatedVisibility(
                visible = animateIn,
                enter = fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.95f, animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(250)) + scaleOut(targetScale = 0.95f, animationSpec = tween(250))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // PREMIUM APP BAR
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("profile_back_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        Text(
                            text = "Client Profile",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        // Edit / Archive / Delete are already exposed in the primary action row.
                        // Keep a matching spacer so the title remains visually centered.
                        Spacer(modifier = Modifier.size(48.dp))
                    }

                    // SCROLLABLE CONTAINER
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        
                        // PREMIUM HERO HEADER CARD
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp, horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Compact Circular Avatar
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .shadow(3.dp, CircleShape)
                                        .background(avatarBg, CircleShape)
                                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = initials,
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 26.sp
                                    )
                                }

                                // Client Name as Primary Title
                                Text(
                                    text = lead.name,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.15.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onBackground,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                // Mobile Number Below
                                Text(
                                    text = "📞  ${lead.mobile}",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Medium,
                                        letterSpacing = 0.25.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                // Status Chip Below
                                val badgeColor = when {
                                    lead.archived -> MaterialTheme.colorScheme.outline
                                    lead.status.lowercase() == "complete" -> Color(0xFF43A047)
                                    else -> Color(0xFFFB8C00)
                                }
                                val badgeText = when {
                                    lead.archived -> "Archived"
                                    lead.status.lowercase() == "complete" -> "Complete"
                                    else -> "Pending"
                                }

                                Box(
                                    modifier = Modifier
                                        .background(badgeColor.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                                        .border(1.dp, badgeColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(badgeColor, CircleShape)
                                        )
                                        Text(
                                            text = badgeText,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = badgeColor
                                        )
                                    }
                                }
                            }
                        }

                        // QUICK ACTIONS ROW (Visually Premium & Highly Accessible)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ProfileActionButton(
                                icon = Icons.Default.Phone,
                                label = "Call",
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                onClick = {
                                    viewModel.markCallInitiated(lead)
                                    try {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${lead.mobile}"))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "No dialer application found.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )

                            ProfileActionButton(
                                painter = painterResource(id = R.drawable.ic_whatsapp),
                                label = "WhatsApp",
                                containerColor = Color(0xFF25D366).copy(alpha = 0.15f),
                                contentColor = Color(0xFF128C7E),
                                onClick = {
                                    val relationText = if (lead.relation == "Other") "Other (${lead.otherRelation})" else lead.relation
                                    val summary = """
                                        *LifeFresh Quick Note Pro*
                                        Name: ${lead.name}
                                        Mobile: ${lead.mobile}
                                        Relation: $relationText
                                        Diseases: $parsedDiseasesString
                                        Status: ${lead.status}
                                        Reminder Status: ${lead.reminderStatus}
                                        Reminder: ${lead.reminderDate.ifEmpty { "None" }}${if (lead.reminderTime.isNotEmpty()) " at ${formatTimeStr(lead.reminderTime)}" else ""}
                                        Notes: ${lead.notes.ifEmpty { "N/A" }}
                                        Last Call: ${lead.lastCall ?: "Never"}
                                    """.trimIndent()

                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=91${lead.mobile}&text=${Uri.encode(summary)}"))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "WhatsApp has not been found on your device.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )

                            ProfileActionButton(
                                icon = Icons.Default.Edit,
                                label = "Edit",
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                onClick = onEditClick
                            )

                            ProfileActionButton(
                                icon = if (lead.archived) Icons.Default.Unarchive else Icons.Default.Archive,
                                label = if (lead.archived) "Restore" else "Archive",
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                onClick = {
                                    viewModel.toggleArchive(lead)
                                    Toast.makeText(
                                        context,
                                        if (lead.archived) "Client restored from archives!" else "Client profile archived!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )

                            ProfileActionButton(
                                icon = Icons.Default.Delete,
                                label = "Delete",
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                onClick = { showDeleteConfirm = true }
                            )
                        }

                        // INDIVIDUAL INFORMATION CARDS (20dp corner radius, 18dp content padding)

                        // CARD 1: Personal Information
                        ProfileSectionCard(title = "Personal Information", icon = Icons.Outlined.Person) {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                ProfileDetailRow(
                                    label = "Full Name",
                                    value = lead.name,
                                    icon = Icons.Outlined.Badge
                                )
                                ProfileDetailRow(
                                    label = "Mobile Number",
                                    value = lead.mobile,
                                    icon = Icons.Outlined.Phone
                                )
                                ProfileDetailRow(
                                    label = "Client Relation",
                                    value = if (lead.relation == "Other" && lead.otherRelation.isNotEmpty()) "Other (${lead.otherRelation})" else lead.relation,
                                    icon = Icons.Outlined.People
                                )
                            }
                        }

                        // CARD 2: Health Issues / Diseases
                        ProfileSectionCard(title = "Wellness Issues", icon = Icons.Outlined.FavoriteBorder) {
                            if (parsedDiseasesList.isEmpty()) {
                                PremiumEmptyState(
                                    icon = Icons.Outlined.HealthAndSafety,
                                    title = "No Issues Specified",
                                    description = "No lifestyle or physical wellness issues have been specified for this client."
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    parsedDiseasesList.forEach { disease ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f),
                                                    RoundedCornerShape(12.dp)
                                                )
                                                .border(
                                                    0.5.dp,
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                                    RoundedCornerShape(12.dp)
                                                )
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = disease,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // CARD 3: Reminder Information
                        val isOverdue = isReminderOverdue(lead)
                        val cardBorder = if (isOverdue && lead.reminderStatus != "Completed" && lead.reminderStatus != "Dismissed") {
                            BorderStroke(1.2.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                        } else null

                        ProfileSectionCard(
                            title = "Reminder Information",
                            icon = Icons.Outlined.Notifications,
                            border = cardBorder,
                            action = if (lead.reminderDate.isNotEmpty()) {
                                {
                                    IconButton(
                                        onClick = { showAddReminderDialog = true },
                                        modifier = Modifier.size(32.dp).testTag("btn_edit_reminder")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Reminder",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            } else null
                        ) {
                            if (lead.reminderDate.isEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "No reminder scheduled",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Add a follow-up reminder for this client.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedButton(
                                        onClick = { showAddReminderDialog = true },
                                        modifier = Modifier.testTag("btn_add_reminder"),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AddAlert,
                                            contentDescription = "Add Reminder",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Add Reminder", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            } else {
                                val todayStr = remember {
                                    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                }
                                val reminderStatusComputed = when {
                                    lead.reminderStatus == "Completed" -> "Completed"
                                    lead.reminderStatus == "Dismissed" -> "Dismissed"
                                    lead.reminderStatus == "Overdue" || isOverdue -> "Overdue"
                                    lead.reminderDate == todayStr -> "Today"
                                    else -> "Upcoming"
                                }

                                // Unified Semantic Colors from Design System v1.0
                                val (semanticColor, semanticBg, statusLabelText) = when (reminderStatusComputed) {
                                    "Today" -> Triple(
                                        Color(0xFF2563EB), // Blue
                                        Color(0xFFEFF6FF), // Light Blue Bg
                                        "Due Today"
                                    )
                                    "Overdue" -> Triple(
                                        Color(0xFFEF4444), // Soft Red
                                        Color(0xFFFEF2F2), // Light Red Bg
                                        "Reminder Overdue"
                                    )
                                    "Completed" -> Triple(
                                        Color(0xFF10B981), // Green
                                        Color(0xFFECFDF5), // Light Green Bg
                                        "Reminder Completed"
                                    )
                                    "Dismissed" -> Triple(
                                        Color(0xFF6B7280), // Grey
                                        Color(0xFFF3F4F6), // Light Grey Bg
                                        "Reminder Dismissed"
                                    )
                                    else -> Triple( // Upcoming
                                        Color(0xFF0D9488), // Fresh Teal/Mint
                                        Color(0xFFF0FDF4), // Light Teal Bg
                                        "Reminder Pending"
                                    )
                                }

                                val finalBg = if (androidx.compose.foundation.isSystemInDarkTheme()) {
                                    semanticColor.copy(alpha = 0.15f)
                                } else {
                                    semanticBg
                                }
                                val finalTextColor = if (androidx.compose.foundation.isSystemInDarkTheme()) {
                                    semanticColor.copy(alpha = 0.9f)
                                } else {
                                    semanticColor
                                }
                                
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    // High-end semantic status block
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(finalBg, RoundedCornerShape(16.dp))
                                            .border(1.dp, finalTextColor.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(finalTextColor.copy(alpha = 0.15f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (reminderStatusComputed == "Overdue") Icons.Default.Warning else Icons.Default.Alarm,
                                                contentDescription = null,
                                                tint = finalTextColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = statusLabelText,
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = finalTextColor
                                            )
                                            Text(
                                                text = "Status: ${lead.reminderStatus}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        ProfileDetailRow(
                                            label = "Reminder Date",
                                            value = formatDateStr(lead.reminderDate),
                                            icon = Icons.Outlined.CalendarToday,
                                            modifier = Modifier.weight(1f)
                                        )
                                        ProfileDetailRow(
                                            label = "Reminder Time",
                                            value = if (lead.reminderTime.isEmpty()) "Not specified" else formatTime12Hour(lead.reminderTime),
                                            icon = Icons.Outlined.AccessTime,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    if (lead.reminderNote.isNotEmpty()) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                                    RoundedCornerShape(12.dp)
                                                )
                                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                                .padding(14.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.padding(bottom = 6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.EditNote,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = "Reminder Note",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Text(
                                                text = lead.reminderNote,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                lineHeight = 20.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // CARD 4: Coaching Notes
                        ProfileSectionCard(
                            title = "Coaching Notes",
                            icon = Icons.Outlined.Assignment,
                            action = if (lead.notes.trim().isNotEmpty()) {
                                {
                                    IconButton(
                                        onClick = { showAddNoteDialog = true },
                                        modifier = Modifier.size(32.dp).testTag("btn_edit_note")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Note",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            } else null
                        ) {
                            if (lead.notes.trim().isEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "No coaching notes added yet.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedButton(
                                        onClick = { showAddNoteDialog = true },
                                        modifier = Modifier.testTag("btn_add_note"),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.EditNote,
                                            contentDescription = "Add Note",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Add Note", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                            RoundedCornerShape(16.dp)
                                        )
                                        .border(
                                            width = 0.5.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FormatQuote,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Text(
                                            text = lead.notes,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                lineHeight = 24.sp,
                                                letterSpacing = 0.25.sp,
                                                fontWeight = FontWeight.Normal
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }

                        // CARD 5: Activity Timeline (Refined Presentation & Chronological Tracking)
                        ProfileSectionCard(title = "Activity Timeline", icon = Icons.Outlined.History) {
                            if (timelineEvents.isEmpty()) {
                                PremiumEmptyState(
                                    icon = Icons.Outlined.History,
                                    title = "No Activity Logged",
                                    description = "Timeline tracking is clean. Actions and updates will populate here."
                                )
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    timelineEvents.forEachIndexed { index, event ->
                                        TimelineRow(
                                            event = event,
                                            isLast = index == timelineEvents.size - 1
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(40.dp))
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Client Profile?") },
            text = { Text("Are you sure you want to permanently delete '${lead.name}'? All data, coaching history, and active reminders will be removed.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteLead(lead)
                        Toast.makeText(context, "Client deleted.", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Focused Add / Edit Reminder Dialog
    if (showAddReminderDialog) {
        ClientAddReminderDialog(
            lead = lead,
            rawDiseases = rawDiseasesList,
            viewModel = viewModel,
            onDismiss = { showAddReminderDialog = false }
        )
    }

    // Focused Add / Edit Coaching Note Dialog
    if (showAddNoteDialog) {
        ClientAddNoteDialog(
            lead = lead,
            rawDiseases = rawDiseasesList,
            viewModel = viewModel,
            onDismiss = { showAddNoteDialog = false }
        )
    }
}

@Composable
fun ProfileActionButton(
    icon: ImageVector? = null,
    painter: Painter? = null,
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.width(60.dp) // responsive sizing to fit 5 actions comfortably
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .shadow(1.dp, CircleShape)
                .background(containerColor, CircleShape)
                .clip(CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (painter != null) {
                Icon(
                    painter = painter,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ProfileSectionCard(
    title: String,
    icon: ImageVector,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = border,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(20.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (action != null) {
                    action()
                }
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            content()
        }
    }
}

@Composable
fun ProfileDetailRow(
    label: String,
    value: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.padding(bottom = 2.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun PremiumEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    tintColor: Color = MaterialTheme.colorScheme.primary
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(tintColor.copy(alpha = 0.08f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tintColor,
                modifier = Modifier.size(26.dp)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

data class TimelineEventItem(
    val title: String,
    val description: String,
    val date: String,
    val time: String,
    val relativeTime: String,
    val icon: ImageVector,
    val color: Color,
    val timestamp: Long
)

@Composable
fun TimelineRow(
    event: TimelineEventItem,
    isLast: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Vertical line & node
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxHeight()
                .width(28.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(event.color.copy(alpha = 0.12f), CircleShape)
                    .border(1.5.dp, event.color, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = event.icon,
                    contentDescription = null,
                    tint = event.color,
                    modifier = Modifier.size(14.dp)
                )
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                )
            }
        }

        // Event Content in Card
        Card(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 12.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            ),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = event.relativeTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = event.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccessTime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "${event.date} at ${event.time}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

// Helper methods
private fun formatDateStr(timestamp: Long): String {
    return SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date(timestamp))
}

private fun formatTimeStr(timestamp: Long): String {
    return SimpleDateFormat("hh:mm a", Locale.US).format(Date(timestamp)).uppercase(Locale.US)
}

private fun formatDateStr(dateStr: String): String {
    if (dateStr.isEmpty()) return ""
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = parser.parse(dateStr) ?: return dateStr
        SimpleDateFormat("MMM dd, yyyy", Locale.US).format(date)
    } catch (e: Exception) {
        dateStr
    }
}

private fun formatTimeStr(timeStr: String): String {
    if (timeStr.isEmpty()) return ""
    return try {
        val parser = SimpleDateFormat("HH:mm", Locale.US)
        val date = parser.parse(timeStr) ?: return timeStr
        SimpleDateFormat("hh:mm a", Locale.US).format(date).uppercase(Locale.US)
    } catch (e: Exception) {
        timeStr
    }
}

private fun formatTime12Hour(timeStr: String): String {
    return formatTimeStr(timeStr)
}

private fun getRelativeTimeString(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    if (diff < 0) return "Just now"
    
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    
    return when {
        seconds < 60 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days == 1L -> "Yesterday"
        days < 30 -> "$days days ago"
        else -> formatDateStr(timestamp)
    }
}

private fun isReminderOverdue(lead: LeadEntity): Boolean {
    if (lead.reminderDate.isEmpty()) return false
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val reminderTimeVal = lead.reminderTime.ifEmpty { "12:00" }
        val date = sdf.parse("${lead.reminderDate} $reminderTimeVal")
        date != null && date.time < System.currentTimeMillis()
    } catch (e: Exception) {
        false
    }
}

@Composable
fun ClientAddReminderDialog(
    lead: LeadEntity,
    rawDiseases: List<String>,
    viewModel: CRMViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var reminderDate by remember { mutableStateOf(lead.reminderDate) }
    var reminderTime by remember { mutableStateOf(lead.reminderTime) }
    var reminderNote by remember { mutableStateOf(lead.reminderNote) }
    var reminderError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AddAlert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = if (lead.reminderDate.isEmpty()) "Set Follow-up Reminder" else "Edit Follow-up Reminder",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Client: ${lead.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Date & Time pickers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Date Picker trigger
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .clickable {
                                val c = Calendar.getInstance()
                                if (reminderDate.isNotEmpty()) {
                                    val parts = reminderDate.split("-")
                                    if (parts.size == 3) {
                                        parts[0].toIntOrNull()?.let { y ->
                                            parts[1].toIntOrNull()?.let { m ->
                                                parts[2].toIntOrNull()?.let { d ->
                                                    c.set(y, m - 1, d)
                                                }
                                            }
                                        }
                                    }
                                }
                                android.app.DatePickerDialog(
                                    context,
                                    { _, y, m, d ->
                                        reminderDate = String.format(Locale.US, "%d-%02d-%02d", y, m + 1, d)
                                        reminderError = null
                                    },
                                    c.get(Calendar.YEAR),
                                    c.get(Calendar.MONTH),
                                    c.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = reminderDate.ifEmpty { "Select Date *" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (reminderDate.isEmpty()) FontWeight.Normal else FontWeight.Bold,
                                    color = if (reminderDate.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (reminderDate.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear Date",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            reminderDate = ""
                                            reminderError = null
                                        }
                                )
                            }
                        }
                    }

                    // Time Picker trigger
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .clickable {
                                val parts = reminderTime.split(":")
                                val initialHour: Int
                                val initialMinute: Int
                                if (parts.size == 2) {
                                    initialHour = parts[0].toIntOrNull() ?: Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                                    initialMinute = parts[1].toIntOrNull() ?: Calendar.getInstance().get(Calendar.MINUTE)
                                } else {
                                    val c = Calendar.getInstance()
                                    initialHour = c.get(Calendar.HOUR_OF_DAY)
                                    initialMinute = c.get(Calendar.MINUTE)
                                }
                                android.app.TimePickerDialog(
                                    context,
                                    { _, h, min ->
                                        reminderTime = String.format(Locale.US, "%02d:%02d", h, min)
                                        reminderError = null
                                    },
                                    initialHour,
                                    initialMinute,
                                    false
                                ).show()
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (reminderTime.isEmpty()) "Time" else formatTime12Hour(reminderTime),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (reminderTime.isEmpty()) FontWeight.Normal else FontWeight.Bold,
                                    color = if (reminderTime.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (reminderTime.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear Time",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            reminderTime = ""
                                            reminderError = null
                                        }
                                )
                            }
                        }
                    }
                }

                if (reminderError != null) {
                    Text(
                        text = reminderError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                // Optional reminder note
                OutlinedTextField(
                    value = reminderNote,
                    onValueChange = { reminderNote = it },
                    label = { Text("Reminder Note (Optional)") },
                    placeholder = { Text("e.g., Call regarding diet plan") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_reminder_note"),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 2,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (reminderDate.isBlank()) {
                        reminderError = "Please select a reminder date"
                        return@Button
                    }
                    isSaving = true
                    coroutineScope.launch {
                        try {
                            val result = viewModel.saveLead(
                                id = lead.id,
                                name = lead.name,
                                mobile = lead.mobile,
                                diseases = rawDiseases,
                                otherDisease = lead.otherDisease,
                                relation = lead.relation,
                                otherRelation = lead.otherRelation,
                                status = lead.status,
                                reminderDate = reminderDate,
                                reminderTime = reminderTime,
                                reminderNote = reminderNote,
                                notes = lead.notes
                            )
                            when (result) {
                                com.example.ui.viewmodel.CRMViewModel.SaveLeadResult.SUCCESS -> {
                                    if (reminderDate.isNotBlank()) {
                                        viewModel.triggerExactAlarmPrompt()
                                    }
                                    Toast.makeText(context, "Reminder saved successfully!", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                                com.example.ui.viewmodel.CRMViewModel.SaveLeadResult.DUPLICATE_REMINDER -> {
                                    reminderError = "A reminder already exists at the selected date and time."
                                    Toast.makeText(context, "Conflict: Select another reminder time.", Toast.LENGTH_LONG).show()
                                }
                                else -> {
                                    Toast.makeText(context, "Reminder could not be saved. Please retry.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } finally {
                            isSaving = false
                        }
                    }
                },
                enabled = !isSaving,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("btn_save_reminder")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Save Reminder")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isSaving,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("btn_cancel_reminder")
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ClientAddNoteDialog(
    lead: LeadEntity,
    rawDiseases: List<String>,
    viewModel: CRMViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var notesText by remember { mutableStateOf(lead.notes) }
    var isSaving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.EditNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = if (lead.notes.isBlank()) "Add Coaching Note" else "Edit Coaching Note",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Client: ${lead.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Coaching Notes") },
                    placeholder = { Text("Enter client consultation notes, wellness goals, dietary observations, etc.") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp, max = 280.dp)
                        .testTag("input_coaching_notes"),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 5
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isSaving = true
                    coroutineScope.launch {
                        try {
                            val result = viewModel.saveLead(
                                id = lead.id,
                                name = lead.name,
                                mobile = lead.mobile,
                                diseases = rawDiseases,
                                otherDisease = lead.otherDisease,
                                relation = lead.relation,
                                otherRelation = lead.otherRelation,
                                status = lead.status,
                                reminderDate = lead.reminderDate,
                                reminderTime = lead.reminderTime,
                                reminderNote = lead.reminderNote,
                                notes = notesText
                            )
                            when (result) {
                                com.example.ui.viewmodel.CRMViewModel.SaveLeadResult.SUCCESS -> {
                                    Toast.makeText(context, "Note saved successfully!", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                                else -> {
                                    Toast.makeText(context, "Note could not be saved. Please retry.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } finally {
                            isSaving = false
                        }
                    }
                },
                enabled = !isSaving,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("btn_save_note")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Save Note")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isSaving,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("btn_cancel_note")
            ) {
                Text("Cancel")
            }
        }
    )
}
