package com.example

import com.example.data.export.ExportManager
import com.example.data.export.ExportMode
import com.example.data.model.Lesson
import com.example.data.model.LessonSection
import com.example.data.parser.ResourceParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    private val parser = ResourceParser()

    @Test
    fun testMarkerFormatV2Parsing() {
        val rawContent = """
LESSON TITLE: Resolution of Non-Performing Loans
LESSON TYPE: Focus Writing
LESSON ID: BD-BANK-NPL-001
LESSON DESCRIPTION: Learn about NPLs and practise focus writing.

GENERAL GUIDELINES START
General information about macro-prudential regulations.
- Important concept 1
- Important concept 2
GENERAL GUIDELINES END

SECTION 1
SECTION TITLE: Introduction to NPLs
SECTION TYPE: Writing

SECTION GUIDE START
Instructions and facts about loan classification.
Vocabulary:
- Asset quality deterioration -> ঋণের গুণগত মানের অবনতি
- Substandard loan -> নিম্নমান ঋণ
SECTION GUIDE END

SECTION TASK: Write an introduction in 100–130 words.
SECTION TIMER: 300

SECTION ANSWER START
A Non-Performing Loan (NPL) represents a delinquent debt instrument.
SECTION ANSWER END

SECTION END

FINAL REVIEW START
- Check accuracy of classification terms.
- Confirm logical transition.
FINAL REVIEW END

LESSON END
        """.trimIndent()

        val previews = parser.parseContent(rawContent)
        assertEquals(1, previews.size)

        val preview = previews[0]
        assertTrue(preview.isValid)

        val lesson = preview.lesson
        assertEquals("Resolution of Non-Performing Loans", lesson.title)
        assertEquals("Focus Writing", lesson.type)
        assertEquals("BD-BANK-NPL-001", lesson.id)
        assertEquals(1, lesson.guidelines.paragraphs.size)
        assertEquals(2, lesson.guidelines.bullets.size)

        assertEquals(1, lesson.sections.size)
        val section = lesson.sections[0]
        assertEquals("Introduction to NPLs", section.title)
        assertEquals("Write an introduction in 100–130 words.", section.task)
        assertEquals(300, section.timerSeconds)
        assertEquals("A Non-Performing Loan (NPL) represents a delinquent debt instrument.", section.modelAnswer)

        assertEquals(2, section.guide.vocabulary.size)
        assertEquals("Asset quality deterioration", section.guide.vocabulary[0].term)
        assertEquals("ঋণের গুণগত মানের অবনতি", section.guide.vocabulary[0].meaningBn)

        assertEquals(2, lesson.finalReview.items.size)
    }

    @Test
    fun testMultipleLessonsInSingleDocument() {
        val raw = """
LESSON TITLE: Lesson One
SECTION 1
SECTION TITLE: Sec 1
SECTION END
LESSON END

LESSON TITLE: Lesson Two
SECTION 1
SECTION TITLE: Sec 2
SECTION END
LESSON END
        """.trimIndent()

        val previews = parser.parseContent(raw)
        assertEquals(2, previews.size)
        assertEquals("Lesson One", previews[0].lesson.title)
        assertEquals("Lesson Two", previews[1].lesson.title)
    }

    @Test
    fun testJsonTopicPackParsing() {
        val json = """
{
  "schemaVersion": 1,
  "topicId": "BD-BANK-NPL-001",
  "title": "Resolution of Non-Performing Loans",
  "titleBn": "খেলাপি ঋণ সমস্যার সমাধান",
  "category": "Banking",
  "writingTypes": ["Focus Writing"],
  "guidelines": {
    "paragraphs": ["General guideline."],
    "bullets": ["Bullet point 1"]
  },
  "sections": [
    {
      "order": 1,
      "title": "Introduction to NPLs",
      "type": "writing_task",
      "task": "Write introduction.",
      "timerSeconds": 300,
      "guide": {
        "paragraphs": ["Guide text."],
        "vocabulary": [
          { "term": "Moral hazard", "meaningBn": "নৈতিক অবক্ষয়জনিত ঝুঁকি" }
        ]
      },
      "answer": {
        "text": "Model answer text."
      }
    }
  ],
  "finalReview": {
    "items": ["Review checklist."]
  }
}
        """.trimIndent()

        val previews = parser.parseContent(json)
        assertEquals(1, previews.size)
        val lesson = previews[0].lesson
        assertEquals("Resolution of Non-Performing Loans", lesson.title)
        assertEquals("খেলাপি ঋণ সমস্যার সমাধান", lesson.titleBn)
        assertEquals("Banking", lesson.category)
        assertEquals(1, lesson.sections.size)
        assertEquals("নৈতিক অবক্ষয়জনিত ঝুঁকি", lesson.sections[0].guide.vocabulary[0].meaningBn)
    }

    @Test
    fun testCompletionCalculation() {
        val sections = listOf(
            LessonSection(id = "s1", lessonId = "l1", order = 1, title = "S1", isCompleted = true),
            LessonSection(id = "s2", lessonId = "l1", order = 2, title = "S2", isCompleted = false, isSkipped = true),
            LessonSection(id = "s3", lessonId = "l1", order = 3, title = "S3", isCompleted = true),
            LessonSection(id = "s4", lessonId = "l1", order = 4, title = "S4", isCompleted = false)
        )
        val lesson = Lesson(id = "l1", title = "L1", sections = sections)

        assertEquals(2, lesson.completedSectionCount)
        assertEquals(4, lesson.totalSectionCount)
        assertEquals(50, lesson.completionPercentage)
    }

    @Test
    fun testExportFormattedTextGeneration() {
        val lesson = Lesson(
            id = "l1",
            title = "Test Lesson",
            type = "Focus Writing",
            category = "Economics",
            sections = listOf(
                LessonSection(
                    id = "s1",
                    lessonId = "l1",
                    order = 1,
                    title = "Intro",
                    userDraft = "This is my essay draft.",
                    modelAnswer = "This is model answer."
                )
            )
        )

        val userOnlyText = ExportManager.generateFormattedText(lesson, ExportMode.USER_WRITING_ONLY)
        assertTrue(userOnlyText.contains("[MY WRITING]"))
        assertTrue(userOnlyText.contains("This is my essay draft."))
        assertTrue(!userOnlyText.contains("[MODEL ANSWER]"))

        val bothText = ExportManager.generateFormattedText(lesson, ExportMode.BOTH)
        assertTrue(bothText.contains("[MY WRITING]"))
        assertTrue(bothText.contains("[MODEL ANSWER]"))
    }

    @Test
    fun testShorthandFormatParsing() {
        val shorthandText = """
@Type: Title: Renewable Energy in Bangladesh
-Description: Assessing green energy transition.
-Guidelines:
Focus on solar and wind potential.
- Need realistic targets
- Grid modernization

Section1: Present Energy Matrix
Guide:
Detail current fossil fuel dependence.
Vocabulary:
- Energy transition -> জ্বালানি রূপান্তর
Answer:
Bangladesh currently relies heavily on natural gas and imported LNG.

Section2:
Solar and Wind Potential
Guide:
Examine geographical limitations.
Answer:
Land scarcity presents a constraint for large-scale solar farms.
        """.trimIndent()

        val previews = parser.parseContent(shorthandText)
        assertEquals(1, previews.size)
        val lesson = previews[0].lesson
        assertEquals("Renewable Energy in Bangladesh", lesson.title)
        assertEquals("Assessing green energy transition.", lesson.description)
        assertEquals(2, lesson.sections.size)

        assertEquals("Present Energy Matrix", lesson.sections[0].title)
        assertTrue(lesson.sections[0].modelAnswer.contains("Bangladesh currently relies"))
        assertEquals(1, lesson.sections[0].guide.vocabulary.size)
        assertEquals("Energy transition", lesson.sections[0].guide.vocabulary[0].term)
        assertEquals("জ্বালানি রূপান্তর", lesson.sections[0].guide.vocabulary[0].meaningBn)

        assertEquals("Solar and Wind Potential", lesson.sections[1].title)
        assertTrue(lesson.sections[1].modelAnswer.contains("Land scarcity presents"))
    }
}
