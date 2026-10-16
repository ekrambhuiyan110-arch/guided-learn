package com.example.ui.screens.study

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.export.ExportManager
import com.example.ui.MainViewModel
import com.example.ui.components.BanglaAwareText
import com.example.ui.components.LessonTableView
import com.example.ui.components.SectionTimerControl
import com.example.ui.components.SegmentedSectionProgressBar
import com.example.ui.components.VocabularySection
import com.example.ui.components.cleanDisplayTitle
import com.example.ui.theme.extendedColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionStudyScreen(
    lessonId: String,
    sectionId: String,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSection: (String, String) -> Unit,
    onNavigateToReview: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(lessonId, sectionId) {
        viewModel.openSection(lessonId, sectionId)
    }

    val lesson by viewModel.currentLesson.collectAsState()
    val section by viewModel.currentSection.collectAsState()
    val defaultTimerMinutes by viewModel.defaultTimerMinutes.collectAsState()
    val showWordCount by viewModel.showWordCount.collectAsState()
    val timerIsRunning by viewModel.timerIsRunning.collectAsState()
    val focusLockEnabled by viewModel.focusLockEnabled.collectAsState()

    var showModelAnswer by remember(sectionId) { mutableStateOf(false) }
    var showWritingArea by remember(sectionId) { mutableStateOf(false) }

    val currentLesson = lesson
    val currentSection = section

    if (currentLesson == null || currentSection == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading section...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val sections = currentLesson.sections
    val currentIndex = sections.indexOfFirst { it.id == currentSection.id }
    val prevSection = if (currentIndex > 0) sections[currentIndex - 1] else null
    val nextSection = if (currentIndex >= 0 && currentIndex < sections.size - 1) sections[currentIndex + 1] else null

    // Determine timer seconds: section timer if > 0, else settings default minutes
    val timerDurationSeconds = if (currentSection.timerSeconds > 0) currentSection.timerSeconds else defaultTimerMinutes * 60

    // Local draft state with debounced auto-saving
    var draftText by remember(currentSection.id) { mutableStateOf(currentSection.userDraft) }
    var saveStatus by remember { mutableStateOf("Saved ✓") }
    val words = draftText.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    val wordCount = if (draftText.isBlank()) 0 else words.size

    // Debounced draft saving (~600ms)
    LaunchedEffect(draftText) {
        if (draftText != currentSection.userDraft) {
            saveStatus = "Saving..."
            delay(600L)
            viewModel.saveDraft(currentSection.id, draftText)
            saveStatus = "Saved ✓"
        }
    }

    // Immediate flush on dispose / leaving section
    DisposableEffect(currentSection.id) {
        onDispose {
            if (draftText != currentSection.userDraft) {
                viewModel.saveDraft(currentSection.id, draftText)
            }
        }
    }

    // Auto-save on system back press without blocking
    BackHandler {
        if (draftText != currentSection.userDraft) {
            viewModel.saveDraft(currentSection.id, draftText)
        }
        onNavigateBack()
    }

    val extColors = MaterialTheme.extendedColors

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                TopAppBar(
                    title = {
                        Column {
                            BanglaAwareText(
                                text = currentSection.title.cleanDisplayTitle(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${currentLesson.title} (${currentSection.order}/${sections.size})",
                                style = MaterialTheme.typography.bodySmall,
                                color = extColors.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (draftText != currentSection.userDraft) {
                                    viewModel.saveDraft(currentSection.id, draftText)
                                }
                                onNavigateBack()
                            },
                            modifier = Modifier.testTag("study_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        // Skip for later action button
                        IconButton(
                            onClick = {
                                if (focusLockEnabled && timerIsRunning) {
                                    Toast.makeText(context, "Focus Lock is active. Finish or pause timer to skip.", Toast.LENGTH_SHORT).show()
                                    return@IconButton
                                }
                                viewModel.recordSectionAction(currentSection.id, currentSection.isCompleted, currentSection.isSkipped)
                                viewModel.markSectionSkipped(currentSection.id)

                                coroutineScope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Section skipped",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.undoLastSectionAction()
                                    }
                                }

                                if (nextSection != null) {
                                    onNavigateToSection(lessonId, nextSection.id)
                                } else {
                                    onNavigateToReview(lessonId)
                                }
                            },
                            modifier = Modifier.testTag("study_skip_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = "Skip for later",
                                tint = extColors.warning
                            )
                        }

                        // Bookmark toggle
                        IconButton(
                            onClick = { viewModel.toggleSectionBookmark(currentSection.id) },
                            modifier = Modifier.testTag("study_bookmark_button")
                        ) {
                            Icon(
                                imageVector = if (currentSection.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "Bookmark Section",
                                tint = if (currentSection.isBookmarked) MaterialTheme.colorScheme.primary else extColors.textMuted
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Segmented progress bar across all sections
                SegmentedSectionProgressBar(
                    totalSections = sections.size,
                    currentIndex = currentIndex,
                    completedIndices = sections.indices.filter { sections[it].isCompleted }.toSet(),
                    skippedIndices = sections.indices.filter { sections[it].isSkipped }.toSet(),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        },
        bottomBar = {
            // Refined Fintech Floating Action Bar: Previous + Primary "Done & Next"
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous Section Button
                    OutlinedButton(
                        onClick = {
                            if (draftText != currentSection.userDraft) {
                                viewModel.saveDraft(currentSection.id, draftText)
                            }
                            prevSection?.let { onNavigateToSection(lessonId, it.id) }
                        },
                        enabled = prevSection != null,
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        modifier = Modifier.testTag("study_prev_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Prev", fontWeight = FontWeight.SemiBold)
                    }

                    // Combined "Done & Next" / "Finish & Review" primary button
                    Button(
                        onClick = {
                            if (focusLockEnabled && timerIsRunning) {
                                Toast.makeText(context, "Focus Lock is active. Complete or pause the timer first.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            if (draftText != currentSection.userDraft) {
                                viewModel.saveDraft(currentSection.id, draftText)
                            }

                            viewModel.recordSectionAction(currentSection.id, currentSection.isCompleted, currentSection.isSkipped)
                            viewModel.markSectionDone(currentSection.id)

                            coroutineScope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = "Section marked done",
                                    actionLabel = "Undo",
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    viewModel.undoLastSectionAction()
                                }
                            }

                            if (nextSection != null) {
                                onNavigateToSection(lessonId, nextSection.id)
                            } else {
                                onNavigateToReview(lessonId)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("study_done_button")
                            .testTag("study_next_button"),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (nextSection != null) "Done & Next" else "Done & Review",
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section Header Card: Clean Title, Focus Type, Completion Status
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = currentSection.type,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            if (currentSection.isCompleted) {
                                Surface(shape = RoundedCornerShape(12.dp), color = extColors.successContainer) {
                                    Text(
                                        text = "Completed",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = extColors.success
                                    )
                                }
                            } else if (currentSection.isSkipped) {
                                Surface(shape = RoundedCornerShape(12.dp), color = extColors.warningContainer) {
                                    Text(
                                        text = "Skipped",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = extColors.warning
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        BanglaAwareText(
                            text = currentSection.title.cleanDisplayTitle(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Practice Countdown Timer (Survives rotation, drift-free)
            if (timerDurationSeconds > 0) {
                item {
                    SectionTimerControl(
                        viewModel = viewModel,
                        initialSeconds = timerDurationSeconds
                    )
                }
            }

            // Task Prompt Card
            if (currentSection.task.isNotBlank()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lightbulb,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Writing Task Prompt",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            BanglaAwareText(
                                text = currentSection.task,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Section Guide & Learning Resources Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Learning Resources & Guide",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        currentSection.guide.paragraphs.forEach { paragraph ->
                            BanglaAwareText(
                                text = paragraph,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        currentSection.guide.bullets.forEach { bullet ->
                            Row(
                                modifier = Modifier.padding(vertical = 3.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                BanglaAwareText(
                                    text = bullet,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Tables
                        currentSection.guide.tables.forEach { table ->
                            Spacer(modifier = Modifier.height(10.dp))
                            LessonTableView(table = table)
                        }

                        // Vocabulary
                        if (currentSection.guide.vocabulary.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            VocabularySection(items = currentSection.guide.vocabulary)
                        }
                    }
                }
            }

            // Action Row: Toggle Buttons for Writing Area and Show Answer side-by-side
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Writing Area Button (Toggles writing area on/off, closed by default)
                        Button(
                            onClick = { showWritingArea = !showWritingArea },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("study_toggle_writing_button"),
                            shape = CircleShape,
                            colors = if (showWritingArea) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                     else ButtonDefaults.filledTonalButtonColors(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (showWritingArea) "Close Writing" else "Writing Area",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Show Answer Button (Toggles model answer on/off)
                        if (currentSection.modelAnswer.isNotBlank()) {
                            Button(
                                onClick = { showModelAnswer = !showModelAnswer },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("toggle_model_answer_button"),
                                shape = CircleShape,
                                colors = if (showModelAnswer) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                         else ButtonDefaults.filledTonalButtonColors(),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = if (showModelAnswer) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (showModelAnswer) "Hide Answer" else "Show Answer",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Writing Area (Closed by default, toggled via Writing Area button)
            if (showWritingArea) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Your Writing Area",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    // Saved indicator
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (saveStatus.contains("✓")) extColors.successContainer else MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = saveStatus,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (saveStatus.contains("✓")) extColors.success else extColors.textMuted
                                        )
                                    }
                                }

                                if (showWordCount) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = "$wordCount words",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = draftText,
                                onValueChange = { newDraft ->
                                    draftText = newDraft
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp)
                                    .testTag("study_writing_input"),
                                placeholder = {
                                    Text(
                                        text = "Compose your answer here...",
                                        color = extColors.textMuted
                                    )
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }
            }

            // Model Answer (Closed by default, toggled via Show Answer button)
            if (showModelAnswer && currentSection.modelAnswer.isNotBlank()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.secondaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lightbulb,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Model Answer",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        ExportManager.copyToClipboard(
                                            context,
                                            currentSection.modelAnswer,
                                            "Model Answer"
                                        )
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy Model Answer",
                                        tint = extColors.textMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            BanglaAwareText(
                                text = currentSection.modelAnswer,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
