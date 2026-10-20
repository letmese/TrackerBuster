package com.omnisentinel.app.features.acoustic

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sin

class ToneGeneratorEngine {

    private val sampleRate = 44100
    private var audioTrack: AudioTrack? = null
    private var generatorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentFrequency = MutableStateFlow(165f)
    val currentFrequency: StateFlow<Float> = _currentFrequency.asStateFlow()

    private val _isWaterEjectMode = MutableStateFlow(false)
    val isWaterEjectMode: StateFlow<Boolean> = _isWaterEjectMode.asStateFlow()

    fun setFrequency(freq: Float) {
        _currentFrequency.value = freq.coerceIn(20f, 22000f)
    }

    fun startTone(frequency: Float = _currentFrequency.value, isWaterEject: Boolean = false) {
        stopTone()
        _currentFrequency.value = frequency
        _isWaterEjectMode.value = isWaterEject
        _isPlaying.value = true

        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()

        if (isWaterEject) {
            scope.launch {
                delay(30_000)
                if (_isWaterEjectMode.value) stopTone()
            }
        }

        generatorJob = scope.launch {
            val chunk = ShortArray(bufferSize / 2)
            var phase = 0.0
            var tick = 0

            while (isActive && _isPlaying.value) {
                val freq = if (_isWaterEjectMode.value) {
                    // Modulate 165Hz with a 5Hz pulse for physical water expulsion
                    tick++
                    if ((tick / 20) % 2 == 0) 165.0 else 130.0
                } else {
                    _currentFrequency.value.toDouble()
                }

                val phaseIncrement = (2.0 * Math.PI * freq) / sampleRate

                for (i in chunk.indices) {
                    // If water eject, boost amplitude to max with square-sine envelope
                    val sample = if (_isWaterEjectMode.value) {
                        (sin(phase) * 32700.0).toInt().toShort()
                    } else {
                        (sin(phase) * 24000.0).toInt().toShort()
                    }
                    chunk[i] = sample
                    phase += phaseIncrement
                    if (phase >= 2.0 * Math.PI) {
                        phase -= 2.0 * Math.PI
                    }
                }

                audioTrack?.write(chunk, 0, chunk.size)
            }
        }
    }

    fun stopTone() {
        _isPlaying.value = false
        _isWaterEjectMode.value = false
        generatorJob?.cancel()
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            // Ignore release exceptions
        }
        audioTrack = null
    }
}
