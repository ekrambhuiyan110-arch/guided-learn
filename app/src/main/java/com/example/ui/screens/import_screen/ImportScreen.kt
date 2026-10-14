package com.example.ui.screens.import_screen

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.export.ExportManager
import com.example.data.model.IssueSeverity
import com.example.data.model.ParsedLessonPreview
import com.example.ui.MainViewModel
import com.example.ui.components.BanglaAwareText
import com.example.ui.theme.AcademicNavy
import com.example.ui.theme.CardSurface
import com.example.ui.theme.ErrorBg
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SoftBlueContainer
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.SuccessBg
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningBg

const val DEFAULT_GOOGLE_DOCS_URL = "https://docs.google.com/document/d/1zhgcWZzjGVmWXnMiZTRo2y-5Ncw6Mid0r7WLvVTJoq8/edit?usp=sharing"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onImportSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val previews by viewModel.importPreviews.collectAsState()
    val importError by viewModel.importError.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("File", "Google Docs", "Paste Text", "AI Guidelines")

    var googleDocsUrl by remember { mutableStateOf(DEFAULT_GOOGLE_DOCS_URL) }
    var rawTextContent by remember { mutableStateOf("") }

    // AI Prompt Builder State
    var aiTopicInput by remember { mutableStateOf("Inflationary Pressure and Monetary Policy in Bangladesh") }
    var aiWritingType by remember { mutableStateOf("Focus Writing") }
    var aiSectionCount by remember { mutableIntStateOf(3) }

    // SAF File Picker
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val contentResolver = context.contentResolver
                val type = contentResolver.getType(uri) ?: ""
                val fileName = uri.lastPathSegment ?: ""
                val isDocx = type.contains("wordprocessingml") || fileName.endsWith(".docx", ignoreCase = true)

                if (isDocx) {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        viewModel.parseDocxForImport(stream)
                    }
                } else {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        val text = stream.bufferedReader().readText()
                        viewModel.parseTextForImport(text)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error opening file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Import Lesson Resources",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("import_back_button")) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            // Source Tabs
            item {
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = {
                                selectedTabIndex = index
                                viewModel.clearImportPreview()
                            },
                            text = { Text(title, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }
            }

            // Tab 0: File Picker (DOCX, TXT, JSON)
            if (selectedTabIndex == 0) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardSurface),
                        shape = RoundedCornerShape(12.dp),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SubtleBorder))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Select Lesson File",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Supports Microsoft Word (.docx), Plain Text (.txt), and Topic Packs (.json).",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    filePickerLauncher.launch(
                                        arrayOf(
                                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                            "text/plain",
                                            "application/json",
                                            "*/*"
                                        )
                                    )
                                },
                                modifier = Modifier.testTag("import_file_picker_button")
                            ) {
                                Icon(imageVector = Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Choose File")
                            }
                        }
                    }
                }
            }

            // Tab 1: Google Docs URL
            if (selectedTabIndex == 1) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardSurface),
                        shape = RoundedCornerShape(12.dp),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SubtleBorder))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Official Shared Google Doc",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Fixed link • Auto-detects 3 full lessons (English & বাংলা)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "The backend automatically searches and extracts all lessons across this shared link (Inflationary Pressure, Middle East Conflict, and বাংলা মূল্যস্ফীতি) and lists them for adding to your library.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            Button(
                                onClick = {
                                    viewModel.fetchGoogleDocsForImport(googleDocsUrl.trim())
                                },
                                enabled = googleDocsUrl.isNotBlank() && !isImporting,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("fetch_google_doc_button")
                            ) {
                                Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Scan & Detect All Lessons from Doc")
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Document URL:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = TextMuted
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = googleDocsUrl,
                                onValueChange = { googleDocsUrl = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("google_docs_url_input"),
                                placeholder = { Text("https://docs.google.com/document/d/...") },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = CardSurface,
                                    focusedContainerColor = CardSurface
                                )
                            )

                            if (googleDocsUrl != DEFAULT_GOOGLE_DOCS_URL) {
                                Spacer(modifier = Modifier.height(6.dp))
                                TextButton(
                                    onClick = { googleDocsUrl = DEFAULT_GOOGLE_DOCS_URL },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Reset to Fixed Official Link", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }

            // Tab 2: Direct Text Paste with Flexible Preset Selectors
            if (selectedTabIndex == 2) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardSurface),
                        shape = RoundedCornerShape(12.dp),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SubtleBorder))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Paste Lesson Resource Content",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Paste shorthand AI text, standard Version 2 markers, or JSON topic packs below:",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Quick Templates Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        rawTextContent = generateShorthandFormat()
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Shorthand AI Format (Minimal)")
                                }

                                OutlinedButton(
                                    onClick = {
                                        rawTextContent = generateSampleFocusWriting()
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("Focus Writing (V2)")
                                }

                                OutlinedButton(
                                    onClick = {
                                        rawTextContent = generateSampleArgumentative()
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("Argumentative (V2)")
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = rawTextContent,
                                onValueChange = { rawTextContent = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .testTag("paste_text_input"),
                                placeholder = { Text("Paste shorthand format (@Type: Title:, Section1: Title, Guide:, Answer:) or Marker Format V2...") },
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = CardSurface,
                                    focusedContainerColor = CardSurface
                                )
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    if (rawTextContent.isNotBlank()) {
                                        viewModel.parseTextForImport(rawTextContent)
                                    }
                                },
                                enabled = rawTextContent.isNotBlank() && !isImporting,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("inspect_pasted_text_button")
                            ) {
                                Text("Inspect & Preview Content")
                            }
                        }
                    }
                }
            }

            // Tab 3: Interactive AI Guidelines & Prompt Generator
            if (selectedTabIndex == 3) {
                // Primary Shorthand Guidelines Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardSurface),
                        shape = RoundedCornerShape(12.dp),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SubtleBorder))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "সহজ এআই লেসন গাইডলাইন (Shorthand Format)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "অতিরিক্ত কোন সেকশন ট্যাগ বা START/END ছাড়া সরাসরি নিচের সহজ প্যাটার্নে যেকোনো AI মডেল (ChatGPT, Gemini, Claude) কে দিয়ে সম্পূর্ণ লেসন লেখাতে পারেন। অ্যাপ স্বয়ংক্রিয়ভাবে এটি ডিটেক্ট করবে:",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                lineHeight = 19.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Exact User Format Pattern Display
                            val sampleShorthandPattern = """
@Type: Title: 
-Description:
-Guidelines:

Section1: Title 
Guide:
Answer:

Section2: 
Title 
Guide:
Answer:
...
...
...
SectionN: 
Title 
Guide:
Answer:
                            """.trimIndent()

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, SubtleBorder, RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = sampleShorthandPattern,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 12.sp),
                                    color = AcademicNavy,
                                    lineHeight = 18.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Key Guidelines Rules Breakdown in Bangla & English
                            Text(
                                text = "গাইডলাইনের মূল নিয়মাবলী:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            val rules = listOf(
                                "১. লেসন শিরোনাম: @Type: Title: দিয়ে শুরু হবে (যেমন: @Focus Writing: বাংলাদেশের মূল্যস্ফীতি অথবা @Type: Focus Writing Title: Inflation)",
                                "২. সারসংক্ষেপ ও গাইডলাইন: -Description: এ সংক্ষিপ্ত বর্ণনা এবং -Guidelines: এর পর মূল পয়েন্ট ও নির্দেশনাবলী",
                                "৩. সেকশন ফরম্যাট: Section1: Title অথবা এক লাইনে Section2: এবং পরের লাইনে Title—দুটোই সরাসরি কাজ করে",
                                "৪. গাইড ও উত্তর: Guide: এর পর লেখার নির্দেশনা ও পয়েন্ট এবং Answer: এর পর মডেল উত্তর",
                                "৫. কোনো START/END দরকার নেই: অতিরিক্ত কোনো সেকশন ট্যাগ বা সমাপ্তি ট্যাগের দরকার নেই; পরবর্তী SectionN: এলেই আগের সেকশন অটো-ক্লোজ হবে",
                                "৬. শব্দার্থ (Vocabulary): গাইডের ভেতরে '- Term -> বাংলা অর্থ' লিখলেই অ্যাপ স্বয়ংক্রিয়ভাবে শব্দার্থ তালিকায় অন্তর্ভুক্ত করবে"
                            )

                            rules.forEach { rule ->
                                Row(modifier = Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = SuccessGreen,
                                        modifier = Modifier.size(16.dp).padding(top = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = rule,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        lineHeight = 18.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        ExportManager.copyToClipboard(context, sampleShorthandPattern, "MicroWrite Shorthand Template")
                                        Toast.makeText(context, "Shorthand template copied to clipboard!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Copy Template")
                                }

                                OutlinedButton(
                                    onClick = {
                                        selectedTabIndex = 2
                                        rawTextContent = generateShorthandFormat()
                                    }
                                ) {
                                    Text("Load into Paste")
                                }
                            }
                        }
                    }
                }

                // AI Prompt Builder Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardSurface),
                        shape = RoundedCornerShape(12.dp),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SubtleBorder))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "AI Master Prompt Generator",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "যেকোনো বিষয়ে এআইকে দিয়ে এই ফরম্যাটে সম্পূর্ণ লেসন প্রস্তুত করতে নিচের প্রম্পটটি কপি করে ChatGPT বা Gemini তে পেস্ট করুন:",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // Topic Input
                            Text(
                                text = "Lesson Topic:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = aiTopicInput,
                                onValueChange = { aiTopicInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = CardSurface,
                                    focusedContainerColor = CardSurface
                                )
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            val masterPrompt = buildAiMasterPrompt(
                                topic = aiTopicInput.ifBlank { "Economic Diversification" },
                                type = aiWritingType,
                                sectionCount = aiSectionCount
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, SubtleBorder, RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = masterPrompt,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = TextSecondary,
                                    lineHeight = 16.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    ExportManager.copyToClipboard(context, masterPrompt, "MicroWrite AI Prompt")
                                    Toast.makeText(context, "AI Master Prompt copied to clipboard! Paste it into your AI model.", Toast.LENGTH_LONG).show()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy AI Master Prompt")
                            }
                        }
                    }
                }
            }

            // Loading Indicator
            if (isImporting) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Inspecting and validating resource...", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                        }
                    }
                }
            }

            // Import Error Banner
            if (importError != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = ErrorBg),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                            Icon(imageVector = Icons.Default.Error, contentDescription = null, tint = ErrorRed)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = importError ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ErrorRed
                            )
                        }
                    }
                }
            }

            // IMPORT PREVIEWS SECTION
            if (previews.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Detected Lessons (${previews.size})",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        val validCount = previews.count { it.isValid }
                        Text(
                            text = "$validCount valid",
                            style = MaterialTheme.typography.labelMedium,
                            color = SuccessGreen
                        )
                    }
                }

                itemsIndexed(previews) { index, preview ->
                    LessonPreviewCard(
                        preview = preview,
                        onToggleSelection = { viewModel.togglePreviewSelection(index) }
                    )
                }

                // Import Action Buttons
                item {
                    val selectedValidCount = previews.count { it.isSelectedForImport && it.isValid }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.commitImport { count ->
                                    Toast.makeText(context, "Successfully imported $count lesson(s)!", Toast.LENGTH_SHORT).show()
                                    onImportSuccess()
                                }
                            },
                            enabled = selectedValidCount > 0,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("commit_import_button"),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add to Library ($selectedValidCount)")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LessonPreviewCard(
    preview: ParsedLessonPreview,
    onToggleSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lesson = preview.lesson
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(10.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SubtleBorder))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = preview.isSelectedForImport && preview.isValid,
                    onCheckedChange = { onToggleSelection() },
                    enabled = preview.isValid
                )

                Column(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(4.dp), color = SoftBlueContainer) {
                            Text(
                                text = lesson.type,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${lesson.sections.size} sections",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    BanglaAwareText(
                        text = lesson.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    lesson.titleBn?.let { bn ->
                        if (bn.isNotBlank()) {
                            BanglaAwareText(
                                text = bn,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }

                // Validation Status Pill
                val statusBg = if (preview.isValid) {
                    if (preview.validationIssues.any { it.severity == IssueSeverity.WARNING }) WarningBg else SuccessBg
                } else ErrorBg

                val statusColor = if (preview.isValid) {
                    if (preview.validationIssues.any { it.severity == IssueSeverity.WARNING }) WarningAmber else SuccessGreen
                } else ErrorRed

                val statusLabel = if (preview.isValid) {
                    if (preview.validationIssues.any { it.severity == IssueSeverity.WARNING }) "Warnings" else "Valid"
                } else "Errors"

                Surface(shape = RoundedCornerShape(4.dp), color = statusBg) {
                    Text(
                        text = statusLabel,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }

            // Validation issues
            if (preview.validationIssues.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(8.dp)
                ) {
                    preview.validationIssues.forEach { issue ->
                        Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = if (issue.severity == IssueSeverity.ERROR) Icons.Default.Error else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (issue.severity == IssueSeverity.ERROR) ErrorRed else WarningAmber,
                                modifier = Modifier.size(16.dp).padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = issue.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            // Quick structure preview
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { isExpanded = !isExpanded }) {
                    Text(if (isExpanded) "Hide Details" else "Preview Sections")
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 6.dp)) {
                    lesson.sections.forEach { sec ->
                        Text(
                            text = "• Section ${sec.order}: ${sec.title} (${sec.type})",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun buildAiMasterPrompt(topic: String, type: String, sectionCount: Int): String {
    return """
Act as an expert academic writing coach and curriculum designer.
Write a complete, structured focus writing article/lesson for the "MicroWrite" Android learning app.
Follow this simplified shorthand format strictly, without any extra markdown backticks, conversational preamble, or explanation.

TOPIC: $topic
TYPE: $type

FORMAT RULES:
- Use this ultra-clean pattern without extra START/END tags:

@Type: $type Title: $topic
-Description: 1-2 sentence overview of the topic and objective.
-Guidelines:
Key concepts, factual context, and writing guidelines.
- Bullet points for key economic/analytical drivers
- Writing rules and formal academic criteria

Section1: Context and Introduction
Guide:
Background information, factual context, and analysis instructions.
Vocabulary:
- Technical Term 1 -> বাংলা অর্থ
- Technical Term 2 -> বাংলা অর্থ
Answer:
A formal, authoritative introductory paragraph (100–130 words).

Section2:
Core Dynamics and Analytical Evaluation
Guide:
Detailed breakdown of causes, effects, trade-offs, and empirical data.
Vocabulary:
- Concept Name -> বাংলা অর্থ
Answer:
A rigorous body paragraph (130–160 words) providing in-depth analysis.

Section3: Policy Recommendations and Strategic Outlook
Guide:
Actionable, evidence-based solutions, institutional reforms, and conclusions.
Answer:
A conclusive synthesis paragraph (100–130 words) with forward-looking remedies.

IMPORTANT:
- Output plain text only.
- Do NOT add ```markdown or any tags like SECTION START or SECTION END.
- Simply use Section1: Title, Guide:, and Answer: as shown above.
    """.trimIndent()
}

private fun generateShorthandFormat(): String {
    return """
@Type: Focus Writing Title: Digital Financial Inclusion in Bangladesh
-Description: Learn about digital financial services, MFS adoption, and financial deepening in emerging economies.
-Guidelines:
Digital financial services expand banking reach to unbanked rural populations.
- MFS agent networks reduce remittance friction and transactional costs
- Interoperability between commercial banks, wallets, and national payment switches
- Prudent consumer protection and cyber resilience

Section1: Mobile Money Adoption and Rural Reach
Guide:
Introduce mobile financial services and evaluate their role in grassroots economic inclusion.
Vocabulary:
- Financial inclusion -> আর্থিক অন্তর্ভুক্তি
- Mobile Financial Services (MFS) -> মোবাইল আর্থিক সেবা
- Unbanked population -> প্রাতিষ্ঠানিক ব্যাংকিং সেবাবহির্ভূত জনগোষ্ঠী
Answer:
Mobile Financial Services have revolutionized transactional velocity across Bangladesh. By converting neighborhood grocery outlets into authorized digital banking touchpoints, previously marginalized demographics gain frictionless access to peer-to-peer transfers, wage disbursements, and utility settlements. This digital democratization significantly mitigates cash-handling risks and integrates informal commerce into the monitored monetary ecosystem.

Section2:
Interoperability and Regulatory Supervision
Guide:
Examine the transition from closed-loop wallets to interoperable national payment architectures.
Vocabulary:
- Interoperability -> আন্তঃব্যবহারযোগ্যতা
- Regulatory oversight -> নিয়ন্ত্রণমূলক তদারকি
Answer:
Sustaining the momentum of financial inclusion requires dismantling closed-loop institutional silos. Standardized QR-code payment protocols and unified national payment switches empower micro-merchants to accept payments across disparate mobile wallets and commercial banks without prohibitive hardware expenditures. Furthermore, rigorous consumer data protection protocols and biometric verification safeguards are essential to preserve systemic confidence.

Section3: Micro-Credit Integration and Future Horizon
Guide:
Propose policy pathways to leverage transaction histories for collateral-free digital nano-loans.
Answer:
The next frontier of digital inclusion lies in credit democratization. Leveraging transaction histories through algorithmic credit scoring enables financial institutions to disburse collateral-free nano-loans to micro-entrepreneurs. Coupled with tailored micro-insurance products, digital finance transforms from a mere payment utility into a robust catalyst for sustainable poverty alleviation.
    """.trimIndent()
}

private fun generateSampleFocusWriting(): String {
    return """
LESSON TITLE: Economic Diversification and Industrial Growth
LESSON TYPE: Focus Writing
LESSON ID: ECON-DIV-003
LESSON CATEGORY: Economics & Trade
LESSON DESCRIPTION: Practice focus writing on industrial export diversification and macroeconomic resilience.

GENERAL GUIDELINES START
Export diversification reduces national vulnerability to single-commodity global market price shocks.
- Maintain an authoritative, objective tone without informal idioms.
- Connect trade data directly to policy prescriptions.
GENERAL GUIDELINES END

SECTION 1
SECTION TITLE: Current Export Concentration
SECTION TYPE: Writing

SECTION GUIDE START
Highlight the over-reliance on a single export sector and compare with peer emerging economies.
Vocabulary:
- Monocultural export basket -> একপণ্যভিত্তিক রপ্তানি কাঠামো
- Vulnerability to external shocks -> বাহ্যিক অভিঘাতের প্রতি ঝুঁকি
- Value addition -> মূল্য সংযোজন
SECTION GUIDE END

SECTION TASK: Write an introductory analysis in 100-130 words defining export concentration.
SECTION TIMER: 300

SECTION ANSWER START
Heavy concentration in a single export basket exposes the national balance of payments to sharp global market contractions. Diversifying into pharmaceuticals, agro-processing, and light engineering is essential for macroeconomic stability.
SECTION ANSWER END

SECTION END

SECTION 2
SECTION TITLE: Infrastructure and Policy Hurdles
SECTION TYPE: Writing

SECTION GUIDE START
Analyze trade logistics, port congestion, and tariff distortions that impede emerging manufacturing sectors.
Vocabulary:
- Logistical bottleneck -> পরিবহন ও লজিস্টিক সংক্রান্ত জটিলতা
- Tariff distortion -> শুল্ক কাঠামোর অসঙ্গতি
SECTION GUIDE END

SECTION TASK: Write 120-150 words examining structural barriers to industrial diversification.
SECTION TIMER: 360

SECTION ANSWER START
Emerging non-traditional export sectors encounter severe structural headwinds, chief among which are protracted customs clearance durations and deficient deep-sea container terminal capacity. Additionally, inverted tariff structures penalize domestic manufacturing by taxing intermediate inputs more heavily than finished imports. Rationalizing trade taxation and modernizing logistics are vital prerequisites to building global competitiveness.
SECTION ANSWER END

SECTION END

SECTION 3
SECTION TITLE: Strategic Export Interventions
SECTION TYPE: Writing

SECTION GUIDE START
Detail specific incentive programs, bonded warehouse facilities, and targeted credit lines.
SECTION GUIDE END

SECTION TASK: Propose policy interventions and conclude in 100-130 words.
SECTION TIMER: 300

SECTION ANSWER START
Expanding industrial resilience demands deliberate institutional intervention. The government must extend bonded warehouse privileges across all prospective export industries and negotiate comprehensive free trade agreements. Simultaneously, specialized export development funds should provide low-interest credit to innovative enterprises, ensuring a balanced, multi-sectoral growth trajectory.
SECTION ANSWER END

SECTION END

FINAL REVIEW START
- Verify smooth paragraph transitions.
- Check compliance with word counts.
FINAL REVIEW END

LESSON END
    """.trimIndent()
}

private fun generateSampleArgumentative(): String {
    return """
LESSON TITLE: Remote Work and Modern Urban Economy
LESSON TYPE: Argumentative Writing
LESSON ID: URBAN-REMOTE-004
LESSON CATEGORY: Society & Economy
LESSON DESCRIPTION: Practice argumentative writing balancing remote workplace flexibility against central business district vibrancy.

GENERAL GUIDELINES START
Argumentative writing requires taking a clear stance, developing persuasive arguments, and addressing counterarguments.
GENERAL GUIDELINES END

SECTION 1
SECTION TITLE: Introduction and Thesis
SECTION TYPE: Writing

SECTION GUIDE START
Introduce the shift toward distributed telework and state your primary thesis clearly.
Vocabulary:
- Telecommuting -> দূরবর্তী কাজের ব্যবস্থা
- Central Business District (CBD) -> কেন্দ্রীয় বাণিজ্যিক এলাকা
SECTION GUIDE END

SECTION TASK: Write an introduction of 100-120 words establishing your thesis.
SECTION TIMER: 300

SECTION ANSWER START
The post-pandemic normalization of remote work has initiated a profound restructuring of urban labor markets and metropolitan geography. While critics lament the declining commercial footfall in downtown districts, telecommuting fundamentally democratizes opportunity and curtails vehicular carbon emissions. This essay argues that remote flexibility revitalizes suburban communities and elevates worker well-being, demanding an adaptive repurposing of city centers rather than coercive return-to-office mandates.
SECTION ANSWER END

SECTION END

SECTION 2
SECTION TITLE: Arguments for Distributed Work
SECTION TYPE: Writing

SECTION GUIDE START
Highlight worker autonomy, reduced commuting stress, and carbon footprint reduction.
SECTION GUIDE END

SECTION TASK: Develop supporting arguments in 120-150 words.
SECTION TIMER: 360

SECTION ANSWER START
Proponents of hybrid work structures highlight measurable enhancements in individual productivity and mental wellness. Eliminating daily rush-hour commutes grants professionals valuable time for family, physical health, and lifelong education. Furthermore, decentralized employment redistributes discretionary spending into local suburban economies, spurring grassroots entrepreneurship outside congested megacity cores.
SECTION ANSWER END

SECTION END

SECTION 3
SECTION TITLE: Counterarguments and Synthesis
SECTION TYPE: Writing

SECTION GUIDE START
Address concerns regarding commercial real estate defaults and reduced spontaneous innovation.
SECTION GUIDE END

SECTION TASK: Synthesize counterarguments and conclude in 100-130 words.
SECTION TIMER: 300

SECTION ANSWER START
Critics legitimately emphasize the precarious situation of commercial real estate and the potential diminution of spontaneous mentorship. Nonetheless, dynamic urban governance can convert vacant office complexes into affordable residential housing and communal innovation labs. Embracing adaptive urbanism ensures cities remain vibrant cultural hubs while honoring the modern imperative for workplace autonomy.
SECTION ANSWER END

SECTION END

FINAL REVIEW START
- Confirm both sides of the argument are addressed with academic rigor.
- Ensure the conclusion provides forward-looking resolution.
FINAL REVIEW END

LESSON END
    """.trimIndent()
}

private fun generateMinimalQuickFormat(): String {
    return """
LESSON TITLE: Digital Financial Inclusion
LESSON TYPE: Focus Writing

GENERAL GUIDELINES START
Digital financial services expand banking reach to unbanked rural populations.
GENERAL GUIDELINES END

SECTION 1
SECTION TITLE: Mobile Financial Services
SECTION GUIDE START
Explain mobile money agents and wallet adoption.
Vocabulary:
- Financial inclusion -> আর্থিক অন্তর্ভুক্তি
- Mobile Financial Services (MFS) -> মোবাইল আর্থিক সেবা
SECTION GUIDE END
SECTION TASK: Write 100 words defining MFS impact.
SECTION TIMER: 300
SECTION ANSWER START
Mobile Financial Services have revolutionized transactional velocity across rural Bangladesh. By converting local grocery outlets into authorized digital banking touchpoints, unbanked demographics gain frictionless access to remittances, utility bill settlements, and micro-savings.
SECTION ANSWER END
SECTION END

LESSON END
    """.trimIndent()
}
