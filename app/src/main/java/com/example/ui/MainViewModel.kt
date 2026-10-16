package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.Lesson
import com.example.data.model.LessonSection
import com.example.data.model.ParsedLessonPreview
import com.example.data.parser.ResourceParser
import com.example.data.repository.LessonRepository
import com.example.data.repository.PreferencesRepository
import com.example.data.seed.SeedLessons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.os.SystemClock
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.io.InputStream

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val lessonRepository = LessonRepository(database.lessonDao(), database.sectionDao())
    val preferencesRepository = PreferencesRepository(application)
    private val resourceParser = ResourceParser()

    // Preferences
    val themeMode: StateFlow<String> = preferencesRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    val colorPalette: StateFlow<String> = preferencesRepository.colorPalette
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "blue")

    val defaultTimerMinutes: StateFlow<Int> = preferencesRepository.defaultTimerMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 5)

    val fontScale: StateFlow<Float> = preferencesRepository.fontScale
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    val showWordCount: StateFlow<Boolean> = preferencesRepository.showWordCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val enforceWordLimit: StateFlow<Boolean> = preferencesRepository.enforceWordLimit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // All lessons
    val allLessons: StateFlow<List<Lesson>> = lessonRepository.allLessons
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Bookmarks
    val bookmarkedLessons: StateFlow<List<Lesson>> = lessonRepository.bookmarkedLessons
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookmarkedSections: StateFlow<List<LessonSection>> = lessonRepository.bookmarkedSections
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Search & Filter
    val searchQuery = MutableStateFlow("")
    val selectedCategoryFilter = MutableStateFlow<String?>(null)
    val selectedTypeFilter = MutableStateFlow<String?>(null)

    val filteredLessons: StateFlow<List<Lesson>> = combine(
        allLessons,
        searchQuery,
        selectedCategoryFilter,
        selectedTypeFilter
    ) { lessons, query, category, type ->
        lessons.filter { lesson ->
            val matchesQuery = query.isBlank() ||
                    lesson.title.contains(query, ignoreCase = true) ||
                    (lesson.titleBn != null && lesson.titleBn.contains(query, ignoreCase = true)) ||
                    lesson.description.contains(query, ignoreCase = true) ||
                    lesson.category.contains(query, ignoreCase = true) ||
                    lesson.type.contains(query, ignoreCase = true)

            val matchesCategory = category == null || lesson.category.equals(category, ignoreCase = true)
            val matchesType = type == null || lesson.type.equals(type, ignoreCase = true)

            matchesQuery && matchesCategory && matchesType
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active studying state
    private val _currentLessonId = MutableStateFlow<String?>(null)
    val currentLessonId = _currentLessonId.asStateFlow()

    private val _currentLesson = MutableStateFlow<Lesson?>(null)
    val currentLesson = _currentLesson.asStateFlow()

    private val _currentSectionId = MutableStateFlow<String?>(null)
    val currentSectionId = _currentSectionId.asStateFlow()

    private val _currentSection = MutableStateFlow<LessonSection?>(null)
    val currentSection = _currentSection.asStateFlow()

    // Import state
    private val _importPreviews = MutableStateFlow<List<ParsedLessonPreview>>(emptyList())
    val importPreviews = _importPreviews.asStateFlow()

    private val _importError = MutableStateFlow<String?>(null)
    val importError = _importError.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting = _isImporting.asStateFlow()

    init {
        // Seed initial lessons if first launch
        viewModelScope.launch {
            val isSeeded = preferencesRepository.isSeedLoaded.first()
            if (!isSeeded) {
                lessonRepository.saveLesson(SeedLessons.nplLesson)
                lessonRepository.saveLesson(SeedLessons.automationLesson)
                preferencesRepository.setSeedLoaded(true)
            }
        }
    }

    fun openLesson(lessonId: String) {
        _currentLessonId.value = lessonId
        viewModelScope.launch {
            lessonRepository.updateLastOpened(lessonId)
            preferencesRepository.setLastOpened(lessonId)
            val lesson = lessonRepository.getLessonDirect(lessonId)
            _currentLesson.value = lesson
        }
    }

    fun openSection(lessonId: String, sectionId: String) {
        _currentLessonId.value = lessonId
        _currentSectionId.value = sectionId
        viewModelScope.launch {
            preferencesRepository.setLastOpened(lessonId, sectionId)
            val lesson = lessonRepository.getLessonDirect(lessonId)
            _currentLesson.value = lesson
            val sec = lesson?.sections?.find { it.id == sectionId }
            _currentSection.value = sec
        }
    }

    fun continueLesson(lesson: Lesson): LessonSection? {
        // Find first incomplete section, or first section
        return lesson.sections.firstOrNull { !it.isCompleted } ?: lesson.sections.firstOrNull()
    }

    fun saveDraft(sectionId: String, draft: String) {
        // Update local memory immediately for snappy UI
        _currentSection.value = _currentSection.value?.copy(userDraft = draft)
        _currentLesson.value = _currentLesson.value?.let { l ->
            val updatedSections = l.sections.map {
                if (it.id == sectionId) it.copy(userDraft = draft) else it
            }
            l.copy(sections = updatedSections)
        }

        viewModelScope.launch {
            lessonRepository.updateSectionDraft(sectionId, draft)
        }
    }

    fun markSectionDone(sectionId: String) {
        _currentSection.value = _currentSection.value?.copy(isCompleted = true, isSkipped = false)
        _currentLesson.value = _currentLesson.value?.let { l ->
            val updatedSections = l.sections.map {
                if (it.id == sectionId) it.copy(isCompleted = true, isSkipped = false) else it
            }
            l.copy(sections = updatedSections)
        }

        viewModelScope.launch {
            lessonRepository.markSectionDone(sectionId)
        }
    }

    fun markSectionSkipped(sectionId: String) {
        _currentSection.value = _currentSection.value?.copy(isCompleted = false, isSkipped = true)
        _currentLesson.value = _currentLesson.value?.let { l ->
            val updatedSections = l.sections.map {
                if (it.id == sectionId) it.copy(isCompleted = false, isSkipped = true) else it
            }
            l.copy(sections = updatedSections)
        }

        viewModelScope.launch {
            lessonRepository.markSectionSkipped(sectionId)
        }
    }

    fun toggleLessonBookmark(lessonId: String) {
        val current = _currentLesson.value?.isBookmarked ?: false
        _currentLesson.value = _currentLesson.value?.copy(isBookmarked = !current)
        viewModelScope.launch {
            lessonRepository.toggleLessonBookmark(lessonId, current)
        }
    }

    fun toggleSectionBookmark(sectionId: String) {
        val current = _currentSection.value?.isBookmarked ?: false
        _currentSection.value = _currentSection.value?.copy(isBookmarked = !current)
        viewModelScope.launch {
            lessonRepository.toggleSectionBookmark(sectionId, current)
        }
    }

    fun resetLessonProgress(lessonId: String) {
        viewModelScope.launch {
            lessonRepository.resetLessonProgress(lessonId)
            val updated = lessonRepository.getLessonDirect(lessonId)
            _currentLesson.value = updated
        }
    }

    fun clearLessonDrafts(lessonId: String) {
        viewModelScope.launch {
            lessonRepository.clearLessonDrafts(lessonId)
            val updated = lessonRepository.getLessonDirect(lessonId)
            _currentLesson.value = updated
        }
    }

    fun deleteLesson(lessonId: String) {
        viewModelScope.launch {
            lessonRepository.deleteLesson(lessonId)
            if (_currentLessonId.value == lessonId) {
                _currentLesson.value = null
                _currentLessonId.value = null
                _currentSection.value = null
            }
        }
    }

    // --- Import methods ---

    fun parseTextForImport(text: String) {
        _isImporting.value = true
        _importError.value = null
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val previews = resourceParser.parseContent(text)
                if (previews.isEmpty()) {
                    _importError.value = "No lessons found in input text. Please check format."
                } else {
                    _importPreviews.value = previews
                }
            } catch (e: Exception) {
                _importError.value = "Failed to parse text: ${e.localizedMessage}"
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun parseDocxForImport(inputStream: InputStream) {
        _isImporting.value = true
        _importError.value = null
        viewModelScope.launch {
            try {
                val previews = resourceParser.parseDocx(inputStream)
                if (previews.isEmpty()) {
                    _importError.value = "No valid lessons could be extracted from the DOCX file."
                } else {
                    _importPreviews.value = previews
                }
            } catch (e: Exception) {
                _importError.value = "Error reading DOCX: ${e.localizedMessage}"
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun fetchGoogleDocsForImport(url: String) {
        _isImporting.value = true
        _importError.value = null
        viewModelScope.launch {
            val (previews, error) = resourceParser.fetchAndParseGoogleDocs(url)
            if (error != null) {
                _importError.value = error
            } else {
                _importPreviews.value = previews
            }
            _isImporting.value = false
        }
    }

    fun togglePreviewSelection(index: Int) {
        val currentList = _importPreviews.value.toMutableList()
        if (index in currentList.indices) {
            val item = currentList[index]
            currentList[index] = item.copy(isSelectedForImport = !item.isSelectedForImport)
            _importPreviews.value = currentList
        }
    }

    fun commitImport(onSuccess: (count: Int) -> Unit) {
        val toSave = _importPreviews.value.filter { it.isSelectedForImport && it.isValid }.map { it.lesson }
        if (toSave.isEmpty()) return

        viewModelScope.launch {
            lessonRepository.saveLessons(toSave)
            _importPreviews.value = emptyList()
            onSuccess(toSave.size)
        }
    }

    fun clearImportPreview() {
        _importPreviews.value = emptyList()
        _importError.value = null
    }

    // --- Settings & Data Management ---

    fun setDefaultTimerMinutes(minutes: Int) {
        viewModelScope.launch {
            preferencesRepository.setDefaultTimerMinutes(minutes)
        }
    }

    fun setFontScale(scale: Float) {
        viewModelScope.launch {
            preferencesRepository.setFontScale(scale)
        }
    }

    fun setShowWordCount(show: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setShowWordCount(show)
        }
    }

    fun setEnforceWordLimit(enforce: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setEnforceWordLimit(enforce)
        }
    }

    suspend fun exportBackupJson(): String {
        return lessonRepository.exportFullBackupJson()
    }

    fun restoreBackup(json: String, onResult: (Boolean, Int) -> Unit) {
        viewModelScope.launch {
            val res = lessonRepository.restoreBackupJson(json)
            if (res.isSuccess) {
                onResult(true, res.getOrNull() ?: 0)
            } else {
                onResult(false, 0)
            }
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
        }
    }

    fun setColorPalette(palette: String) {
        viewModelScope.launch {
            preferencesRepository.setColorPalette(palette)
        }
    }

    // --- Resilient Timer Engine (Drift-free, survives orientation change) ---
    private val _timerRemainingSeconds = MutableStateFlow(0)
    val timerRemainingSeconds = _timerRemainingSeconds.asStateFlow()

    private val _timerTotalSeconds = MutableStateFlow(0)
    val timerTotalSeconds = _timerTotalSeconds.asStateFlow()

    private val _timerIsRunning = MutableStateFlow(false)
    val timerIsRunning = _timerIsRunning.asStateFlow()

    private val _timerIsFinished = MutableStateFlow(false)
    val timerIsFinished = _timerIsFinished.asStateFlow()

    private val _focusLockEnabled = MutableStateFlow(false)
    val focusLockEnabled = _focusLockEnabled.asStateFlow()

    private var timerJob: Job? = null
    private var timerTargetElapsedMillis: Long = 0L

    fun initTimer(durationSeconds: Int) {
        if (!_timerIsRunning.value && (_timerTotalSeconds.value != durationSeconds || _timerRemainingSeconds.value == 0)) {
            _timerTotalSeconds.value = durationSeconds
            _timerRemainingSeconds.value = durationSeconds
            _timerIsFinished.value = false
        }
    }

    fun startTimer() {
        if (_timerRemainingSeconds.value <= 0) return
        _timerIsRunning.value = true
        _timerIsFinished.value = false
        timerTargetElapsedMillis = SystemClock.elapsedRealtime() + (_timerRemainingSeconds.value * 1000L)
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_timerIsRunning.value) {
                val now = SystemClock.elapsedRealtime()
                val remainingMillis = timerTargetElapsedMillis - now
                val remainingSec = (remainingMillis / 1000L).coerceAtLeast(0L).toInt()
                _timerRemainingSeconds.value = remainingSec
                if (remainingSec <= 0) {
                    _timerIsRunning.value = false
                    _timerIsFinished.value = true
                    break
                }
                delay(250L)
            }
        }
    }

    fun pauseTimer() {
        _timerIsRunning.value = false
        timerJob?.cancel()
    }

    fun resetTimer(durationSeconds: Int = _timerTotalSeconds.value) {
        _timerIsRunning.value = false
        timerJob?.cancel()
        _timerRemainingSeconds.value = durationSeconds
        _timerTotalSeconds.value = durationSeconds
        _timerIsFinished.value = false
    }

    fun addTimerMinute() {
        val newSec = _timerRemainingSeconds.value + 60
        _timerRemainingSeconds.value = newSec
        _timerTotalSeconds.value = maxOf(_timerTotalSeconds.value, newSec)
        if (_timerIsRunning.value) {
            timerTargetElapsedMillis += 60_000L
        }
        _timerIsFinished.value = false
    }

    fun setFocusLock(enabled: Boolean) {
        _focusLockEnabled.value = enabled
    }

    // --- Undo Action Support ---
    data class LastSectionAction(
        val sectionId: String,
        val wasCompleted: Boolean,
        val wasSkipped: Boolean
    )

    private val _lastSectionAction = MutableStateFlow<LastSectionAction?>(null)
    val lastSectionAction = _lastSectionAction.asStateFlow()

    fun recordSectionAction(sectionId: String, prevCompleted: Boolean, prevSkipped: Boolean) {
        _lastSectionAction.value = LastSectionAction(sectionId, prevCompleted, prevSkipped)
    }

    fun undoLastSectionAction(onUndone: (String) -> Unit = {}) {
        val action = _lastSectionAction.value ?: return
        viewModelScope.launch {
            lessonRepository.setSectionStatus(action.sectionId, action.wasCompleted, action.wasSkipped)
            _currentSection.value = _currentSection.value?.let {
                if (it.id == action.sectionId) it.copy(isCompleted = action.wasCompleted, isSkipped = action.wasSkipped) else it
            }
            _currentLesson.value = _currentLesson.value?.let { l ->
                val updated = l.sections.map {
                    if (it.id == action.sectionId) it.copy(isCompleted = action.wasCompleted, isSkipped = action.wasSkipped) else it
                }
                l.copy(sections = updated)
            }
            _lastSectionAction.value = null
            onUndone(action.sectionId)
        }
    }

    fun deleteAllData(onComplete: () -> Unit) {
        viewModelScope.launch {
            lessonRepository.deleteAllData()
            preferencesRepository.clearAll()
            _currentLesson.value = null
            _currentSection.value = null
            onComplete()
        }
    }
}
