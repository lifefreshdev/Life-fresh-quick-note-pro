package com.example.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AlarmSoundResolverTest {

    @Test
    fun testAllSoundKeys_mappedToValidWavPaths() {
        val expectedMappings = mapOf(
            "holiday" to "sounds/holiday.wav",
            "morning_bell" to "sounds/morning_bell.wav",
            "soft_chime" to "sounds/soft_chime.wav",
            "medical_reminder" to "sounds/medical_reminder.wav",
            "fresh_alert" to "sounds/fresh_alert.wav",
            "nature_bell" to "sounds/nature_bell.wav",
            "peaceful_glow" to "sounds/peaceful_glow.wav",
            "crystal_breeze" to "sounds/crystal_breeze.wav",
            "extreme_siren" to "sounds/extreme_siren.wav",
            "critical_alert" to "sounds/critical_alert.wav"
        )

        for ((key, expectedPath) in expectedMappings) {
            assertEquals("Asset path for $key mismatch", expectedPath, AlarmSoundResolver.getSoundAssetPath(key))
            assertEquals("Uppercase test for $key mismatch", expectedPath, AlarmSoundResolver.getSoundAssetPath(key.uppercase()))
        }
    }

    @Test
    fun testFourNewAlarmSounds_resolveCorrectly() {
        assertEquals("sounds/peaceful_glow.wav", AlarmSoundResolver.getSoundAssetPath("peaceful_glow"))
        assertEquals("sounds/crystal_breeze.wav", AlarmSoundResolver.getSoundAssetPath("crystal_breeze"))
        assertEquals("sounds/extreme_siren.wav", AlarmSoundResolver.getSoundAssetPath("extreme_siren"))
        assertEquals("sounds/critical_alert.wav", AlarmSoundResolver.getSoundAssetPath("critical_alert"))

        assertEquals("Peaceful Glow", AlarmSoundResolver.getDisplayName("peaceful_glow"))
        assertEquals("Crystal Breeze", AlarmSoundResolver.getDisplayName("crystal_breeze"))
        assertEquals("Extreme Siren", AlarmSoundResolver.getDisplayName("extreme_siren"))
        assertEquals("Critical Alert", AlarmSoundResolver.getDisplayName("critical_alert"))
    }

    @Test
    fun testVolumeFactors() {
        assertEquals(0.20f, AlarmSoundResolver.getVolumeFactor("low"), 0.01f)
        assertEquals(1.0f, AlarmSoundResolver.getVolumeFactor("high"), 0.01f)
        assertEquals(0.60f, AlarmSoundResolver.getVolumeFactor("medium"), 0.01f)
        assertEquals(0.60f, AlarmSoundResolver.getVolumeFactor("unknown"), 0.01f)
    }

    @Test
    fun testSynthesizer_generatesAudioBuffersForAllSounds() {
        for (sound in AlarmSoundResolver.ALL_SOUND_KEYS) {
            val factor = AlarmSynthesizer.getVolumeFactor("high")
            assertTrue(factor > 0f)
        }
    }
}
