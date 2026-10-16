package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "microwrite_preferences")

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val DEFAULT_TIMER_MINUTES = intPreferencesKey("default_timer_minutes")
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val SHOW_WORD_COUNT = booleanPreferencesKey("show_word_count")
        val ENFORCE_WORD_LIMIT = booleanPreferencesKey("enforce_word_limit")
        val LAST_OPENED_LESSON_ID = stringPreferencesKey("last_opened_lesson_id")
        val LAST_OPENED_SECTION_ID = stringPreferencesKey("last_opened_section_id")
        val SEED_LOADED = booleanPreferencesKey("seed_loaded_v1")
        val THEME_MODE = stringPreferencesKey("theme_mode") // "system", "light", "dark"
        val COLOR_PALETTE = stringPreferencesKey("color_palette") // "blue", "orange", "green"
    }

    val themeMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.THEME_MODE] ?: "system"
    }

    val colorPalette: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.COLOR_PALETTE] ?: "blue"
    }

    val defaultTimerMinutes: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.DEFAULT_TIMER_MINUTES] ?: 5
    }

    val fontScale: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[Keys.FONT_SCALE] ?: 1.0f
    }

    val showWordCount: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.SHOW_WORD_COUNT] ?: true
    }

    val enforceWordLimit: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.ENFORCE_WORD_LIMIT] ?: false
    }

    val lastOpenedLessonId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.LAST_OPENED_LESSON_ID]
    }

    val isSeedLoaded: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.SEED_LOADED] ?: false
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode
        }
    }

    suspend fun setColorPalette(palette: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.COLOR_PALETTE] = palette
        }
    }

    suspend fun setDefaultTimerMinutes(minutes: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DEFAULT_TIMER_MINUTES] = minutes
        }
    }

    suspend fun setFontScale(scale: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.FONT_SCALE] = scale
        }
    }

    suspend fun setShowWordCount(show: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SHOW_WORD_COUNT] = show
        }
    }

    suspend fun setEnforceWordLimit(enforce: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ENFORCE_WORD_LIMIT] = enforce
        }
    }

    suspend fun setLastOpened(lessonId: String, sectionId: String? = null) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_OPENED_LESSON_ID] = lessonId
            if (sectionId != null) {
                prefs[Keys.LAST_OPENED_SECTION_ID] = sectionId
            }
        }
    }

    suspend fun setSeedLoaded(loaded: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SEED_LOADED] = loaded
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
