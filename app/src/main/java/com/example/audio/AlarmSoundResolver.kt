package com.example.audio

object AlarmSoundResolver {
    const val DEFAULT_SOUND = "holiday"
    const val DEFAULT_ASSET_PATH = "sounds/holiday.wav"

    val ALL_SOUND_KEYS = listOf(
        "holiday",
        "morning_bell",
        "soft_chime",
        "medical_reminder",
        "fresh_alert",
        "nature_bell",
        "peaceful_glow",
        "crystal_breeze",
        "extreme_siren",
        "critical_alert"
    )

    fun getSoundAssetPath(soundKey: String): String {
        return when (soundKey.lowercase().trim()) {
            "holiday" -> "sounds/holiday.wav"
            "morning_bell" -> "sounds/morning_bell.wav"
            "soft_chime" -> "sounds/soft_chime.wav"
            "medical_reminder" -> "sounds/medical_reminder.wav"
            "fresh_alert" -> "sounds/fresh_alert.wav"
            "nature_bell" -> "sounds/nature_bell.wav"
            "peaceful_glow" -> "sounds/peaceful_glow.wav"
            "crystal_breeze" -> "sounds/crystal_breeze.wav"
            "extreme_siren" -> "sounds/extreme_siren.wav"
            "critical_alert" -> "sounds/critical_alert.wav"
            else -> DEFAULT_ASSET_PATH
        }
    }

    fun getDisplayName(soundKey: String): String {
        return when (soundKey.lowercase().trim()) {
            "holiday" -> "Holiday"
            "morning_bell" -> "Morning Bell"
            "soft_chime" -> "Soft Chime"
            "medical_reminder" -> "Wellness Reminder"
            "fresh_alert" -> "Fresh Alert"
            "nature_bell" -> "Nature Bell"
            "peaceful_glow" -> "Peaceful Glow"
            "crystal_breeze" -> "Crystal Breeze"
            "extreme_siren" -> "Extreme Siren"
            "critical_alert" -> "Critical Alert"
            else -> "Holiday"
        }
    }

    fun getVolumeFactor(volumeLevel: String): Float {
        return when (volumeLevel.lowercase().trim()) {
            "low" -> 0.20f
            "high" -> 1.0f
            else -> 0.60f
        }
    }
}
