package com.mediminder.app.util

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.mediminder.app.container
import java.util.Locale

/**
 * Per-app language handling. English is the default; Bengali is the alternative.
 * On Android 13+ the platform stores the choice; on older versions we keep it in our settings.
 */
object LocaleHelper {
    val supported = listOf("en", "bn")

    fun currentLanguage(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val lm = context.getSystemService(LocaleManager::class.java)
            val tag = lm?.applicationLocales?.takeIf { !it.isEmpty }?.get(0)?.language
            return if (tag != null && tag in supported) tag else "en"
        }
        return context.container.settings.current.language
    }

    fun applyOnStart(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val lm = context.getSystemService(LocaleManager::class.java) ?: return
            if (lm.applicationLocales.isEmpty) lm.applicationLocales = LocaleList.forLanguageTags("en")
        } else {
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(context.container.settings.current.language),
            )
        }
    }

    fun setLanguage(context: Context, tag: String) {
        context.container.settings.setLanguage(tag)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    /** Context whose resources use the app language (needed for notifications on Android < 13). */
    fun localized(context: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return context
        val locale = Locale.forLanguageTag(currentLanguage(context))
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
