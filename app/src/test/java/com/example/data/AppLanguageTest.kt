package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLanguageTest {

    @Test
    fun testLanguagesListContainsExactlyFourLanguages() {
        val languages = AppLanguage.ALL
        assertEquals(4, languages.size)
        assertEquals(listOf(AppLanguage.ENGLISH, AppLanguage.HINDI, AppLanguage.URDU, AppLanguage.TAMIL), languages)
    }

    @Test
    fun testHinglishRemovalAndFallbackToEnglish() {
        // Hinglish code or tags must safely fallback to English
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromCode("hinglish"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromCode("HINGLISH"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromCode("hi-Latn"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromCode("invalid_code"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromCode(null))
    }

    @Test
    fun testValidLanguageResolution() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromCode("en"))
        assertEquals(AppLanguage.HINDI, AppLanguage.fromCode("hi"))
        assertEquals(AppLanguage.URDU, AppLanguage.fromCode("ur"))
        assertEquals(AppLanguage.TAMIL, AppLanguage.fromCode("ta"))
    }

    @Test
    fun testRtlConfiguration() {
        assertTrue(AppLanguage.URDU.isRtl)
        assertFalse(AppLanguage.ENGLISH.isRtl)
        assertFalse(AppLanguage.HINDI.isRtl)
        assertFalse(AppLanguage.TAMIL.isRtl)
    }

    @Test
    fun testLanguagePackFoundationMetadata() {
        for (lang in AppLanguage.ALL) {
            val metadata = lang.toMetadata()
            assertEquals(lang.code, metadata.code)
            assertEquals(lang.displayName, metadata.displayName)
            assertEquals(lang.nativeName, metadata.nativeName)
            assertEquals(lang.localeTag, metadata.localeTag)
            assertEquals(lang.isRtl, metadata.isRtl)
            assertTrue(metadata.isBundled)
            assertEquals(LanguagePackStatus.INSTALLED, metadata.status)
        }
    }

    @Test
    fun testAppStringsResolutionForAllFourLanguages() {
        val testKey = "nav_dashboard"
        assertEquals("Dashboard", AppStrings.getString(testKey, AppLanguage.ENGLISH))
        assertEquals("डैशबोर्ड", AppStrings.getString(testKey, AppLanguage.HINDI))
        assertEquals("ڈیش بورڈ", AppStrings.getString(testKey, AppLanguage.URDU))
        assertEquals("டாஷ்போர்டு", AppStrings.getString(testKey, AppLanguage.TAMIL))
    }
}
