package com.Khalil.trackster.ui.common

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object AppSettings {
    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"
    private const val PREFS = "trackster_settings"
    private const val KEY_THEME = "theme"

    // Purpose: Applies the saved theme choice, or the device theme, when the app starts.
    fun applySavedTheme(context: Context) {
        AppCompatDelegate.setDefaultNightMode(when (theme(context)) {
            THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        })
    }

    // Purpose: Returns the user's saved theme preference.
    fun theme(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM

    // Purpose: Stores and applies a new theme preference.
    fun setTheme(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_THEME, value).apply()
        applySavedTheme(context)
    }

    // Purpose: Returns the app's currently active language tag.
    fun languageTag(): String = AppCompatDelegate.getApplicationLocales().toLanguageTags()

    // Purpose: Changes the app language and applies RTL layout for Hebrew.
    fun setLanguage(tag: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }
}
