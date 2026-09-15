package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object AppLanguageManager {
    private const val PREFS_NAME = "lifefresh_language_prefs"
    private const val KEY_LANGUAGE = "selected_language_code"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getLanguageCode(context: Context): String {
        val prefs = getPrefs(context)
        val code = prefs.getString(KEY_LANGUAGE, AppLanguage.ENGLISH.code) ?: AppLanguage.ENGLISH.code
        if (code.equals("hinglish", ignoreCase = true)) {
            // Safely migrate stored Hinglish preference to English
            prefs.edit().putString(KEY_LANGUAGE, AppLanguage.ENGLISH.code).apply()
            return AppLanguage.ENGLISH.code
        }
        return code
    }

    fun getLanguage(context: Context): AppLanguage {
        val code = getLanguageCode(context)
        return AppLanguage.fromCode(code)
    }

    fun getActiveLanguageMetadata(context: Context): LanguagePackMetadata {
        val code = getLanguageCode(context)
        // Check if bundled
        val bundled = AppLanguage.ALL.firstOrNull { it.code.equals(code, ignoreCase = true) }
        if (bundled != null) {
            return bundled.toMetadata()
        }
        // Check if downloaded installed pack
        val custom = LanguagePackManager.getMetadata(code)
        if (custom != null && custom.status == LanguagePackStatus.INSTALLED) {
            return custom
        }
        // Fallback to English
        return AppLanguage.ENGLISH.toMetadata()
    }

    fun setLanguage(context: Context, language: AppLanguage) {
        getPrefs(context).edit().putString(KEY_LANGUAGE, language.code).apply()
        applyLocale(context, language.toMetadata())
    }

    fun setLanguage(context: Context, metadata: LanguagePackMetadata) {
        getPrefs(context).edit().putString(KEY_LANGUAGE, metadata.code).apply()
        applyLocale(context, metadata)
    }

    fun setLanguage(context: Context, languageCode: String) {
        val meta = LanguagePackManager.getMetadata(languageCode) ?: AppLanguage.fromCode(languageCode).toMetadata()
        setLanguage(context, meta)
    }

    fun applyLocale(context: Context, language: AppLanguage) {
        applyLocale(context, language.toMetadata())
    }

    fun applyLocale(context: Context, metadata: LanguagePackMetadata) {
        val locale = getJavaLocale(metadata.localeTag)
        Locale.setDefault(locale)

        val resources = context.resources
        val config = Configuration(resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLayoutDirection(locale)
        }
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    fun wrapContext(context: Context, language: AppLanguage): Context {
        return wrapContext(context, language.toMetadata())
    }

    fun wrapContext(context: Context, metadata: LanguagePackMetadata): Context {
        val locale = getJavaLocale(metadata.localeTag)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLayoutDirection(locale)
        }
        return context.createConfigurationContext(config)
    }

    fun getJavaLocale(language: AppLanguage): Locale {
        return getJavaLocale(language.localeTag)
    }

    fun getJavaLocale(localeTag: String): Locale {
        return when (localeTag.lowercase()) {
            "en" -> Locale("en")
            "hi" -> Locale("hi")
            "ur" -> Locale("ur")
            "ta" -> Locale("ta")
            else -> {
                try {
                    Locale.forLanguageTag(localeTag)
                } catch (e: Exception) {
                    Locale(localeTag)
                }
            }
        }
    }

    /**
     * Checks if a language is available for immediate selection (either base-bundled or Play-installed).
     */
    fun isLanguageAvailable(context: Context, language: AppLanguage): Boolean {
        return PlayLanguageDeliveryManager.getInstance(context).isLanguageAvailable(language)
    }

    /**
     * Returns the set of language codes directly reported as installed by Play Feature Delivery.
     */
    fun getInstalledPlayLanguages(context: Context): Set<String> {
        return PlayLanguageDeliveryManager.getInstance(context).getInstalledPlayLanguages()
    }
}
