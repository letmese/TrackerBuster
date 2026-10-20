package com.omnisentinel.app.features.camguard

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sin
import kotlin.math.sqrt

class MagneticScannerEngine(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private val _reading = MutableStateFlow(EmfReading())
    val reading: StateFlow<EmfReading> = _reading.asStateFlow()

    private val _history = MutableStateFlow<List<Float>>(emptyList())
    val history: StateFlow<List<Float>> = _history.asStateFlow()

    private val _isGeigerSoundEnabled = MutableStateFlow(true)
    val isGeigerSoundEnabled: StateFlow<Boolean> = _isGeigerSoundEnabled.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isSimulation = MutableStateFlow(false)
    val isSimulation: StateFlow<Boolean> = _isSimulation.asStateFlow()

    private var baselineUf = 45.0f
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var geigerJob: Job? = null
    private var simulationJob: Job? = null

    // Audio click for Geiger counter
    private var geigerTrack: AudioTrack? = null
    private val clickSample = ShortArray(220) { i ->
        (sin(2.0 * Math.PI * 1800.0 * i / 44100.0) * (1.0 - i / 220.0) * 20000.0).toInt().toShort()
    }

    init {
        initGeigerTrack()
    }

    private fun initGeigerTrack() {
        try {
            geigerTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(44100)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(clickSample.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            geigerTrack?.write(clickSample, 0, clickSample.size)
        } catch (e: Exception) {
            // AudioTrack init fallback
        }
    }

    fun start() {
        if (_isScanning.value) return
        _isScanning.value = true

        if (_isSimulation.value) {
            startSimulation()
            return
        }

        if (magnetometer == null) {
            // No hardware sensor, do not invent fake anomalies
            return
        }

        magnetometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        startGeigerLoop()
    }

    fun stop() {
        _isScanning.value = false
        sensorManager.unregisterListener(this)
        geigerJob?.cancel()
        simulationJob?.cancel()
    }

    fun toggleGeigerSound(enabled: Boolean) {
        _isGeigerSoundEnabled.value = enabled
    }

    fun toggleSimulation(enabled: Boolean) {
        _isSimulation.value = enabled
        if (_isScanning.value) {
            stop()
            start()
        }
    }

    fun tareBaseline() {
        baselineUf = _reading.value.magnitudeUf
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_MAGNETIC_FIELD) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val mag = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

        processReading(x, y, z, mag)
    }

    private fun processReading(x: Float, y: Float, z: Float, mag: Float) {
        val delta = kotlin.math.abs(mag - baselineUf)
        val level = when {
            delta >= 120f || mag >= 180f -> AnomalyLevel.CRITICAL
            delta >= 60f || mag >= 110f -> AnomalyLevel.HIGH
            delta >= 25f || mag >= 75f -> AnomalyLevel.ELEVATED
            else -> AnomalyLevel.NORMAL
        }

        val updated = EmfReading(
            magnitudeUf = mag,
            x = x,
            y = y,
            z = z,
            baselineUf = baselineUf,
            anomalyDeltaUf = delta,
            isAnomaly = level != AnomalyLevel.NORMAL,
            anomalyLevel = level
        )
        _reading.value = updated

        // Push to rolling history
        val hist = _history.value.toMutableList()
        hist.add(mag)
        if (hist.size > 50) hist.removeAt(0)
        _history.value = hist
    }

    private fun startGeigerLoop() {
        geigerJob?.cancel()
        geigerJob = scope.launch {
            while (isActive && _isScanning.value) {
                val delta = _reading.value.anomalyDeltaUf
                val delayMs = (800 - (delta * 6)).coerceIn(50f, 800f).toLong()

                if (_isGeigerSoundEnabled.value && delta > 10f) {
                    try {
                        geigerTrack?.stop()
                        geigerTrack?.reloadStaticData()
                        geigerTrack?.play()
                    } catch (e: Exception) {
                        // Ignore sound failure
                    }
                }


                delay(delayMs)
            }
        }
    }

    private fun startSimulation() {
        simulationJob?.cancel()
        simulationJob = scope.launch {
            var step = 0
            while (isActive && _isScanning.value) {
                step++
                val isNearHiddenCam = (step % 40) in 15..30
                val simMag = if (isNearHiddenCam) {
                    145f + (sin(step * 0.4) * 25f).toFloat()
                } else {
                    44f + (sin(step * 0.1) * 4f).toFloat()
                }
                processReading(simMag * 0.4f, simMag * 0.3f, simMag * 0.7f, simMag)
                delay(40)
            }
        }
        startGeigerLoop()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
