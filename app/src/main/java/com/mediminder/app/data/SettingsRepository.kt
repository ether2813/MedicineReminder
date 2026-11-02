package com.mediminder.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import com.mediminder.app.data.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val snoozeMinutes: Int = 10,
    val repeatMinutes: Int = 10,
    val repeatCount: Int = 3,
    val language: String = "en",
)

/**
 * Small settings store backed by SharedPreferences so it can be read synchronously from
 * broadcast receivers and at application start.
 */
class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state.asStateFlow()
    val current: AppSettings get() = _state.value

    // Held as a field: SharedPreferences only keeps a weak reference to listeners.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _state.value = read()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    private fun read() = AppSettings(
        themeMode = runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null)!!) }
            .getOrDefault(ThemeMode.SYSTEM),
        dynamicColor = prefs.getBoolean(KEY_DYNAMIC, false),
        snoozeMinutes = prefs.getInt(KEY_SNOOZE, 10),
        repeatMinutes = prefs.getInt(KEY_REPEAT_MIN, 10),
        repeatCount = prefs.getInt(KEY_REPEAT_COUNT, 3),
        language = prefs.getString(KEY_LANGUAGE, "en") ?: "en",
    )

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit { putString(KEY_THEME, mode.name) }
        applyThemeMode(mode)
    }

    fun setDynamicColor(enabled: Boolean) = prefs.edit { putBoolean(KEY_DYNAMIC, enabled) }
    fun setSnoozeMinutes(minutes: Int) = prefs.edit { putInt(KEY_SNOOZE, minutes) }
    fun setRepeatMinutes(minutes: Int) = prefs.edit { putInt(KEY_REPEAT_MIN, minutes) }
    fun setRepeatCount(count: Int) = prefs.edit { putInt(KEY_REPEAT_COUNT, count) }
    fun setLanguage(tag: String) = prefs.edit { putString(KEY_LANGUAGE, tag) }

    var notificationPermissionAsked: Boolean
        get() = prefs.getBoolean(KEY_NOTIF_ASKED, false)
        set(value) = prefs.edit { putBoolean(KEY_NOTIF_ASKED, value) }

    fun restore(settings: AppSettings) {
        prefs.edit {
            putString(KEY_THEME, settings.themeMode.name)
            putBoolean(KEY_DYNAMIC, settings.dynamicColor)
            putInt(KEY_SNOOZE, settings.snoozeMinutes)
            putInt(KEY_REPEAT_MIN, settings.repeatMinutes)
            putInt(KEY_REPEAT_COUNT, settings.repeatCount)
        }
        applyThemeMode(settings.themeMode)
    }

    fun applyThemeMode(mode: ThemeMode = current.themeMode) {
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            },
        )
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_DYNAMIC = "dynamic_color"
        const val KEY_SNOOZE = "snooze_minutes"
        const val KEY_REPEAT_MIN = "repeat_minutes"
        const val KEY_REPEAT_COUNT = "repeat_count"
        const val KEY_LANGUAGE = "language"
        const val KEY_NOTIF_ASKED = "notif_permission_asked"
    }
}
