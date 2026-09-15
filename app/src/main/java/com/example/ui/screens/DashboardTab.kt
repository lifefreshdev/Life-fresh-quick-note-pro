package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.data.database.LeadEntity
import com.example.ui.viewmodel.CRMViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardTab(
    viewModel: CRMViewModel,
    authViewModel: com.example.ui.viewmodel.AuthViewModel,
    onViewLeadProfile: (LeadEntity) -> Unit,
    onOpenAIAssistant: () -> Unit = {}
) {
    val allLeads by viewModel.allLeadsList.collectAsStateWithLifecycle()
    val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()

    // Filter out archived for analytic calculations
    val activeLeads = remember(allLeads) { allLeads.filter { !it.archived } }

    val total = activeLeads.size
    val pending = activeLeads.count { it.status.equals("Pending", ignoreCase = true) }
    val complete = activeLeads.count { it.status.equals("Complete", ignoreCase = true) }

    // Calc reminders
    var remToday = 0
    var remUpcoming = 0
    var remOverdue = 0

    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }

    activeLeads.forEach { lead ->
        if (lead.reminderDate.isNotEmpty()) {
            if (lead.reminderStatus == "Overdue") {
                remOverdue++
            } else if (lead.reminderStatus == "Pending") {
                val cat = when {
                    lead.reminderDate == todayStr -> "today"
                    lead.reminderDate < todayStr -> "overdue"
                    else -> "upcoming"
                }
                when (cat) {
                    "today" -> remToday++
                    "upcoming" -> remUpcoming++
                    "overdue" -> remOverdue++
                }
            }
        }
    }

    // Dynamic, accessible theme-aware colors
    val isDark = isSystemInDarkTheme()
    val pendingColor = if (isDark) Color(0xFFFFB74D) else Color(0xFFD97706)
    val completeColor = MaterialTheme.colorScheme.primary
    val overdueColor = MaterialTheme.colorScheme.error

    // Subtle fade-in and scale animation when tab is displayed
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }
    val scaleState by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.96f,
        animationSpec = tween(durationMillis = 650),
        label = "scale"
    )
    val alphaState by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 650),
        label = "alpha"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .graphicsLayer(
                    scaleX = scaleState,
                    scaleY = scaleState,
                    alpha = alphaState
                )
                .testTag("dashboard_tab"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Personalized Welcome Section
            currentUser?.let { user ->
                val isAnonymous = user.isAnonymous
                val displayName = when {
                    isAnonymous -> "Guest"
                    !user.displayName.isNullOrBlank() -> user.displayName ?: "User"
                    !user.email.isNullOrBlank() -> user.email ?: "User"
                    else -> "Pro Member"
                }

                Card(
                    shape = RoundedCornerShape(20.dp), // Cards 20dp as per Design System
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.5.dp,
                            Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                                )
                            ),
                            RoundedCornerShape(20.dp)
                        )
                        .testTag("dashboard_welcome_card")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp), // On 8dp grid
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.secondary
                                        )
                                    ),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isAnonymous) "G" else displayName.take(1).uppercase(Locale.getDefault()),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (isAnonymous) "Welcome, Guest 👋" else "Welcome back, $displayName 👋",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 20.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp)) // On 8dp grid
                            Text(
                                text = "Empowering health and wellness journeys",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // Overview Section Card
            Card(
                shape = RoundedCornerShape(20.dp), // Cards 20dp
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp), // On 8dp grid
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.TrendingUp, // Outlined as per Design System
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Overview",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Stats Grid (Equal spacing, uniform heights)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(8.dp) // On 8dp grid
                    ) {
                        StatCard(
                            value = total.toString(),
                            label = stringResource(R.string.dash_total_leads),
                            icon = Icons.Outlined.Group, // Outlined
                            color = completeColor,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dashboard_stat_total")
                        )
                        StatCard(
                            value = pending.toString(),
                            label = stringResource(R.string.dash_pending),
                            icon = Icons.Outlined.AccessTime, // Outlined
                            color = pendingColor,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dashboard_stat_pending")
                        )
                        StatCard(
                            value = complete.toString(),
                            label = stringResource(R.string.dash_completed),
                            icon = Icons.Outlined.CheckCircle, // Outlined
                            color = completeColor,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dashboard_stat_complete")
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Doughnut Chart via Canvas drawings
                    if (total > 0) {
                        val ratio = if (total > 0) (complete * 100 / total) else 0
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics(mergeDescendants = true) {
                                    contentDescription = "Completion progress: $ratio% ($complete of $total completed)"
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(110.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                DoughnutChart(pending = pending, complete = complete)

                                Text(
                                    text = "$ratio%",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Text(
                                text = "$complete of $total completed",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Group,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = "No leads yet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // Reminders Summary Card
            Card(
                shape = RoundedCornerShape(20.dp), // Cards 20dp
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp), // On 8dp grid
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Alarm, // Outlined
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Reminders Summary",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(8.dp) // On 8dp grid
                    ) {
                        ReminderStatCard(
                            value = remToday.toString(),
                            label = stringResource(R.string.leads_filter_today),
                            icon = Icons.Outlined.Today, // Outlined
                            color = Color(0xFF2563EB), // Unified Blue for Today
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dashboard_rem_today")
                        )
                        ReminderStatCard(
                            value = remUpcoming.toString(),
                            label = stringResource(R.string.leads_filter_upcoming),
                            icon = Icons.Outlined.DateRange, // Outlined
                            color = Color(0xFF0D9488), // Unified Teal for Upcoming
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dashboard_rem_upcoming")
                        )
                        ReminderStatCard(
                            value = remOverdue.toString(),
                            label = stringResource(R.string.leads_filter_overdue),
                            icon = Icons.Outlined.Warning, // Outlined
                            color = MaterialTheme.colorScheme.error, // Unified Soft Red for Overdue
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dashboard_rem_overdue")
                        )
                    }
                }
            }

            // Recent Activity List Header
            Text(
                text = "Recent Activity Log",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, top = 16.dp) // Large top spacing to breathe
            )

            val recentLeads = remember(activeLeads) { activeLeads.take(3) }

            if (recentLeads.isEmpty()) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "No Recent Activity",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Create or edit wellness client records to see activity updates logged here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                recentLeads.forEach { lead ->
                    RecentActivityItem(
                        lead = lead,
                        pendingColor = pendingColor,
                        completeColor = completeColor,
                        onClick = { onViewLeadProfile(lead) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun StatCard(
    value: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp), // Harmonious internal sub-card radius
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
            .heightIn(min = 100.dp)
            .fillMaxHeight()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                RoundedCornerShape(16.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(color.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = color,
                    fontSize = 22.sp,
                    lineHeight = 26.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = label.uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    letterSpacing = 0.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
fun ReminderStatCard(
    value: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp), // Sub-card radius
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
            .heightIn(min = 100.dp)
            .fillMaxHeight()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                RoundedCornerShape(16.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = color,
                fontSize = 22.sp,
                lineHeight = 26.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

@Composable
fun RecentActivityItem(
    lead: LeadEntity,
    pendingColor: Color,
    completeColor: Color,
    onClick: () -> Unit
) {
    val initials = remember(lead.name) {
        lead.name.split(" ")
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.take(1).uppercase() }
            .let { if (it.isEmpty()) "CL" else it }
    }
    
    val avatarBg = remember(lead.id) {
        val colors = listOf(
            Color(0xFF1B5E20), // Dark green
            Color(0xFF2E7D32), // Forest green
            Color(0xFF37474F), // Blue grey
            Color(0xFF006064), // Cyan dark
            Color(0xFF4E342E)  // Brown
        )
        colors[kotlin.math.abs(lead.id.hashCode() % colors.size)]
    }

    val relativeTime = remember(lead.timestamp) { getRelativeTimeString(lead.timestamp) }
    val badgeColor = if (lead.status.lowercase() == "complete") completeColor else pendingColor

    OutlinedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() }
            .testTag("recent_activity_item_${lead.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Circle Initials
            Box(
                modifier = Modifier
                    .size(44.dp)
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

            // Info Details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = lead.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp
                    )
                    Text(
                        text = relativeTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = lead.mobile.ifEmpty { "No phone number" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }

            // Status Badge
            Box(
                modifier = Modifier
                    .background(
                        color = badgeColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = badgeColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (lead.status.lowercase() == "complete") "Complete" else "Pending",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor
                )
            }
        }
    }
}

@Composable
fun DoughnutChart(pending: Int, complete: Int) {
    val isDark = isSystemInDarkTheme()
    val pendingColor = if (isDark) Color(0xFFFFB74D) else Color(0xFFD97706)
    val completeColor = MaterialTheme.colorScheme.primary

    val pendingArc = if (pending + complete > 0) (pending.toFloat() / (pending + complete)) * 360f else 0f
    val completeArc = if (pending + complete > 0) (complete.toFloat() / (pending + complete)) * 360f else 0f

    Canvas(
        modifier = Modifier
            .size(110.dp)
            .testTag("dashboard_chart_canvas")
    ) {
        val strokeWidthVal = 16.dp.toPx()
        val arcSize = size.height - strokeWidthVal

        // Sweep from top (-90 degrees)
        if (pending + complete == 0) {
            drawArc(
                color = Color.LightGray.copy(alpha = 0.3f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidthVal),
                size = Size(arcSize, arcSize),
                topLeft = androidx.compose.ui.geometry.Offset(strokeWidthVal / 2, strokeWidthVal / 2)
            )
        } else {
            // Draw Pending
            drawArc(
                color = pendingColor,
                startAngle = -90f,
                sweepAngle = pendingArc,
                useCenter = false,
                style = Stroke(width = strokeWidthVal, cap = StrokeCap.Round),
                size = Size(arcSize, arcSize),
                topLeft = androidx.compose.ui.geometry.Offset(strokeWidthVal / 2, strokeWidthVal / 2)
            )

            // Draw Complete
            drawArc(
                color = completeColor,
                startAngle = -90f + pendingArc,
                sweepAngle = completeArc,
                useCenter = false,
                style = Stroke(width = strokeWidthVal, cap = StrokeCap.Round),
                size = Size(arcSize, arcSize),
                topLeft = androidx.compose.ui.geometry.Offset(strokeWidthVal / 2, strokeWidthVal / 2)
            )
        }
    }
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
        else -> {
            val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}
