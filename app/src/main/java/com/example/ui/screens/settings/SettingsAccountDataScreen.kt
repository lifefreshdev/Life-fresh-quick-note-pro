package com.example.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsAccountDataScreen(
    isGuest: Boolean,
    displayNameStr: String,
    emailStr: String,
    onBack: () -> Unit,
    onRequestAccountDeletion: () -> Unit,
    onGuestNotice: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("btn_back_to_settings")
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back to Settings"
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Account & Data",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Account Summary Card
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isGuest) Icons.Default.PersonOutline else Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = displayNameStr,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isGuest) "Guest Session (Local Storage Only)" else emailStr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 2. Account Deletion Policy Guide Card
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Account Deletion Policy",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                AccountPolicyItem(
                    icon = Icons.Default.HourglassTop,
                    title = "7-Day Grace Period",
                    description = "Account deletion uses a 7-day grace period. When requested, your account is queued for deletion rather than deleted immediately."
                )

                AccountPolicyItem(
                    icon = Icons.Default.LockReset,
                    title = "Reactivation on Sign-In",
                    description = "During those 7 days, if you sign in again with the same account, the deletion request is cancelled and your existing account and data remain fully available."
                )

                AccountPolicyItem(
                    icon = Icons.Default.DeleteSweep,
                    title = "Permanent Deletion After 7 Days",
                    description = "After 7 days, the account and its related cloud data are permanently deleted from our systems."
                )

                AccountPolicyItem(
                    icon = Icons.Default.PersonAddAlt,
                    title = "Fresh Account Sign-In",
                    description = "If you sign in again after permanent deletion, it will be treated as a fresh, brand-new account."
                )
            }
        }

        // 3. Danger Zone Section
        val isDarkDanger = isSystemInDarkTheme()
        val dangerCardBg = if (isDarkDanger) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.12f)
        } else {
            Color(0xFFFEF2F2)
        }
        val dangerCardBorderColor = if (isDarkDanger) {
            MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
        } else {
            Color(0xFFFECACA)
        }
        val dangerPrimaryColor = if (isDarkDanger) {
            MaterialTheme.colorScheme.error
        } else {
            Color(0xFFDC2626)
        }
        val dangerTextColor = if (isDarkDanger) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            Color(0xFF4A1515)
        }
        val dangerButtonBg = if (isDarkDanger) {
            MaterialTheme.colorScheme.error
        } else {
            Color(0xFFDC2626)
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = dangerCardBg
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    BorderStroke(1.dp, dangerCardBorderColor),
                    RoundedCornerShape(20.dp)
                )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = dangerPrimaryColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Danger Zone",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = dangerPrimaryColor
                    )
                }

                Text(
                    text = "Requesting account deletion begins a 7-day grace period. You can sign in anytime during the 7 days to cancel the request.",
                    style = MaterialTheme.typography.bodySmall,
                    color = dangerTextColor
                )

                Button(
                    onClick = {
                        if (isGuest) {
                            onGuestNotice()
                        } else {
                            onRequestAccountDeletion()
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = dangerButtonBg,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("btn_request_account_deletion")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Request Account Deletion",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
