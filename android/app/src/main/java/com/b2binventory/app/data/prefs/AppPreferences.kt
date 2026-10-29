package com.b2binventory.app.data.prefs

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private val Context.prefsStore by preferencesDataStore(name = "app_prefs")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** A language the app is translated into; an empty [tag] means "follow the phone". */
data class AppLanguage(val tag: String, val nativeName: String)

/**
 * Device-level preferences. Unlike the session these survive sign-out, so the theme and
 * language stay the same for whoever uses the phone next.
 */
class AppPreferences(private val context: Context,private val scope: CoroutineScope) {

    private val themeKey = stringPreferencesKey("theme_mode")

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    init {
    // Read in the background instead of blocking the constructor.
        scope.launch {                 // ← needs a CoroutineScope
            _themeMode.value = context.prefsStore.data.first()[themeKey]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.prefsStore.edit { it[themeKey] = mode.name }
        _themeMode.value = mode
    }

    /** The chosen language tag, or "" when following the phone. AppCompat persists it for us. */
    val languageTag: String
        get() = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore(',')

    fun setLanguage(tag: String) {
        AppCompatDelegate.setApplicationLocales(
            if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
        )
    }

    companion object {
        val languages = listOf(
            AppLanguage("en", "English"),
            AppLanguage("hi", "हिन्दी"),
            AppLanguage("bn", "বাংলা"),
            AppLanguage("ta", "தமிழ்"),
            AppLanguage("mr", "मराठी"),
            AppLanguage("ar", "العربية"),
            AppLanguage("ur", "اردو"),
        )
    }
}
