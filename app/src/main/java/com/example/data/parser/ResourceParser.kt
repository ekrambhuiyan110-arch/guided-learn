package com.example.data.parser

import com.example.data.model.FinalReview
import com.example.data.model.Guidelines
import com.example.data.model.ImportValidationIssue
import com.example.data.model.IssueSeverity
import com.example.data.model.Lesson
import com.example.data.model.LessonSection
import com.example.data.model.ParsedLessonPreview
import com.example.data.model.SectionGuide
import com.example.data.model.TableData
import com.example.data.model.VocabularyItem
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.StringReader
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import java.util.zip.ZipInputStream

class ResourceParser(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    /**
     * Parses text content (which could be marker-based V2 format or JSON format).
     * Returns a list of preview lessons with validation issues.
     */
    fun parseContent(rawContent: String): List<ParsedLessonPreview> {
        val trimmed = rawContent
            .replace("\uFEFF", "")
            .replace("\u200B", "")
            .replace("\u00A0", " ")
            .trim()
        if (trimmed.isEmpty()) {
            return emptyList()
        }

        // Check if JSON
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            val jsonLessons = tryParseJson(trimmed)
            if (jsonLessons.isNotEmpty()) {
                return jsonLessons
            }
        }

        // Default to Marker Parser V2 & Shorthand Parser
        return parseMarkerFormat(trimmed)
    }

    /**
     * Parses a .docx document from InputStream by reading word/document.xml
     */
    suspend fun parseDocx(inputStream: InputStream): List<ParsedLessonPreview> = withContext(Dispatchers.IO) {
        val extractedText = extractTextFromDocx(inputStream)
        parseContent(extractedText)
    }

    /**
     * Fetches public Google Doc plain text via export URL
     */
    suspend fun fetchAndParseGoogleDocs(url: String): Pair<List<ParsedLessonPreview>, String?> = withContext(Dispatchers.IO) {
        val docId = extractGoogleDocId(url)
            ?: return@withContext Pair(emptyList(), "Invalid Google Docs URL. Make sure it follows: https://docs.google.com/document/d/YOUR_DOC_ID/...")

        val exportUrl = "https://docs.google.com/document/d/$docId/export?format=txt"
        val request = Request.Builder()
            .url(exportUrl)
            .header("User-Agent", "MicroWrite-App/1.0")
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    val code = resp.code
                    val errorMsg = when (code) {
                        401, 403 -> "Google Doc is private. Please set link sharing to 'Anyone with the link can view'."
                        404 -> "Document not found (404). Check if the document ID is correct and public."
                        else -> "Failed to download Google Doc. HTTP $code: ${resp.message}"
                    }
                    return@withContext Pair(emptyList(), errorMsg)
                }

                val bodyText = (resp.body?.string() ?: "")
                    .replace("\uFEFF", "")
                    .replace("\u200B", "")
                    .replace("\u00A0", " ")
                if (bodyText.isBlank()) {
                    return@withContext Pair(emptyList(), "Google Doc appears to be empty.")
                }

                val parsed = parseContent(bodyText)
                if (parsed.isEmpty()) {
                    return@withContext Pair(emptyList(), "No valid lessons found in the Google Doc. Please check that it uses the MicroWrite format.")
                }
                Pair(parsed, null)
            }
        } catch (e: Exception) {
            Pair(emptyList(), "Network error: ${e.localizedMessage ?: "Unable to reach Google Docs"}")
        }
    }

    /**
     * Extracts text and tables from DOCX archive's word/document.xml
     */
    fun extractTextFromDocx(inputStream: InputStream): String {
        val zip = ZipInputStream(inputStream)
        val sb = StringBuilder()
        var entry = zip.nextEntry
        while (entry != null) {
            if (entry.name == "word/document.xml") {
                val xmlContent = zip.bufferedReader(Charsets.UTF_8).readText()
                parseWordDocumentXml(xmlContent, sb)
                break
            }
            entry = zip.nextEntry
        }
        return sb.toString()
    }

    private fun parseWordDocumentXml(xml: String, sb: StringBuilder) {
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var inParagraph = false
            var inTableCell = false
            var inTableRow = false
            val rowCells = mutableListOf<String>()
            val paragraphText = StringBuilder()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name
                        when (name) {
                            "p" -> {
                                inParagraph = true
                                paragraphText.clear()
                            }
                            "tr" -> {
                                inTableRow = true
                                rowCells.clear()
                            }
                            "tc" -> {
                                inTableCell = true
                            }
                            "t" -> {
                                val text = parser.nextText()
                                paragraphText.append(text)
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val name = parser.name
                        when (name) {
                            "tc" -> {
                                inTableCell = false
                                rowCells.add(paragraphText.toString().trim())
                                paragraphText.clear()
                            }
                            "tr" -> {
                                inTableRow = false
                                if (rowCells.isNotEmpty()) {
                                    sb.append("| ").append(rowCells.joinToString(" | ")).append(" |\n")
                                }
                            }
                            "p" -> {
                                inParagraph = false
                                if (!inTableCell) {
                                    val text = paragraphText.toString().trim()
                                    if (text.isNotEmpty()) {
                                        sb.append(text).append("\n\n")
                                    }
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
            // Fallback: strip tags via regex
            val fallback = xml.replace(Regex("<[^>]+>"), " ")
                .replace(Regex("\\s+"), " ")
            sb.append(fallback)
        }
    }

    private fun extractGoogleDocId(url: String): String? {
        val normalized = url.replace("docs*google*com", "docs.google.com")
            .replace("docs*google", "docs.google")
            .replace("*", ".")
        val pattern = Pattern.compile("/document/d/([a-zA-Z0-9_-]+)")
        val matcher = pattern.matcher(normalized)
        return if (matcher.find()) {
            matcher.group(1)
        } else null
    }

    /**
     * Parses Standard Resource Format Version 2 and Shorthand Format
     */
    fun parseMarkerFormat(text: String): List<ParsedLessonPreview> {
        val lines = text.lines()
        val lessonBlocks = splitIntoLessonBlocks(lines)
        val previews = mutableListOf<ParsedLessonPreview>()

        for (block in lessonBlocks) {
            previews.add(parseSingleLessonBlock(block))
        }

        return previews
    }

    private fun isLessonStartLine(line: String): Boolean {
        val clean = line.replace("\uFEFF", "").replace("\u200B", "").trim()
        val upper = clean.uppercase()
        return upper.startsWith("LESSON TITLE:") ||
                upper.startsWith("LESSON TITLE :") ||
                upper.startsWith("@TYPE:") ||
                upper.startsWith("@TYPE :") ||
                upper.startsWith("@FOCUS") ||
                upper.startsWith("@ARGUMENTATIVE") ||
                (upper.startsWith("@") && (upper.contains(":") || upper.contains("TITLE")))
    }

    private fun splitIntoLessonBlocks(lines: List<String>): List<List<String>> {
        val blocks = mutableListOf<List<String>>()
        var currentBlock = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.replace("\uFEFF", "").replace("\u200B", "").trim()
            val upper = trimmed.uppercase()

            if (isLessonStartLine(trimmed)) {
                if (currentBlock.any { it.isNotBlank() }) {
                    blocks.add(currentBlock)
                    currentBlock = mutableListOf()
                }
            }

            currentBlock.add(line)

            if (upper == "LESSON END" || upper == "END LESSON") {
                if (currentBlock.any { it.isNotBlank() }) {
                    blocks.add(currentBlock)
                    currentBlock = mutableListOf()
                }
            }
        }

        if (currentBlock.any { it.isNotBlank() }) {
            blocks.add(currentBlock)
        }

        return blocks
    }

    private fun parseSingleLessonBlock(lines: List<String>): ParsedLessonPreview {
        val issues = mutableListOf<ImportValidationIssue>()

        var title = ""
        var lessonId = ""
        var lessonType = "Focus Writing"
        var description = ""
        var category = "General"

        val guidelinesLines = mutableListOf<String>()
        var inGuidelines = false
        var guidelinesStartFound = false

        val finalReviewLines = mutableListOf<String>()
        var inFinalReview = false

        // Section parsing
        val sectionBlocks = mutableListOf<MutableList<String>>()
        var currentSectionLines: MutableList<String>? = null

        for (line in lines) {
            val trimmed = line.replace("\uFEFF", "").replace("\u200B", "").trim()
            val upper = trimmed.uppercase()

            if (trimmed.startsWith("@")) {
                val withoutAt = trimmed.removePrefix("@").trim()
                if (withoutAt.contains("Title:", ignoreCase = true)) {
                    val parts = withoutAt.split(Regex("Title:", RegexOption.IGNORE_CASE), limit = 2)
                    val tCand = parts[0].replace(Regex("^Type:\\s*", RegexOption.IGNORE_CASE), "").trim()
                    if (tCand.isNotBlank()) lessonType = tCandidateClean(tCand)
                    title = parts[1].trim()
                } else if (withoutAt.contains(":")) {
                    val parts = withoutAt.split(":", limit = 2)
                    val tCand = parts[0].replace(Regex("^Type:\\s*", RegexOption.IGNORE_CASE), "").trim()
                    if (tCand.isNotBlank() && (tCand.contains("Writing", true) || tCand.contains("Focus", true) || tCand.contains("Argument", true))) {
                        lessonType = tCandidateClean(tCand)
                        title = parts[1].trim()
                    } else {
                        title = parts[1].trim()
                    }
                } else {
                    title = withoutAt
                }
            } else if (upper.startsWith("LESSON TITLE:") || upper.startsWith("LESSON TITLE :") ||
                upper.startsWith("TITLE:") || upper.startsWith("TOPIC:") ||
                (upper.startsWith("# ") && title.isBlank())) {
                title = trimmed.substringAfter(":").removePrefix("#").trim()
            } else if (upper.startsWith("-DESCRIPTION:") || upper.startsWith("DESCRIPTION:") ||
                upper.startsWith("LESSON DESCRIPTION:") || upper.startsWith("LESSON DESCRIPTION :")) {
                description = trimmed.substringAfter(":").trim()
            } else if (upper.startsWith("-GUIDELINES:") || upper.startsWith("GUIDELINES:") ||
                upper.startsWith("GENERAL GUIDELINES:") ||
                upper == "GENERAL GUIDELINES START" || upper == "GUIDELINES START" ||
                upper == "GUIDELINE START" || upper == "GENERAL START") {
                inGuidelines = true
                guidelinesStartFound = true
                val inlineContent = if (trimmed.contains(":")) trimmed.substringAfter(":").trim() else ""
                if (inlineContent.isNotBlank() && !inlineContent.equals("START", true)) {
                    guidelinesLines.add(inlineContent)
                }
            } else if (upper == "GENERAL GUIDELINES END" || upper == "GUIDELINES END" ||
                upper == "GUIDELINE END" || upper == "GENERAL END") {
                inGuidelines = false
            } else if (upper.startsWith("LESSON ID:") || upper.startsWith("ID:") || upper.startsWith("TOPIC ID:")) {
                lessonId = trimmed.substringAfter(":").trim()
            } else if (upper.startsWith("LESSON TYPE:") || upper.startsWith("TYPE:") || upper.startsWith("WRITING TYPE:")) {
                lessonType = trimmed.substringAfter(":").trim()
            } else if (upper.startsWith("LESSON CATEGORY:") || upper.startsWith("CATEGORY:")) {
                category = trimmed.substringAfter(":").trim()
            } else if (upper == "FINAL REVIEW START" || upper == "REVIEW START" || upper == "CHECKLIST START" ||
                upper.startsWith("-FINAL REVIEW:") || upper.startsWith("FINAL REVIEW:")) {
                inFinalReview = true
                inGuidelines = false
                val inlineContent = if (trimmed.contains(":")) trimmed.substringAfter(":").trim() else ""
                if (inlineContent.isNotBlank() && !inlineContent.equals("START", true)) {
                    finalReviewLines.add(inlineContent)
                }
            } else if (upper == "FINAL REVIEW END" || upper == "REVIEW END" || upper == "CHECKLIST END") {
                inFinalReview = false
            } else if (upper == "LESSON END" || upper == "END LESSON") {
                inGuidelines = false
                inFinalReview = false
            } else if (isSectionStartMarker(upper)) {
                inGuidelines = false
                inFinalReview = false
                currentSectionLines?.let { sectionBlocks.add(it) }
                currentSectionLines = mutableListOf(line)
            } else if (isSectionEndMarker(upper)) {
                currentSectionLines?.add(line)
                currentSectionLines?.let { sectionBlocks.add(it) }
                currentSectionLines = null
            } else {
                if (inGuidelines) {
                    guidelinesLines.add(line)
                } else if (inFinalReview) {
                    finalReviewLines.add(line)
                } else if (currentSectionLines != null) {
                    currentSectionLines.add(line)
                }
            }
        }

        currentSectionLines?.let { sectionBlocks.add(it) }

        // Validation - lenient and auto-healed
        if (title.isBlank()) {
            title = "Untitled Lesson"
        }

        if (lessonId.isBlank()) {
            lessonId = generateLessonId(title)
        }

        // Parse Guidelines content
        val guidelines = parseGuidelines(guidelinesLines)

        // Parse Sections
        val sections = mutableListOf<LessonSection>()
        for ((index, secLines) in sectionBlocks.withIndex()) {
            val sectionOrder = index + 1
            val parsedSection = parseSingleSection(lessonId, sectionOrder, secLines, issues)
            sections.add(parsedSection)
        }

        // Parse Final Review
        val finalReview = parseFinalReview(finalReviewLines)

        val totalMinutes = sections.sumOf { if (it.timerSeconds > 0) it.timerSeconds / 60 else 5 }
        val estimatedMinutes = if (totalMinutes > 0) totalMinutes else 15

        val lesson = Lesson(
            id = lessonId,
            title = title,
            type = if (lessonType.isNotBlank()) lessonType else "Focus Writing",
            category = category,
            description = description,
            estimatedMinutes = estimatedMinutes,
            guidelines = guidelines,
            sections = sections,
            finalReview = finalReview
        )

        return ParsedLessonPreview(lesson, issues)
    }

    private fun tCandidateClean(cand: String): String {
        return when {
            cand.contains("Argument", true) -> "Argumentative Writing"
            cand.contains("Focus", true) -> "Focus Writing"
            else -> cand.ifBlank { "Focus Writing" }
        }
    }

    private fun isSectionStartMarker(upper: String): Boolean {
        val cleaned = upper.replace(Regex("^[#\\-*•]+\\s*"), "").trim()
        val match = Regex("^(SECTION\\s*\\d+|SECTION\\d+|PART\\s*\\d+)", RegexOption.IGNORE_CASE).find(cleaned)
        if (match != null) {
            val after = cleaned.substring(match.range.last + 1).trim()
            if (!after.startsWith("END")) {
                return true
            }
        }
        return false
    }

    private fun isSectionEndMarker(upper: String): Boolean {
        val cleaned = upper.replace(Regex("^[#\\-*•]+\\s*"), "").trim()
        return cleaned == "SECTION END" || cleaned == "END SECTION" ||
                Regex("^SECTION\\s*\\d+\\s+END$", RegexOption.IGNORE_CASE).matches(cleaned)
    }

    private fun cleanSectionTitle(raw: String): String {
        return raw
            .replace(Regex("^(?:SECTION\\s*TITLE|SECTION\\s*HEADING|TITLE|HEADING)[:\\s]*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^START[:\\s]*", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private fun parseSingleSection(
        lessonId: String,
        order: Int,
        lines: List<String>,
        issues: MutableList<ImportValidationIssue>
    ): LessonSection {
        val firstLine = lines.firstOrNull()?.trim() ?: ""
        val secMatch = Regex("^(?:SECTION\\s*\\d+|SECTION\\d+|PART\\s*\\d+)[:\\s]*(.*)$", RegexOption.IGNORE_CASE).find(firstLine)
        var secTitle = cleanSectionTitle(secMatch?.groupValues?.get(1) ?: "")
        var startIndex = 1

        if (secTitle.isBlank() && lines.size > 1) {
            val nextCandidate = lines[1].trim()
            val upperCand = nextCandidate.uppercase()
            if (!upperCand.startsWith("GUIDE") && !upperCand.startsWith("TASK") &&
                !upperCand.startsWith("TIMER") && !upperCand.startsWith("ANSWER") &&
                !upperCand.startsWith("-GUIDE") && !upperCand.startsWith("-TASK") &&
                !upperCand.startsWith("-ANSWER") && !upperCand.startsWith("SECTION GUIDE") &&
                !upperCand.startsWith("SECTION TASK") && !upperCand.startsWith("SECTION ANSWER") &&
                !upperCand.startsWith("SECTION TYPE") && !upperCand.startsWith("TYPE:")) {
                secTitle = cleanSectionTitle(nextCandidate)
                startIndex = 2
            }
        }

        var secType = "Writing"
        var task = ""
        var timerSeconds = 0
        val guideLines = mutableListOf<String>()
        val answerLines = mutableListOf<String>()
        var mode = "none" // "guide", "answer"

        for (i in startIndex until lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            val upper = trimmed.uppercase()

            if (upper.startsWith("SECTION TITLE:") || upper.startsWith("TITLE:") || upper.startsWith("SECTION HEADING:")) {
                val candidateTitle = trimmed.substringAfter(":").trim()
                if (candidateTitle.isNotBlank()) secTitle = candidateTitle
            } else if (upper.startsWith("SECTION TYPE:") || upper.startsWith("TYPE:")) {
                secType = trimmed.substringAfter(":").trim()
            } else if (upper.startsWith("SECTION TASK:") || upper.startsWith("TASK:") ||
                upper.startsWith("-TASK:") || upper.startsWith("PROMPT:")) {
                task = trimmed.substringAfter(":").trim()
                mode = "none"
            } else if (upper.startsWith("SECTION TIMER:") || upper.startsWith("TIMER:") ||
                upper.startsWith("-TIMER:") || upper.startsWith("TIME:")) {
                timerSeconds = parseTimerSeconds(trimmed.substringAfter(":").trim())
            } else if (upper == "SECTION GUIDE START" || upper == "GUIDE START" ||
                upper == "GUIDE:" || upper == "-GUIDE:" || upper.startsWith("GUIDE:") ||
                upper == "GUIDE" || upper == "INSTRUCTIONS START" || upper == "RESOURCES START") {
                mode = "guide"
                val inlineText = if (trimmed.contains(":")) trimmed.substringAfter(":").trim() else ""
                if (inlineText.isNotBlank() && !inlineText.equals("START", true)) {
                    guideLines.add(inlineText)
                }
            } else if (upper == "SECTION GUIDE END" || upper == "GUIDE END" ||
                upper == "INSTRUCTIONS END" || upper == "RESOURCES END") {
                mode = "none"
            } else if (upper == "SECTION ANSWER START" || upper == "ANSWER START" ||
                upper == "MODEL ANSWER START" || upper == "SOLUTION START" ||
                upper == "ANSWER:" || upper == "-ANSWER:" || upper.startsWith("ANSWER:") ||
                upper == "ANSWER" || upper.startsWith("MODEL ANSWER:")) {
                mode = "answer"
                val inlineText = if (trimmed.contains(":")) trimmed.substringAfter(":").trim() else ""
                if (inlineText.isNotBlank() && !inlineText.equals("START", true)) {
                    answerLines.add(inlineText)
                }
            } else if (upper == "SECTION ANSWER END" || upper == "ANSWER END" ||
                upper == "MODEL ANSWER END" || upper == "SOLUTION END") {
                mode = "none"
            } else if (isSectionEndMarker(upper)) {
                mode = "none"
            } else {
                if (mode == "guide") {
                    guideLines.add(line)
                } else if (mode == "answer") {
                    answerLines.add(line)
                }
            }
        }

        if (secTitle.isBlank()) {
            secTitle = "Section $order"
        }

        val guide = parseSectionGuide(guideLines)
        val modelAnswer = answerLines.joinToString("\n").trim()

        return LessonSection(
            id = "${lessonId}_sec_$order",
            lessonId = lessonId,
            order = order,
            title = secTitle,
            type = secType,
            guide = guide,
            task = task,
            timerSeconds = timerSeconds,
            modelAnswer = modelAnswer
        )
    }

    private fun parseTimerSeconds(str: String): Int {
        val trimmed = str.lowercase().trim()
        if (trimmed.endsWith("m") || trimmed.contains("min")) {
            val num = trimmed.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
            return num * 60
        }
        val cleanSec = trimmed.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
        return cleanSec
    }

    private fun parseGuidelines(lines: List<String>): Guidelines {
        val paragraphs = mutableListOf<String>()
        val bullets = mutableListOf<String>()
        val tables = mutableListOf<TableData>()

        var currentTableLines = mutableListOf<String>()
        val currentParagraph = StringBuilder()

        fun flushParagraph() {
            val text = currentParagraph.toString().trim()
            if (text.isNotEmpty()) {
                paragraphs.add(text)
                currentParagraph.clear()
            }
        }

        fun flushTable() {
            if (currentTableLines.isNotEmpty()) {
                parseMarkdownTable(currentTableLines)?.let { tables.add(it) }
                currentTableLines = mutableListOf()
            }
        }

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                flushParagraph()
                flushTable()
                continue
            }

            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                flushParagraph()
                currentTableLines.add(trimmed)
            } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ") || isNumberedList(trimmed)) {
                flushParagraph()
                flushTable()
                val item = cleanBullet(trimmed)
                if (item.isNotEmpty()) {
                    bullets.add(item)
                }
            } else {
                flushTable()
                if (currentParagraph.isNotEmpty()) currentParagraph.append(" ")
                currentParagraph.append(trimmed)
            }
        }

        flushParagraph()
        flushTable()

        return Guidelines(paragraphs, bullets, tables)
    }

    private fun parseSectionGuide(lines: List<String>): SectionGuide {
        val paragraphs = mutableListOf<String>()
        val bullets = mutableListOf<String>()
        val vocabulary = mutableListOf<VocabularyItem>()
        val tables = mutableListOf<TableData>()

        var currentTableLines = mutableListOf<String>()
        val currentParagraph = StringBuilder()

        fun flushParagraph() {
            val text = currentParagraph.toString().trim()
            if (text.isNotEmpty()) {
                paragraphs.add(text)
                currentParagraph.clear()
            }
        }

        fun flushTable() {
            if (currentTableLines.isNotEmpty()) {
                parseMarkdownTable(currentTableLines)?.let { tables.add(it) }
                currentTableLines = mutableListOf()
            }
        }

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                flushParagraph()
                flushTable()
                continue
            }

            // Check table
            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                flushParagraph()
                currentTableLines.add(trimmed)
                continue
            }

            // Check vocabulary item (e.g., "Term: Meaning" or "- Term -> Meaning" or "Term (Meaning)")
            val vocab = tryParseVocabularyItem(trimmed)
            if (vocab != null) {
                flushParagraph()
                flushTable()
                vocabulary.add(vocab)
                continue
            }

            // Check bullet
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ") || isNumberedList(trimmed)) {
                flushParagraph()
                flushTable()
                val item = cleanBullet(trimmed)
                if (item.isNotEmpty()) {
                    bullets.add(item)
                }
                continue
            }

            // Otherwise paragraph
            flushTable()
            if (currentParagraph.isNotEmpty()) currentParagraph.append(" ")
            currentParagraph.append(trimmed)
        }

        flushParagraph()
        flushTable()

        return SectionGuide(paragraphs, bullets, vocabulary, tables)
    }

    private fun parseFinalReview(lines: List<String>): FinalReview {
        val items = mutableListOf<String>()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isNotEmpty()) {
                val cleaned = cleanBullet(trimmed)
                if (cleaned.isNotEmpty()) {
                    items.add(cleaned)
                }
            }
        }
        return FinalReview(items)
    }

    private fun cleanBullet(line: String): String {
        return line.replaceFirst(Regex("^[-*•]\\s+"), "")
            .replaceFirst(Regex("^\\d+[.)]\\s+"), "")
            .trim()
    }

    private fun isNumberedList(line: String): Boolean {
        return Regex("^\\d+[.)]\\s+").containsMatchIn(line)
    }

    private fun tryParseVocabularyItem(line: String): VocabularyItem? {
        val cleaned = cleanBullet(line)

        // Check for Bengali Unicode characters range (\u0980-\u09FF)
        val hasBengali = cleaned.any { it in '\u0980'..'\u09FF' }

        // Formats:
        // 1. "Term -> MeaningBn" or "Term — MeaningBn"
        // 2. "Term: MeaningBn"
        // 3. "Term (MeaningBn)"
        if (cleaned.contains("->")) {
            val parts = cleaned.split("->", limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                return VocabularyItem(parts[0].trim(), parts[1].trim())
            }
        }

        if (cleaned.contains("—")) {
            val parts = cleaned.split("—", limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                return VocabularyItem(parts[0].trim(), parts[1].trim())
            }
        }

        if (hasBengali && cleaned.contains(":")) {
            val parts = cleaned.split(":", limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                return VocabularyItem(parts[0].trim(), parts[1].trim())
            }
        }

        val parenMatch = Regex("^([^()]+)\\s*\\(([\\u0980-\\u09FF\\s,]+)\\)$").find(cleaned)
        if (parenMatch != null) {
            val term = parenMatch.groupValues[1].trim()
            val meaning = parenMatch.groupValues[2].trim()
            return VocabularyItem(term, meaning)
        }

        return null
    }

    private fun parseMarkdownTable(lines: List<String>): TableData? {
        if (lines.size < 2) return null
        val rows = mutableListOf<List<String>>()

        for (line in lines) {
            val trimmed = line.trim()
            // Ignore separator row like | --- | --- |
            if (trimmed.matches(Regex("^\\|[\\s\\-:\\|]+\\|$"))) {
                continue
            }
            val cells = trimmed.split("|")
                .map { it.trim() }
                .filterIndexed { index, _ -> index > 0 && index < trimmed.split("|").size - 1 }
            if (cells.isNotEmpty()) {
                rows.add(cells)
            }
        }

        if (rows.isEmpty()) return null
        val headers = rows.first()
        val contentRows = if (rows.size > 1) rows.drop(1) else emptyList()
        return TableData(headers, contentRows)
    }

    /**
     * Parses JSON topic packs (both single object and array of objects)
     */
    private fun tryParseJson(json: String): List<ParsedLessonPreview> {
        val previews = mutableListOf<ParsedLessonPreview>()
        try {
            // Check if array
            if (json.startsWith("[")) {
                val listType = Types.newParameterizedType(List::class.java, Map::class.java)
                val adapter = moshi.adapter<List<Map<String, Any>>>(listType)
                val list = adapter.fromJson(json) ?: return emptyList()
                for (item in list) {
                    val preview = parseJsonObjectToPreview(item)
                    if (preview != null) previews.add(preview)
                }
            } else {
                val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
                val adapter = moshi.adapter<Map<String, Any>>(mapType)
                val map = adapter.fromJson(json) ?: return emptyList()
                val preview = parseJsonObjectToPreview(map)
                if (preview != null) previews.add(preview)
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return previews
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseJsonObjectToPreview(map: Map<String, Any>): ParsedLessonPreview? {
        val issues = mutableListOf<ImportValidationIssue>()

        val title = (map["title"] as? String) ?: run {
            issues.add(ImportValidationIssue(IssueSeverity.ERROR, "JSON is missing 'title' field."))
            "Untitled Lesson"
        }

        val topicId = (map["topicId"] as? String) ?: (map["id"] as? String) ?: generateLessonId(title)
        val titleBn = map["titleBn"] as? String
        val category = (map["category"] as? String) ?: "General"
        val description = (map["description"] as? String) ?: ""
        val type = (map["type"] as? String) ?: (map["writingTypes"] as? List<*>)?.firstOrNull()?.toString() ?: "Focus Writing"

        // Guidelines
        val guidelinesMap = map["guidelines"] as? Map<String, Any>
        val gParagraphs = (guidelinesMap?.get("paragraphs") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
        val gBullets = (guidelinesMap?.get("bullets") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
        val guidelines = Guidelines(paragraphs = gParagraphs, bullets = gBullets)

        // Sections
        val sectionsList = map["sections"] as? List<Map<String, Any>>
        val sections = mutableListOf<LessonSection>()

        if (sectionsList != null) {
            for ((index, secMap) in sectionsList.withIndex()) {
                val secOrder = (secMap["order"] as? Number)?.toInt() ?: (index + 1)
                val secTitle = (secMap["title"] as? String) ?: "Section $secOrder"
                val secType = (secMap["type"] as? String) ?: "Writing"
                val task = (secMap["task"] as? String) ?: ""
                val timerSeconds = (secMap["timerSeconds"] as? Number)?.toInt() ?: 0

                val answerMap = secMap["answer"] as? Map<String, Any>
                val modelAnswer = (answerMap?.get("text") as? String) ?: (secMap["answer"] as? String) ?: ""

                val guideMap = secMap["guide"] as? Map<String, Any>
                val sParagraphs = (guideMap?.get("paragraphs") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                val sBullets = (guideMap?.get("bullets") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()

                val vocabList = guideMap?.get("vocabulary") as? List<Map<String, Any>>
                val vocabulary = mutableListOf<VocabularyItem>()
                if (vocabList != null) {
                    for (v in vocabList) {
                        val term = v["term"] as? String ?: continue
                        val meaningBn = v["meaningBn"] as? String ?: ""
                        val note = v["note"] as? String
                        vocabulary.add(VocabularyItem(term, meaningBn, note))
                    }
                }

                val guide = SectionGuide(
                    paragraphs = sParagraphs,
                    bullets = sBullets,
                    vocabulary = vocabulary
                )

                sections.add(
                    LessonSection(
                        id = "${topicId}_sec_$secOrder",
                        lessonId = topicId,
                        order = secOrder,
                        title = secTitle,
                        type = secType,
                        guide = guide,
                        task = task,
                        timerSeconds = timerSeconds,
                        modelAnswer = modelAnswer
                    )
                )
            }
        }

        if (sections.isEmpty()) {
            issues.add(ImportValidationIssue(IssueSeverity.WARNING, "No sections found in JSON topic pack."))
        }

        // Final Review
        val finalReviewMap = map["finalReview"] as? Map<String, Any>
        val frItems = (finalReviewMap?.get("items") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
        val finalReview = FinalReview(frItems)

        val totalMinutes = sections.sumOf { if (it.timerSeconds > 0) it.timerSeconds / 60 else 5 }

        val lesson = Lesson(
            id = topicId,
            title = title,
            titleBn = titleBn,
            type = type,
            category = category,
            description = description,
            estimatedMinutes = if (totalMinutes > 0) totalMinutes else 15,
            guidelines = guidelines,
            sections = sections,
            finalReview = finalReview
        )

        return ParsedLessonPreview(lesson, issues)
    }

    private fun generateLessonId(title: String): String {
        val slug = title.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(24)
        return if (slug.isNotBlank()) "MW-$slug" else "MW-${UUID.randomUUID().toString().take(8)}"
    }
}
