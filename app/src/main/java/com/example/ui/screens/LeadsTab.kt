package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.animation.Crossfade
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.data.database.LeadEntity
import com.example.ui.viewmodel.CRMViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LeadsTab(
    viewModel: CRMViewModel,
    onEditLead: (LeadEntity) -> Unit,
    onAddLeadTrigger: () -> Unit,
    onViewLeadProfile: (LeadEntity) -> Unit
) {
    val context = LocalContext.current
    val leads by viewModel.filteredLeads.collectAsStateWithLifecycle()
    val activeFilter by viewModel.currentFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    var showDeleteConfirmDialog by remember { mutableStateOf<LeadEntity?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .testTag("leads_tab"),
            verticalArrangement = Arrangement.spacedBy(16.dp) // Airy 16dp spacing
        ) {
        // Search & Filter controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search Input (Compact, Elegant, follows Design System v1.0)
            val focusRequester = remember { FocusRequester() }
            var isSearchFocused by remember { mutableStateOf(false) }
            
            val searchBorderWidth by androidx.compose.animation.core.animateDpAsState(
                targetValue = if (isSearchFocused) 2.dp else 1.dp,
                label = "border_width"
            )
            val searchBorderColor by androidx.compose.animation.animateColorAsState(
                targetValue = if (isSearchFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                label = "border_color"
            )
            val searchBgColor by androidx.compose.animation.animateColorAsState(
                targetValue = if (isSearchFocused) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                label = "bg_color"
            )
            val searchIconColor by androidx.compose.animation.animateColorAsState(
                targetValue = if (isSearchFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                label = "icon_color"
            )
            val searchElevation by androidx.compose.animation.core.animateDpAsState(
                targetValue = if (isSearchFocused) 4.dp else 0.dp,
                label = "elevation"
            )

            Surface(
                shape = CircleShape,
                color = searchBgColor,
                border = androidx.compose.foundation.BorderStroke(searchBorderWidth, searchBorderColor),
                tonalElevation = searchElevation,
                shadowElevation = searchElevation,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Search Icon",
                        tint = searchIconColor,
                        modifier = Modifier.size(22.dp)
                    )
                    
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = stringResource(R.string.leads_search_placeholder),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1
                            )
                        }
                        androidx.compose.foundation.text.BasicTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.searchQuery.value = it },
                            singleLine = true,
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .onFocusChanged { isSearchFocused = it.isFocused }
                                .testTag("leads_search_field")
                        )
                    }
                    
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.searchQuery.value = "" },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("clear_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cancel,
                                contentDescription = "Clear Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Horizontal Filters Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChipBtn(
                    label = stringResource(R.string.leads_filter_all),
                    active = (activeFilter == "all"),
                    icon = if (activeFilter == "all") Icons.Filled.Group else Icons.Outlined.Group,
                    onClick = { viewModel.currentFilter.value = "all" }
                )
                FilterChipBtn(
                    label = stringResource(R.string.leads_filter_pending),
                    active = (activeFilter == "pending"),
                    icon = if (activeFilter == "pending") Icons.Filled.AccessTime else Icons.Outlined.AccessTime,
                    onClick = { viewModel.currentFilter.value = "pending" }
                )
                FilterChipBtn(
                    label = stringResource(R.string.leads_filter_complete),
                    active = (activeFilter == "complete"),
                    icon = if (activeFilter == "complete") Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                    onClick = { viewModel.currentFilter.value = "complete" }
                )
                FilterChipBtn(
                    label = stringResource(R.string.leads_filter_archived),
                    active = (activeFilter == "archived"),
                    icon = if (activeFilter == "archived") Icons.Filled.Archive else Icons.Outlined.Archive,
                    onClick = { viewModel.currentFilter.value = "archived" }
                )
                FilterChipBtn(
                    label = stringResource(R.string.leads_filter_today),
                    active = (activeFilter == "rem-today"),
                    icon = if (activeFilter == "rem-today") Icons.Filled.Today else Icons.Outlined.Today,
                    onClick = { viewModel.currentFilter.value = "rem-today" }
                )
                FilterChipBtn(
                    label = stringResource(R.string.leads_filter_upcoming),
                    active = (activeFilter == "rem-upcoming"),
                    icon = if (activeFilter == "rem-upcoming") Icons.Filled.CalendarMonth else Icons.Outlined.CalendarMonth,
                    onClick = { viewModel.currentFilter.value = "rem-upcoming" }
                )
                FilterChipBtn(
                    label = stringResource(R.string.leads_filter_overdue),
                    active = (activeFilter == "rem-overdue"),
                    icon = if (activeFilter == "rem-overdue") Icons.Filled.Warning else Icons.Outlined.Warning,
                    onClick = { viewModel.currentFilter.value = "rem-overdue" }
                )
                FilterChipBtn(
                    label = stringResource(R.string.dash_today_reminders),
                    active = (activeFilter == "rem-all"),
                    icon = if (activeFilter == "rem-all") Icons.Filled.Notifications else Icons.Outlined.Notifications,
                    onClick = { viewModel.currentFilter.value = "rem-all" }
                )
            }
        }

        // Leads list or Empty State with Premium Crossfade Animation
        Crossfade(
            targetState = leads,
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 300),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "leads_list_crossfade"
        ) { currentLeads ->
            if (currentLeads.isEmpty()) {
            val isReminderFilter = activeFilter.startsWith("rem-")
            val isSearchMode = searchQuery.isNotEmpty()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.widthIn(max = 400.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(112.dp)
                            .background(
                                color = if (isSearchMode) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
                                else if (isReminderFilter) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f), 
                                CircleShape
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSearchMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .background(
                                    color = if (isSearchMode) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f) else Color.Transparent,
                                    shape = CircleShape
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSearchMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent,
                                    shape = CircleShape
                                )
                        )
                        Icon(
                            imageVector = if (isSearchMode) Icons.Outlined.Search
                            else if (isReminderFilter) {
                                when (activeFilter) {
                                    "rem-today" -> Icons.Outlined.Today
                                    "rem-overdue" -> Icons.Outlined.Warning
                                    "rem-upcoming" -> Icons.Outlined.CalendarMonth
                                    else -> Icons.Outlined.Notifications
                                }
                            } else {
                                Icons.Outlined.Group
                            },
                            contentDescription = "Empty State Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(52.dp)
                        )
                    }
                    
                    Text(
                        text = if (isSearchMode) {
                            "No Matching Clients Found"
                        } else if (isReminderFilter) {
                            when (activeFilter) {
                                "rem-today" -> "No Reminders Today"
                                "rem-overdue" -> "No Overdue Reminders"
                                "rem-upcoming" -> "No Upcoming Reminders"
                                else -> "No Reminders Scheduled"
                            }
                        } else {
                            "No Wellness Clients Found"
                        },
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    
                    Text(
                        text = if (isSearchMode) {
                            "We couldn't find any clients matching \"$searchQuery\". Try checking the spelling, searching by mobile number, or wellness issue."
                        } else if (isReminderFilter) {
                            when (activeFilter) {
                                "rem-today" -> "You're all caught up for today! No wellness follow-ups or alerts are scheduled right now."
                                "rem-overdue" -> "Great job! You have no outstanding or missed reminders. All follow-ups are fully up to date."
                                "rem-upcoming" -> "No upcoming wellness check-ins scheduled. Set custom reminders on client profiles to organize your coaching calendar."
                                else -> "Your coaching schedule is completely clear of alerts. Set a follow-up reminder to keep your clients on track."
                            }
                        } else {
                            "Start tracking your wellness client progress, scheduling reminders, and logging call sessions by creating your first client record."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isSearchMode) {
                        Button(
                            onClick = { viewModel.searchQuery.value = "" },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("clear_search_query_btn")
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Clear Search",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    } else if (isReminderFilter) {
                        Button(
                            onClick = { viewModel.currentFilter.value = "all" },
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.Group, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("View All Clients", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    } else if (activeFilter != "all") {
                        Button(
                            onClick = { viewModel.currentFilter.value = "all" },
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Show All Clients", style = MaterialTheme.typography.labelLarge)
                        }
                    } else {
                        Button(
                            onClick = onAddLeadTrigger,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("Add New Client", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 80.dp), // padding for FAB offset
                verticalArrangement = Arrangement.spacedBy(16.dp) // Consistent spacing of 16dp
            ) {
                items(leads, key = { "${it.ownerUid}_${it.id}" }) { lead ->
                    LeadCardItem(
                        lead = lead,
                        onViewProfile = { onViewLeadProfile(lead) },
                        onCall = {
                            viewModel.markCallInitiated(lead)
                            try {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${lead.mobile}"))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "No dialer application found.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onWhatsApp = {
                            val relationText = if (lead.relation == "Other") "Other (${lead.otherRelation})" else lead.relation
                            val parsedDiseases = try {
                                val arr = JSONArray(lead.diseases)
                                val list = mutableListOf<String>()
                                for (i in 0 until arr.length()) {
                                    val d = arr.getString(i)
                                    list.add(if (d == "Other" && lead.otherDisease.isNotEmpty()) lead.otherDisease else d)
                                }
                                list.joinToString(", ")
                            } catch (e: Exception) {
                                "None"
                            }

                            val summary = """
                                *LifeFresh Quick Note Pro*
                                Name: ${lead.name}
                                Mobile: ${lead.mobile}
                                Relation: $relationText
                                Diseases: $parsedDiseases
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
                        },
                        onCopyText = {
                            val relationText = if (lead.relation == "Other") "Other (${lead.otherRelation})" else lead.relation
                            val parsedDiseases = try {
                                val arr = JSONArray(lead.diseases)
                                val list = mutableListOf<String>()
                                for (i in 0 until arr.length()) {
                                    val d = arr.getString(i)
                                    list.add(if (d == "Other" && lead.otherDisease.isNotEmpty()) lead.otherDisease else d)
                                }
                                list.joinToString(", ")
                            } catch (e: Exception) {
                                "None"
                            }

                            val summary = """
                                *LifeFresh Quick Note Pro*
                                Name: ${lead.name}
                                Mobile: ${lead.mobile}
                                Relation: $relationText
                                Diseases: $parsedDiseases
                                Status: ${lead.status}
                                Reminder Status: ${lead.reminderStatus}
                                Reminder: ${lead.reminderDate.ifEmpty { "None" }}${if (lead.reminderTime.isNotEmpty()) " at ${formatTimeStr(lead.reminderTime)}" else ""}
                                Notes: ${lead.notes.ifEmpty { "N/A" }}
                                Last Call: ${lead.lastCall ?: "Never"}
                            """.trimIndent()

                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("LifeFresh CRM Info", summary)
                            clipboard.setPrimaryClip(clip)
                            coroutineScope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                snackbarHostState.showSnackbar("Lead details copied")
                            }
                        },
                        onEdit = { onEditLead(lead) },
                        onArchiveToggle = {
                            val isArchivedNow = lead.archived
                            viewModel.toggleArchive(lead)
                            coroutineScope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                val message = if (isArchivedNow) "Lead restored" else "Lead archived"
                                val result = snackbarHostState.showSnackbar(
                                    message = message,
                                    actionLabel = "Undo",
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    viewModel.toggleArchive(lead)
                                }
                            }
                        },
                        onDelete = { showDeleteConfirmDialog = lead },
                        onReactivateReminder = {
                            val c = Calendar.getInstance()
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    val selectedDate = String.format(Locale.US, "%d-%02d-%02d", y, m + 1, d)
                                    TimePickerDialog(
                                        context,
                                        { _, h, min ->
                                            val selectedTime = String.format(Locale.US, "%02d:%02d", h, min)
                                            try {
                                                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                                                val selectedDateTime = sdf.parse("$selectedDate $selectedTime")
                                                val now = System.currentTimeMillis()
                                                if (selectedDateTime != null && selectedDateTime.time > now) {
                                                    val success = viewModel.reactivateReminder(lead.id, selectedDate, selectedTime)
                                                    if (success) {
                                                        Toast.makeText(context, "Reminder reactivated for $selectedDate at $selectedTime", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "A reminder already exists at the selected date and time. Please choose a different time.", Toast.LENGTH_LONG).show()
                                                    }
                                                } else {
                                                    Toast.makeText(context, "Error: Selected date/time must be in the future.", Toast.LENGTH_LONG).show()
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Error parsing selected date and time.", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        c.get(Calendar.HOUR_OF_DAY),
                                        c.get(Calendar.MINUTE),
                                        false
                                    ).show()
                                },
                                c.get(Calendar.YEAR),
                                c.get(Calendar.MONTH),
                                c.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }
                    )
                }
            }
        }
    }
}

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .testTag("leads_snackbar_host")
        )
    }

    // Delete confirmation popup dialog
    showDeleteConfirmDialog?.let { lead ->
        var isDeleting by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteConfirmDialog = null },
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = "Delete Lead?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Permanently delete ${lead.name} and their saved CRM details?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!isDeleting) {
                            isDeleting = true
                            viewModel.deleteLead(lead)
                            showDeleteConfirmDialog = null
                            Toast.makeText(context, "Lead deleted", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isDeleting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.testTag("delete_confirm_ok")
                ) {
                    Text("Delete", style = MaterialTheme.typography.labelMedium)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteConfirmDialog = null },
                    enabled = !isDeleting,
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Cancel", style = MaterialTheme.typography.labelMedium)
                }
            }
        )
    }
}

@Composable
fun FilterChipBtn(
    label: String,
    active: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    val containerColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = if (active) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(24.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("filter_chip_${label.replace(" ", "")}")
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = contentColor)
            }
            Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = contentColor)
        }
    }
}

@Composable
fun LeadCardItem(
    lead: LeadEntity,
    onViewProfile: () -> Unit,
    onCall: () -> Unit,
    onWhatsApp: () -> Unit,
    onCopyText: () -> Unit,
    onEdit: () -> Unit,
    onArchiveToggle: () -> Unit,
    onDelete: () -> Unit,
    onReactivateReminder: () -> Unit
) {
    val relationText = remember(lead.relation, lead.otherRelation) {
        if (lead.relation == "Other") "Other (${lead.otherRelation})" else lead.relation
    }
    val parsedDiseases = remember(lead.diseases, lead.otherDisease) {
        try {
            val arr = JSONArray(lead.diseases)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val d = arr.getString(i)
                list.add(if (d == "Other" && lead.otherDisease.isNotEmpty()) lead.otherDisease else d)
            }
            list.joinToString(", ")
        } catch (e: Exception) {
            "None"
        }
    }

    val initials = remember(lead.name) {
        lead.name.split(" ")
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.take(1).uppercase() }
            .let { if (it.isEmpty()) "CL" else it }
    }
    
    val avatarBg = remember(lead.id) {
        val colors = listOf(
            Color(0xFF1B5E20), // Organic Green
            Color(0xFF0F766E), // Teal
            Color(0xFF1E3A8A), // Deep Blue
            Color(0xFF6B21A8), // Purple
            Color(0xFF9A3412)  // Rust Orange
        )
        colors[kotlin.math.abs(lead.id.hashCode() % colors.size)]
    }

    Card(
        onClick = onViewProfile, // Proper M3 clickable ripple and behavior
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
            .testTag("lead_card_${lead.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Circle Initials Avatar as per Design System
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(avatarBg.copy(alpha = 0.12f), CircleShape)
                        .border(1.dp, avatarBg.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        color = avatarBg,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                // Client Identity & Status Badge
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically, 
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Text(
                                text = lead.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (lead.archived) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("Archived", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Lead Status Badge
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (lead.status.lowercase() == "complete") MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color(0xFFD97706).copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (lead.status.lowercase() == "complete") MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color(0xFFD97706).copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = lead.status,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (lead.status.lowercase() == "complete") MaterialTheme.colorScheme.primary else Color(0xFFD97706)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "📞 ${lead.mobile}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Details Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 60.dp), // Align perfectly with textual content start
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Relation and Diseases/Issues
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Relation",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = relationText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Wellness Issues",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = parsedDiseases.ifEmpty { "None" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }

                // Last Call and Notes
                if (!lead.lastCall.isNullOrEmpty() || lead.notes.isNotEmpty()) {
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    
                    if (!lead.lastCall.isNullOrEmpty()) {
                        val formattedLastCall = remember(lead.lastCall) { formatIsoString(lead.lastCall) }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Phone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Last Call: $formattedLastCall",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (lead.notes.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Notes,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = lead.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Reminder section
                if (lead.reminderDate.isNotEmpty()) {
                    val rStatus = lead.reminderStatus
                    val todayStr = remember {
                        val sdf = dateParserThreadLocal.get()!!
                        sdf.format(Date())
                    }
                    
                    // Determine semantic status & colors based on specifications
                    val isOverdue = lead.reminderDate < todayStr && rStatus == "Pending"
                    val reminderStatusComputed = when {
                        rStatus == "Completed" -> "Completed"
                        rStatus == "Dismissed" -> "Dismissed"
                        rStatus == "Missed" -> "Missed"
                        rStatus == "Overdue" || isOverdue -> "Overdue"
                        lead.reminderDate == todayStr -> "Today"
                        else -> "Upcoming"
                    }

                    // Semantic Colors from Design System v1.0
                    val (statusColor, statusBgColor, statusLabelText) = when (reminderStatusComputed) {
                        "Today" -> Triple(
                            Color(0xFF2563EB), // Blue
                            Color(0xFFEFF6FF), // Light Blue Bg
                            "Today"
                        )
                        "Overdue" -> Triple(
                            Color(0xFFEF4444), // Soft Red
                            Color(0xFFFEF2F2), // Light Red Bg
                            "Overdue"
                        )
                        "Missed" -> Triple(
                            Color(0xFFDC2626), // Soft Red
                            Color(0xFFFEF2F2), // Light Red Bg
                            "Missed"
                        )
                        "Completed" -> Triple(
                            Color(0xFF10B981), // Green
                            Color(0xFFECFDF5), // Light Green Bg
                            "Completed"
                        )
                        "Dismissed" -> Triple(
                            Color(0xFF6B7280), // Grey
                            Color(0xFFF3F4F6), // Light Grey Bg
                            "Dismissed"
                        )
                        else -> Triple( // Upcoming
                            Color(0xFF0D9488), // Fresh Teal/Mint
                            Color(0xFFF0FDF4), // Light Teal Bg
                            "Upcoming"
                        )
                    }

                    val finalBgColor = if (androidx.compose.foundation.isSystemInDarkTheme()) {
                        statusColor.copy(alpha = 0.15f)
                    } else {
                        statusBgColor
                    }
                    val finalTextColor = if (androidx.compose.foundation.isSystemInDarkTheme()) {
                        statusColor.copy(alpha = 0.9f)
                    } else {
                        statusColor
                    }

                    val formattedTime = remember(lead.reminderTime) {
                        if (lead.reminderTime.isNotEmpty()) " • ${formatTimeStr(lead.reminderTime)}" else ""
                    }
                    val formattedDate = remember(lead.reminderDate) {
                        formatDateStr(lead.reminderDate)
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Header: Status chip + Date & Time
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Status Chip
                            Box(
                                modifier = Modifier
                                    .background(finalBgColor, RoundedCornerShape(12.dp))
                                    .border(0.5.dp, finalTextColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = when (reminderStatusComputed) {
                                            "Completed" -> Icons.Default.CheckCircle
                                            "Overdue", "Missed" -> Icons.Default.Warning
                                            "Today" -> Icons.Default.Today
                                            "Dismissed" -> Icons.Default.Block
                                            else -> Icons.Default.CalendarMonth
                                        },
                                        contentDescription = null,
                                        tint = finalTextColor,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = statusLabelText,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = finalTextColor
                                    )
                                }
                            }

                            // Date and Time presentation
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AccessTime,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "$formattedDate$formattedTime",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Reminder Note
                        if (lead.reminderNote.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Notes,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = lead.reminderNote,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.5.sp,
                                        lineHeight = 16.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Reactivate Reminder button with proper touch targets
                        if (rStatus == "Dismissed" || rStatus == "Completed" || rStatus == "Overdue" || rStatus == "Missed" || isOverdue) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                OutlinedButton(
                                    onClick = onReactivateReminder,
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    shape = RoundedCornerShape(24.dp),
                                    modifier = Modifier
                                        .height(44.dp)
                                        .testTag("reactivate_reminder_btn_${lead.id}")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "Reactivate Reminder",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // Action Buttons Row with expanded touch targets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Call Action Button
                    FilledIconButton(
                        onClick = onCall,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("action_call_${lead.id}")
                    ) {
                        Icon(Icons.Outlined.Phone, contentDescription = "Call ${lead.name}", modifier = Modifier.size(18.dp))
                    }

                    // WhatsApp Action Button
                    FilledIconButton(
                        onClick = onWhatsApp,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color(0xFF25D366).copy(alpha = 0.15f),
                            contentColor = Color(0xFF128C7E)
                        ),
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("action_whatsapp_${lead.id}")
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_whatsapp),
                            contentDescription = "WhatsApp ${lead.name}",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Edit Action Button
                    FilledIconButton(
                        onClick = onEdit,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("action_edit_${lead.id}")
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit ${lead.name}", modifier = Modifier.size(18.dp))
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Overflow Menu Button
                    var showOverflowMenu by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.wrapContentSize()) {
                        IconButton(
                            onClick = { showOverflowMenu = true },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("action_more_${lead.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More actions for ${lead.name}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Copy Lead Details") },
                                onClick = {
                                    showOverflowMenu = false
                                    onCopyText()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                modifier = Modifier.testTag("action_copy_${lead.id}")
                            )

                            DropdownMenuItem(
                                text = { Text(if (lead.archived) "Restore Lead" else "Archive Lead") },
                                onClick = {
                                    showOverflowMenu = false
                                    onArchiveToggle()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (lead.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                modifier = Modifier.testTag("action_archive_${lead.id}")
                            )

                            DropdownMenuItem(
                                text = { Text("Delete Lead") },
                                onClick = {
                                    showOverflowMenu = false
                                    onDelete()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                modifier = Modifier.testTag("action_delete_${lead.id}")
                            )
                        }
                    }
                }
            }
        }
    }
}

// Helpers
private val isoParserThreadLocal = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue(): SimpleDateFormat {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        parser.timeZone = TimeZone.getTimeZone("UTC")
        return parser
    }
}

private val isoFormatterThreadLocal = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue(): SimpleDateFormat {
        return SimpleDateFormat("dd-MMM-yyyy HH:mm", Locale.getDefault())
    }
}

private val dateParserThreadLocal = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue(): SimpleDateFormat {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }
}

private val dateFormatterThreadLocal = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue(): SimpleDateFormat {
        return SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    }
}

private val timeParserThreadLocal = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue(): SimpleDateFormat {
        return SimpleDateFormat("HH:mm", Locale.US)
    }
}

private val timeFormatterThreadLocal = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue(): SimpleDateFormat {
        return SimpleDateFormat("hh:mm a", Locale.US)
    }
}

private fun formatIsoString(isoString: String): String {
    return try {
        val parser = isoParserThreadLocal.get()!!
        val date = parser.parse(isoString) ?: return ""
        val formatter = isoFormatterThreadLocal.get()!!
        formatter.format(date)
    } catch (e: Exception) {
        isoString
    }
}

private fun formatDateStr(dateStr: String): String {
    return try {
        val parser = dateParserThreadLocal.get()!!
        val date = parser.parse(dateStr) ?: return dateStr
        val formatter = dateFormatterThreadLocal.get()!!
        formatter.format(date)
    } catch (e: Exception) {
        dateStr
    }
}

private fun formatTimeStr(timeStr: String): String {
    return try {
        val parser = timeParserThreadLocal.get()!!
        val date = parser.parse(timeStr) ?: return timeStr
        val formatter = timeFormatterThreadLocal.get()!!
        formatter.format(date).uppercase(Locale.US)
    } catch (e: Exception) {
        timeStr
    }
}
