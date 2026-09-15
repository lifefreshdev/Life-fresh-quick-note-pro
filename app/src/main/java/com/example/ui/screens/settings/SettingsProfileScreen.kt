package com.example.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.example.BuildConfig
import com.google.firebase.auth.FirebaseUser

@Composable
fun SettingsProfileScreen(
    currentUser: FirebaseUser?,
    isAnonymous: Boolean,
    isGoogle: Boolean,
    usesPasswordProvider: Boolean,
    displayNameStr: String,
    emailStr: String,
    loginMethodStr: String,
    darkModeEnabled: Boolean,
    onBack: () -> Unit,
    onEditProfileClick: () -> Unit,
    onChangePasswordClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
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
            text = "My Profile",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (currentUser != null) {
            if (isAnonymous) {
                val warningBg = if (darkModeEnabled) Color(0xFF3E2723) else Color(0xFFFFF3E0)
                val warningBorder = if (darkModeEnabled) Color(0xFFD84315) else Color(0xFFFFB74D)
                val warningText = if (darkModeEnabled) Color(0xFFFFCCBC) else Color(0xFFE65100)
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = warningBg
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, warningBorder, RoundedCornerShape(20.dp))
                        .testTag("guest_warning_card")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(warningText.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Warning",
                                tint = warningText,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Guest Mode Active",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = warningText
                            )
                            Text(
                                text = "You are currently exploring as a guest. Your CRM data is stored locally. Sign in with a Google or email account to sync across devices and access cloud backups.",
                                style = MaterialTheme.typography.bodySmall,
                                color = warningText.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }

            // User Info Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth().testTag("profile_info_card")
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = displayNameStr,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("profile_display_name_text")
                            )
                            Text(
                                text = emailStr,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("profile_email_text")
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ProfileDetailRow(
                        icon = Icons.Default.Email,
                        label = "Email Address",
                        value = emailStr,
                        testTag = "profile_row_email"
                    )

                    ProfileDetailRow(
                        icon = Icons.Default.Badge,
                        label = "Display Name",
                        value = displayNameStr,
                        testTag = "profile_row_name"
                    )

                    ProfileDetailRow(
                        icon = Icons.Default.Login,
                        label = "Login Method",
                        value = loginMethodStr,
                        testTag = "profile_row_login_method"
                    )
                }
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth().testTag("profile_actions_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Profile Actions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = onEditProfileClick,
                        modifier = Modifier.fillMaxWidth().height(44.dp).testTag("btn_edit_profile"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Edit Profile", fontWeight = FontWeight.Bold)
                    }

                    if (usesPasswordProvider && !isAnonymous) {
                        OutlinedButton(
                            onClick = onChangePasswordClick,
                            modifier = Modifier.fillMaxWidth().height(44.dp).testTag("btn_change_password"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Change Password", fontWeight = FontWeight.Bold)
                        }
                    } else if (isGoogle) {
                        Text(
                            text = "Password security is managed by your Google Account.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            Text("No logged in profile details available.", style = MaterialTheme.typography.bodyMedium)
        }

        // AI Assistant Configuration (BYOK & Quota Indicator)
        AIAssistantConfigCard()
    }
}
