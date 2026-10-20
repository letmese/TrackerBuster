package com.omnisentinel.app.features.acoustic

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*

class AcousticEngine(private val context: Context) {

    private val sampleRate = 44100
    private val bufferSize = 1024
    private val fftSize = 512

    private val _acousticState = MutableStateFlow(AcousticState())
    val acousticState: StateFlow<AcousticState> = _acousticState.asStateFlow()

    private val _ultrasonicHistory = MutableStateFlow<List<UltrasonicTrackerEvent>>(emptyList())
    val ultrasonicHistory: StateFlow<List<UltrasonicTrackerEvent>> = _ultrasonicHistory.asStateFlow()

    private val _waterfallBuffer = MutableStateFlow<List<FloatArray>>(emptyList())
    val waterfallBuffer: StateFlow<List<FloatArray>> = _waterfallBuffer.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var isSimulating = false

    fun toggleSimulation(enable: Boolean) {
        isSimulating = enable
        _acousticState.value = _acousticState.value.copy(isSimulation = enable)
        if (_acousticState.value.isRecording) {
            stop()
            start()
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (_acousticState.value.isRecording) return

        if (isSimulating) {
            startSimulation()
            return
        }

        try {
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val realBufSize = maxOf(minBuf, bufferSize * 2)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                realBufSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.w("AcousticEngine", "AudioRecord initialization failed. Microphone may be in use or permission denied.")
                _acousticState.value = _acousticState.value.copy(isRecording = false)
                return
            }

            audioRecord?.startRecording()
            _acousticState.value = _acousticState.value.copy(isRecording = true)

            recordingJob = scope.launch {
                val audioBuffer = ShortArray(fftSize)
                var rollingDbSum = 0f
                var dbCount = 0
                var currentPeakDb = 30f

                while (isActive && _acousticState.value.isRecording) {
                    val read = audioRecord?.read(audioBuffer, 0, fftSize) ?: 0
                    if (read > 0) {
                        // Calculate RMS & dB SPL
                        var sumSquares = 0.0
                        for (i in 0 until read) {
                            sumSquares += audioBuffer[i] * audioBuffer[i]
                        }
                        val rms = sqrt(sumSquares / read)
                        // Note: Measurements are calculated relative to phone microphone digital full-scale (dBFS),
                        // not calibrated dB SPL. Microphones vary across Android devices.
                        // Approximate mapping to standard dB SPL scale (roughly 30dB quiet room to 105dB loud)
                        val db = (20.0 * log10((rms.coerceAtLeast(1.0)) / 32767.0) + 95.0).toFloat().coerceIn(25f, 120f)

                        currentPeakDb = maxOf(currentPeakDb * 0.98f, db)
                        rollingDbSum += db
                        dbCount++
                        val avgDb = rollingDbSum / dbCount

                        // Compute FFT magnitudes
                        val mags = FastFourierTransform.computeMagnitudes(audioBuffer, fftSize)

                        // Analyze Ultrasonic Band (18kHz - 22kHz)
                        // Bins: index * (44100 / 512) -> binWidth is 86.13 Hz
                        val binWidth = sampleRate.toFloat() / fftSize
                        val ultrasonicStartBin = (18000f / binWidth).toInt().coerceIn(0, mags.size - 1)
                        val ultrasonicEndBin = (22000f / binWidth).toInt().coerceIn(0, mags.size - 1)

                        var maxUltrasonicMag = 0f
                        var maxUltrasonicBin = ultrasonicStartBin
                        var ultrasonicAvgMag = 0f

                        for (b in ultrasonicStartBin..ultrasonicEndBin) {
                            ultrasonicAvgMag += mags[b]
                            if (mags[b] > maxUltrasonicMag) {
                                maxUltrasonicMag = mags[b]
                                maxUltrasonicBin = b
                            }
                        }
                        val rangeSize = (ultrasonicEndBin - ultrasonicStartBin + 1).coerceAtLeast(1)
                        ultrasonicAvgMag /= rangeSize

                        // Beacon detection condition: prominent peak relative to average noise floor
                        val isUltrasonicActive = maxUltrasonicMag > 0.18f && maxUltrasonicMag > (ultrasonicAvgMag * 2.2f)
                        val detectedFreq = if (isUltrasonicActive) maxUltrasonicBin * binWidth else 0f

                        if (isUltrasonicActive && detectedFreq > 18000f) {
                            logUltrasonicEvent(detectedFreq, db)
                        }

                        // Determine OSHA safety classification
                        val osha = when {
                            db >= 100f -> OshaStatus.HAZARD
                            db >= 85f -> OshaStatus.WARNING
                            db >= 70f -> OshaStatus.MODERATE
                            else -> OshaStatus.SAFE
                        }

                        _acousticState.value = _acousticState.value.copy(
                            currentDb = db,
                            peakDb = currentPeakDb,
                            averageDb = avgDb,
                            isUltrasonicDetected = isUltrasonicActive,
                            ultrasonicFreqHz = detectedFreq,
                            oshaStatus = osha,
                            fftMagnitudes = mags
                        )

                        // Push slice to waterfall (keep last 60 slices)
                        updateWaterfall(mags)
                    }
                    delay(25) // ~40-50 fps refresh
                }
            }
        } catch (e: Exception) {
            Log.e("AcousticEngine", "Error starting AudioRecord: ${e.message}")
            _acousticState.value = _acousticState.value.copy(isRecording = false)
        }
    }

    fun stop() {
        _acousticState.value = _acousticState.value.copy(isRecording = false)
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e("AcousticEngine", "Error releasing audioRecord: ${e.message}")
        }
        audioRecord = null
        recordingJob?.cancel()
    }

    private fun updateWaterfall(mags: FloatArray) {
        val current = _waterfallBuffer.value.toMutableList()
        current.add(0, mags.copyOf())
        if (current.size > 70) {
            current.removeAt(current.size - 1)
        }
        _waterfallBuffer.value = current
    }

    private fun logUltrasonicEvent(freq: Float, db: Float) {
        val existingRecent = _ultrasonicHistory.value.firstOrNull()
        if (existingRecent != null && System.currentTimeMillis() - existingRecent.timestamp < 3000) {
            return
        }

        val protocol = when {
            freq in 19000f..19900f -> "Silverpush / Lisnr Ad Beacon"
            freq in 20000f..20800f -> "Shopkick / Retail Inaudible Beacon"
            else -> "Inaudible Cross-Device Acoustic Tracking Signal"
        }

        val event = UltrasonicTrackerEvent(
            frequencyHz = freq,
            amplitudeDb = db,
            matchedProtocol = protocol,
            description = "High-frequency ultrasonic tone detected emitting from TV/speaker for cross-device telemetry."
        )
        _ultrasonicHistory.value = listOf(event) + _ultrasonicHistory.value.take(20)
    }

    private fun startSimulation() {
        _acousticState.value = _acousticState.value.copy(isRecording = true, isSimulation = true)
        recordingJob?.cancel()
        recordingJob = scope.launch {
            var step = 0
            while (isActive && _acousticState.value.isRecording) {
                step++
                val simulatedDb = (42f + (sin(step * 0.1) * 8f) + (Math.random() * 4f)).toFloat()
                val isSimulatedBeacon = (step % 50) in 15..35

                val mags = FloatArray(256)
                // Add ambient noise curve (pink noise profile)
                for (i in mags.indices) {
                    val freq = i * (44100f / 512f)
                    val pinkFalloff = 1f / sqrt((freq / 200f).coerceAtLeast(1f))
                    mags[i] = (pinkFalloff * 0.35f * Math.random().toFloat()).coerceIn(0f, 1f)
                }

                // If simulated ultrasonic ad tracker is on, inject 19,500 Hz peak!
                val beaconBin = (19500f / (44100f / 512f)).toInt().coerceIn(0, 255)
                if (isSimulatedBeacon) {
                    mags[beaconBin] = 0.85f
                    if (beaconBin > 0) mags[beaconBin - 1] = 0.45f
                    if (beaconBin < 255) mags[beaconBin + 1] = 0.45f
                    logUltrasonicEvent(19500f, simulatedDb)
                }

                val osha = when {
                    simulatedDb >= 100f -> OshaStatus.HAZARD
                    simulatedDb >= 85f -> OshaStatus.WARNING
                    simulatedDb >= 70f -> OshaStatus.MODERATE
                    else -> OshaStatus.SAFE
                }

                _acousticState.value = _acousticState.value.copy(
                    currentDb = simulatedDb,
                    peakDb = maxOf(_acousticState.value.peakDb, simulatedDb),
                    averageDb = 44f,
                    isUltrasonicDetected = isSimulatedBeacon,
                    ultrasonicFreqHz = if (isSimulatedBeacon) 19500f else 0f,
                    oshaStatus = osha,
                    fftMagnitudes = mags
                )

                updateWaterfall(mags)
                delay(30)
            }
        }
    }
}
