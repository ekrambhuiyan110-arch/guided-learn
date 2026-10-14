package com.example.ui.screens.lesson_detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Lesson
import com.example.data.model.LessonSection
import com.example.ui.MainViewModel
import com.example.ui.components.BanglaAwareText
import com.example.ui.components.LessonProgressIndicator
import com.example.ui.components.LessonTableView
import com.example.ui.theme.CardSurface
import com.example.ui.theme.SoftBlueContainer
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.SuccessBg
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningBg

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonDetailScreen(
    lessonId: String,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSection: (String, String) -> Unit,
    onNavigateToReview: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(lessonId) {
        viewModel.openLesson(lessonId)
    }

    val lesson by viewModel.currentLesson.collectAsState()
    var isGuidelinesExpanded by remember { mutableStateOf(true) }
    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Progress?") },
            text = { Text("This will reset all section completions and timers for this lesson. Saved drafts will be kept safely unless deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetLessonProgress(lessonId)
                        showResetDialog = false
                    }
                ) {
                    Text("Reset Progress", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Lesson Overview",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.toggleLessonBookmark(lessonId) },
                        modifier = Modifier.testTag("detail_bookmark_button")
                    ) {
                        Icon(
                            imageVector = if (lesson?.isBookmarked == true) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Bookmark Lesson",
                            tint = if (lesson?.isBookmarked == true) MaterialTheme.colorScheme.primary else TextMuted
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        val currentLesson = lesson
        if (currentLesson == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Loading lesson...", color = TextMuted)
            }
            return@Scaffold
        }

        val nextSection = viewModel.continueLesson(currentLesson)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header card
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
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SoftBlueContainer
                            ) {
                                Text(
                                    text = currentLesson.type,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "~${currentLesson.estimatedMinutes} min",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
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
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }

                        if (currentLesson.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = currentLesson.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        LessonProgressIndicator(
                            completed = currentLesson.completedSectionCount,
                            total = currentLesson.totalSectionCount
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Primary action row: Continue, Reset, Review
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (nextSection != null) {
                                Button(
                                    onClick = { onNavigateToSection(currentLesson.id, nextSection.id) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("detail_continue_button"),
                                    contentPadding = PaddingValues(vertical = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        if (currentLesson.completedSectionCount == 0) "Start Learning"
                                        else "Continue (${nextSection.order}/${currentLesson.totalSectionCount})"
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = { onNavigateToReview(currentLesson.id) },
                                modifier = Modifier.testTag("detail_review_button"),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Review")
                            }

                            IconButton(
                                onClick = { showResetDialog = true },
                                modifier = Modifier.testTag("detail_reset_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reset Progress",
                                    tint = TextMuted
                                )
                            }
                        }
                    }
                }
            }

            // General Guidelines Section (Collapsible)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SubtleBorder))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isGuidelinesExpanded = !isGuidelinesExpanded },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "General Guidelines",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = if (isGuidelinesExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Toggle Guidelines",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        AnimatedVisibility(visible = isGuidelinesExpanded) {
                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                currentLesson.guidelines.paragraphs.forEach { paragraph ->
                                    BanglaAwareText(
                                        text = paragraph,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                currentLesson.guidelines.bullets.forEach { bullet ->
                                    Row(
                                        modifier = Modifier.padding(vertical = 3.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = "•",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                        BanglaAwareText(
                                            text = bullet,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextSecondary
                                        )
                                    }
                                }

                                currentLesson.guidelines.tables.forEach { table ->
                                    Spacer(modifier = Modifier.height(10.dp))
                                    LessonTableView(table = table)
                                }
                            }
                        }
                    }
                }
            }

            // Sections List Header
            item {
                Text(
                    text = "Sections (${currentLesson.totalSectionCount})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            }

            // Sections List Items
            items(currentLesson.sections, key = { it.id }) { section ->
                SectionListItemCard(
                    section = section,
                    onClick = { onNavigateToSection(currentLesson.id, section.id) }
                )
            }
        }
    }
}

@Composable
fun SectionListItemCard(
    section: LessonSection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusBg = when {
        section.isCompleted -> SuccessBg
        section.isSkipped -> WarningBg
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val statusColor = when {
        section.isCompleted -> SuccessGreen
        section.isSkipped -> WarningAmber
        else -> TextMuted
    }

    val statusText = when {
        section.isCompleted -> "Completed"
        section.isSkipped -> "Skipped"
        section.userDraft.isNotBlank() -> "In Progress"
        else -> "Not Started"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("section_card_${section.order}")
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(10.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SubtleBorder))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status Icon Circle
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(statusBg),
                contentAlignment = Alignment.Center
            ) {
                if (section.isCompleted) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Completed",
                        tint = SuccessGreen,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        text = "${section.order}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BanglaAwareText(
                        text = section.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = statusBg
                    ) {
                        Text(
                            text = statusText,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = statusColor
                        )
                    }
                }

                if (section.task.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = section.task,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (section.userDraft.isNotBlank()) {
                    val wordCount = section.userDraft.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Draft: $wordCount words saved",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Open Section",
                tint = TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
