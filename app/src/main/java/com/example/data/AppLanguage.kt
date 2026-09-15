package com.example.data

/**
 * Represents the lifecycle status for language packs.
 * The 4 bundled core languages (English, Hindi, Urdu, Tamil) are always INSTALLED/BUNDLED.
 * Prepared for future downloadable language extension.
 */
enum class LanguagePackStatus {
    AVAILABLE,
    DOWNLOADING,
    INSTALLED,
    FAILED
}

/**
 * Language metadata model representing language pack properties.
 */
data class LanguagePackMetadata(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val localeTag: String,
    val isRtl: Boolean = false,
    val isBundled: Boolean = true,
    val packVersion: Int = 1,
    val status: LanguagePackStatus = LanguagePackStatus.INSTALLED,
    val downloadUrl: String? = null,
    val sha256Checksum: String? = null,
    val downloadProgress: Float = 0f,
    val errorMessage: String? = null
)

enum class AppLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val localeTag: String,
    val isRtl: Boolean = false,
    val isBundled: Boolean = true,
    val status: LanguagePackStatus = LanguagePackStatus.INSTALLED
) {
    ENGLISH("en", "English", "English", "en", isRtl = false, isBundled = true, status = LanguagePackStatus.INSTALLED),
    HINDI("hi", "Hindi", "हिन्दी", "hi", isRtl = false, isBundled = true, status = LanguagePackStatus.INSTALLED),
    URDU("ur", "Urdu", "اردو", "ur", isRtl = true, isBundled = true, status = LanguagePackStatus.INSTALLED),
    TAMIL("ta", "Tamil", "தமிழ்", "ta", isRtl = false, isBundled = true, status = LanguagePackStatus.INSTALLED);

    fun toMetadata(): LanguagePackMetadata {
        return LanguagePackMetadata(
            code = code,
            displayName = displayName,
            nativeName = nativeName,
            localeTag = localeTag,
            isRtl = isRtl,
            isBundled = isBundled,
            status = status
        )
    }

    companion object {
        val ALL = listOf(ENGLISH, HINDI, URDU, TAMIL)

        fun fromCode(code: String?): AppLanguage {
            if (code == null || code.equals("hinglish", ignoreCase = true)) return ENGLISH
            return entries.firstOrNull {
                it.code.equals(code, ignoreCase = true) ||
                it.localeTag.equals(code, ignoreCase = true)
            } ?: ENGLISH
        }
    }
}

