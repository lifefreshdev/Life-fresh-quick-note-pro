package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import kotlinx.coroutines.*
import kotlin.math.*

object AlarmSynthesizer {
    private const val TAG = "AlarmSynthesizer"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var activeJob: Job? = null
    private var activeAudioTrack: AudioTrack? = null

    // Volume multipliers matching the HTML volume keys: low, medium, high
    fun getVolumeFactor(volumeLevel: String): Float {
        val factor = when (volumeLevel.lowercase()) {
            "low" -> 0.15f
            "high" -> 0.90f
            else -> 0.45f // medium
        }
        android.util.Log.d(TAG, "getVolumeFactor: level $volumeLevel -> volume factor $factor")
        return factor
    }

    /**
     * Synthesizes and plays an alarm tone in a background loop or single shot.
     */
    fun playAlarmSound(soundName: String, volumeLevel: String, loop: Boolean) {
        android.util.Log.d(TAG, "playAlarmSound called: soundName=$soundName, volumeLevel=$volumeLevel, loop=$loop")
        stopAlarmSound()
        val volume = getVolumeFactor(volumeLevel)

        activeJob = scope.launch {
            val sampleRate = 44100
            val soundBuffer = generateSoundBuffer(soundName, sampleRate)
            if (soundBuffer.isEmpty()) {
                android.util.Log.e(TAG, "playAlarmSound: Generated sound buffer is empty!")
                return@launch
            }

            android.util.Log.d(TAG, "playAlarmSound: Generated sound buffer of size ${soundBuffer.size} shorts.")

            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = minBufferSize.coerceAtLeast(soundBuffer.size * 2)
            android.util.Log.d(TAG, "playAlarmSound: minBufferSize=$minBufferSize, chosen bufferSize=$bufferSize")

            // Create AudioTrack in stream mode with media usage as standard for absolute compatibility on all Android OS versions
            val audioTrack = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(bufferSize)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize,
                        AudioTrack.MODE_STREAM
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "playAlarmSound: Failed to initialize AudioTrack", e)
                return@launch
            }

            activeAudioTrack = audioTrack
            android.util.Log.d(TAG, "playAlarmSound: AudioTrack initialized successfully. state=${audioTrack.state}")

            // Apply Volume
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    audioTrack.setVolume(volume)
                } else {
                    @Suppress("DEPRECATION")
                    audioTrack.setStereoVolume(volume, volume)
                }
                android.util.Log.d(TAG, "playAlarmSound: Volume level $volume applied to AudioTrack.")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "playAlarmSound: Failed to set volume on AudioTrack", e)
            }

            try {
                audioTrack.play()
                android.util.Log.d(TAG, "playAlarmSound: Called audioTrack.play(), now starting PCM streaming loop.")

                do {
                    android.util.Log.v(TAG, "playAlarmSound: Streaming a new chunk iteration...")
                    var written = 0
                    while (written < soundBuffer.size && isActive) {
                        val count = audioTrack.write(soundBuffer, written, soundBuffer.size - written)
                        if (count <= 0) {
                            android.util.Log.w(TAG, "playAlarmSound: write returned $count. Yielding.")
                            delay(15)
                            break
                        }
                        written += count
                    }
                    if (!loop) {
                        android.util.Log.d(TAG, "playAlarmSound: Single playback finished.")
                        break
                    }
                } while (isActive)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "playAlarmSound: Error during audio playback loop", e)
            } finally {
                android.util.Log.d(TAG, "playAlarmSound: Releasing AudioTrack Resources in finally block.")
                try {
                    audioTrack.stop()
                    audioTrack.release()
                } catch (e: Exception) {
                    // Ignore
                }
                if (activeAudioTrack == audioTrack) {
                    activeAudioTrack = null
                }
            }
        }
    }

    /**
     * Instantly stops any active synthesizer audio output.
     */
    fun stopAlarmSound() {
        android.util.Log.d(TAG, "stopAlarmSound invoked.")
        activeJob?.cancel()
        activeJob = null
        try {
            activeAudioTrack?.let {
                android.util.Log.d(TAG, "stopAlarmSound: stopping dynamic track.")
                it.stop()
                it.release()
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "stopAlarmSound exception occurred: ${e.message}")
        }
        activeAudioTrack = null
    }

    // Keep backwards compatibility with startPlaying / stopPlaying
    fun startPlaying(soundName: String, volumeLevel: String, loop: Boolean) {
        playAlarmSound(soundName, volumeLevel, loop)
    }

    fun stopPlaying() {
        stopAlarmSound()
    }

    /**
     * Synthesizes 16-bit PCM buffers for our customized audio wave sounds.
     */
    private fun generateSoundBuffer(soundName: String, sampleRate: Int): ShortArray {
        return when (soundName.lowercase()) {
            "classic" -> createClassicBeep(sampleRate)
            "bell" -> createBellChime(sampleRate)
            "notification" -> createNotificationTone(sampleRate)
            "digital" -> createDigitalAlarm(sampleRate)
            "gentle" -> createGentleReminder(sampleRate)
            "holiday" -> createClassicBeep(sampleRate)
            "morning_bell" -> createBellChime(sampleRate)
            "soft_chime" -> createGentleReminder(sampleRate)
            "medical_reminder" -> createNotificationTone(sampleRate)
            "fresh_alert" -> createDigitalAlarm(sampleRate)
            "nature_bell" -> createBellChime(sampleRate)
            "peaceful_glow" -> createPeacefulGlow(sampleRate)
            "crystal_breeze" -> createCrystalBreeze(sampleRate)
            "extreme_siren" -> createExtremeSiren(sampleRate)
            "critical_alert" -> createCriticalAlert(sampleRate)
            else -> createClassicBeep(sampleRate)
        }
    }

    private fun createClassicBeep(sampleRate: Int): ShortArray {
        // Classic: 800Hz / 850Hz square waves for 0.35s, with trailing quiet section (0.65s) for rhythmic repeats
        val soundDuration = 0.35
        val silenceDuration = 0.65
        val totalDuration = soundDuration + silenceDuration
        val size = (totalDuration * sampleRate).toInt()
        val buffer = ShortArray(size)

        val soundSamples = (soundDuration * sampleRate).toInt()
        val freq1 = 800.0
        val freq2 = 850.0

        for (i in 0 until soundSamples) {
            val t = i.toDouble() / sampleRate
            // Square wave 1
            val wave1 = if (sin(2.0 * PI * freq1 * t) >= 0) 1.0 else -1.0
            // Square wave 2
            val wave2 = if (sin(2.0 * PI * freq2 * t) >= 0) 1.0 else -1.0

            val amplitude = 0.5 * (wave1 + wave2)
            buffer[i] = (amplitude * Short.MAX_VALUE).toInt().toShort()
        }
        // Silence remains filled with zeros
        return buffer
    }

    private fun createBellChime(sampleRate: Int): ShortArray {
        // Bell: 987.77Hz sine + 1479.98Hz triangle for 1.2s with exponential decay, with trailing quiet section (1.0s)
        val soundDuration = 1.2
        val silenceDuration = 1.0
        val totalDuration = soundDuration + silenceDuration
        val size = (totalDuration * sampleRate).toInt()
        val buffer = ShortArray(size)

        val soundSamples = (soundDuration * sampleRate).toInt()
        val fSine = 987.77
        val fTriangle = 1479.98

        for (i in 0 until soundSamples) {
            val t = i.toDouble() / sampleRate
            
            // Sine wave
            val vSine = sin(2.0 * PI * fSine * t)
            
            // Triangle wave
            val vTriangle = asin(sin(2.0 * PI * fTriangle * t)) / (PI / 2.0)
            
            // Exponential decay envelope
            val envelope = exp(-3.0 * t / soundDuration)
            
            val amplitude = 0.5 * (vSine + vTriangle) * envelope
            buffer[i] = (amplitude * Short.MAX_VALUE).toInt().toShort()
        }
        return buffer
    }

    private fun createNotificationTone(sampleRate: Int): ShortArray {
        // Notification: Sweep pitch 523.25Hz (0.12s) -> 783.99Hz (0.23s)
        val tone1Duration = 0.12
        val tone2Duration = 0.23
        val silenceDuration = 0.65
        val totalDuration = tone1Duration + tone2Duration + silenceDuration
        val size = (totalDuration * sampleRate).toInt()
        val buffer = ShortArray(size)

        val samples1 = (tone1Duration * sampleRate).toInt()
        val samples2 = (tone2Duration * sampleRate).toInt()

        // Tone 1: 523.25Hz
        for (i in 0 until samples1) {
            val t = i.toDouble() / sampleRate
            val amplitude = sin(2.0 * PI * 523.25 * t)
            buffer[i] = (amplitude * Short.MAX_VALUE).toInt().toShort()
        }

        // Tone 2: 783.99Hz
        for (i in 0 until samples2) {
            val t = i.toDouble() / sampleRate
            val amplitude = sin(2.0 * PI * 783.99 * t)
            buffer[samples1 + i] = (amplitude * Short.MAX_VALUE).toInt().toShort()
        }

        return buffer
    }

    private fun createDigitalAlarm(sampleRate: Int): ShortArray {
        // Digital: High frequency square 2700Hz for 0.15s, quiet 0.85s
        val soundDuration = 0.15
        val silenceDuration = 0.85
        val totalDuration = soundDuration + silenceDuration
        val size = (totalDuration * sampleRate).toInt()
        val buffer = ShortArray(size)

        val soundSamples = (soundDuration * sampleRate).toInt()
        val freq = 2700.0

        for (i in 0 until soundSamples) {
            val t = i.toDouble() / sampleRate
            val wave = if (sin(2.0 * PI * freq * t) >= 0) 1.0 else -1.0
            buffer[i] = (wave * Short.MAX_VALUE * 0.7).toInt().toShort()
        }
        return buffer
    }

    private fun createGentleReminder(sampleRate: Int): ShortArray {
        // Gentle: Staggered arpeggio [261.63, 329.63, 392.00, 523.25] for 1.6s, quiet 1.4s
        val soundDuration = 1.6
        val silenceDuration = 1.4
        val totalDuration = soundDuration + silenceDuration
        val size = (totalDuration * sampleRate).toInt()
        val buffer = ShortArray(size)

        val soundSamples = (soundDuration * sampleRate).toInt()
        val frequencies = doubleArrayOf(261.63, 329.63, 392.00, 523.25)
        val startOffsets = doubleArrayOf(0.0, 0.08, 0.16, 0.24)

        for (i in 0 until soundSamples) {
            val t = i.toDouble() / sampleRate
            var sum = 0.0

            for (j in frequencies.indices) {
                val offset = startOffsets[j]
                if (t >= offset) {
                    val dt = t - offset
                    val signal = sin(2.0 * PI * frequencies[j] * dt)
                    
                    // Linear attack (0.2s) and exponential decay
                    val attack = (dt / 0.2).coerceAtMost(1.0)
                    val decay = exp(-2.0 * dt)
                    sum += signal * attack * decay * 0.25
                }
            }

            buffer[i] = (sum * Short.MAX_VALUE).toInt().toShort().coerceAtLeast(Short.MIN_VALUE).coerceAtMost(Short.MAX_VALUE)
        }
        return buffer
    }

    private fun createPeacefulGlow(sampleRate: Int): ShortArray {
        val soundDuration = 2.5
        val size = (soundDuration * sampleRate).toInt()
        val buffer = ShortArray(size)

        val chimes = arrayOf(
            Triple(0.00, 523.25, 1.2 to 0.40),
            Triple(0.08, 659.25, 1.4 to 0.35),
            Triple(0.18, 783.99, 1.5 to 0.35),
            Triple(0.30, 987.77, 1.8 to 0.30),
            Triple(0.45, 1318.51, 2.0 to 0.25)
        )

        var maxVal = 1.0
        val raw = DoubleArray(size)
        for (i in 0 until size) {
            val t = i.toDouble() / sampleRate
            var sum = 0.0
            for (chime in chimes) {
                val startT = chime.first
                val freq = chime.second
                val decay = chime.third.first
                val amp = chime.third.second
                if (t >= startT) {
                    val dt = t - startT
                    val sig = sin(2.0 * PI * freq * dt) +
                            0.3 * sin(2.0 * PI * freq * 2.005 * dt) +
                            0.1 * sin(2.0 * PI * freq * 3.01 * dt)
                    val attack = (dt / 0.04).coerceAtMost(1.0)
                    val env = attack * exp(-decay * dt)
                    sum += sig * env * amp
                }
            }
            raw[i] = sum
            if (abs(sum) > maxVal) maxVal = abs(sum)
        }

        for (i in 0 until size) {
            val normalized = (raw[i] / maxVal) * 0.95
            buffer[i] = (normalized * Short.MAX_VALUE).toInt().toShort()
        }
        return buffer
    }

    private fun createCrystalBreeze(sampleRate: Int): ShortArray {
        val soundDuration = 2.2
        val size = (soundDuration * sampleRate).toInt()
        val buffer = ShortArray(size)

        val notes = arrayOf(
            Triple(0.00, 880.00, 1.8 to 0.30),
            Triple(0.10, 1108.73, 2.0 to 0.35),
            Triple(0.20, 1318.51, 2.2 to 0.35),
            Triple(0.32, 1661.22, 2.5 to 0.40),
            Triple(0.48, 1975.53, 3.0 to 0.30),
            Triple(0.68, 1661.22, 2.5 to 0.25),
            Triple(0.88, 1318.51, 2.0 to 0.20)
        )

        var maxVal = 1.0
        val raw = DoubleArray(size)
        for (i in 0 until size) {
            val t = i.toDouble() / sampleRate
            var sum = 0.0
            for (note in notes) {
                val startT = note.first
                val freq = note.second
                val decay = note.third.first
                val amp = note.third.second
                if (t >= startT) {
                    val dt = t - startT
                    val sig = sin(2.0 * PI * freq * dt) +
                            0.25 * sin(2.0 * PI * (freq * 2.76) * dt) +
                            0.15 * sin(2.0 * PI * (freq * 5.4) * dt)
                    val attack = (dt / 0.015).coerceAtMost(1.0)
                    val env = attack * exp(-decay * dt)
                    sum += sig * env * amp
                }
            }
            raw[i] = sum
            if (abs(sum) > maxVal) maxVal = abs(sum)
        }

        for (i in 0 until size) {
            val normalized = (raw[i] / maxVal) * 0.95
            buffer[i] = (normalized * Short.MAX_VALUE).toInt().toShort()
        }
        return buffer
    }

    private fun createExtremeSiren(sampleRate: Int): ShortArray {
        val soundDuration = 2.0
        val size = (soundDuration * sampleRate).toInt()
        val buffer = ShortArray(size)
        val edgeSamples = (sampleRate * 0.02).toInt()

        var maxVal = 1.0
        val raw = DoubleArray(size)
        for (i in 0 until size) {
            val t = i.toDouble() / sampleRate
            val phase = 2.0 * PI * 1050.0 * t - (400.0 / 3.0) * cos(2.0 * PI * 3.0 * t)
            val sig = sin(phase) + 0.35 * sin(phase * 2.0) + 0.15 * sin(phase * 3.0)
            val subWarble = sin(2.0 * PI * 15.0 * t) * 0.15
            var v = sig * (0.85 + subWarble)

            if (i < edgeSamples) {
                v *= (i.toDouble() / edgeSamples)
            } else if (i > size - edgeSamples) {
                v *= ((size - i).toDouble() / edgeSamples)
            }
            raw[i] = v
            if (abs(v) > maxVal) maxVal = abs(v)
        }

        for (i in 0 until size) {
            val normalized = (raw[i] / maxVal) * 0.95
            buffer[i] = (normalized * Short.MAX_VALUE).toInt().toShort()
        }
        return buffer
    }

    private fun createCriticalAlert(sampleRate: Int): ShortArray {
        val soundDuration = 2.0
        val size = (soundDuration * sampleRate).toInt()
        val buffer = ShortArray(size)
        val pulseStarts = doubleArrayOf(0.0, 0.45, 0.90, 1.35)

        var maxVal = 1.0
        val raw = DoubleArray(size)
        for (i in 0 until size) {
            val t = i.toDouble() / sampleRate
            var sum = 0.0
            for (pStart in pulseStarts) {
                val pLen = 0.30
                if (t >= pStart && t < pStart + pLen) {
                    val dt = t - pStart
                    val tone = 0.55 * sin(2.0 * PI * 480.0 * dt) +
                            0.35 * sin(2.0 * PI * 960.0 * dt) +
                            0.40 * sin(2.0 * PI * 1440.0 * dt)
                    val attack = (dt / 0.01).coerceAtMost(1.0)
                    val decay = if (dt >= 0.18) exp(-15.0 * (dt - 0.18)) else 1.0
                    sum += tone * attack * decay
                }
            }
            raw[i] = sum
            if (abs(sum) > maxVal) maxVal = abs(sum)
        }

        for (i in 0 until size) {
            val normalized = (raw[i] / maxVal) * 0.95
            buffer[i] = (normalized * Short.MAX_VALUE).toInt().toShort()
        }
        return buffer
    }
}
