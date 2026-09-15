package com.example.ui.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.database.LeadEntity
import com.example.pdf.PdfGenerator
import com.example.ui.viewmodel.CRMViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.FileInputStream
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsTab(viewModel: CRMViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val allLeads by viewModel.allLeadsList.collectAsStateWithLifecycle()
    val activeLeads = remember(allLeads) { allLeads.filter { !it.archived } }

    var isDownloading by remember { mutableStateOf(false) }
    var isSharing by remember { mutableStateOf(false) }
    var showInstructionsGuide by remember { mutableStateOf(false) }

    val onDownloadPDF = {
        if (activeLeads.isEmpty()) {
            Toast.makeText(context, "No active leads available to generate a report.", Toast.LENGTH_SHORT).show()
        } else if (!isDownloading && !isSharing) {
            isDownloading = true
            coroutineScope.launch {
                try {
                    val tempFile = withContext(Dispatchers.IO) {
                        PdfGenerator.generateCrmReport(context, activeLeads)
                    }
                    
                    val displayName = "LifeFresh_CRM_Report_${System.currentTimeMillis() / 1000}.pdf"
                    val savedUri = withContext(Dispatchers.IO) {
                        savePdfToDownloads(context, tempFile, displayName)
                    }
                    
                    if (savedUri != null) {
                        val locationInfo = if (savedUri.scheme == "content") "Downloads" else savedUri.path ?: "Downloads"
                        Toast.makeText(context, "Report downloaded successfully to $locationInfo", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Failed to save PDF report to Downloads.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error generating report: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                } finally {
                    isDownloading = false
                }
            }
        }
    }

    val onSharePDF = {
        if (activeLeads.isEmpty()) {
            Toast.makeText(context, "No active leads available to generate a report.", Toast.LENGTH_SHORT).show()
        } else if (!isDownloading && !isSharing) {
            isSharing = true
            coroutineScope.launch {
                try {
                    val tempFile = withContext(Dispatchers.IO) {
                        PdfGenerator.generateCrmReport(context, activeLeads)
                    }
                    
                    val authority = "${context.packageName}.fileprovider"
                    val fileUri: Uri = FileProvider.getUriForFile(context, authority, tempFile)
                    
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, fileUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    
                    val chooser = Intent.createChooser(shareIntent, "Share LifeFresh PDF Report")
                    context.startActivity(chooser)
                    Toast.makeText(context, "Report ready to share", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error sharing report: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                } finally {
                    isSharing = false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("reports_tab"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // PDF Section Hero Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(64.dp)
                )

                Text(
                    text = "CRM Report",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Generate a professional PDF summary of your leads and CRM activity.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Action 1: Download Report
                Button(
                    onClick = onDownloadPDF,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("reports_download_pdf_btn"),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isDownloading && !isSharing
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Preparing your PDF report...", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Download Report", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Action 2: Share PDF
                OutlinedButton(
                    onClick = onSharePDF,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("reports_share_pdf_btn"),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isDownloading && !isSharing
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSharing) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Preparing your PDF report...", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Share PDF", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Compact Instructions Entry Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showInstructionsGuide = true }
                .testTag("reports_instructions_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "How PDF reports work",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Learn what is included and where your report is saved.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Open PDF report guide",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Space out bottom
        Spacer(modifier = Modifier.height(24.dp))
    }

    // PDF Report Guide Bottom Sheet
    if (showInstructionsGuide) {
        ModalBottomSheet(
            onDismissRequest = { showInstructionsGuide = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            modifier = Modifier.testTag("dialog_pdf_instructions")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Sheet Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "PDF Report Guide",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = { showInstructionsGuide = false },
                        modifier = Modifier.testTag("btn_close_instructions")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close guide",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // Section 1: What is included
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "📋 What the Report Includes",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Contains active client profiles, mobile contacts, wellness notes, and follow-up schedules. Archived leads are strictly excluded to maintain a clean business summary.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }

                // Section 2: How to save or share
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "📱 How to Save or Share PDF",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "1. Tap \"Download Report\" to directly save the file into your device's Downloads folder.\n" +
                               "2. Tap \"Share PDF\" to send the report instantly via WhatsApp, Email, or other platforms.\n" +
                               "3. Choose print or save destinations directly from the standard system share sheet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }

                // Section 3: Save location
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "📂 Default Save Location",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Report files are stored in your device's Downloads folder (Files app > Downloads, or Internal Storage > Download).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }

                // Section 4: Can't find file
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "🔍 Can't Find the PDF?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Open your device's Files app and search for \".pdf\" or \"LifeFresh_CRM_Report\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }

                // Section 5: Important note
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "⚠️ Important Note",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "To include archived clients in reports, restore them temporarily from the Leads tab first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 17.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { showInstructionsGuide = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Close", style = MaterialTheme.typography.labelLarge, color = Color.White)
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

fun savePdfToDownloads(context: Context, srcFile: File, displayName: String): Uri? {
    val contentResolver = context.contentResolver
    
    // Strategy 1: MediaStore for Android Q (API 29) and above
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download")
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    FileInputStream(srcFile).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                return uri
            }
        } catch (e: Exception) {
            android.util.Log.e("PdfSave", "MediaStore insert failed, trying legacy fallback", e)
        }
    }
    
    // Strategy 2: Legacy public external Downloads folder (Pre-Q or as fallback)
    try {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (downloadsDir != null) {
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            val destFile = File(downloadsDir, displayName)
            FileInputStream(srcFile).use { inputStream ->
                FileOutputStream(destFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            return Uri.fromFile(destFile)
        }
    } catch (e: Exception) {
        android.util.Log.e("PdfSave", "Legacy external storage copy failed, trying app folder fallback", e)
    }

    // Strategy 3: Guaranteed success fallback - App-specific external download folder (No permissions needed)
    try {
        val appDownloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (appDownloadsDir != null) {
            if (!appDownloadsDir.exists()) {
                appDownloadsDir.mkdirs()
            }
            val destFile = File(appDownloadsDir, displayName)
            FileInputStream(srcFile).use { inputStream ->
                FileOutputStream(destFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            return Uri.fromFile(destFile)
        }
    } catch (e: Exception) {
        android.util.Log.e("PdfSave", "App-specific external storage copy failed", e)
    }

    return null
}
