package com.example.ui.screens.review

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.export.ExportManager
import com.example.data.export.ExportMode
import com.example.ui.MainViewModel
import com.example.ui.components.BanglaAwareText
import com.example.ui.theme.CardSurface
import com.example.ui.theme.SoftBlueContainer
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinalReviewScreen(
    lessonId: String,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(lessonId) {
        viewModel.openLesson(lessonId)
    }

    val lesson by viewModel.currentLesson.collectAsState()

    var viewMode by remember { mutableStateOf(ExportMode.USER_WRITING_ONLY) }
    var exportMode by remember { mutableStateOf(ExportMode.USER_WRITING_ONLY) }
    var showExportSheet by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }

    val currentLesson = lesson
    if (currentLesson == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading review...", color = TextMuted)
        }
        return
    }

    if (showExportSheet) {
        ModalBottomSheet(
            onDismissRequest = { showExportSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = "Export Options",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Content to include:",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(6.dp))

                ExportMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = exportMode == mode,
                            onClick = { exportMode = mode }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isExporting) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // PDF Export Button
                        Button(
                            onClick = {
                                isExporting = true
                                coroutineScope.launch {
                                    try {
                                        val uri = ExportManager.generatePdf(context, currentLesson, exportMode)
                                        showExportSheet = false
                                        ExportManager.shareExportedFile(context, uri, "application/pdf", "Share Lesson PDF")
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isExporting = false
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_pdf_button"),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PDF (A4)")
                        }

                        // JPEG Export Button
                        OutlinedButton(
                            onClick = {
                                isExporting = true
                                coroutineScope.launch {
                                    try {
                                        val uris = ExportManager.generateJpegPages(context, currentLesson, exportMode)
                                        showExportSheet = false
                                        if (uris.isNotEmpty()) {
                                            ExportManager.shareExportedFile(context, uris.first(), "image/jpeg", "Share Lesson Image")
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isExporting = false
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_jpeg_button"),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("JPEG")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Copy Text
                        OutlinedButton(
                            onClick = {
                                val text = ExportManager.generateFormattedText(currentLesson, exportMode)
                                ExportManager.copyToClipboard(context, text, currentLesson.title)
                                Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                                showExportSheet = false
                            },
                            modifier = Modifier.weight(1f).testTag("export_copy_button"),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy Text")
                        }

                        // Share Text
                        OutlinedButton(
                            onClick = {
                                val text = ExportManager.generateFormattedText(currentLesson, exportMode)
                                ExportManager.shareText(context, text, currentLesson.title)
                                showExportSheet = false
                            },
                            modifier = Modifier.weight(1f).testTag("export_share_button"),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Final Review",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("review_back_button")) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = { showExportSheet = true },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("review_export_button"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Lesson Header
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SubtleBorder))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Surface(shape = RoundedCornerShape(6.dp), color = SoftBlueContainer) {
                                Text(
                                    text = currentLesson.type,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = "${currentLesson.completedSectionCount}/${currentLesson.totalSectionCount} Completed",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (currentLesson.completedSectionCount == currentLesson.totalSectionCount) SuccessGreen else MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        BanglaAwareText(
                            text = currentLesson.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        currentLesson.titleBn?.let { bn ->
                            if (bn.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                BanglaAwareText(
                                    text = bn,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }

            // View Mode Selector (Tabs)
            item {
                TabRow(
                    selectedTabIndex = viewMode.ordinal,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = viewMode == ExportMode.USER_WRITING_ONLY,
                        onClick = { viewMode = ExportMode.USER_WRITING_ONLY },
                        text = { Text("My Writing") }
                    )
                    Tab(
                        selected = viewMode == ExportMode.MODEL_ANSWERS_ONLY,
                        onClick = { viewMode = ExportMode.MODEL_ANSWERS_ONLY },
                        text = { Text("Model Answers") }
                    )
                    Tab(
                        selected = viewMode == ExportMode.BOTH,
                        onClick = { viewMode = ExportMode.BOTH },
                        text = { Text("Both") }
                    )
                }
            }

            // Checklist from Final Review
            if (currentLesson.finalReview.items.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(10.dp),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SubtleBorder))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Final Review Checklist",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            currentLesson.finalReview.items.forEach { item ->
                                Row(
                                    modifier = Modifier.padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = SuccessGreen,
                                        modifier = Modifier.size(16.dp).padding(top = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    BanglaAwareText(
                                        text = item,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Sections Combined Content
            items(currentLesson.sections, key = { it.id }) { sec ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    shape = RoundedCornerShape(10.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SubtleBorder))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "SECTION ${sec.order}: ${sec.title}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (sec.isCompleted) SoftBlueContainer else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = if (sec.isCompleted) "Done" else "Incomplete",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (sec.isCompleted) MaterialTheme.colorScheme.primary else TextMuted
                                )
                            }
                        }

                        if (sec.task.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Prompt: ${sec.task}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }

                        // User Writing Block
                        if (viewMode == ExportMode.USER_WRITING_ONLY || viewMode == ExportMode.BOTH) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "MY WRITING",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            if (sec.userDraft.isNotBlank()) {
                                BanglaAwareText(
                                    text = sec.userDraft.trim(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary
                                )
                            } else {
                                Text(
                                    text = "(No answer drafted yet for this section)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextMuted,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            }
                        }

                        // Model Answer Block
                        if (viewMode == ExportMode.MODEL_ANSWERS_ONLY || viewMode == ExportMode.BOTH) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "MODEL ANSWER",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            if (sec.modelAnswer.isNotBlank()) {
                                BanglaAwareText(
                                    text = sec.modelAnswer.trim(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            } else {
                                Text(
                                    text = "(No model answer provided)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextMuted,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
