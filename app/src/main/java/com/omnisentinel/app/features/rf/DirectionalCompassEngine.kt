package com.omnisentinel.app.features.rf

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*

enum class ProximityZone(val label: String, val thermalColorHex: Long) {
    FREEZING("FREEZING (>10m)", 0xFF2196F3),
    COLD("COLD (6-10m)", 0xFF00E5FF),
    WARM("WARM (3-6m)", 0xFFFFB703),
    HOT("HOT (1-3m)", 0xFFFF5722),
    BURNING("BURNING HOT (<1m)", 0xFFFF1744)
}

data class CompassTargetState(
    val targetDevice: DiscoveredDevice? = null,
    val smoothedRssi: Float = -70f,
    val distanceEstimateMeters: Float = 5.0f,
    val proximityPercent: Int = 30,
    val zone: ProximityZone = ProximityZone.COLD,
    val azimuthDegrees: Float = 0f,
    val isTracking: Boolean = false
)

class DirectionalCompassEngine(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private val _compassState = MutableStateFlow(CompassTargetState())
    val compassState: StateFlow<CompassTargetState> = _compassState.asStateFlow()

    private var orientationSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var hapticPulsingJob: Job? = null

    fun startTracking(target: DiscoveredDevice) {
        _compassState.value = _compassState.value.copy(
            targetDevice = target,
            smoothedRssi = target.rssi.toFloat(),
            isTracking = true
        )
        orientationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        startHapticPulsing()
    }

    fun stopTracking() {
        _compassState.value = _compassState.value.copy(isTracking = false)
        sensorManager.unregisterListener(this)
        hapticPulsingJob?.cancel()
    }

    fun updateTargetRssi(newRssi: Int) {
        val current = _compassState.value
        if (!current.isTracking || current.targetDevice == null) return

        // Exponential smoothing filter
        val alpha = 0.25f
        val smoothed = current.smoothedRssi * (1f - alpha) + newRssi * alpha

        // Map RSSI (-95 to -35) to 0 - 100% proximity
        val proximity = ((smoothed - (-95f)) / (-35f - (-95f)) * 100f).coerceIn(0f, 100f).roundToInt()

        val zone = when {
            smoothed >= -50 -> ProximityZone.BURNING
            smoothed >= -65 -> ProximityZone.HOT
            smoothed >= -75 -> ProximityZone.WARM
            smoothed >= -85 -> ProximityZone.COLD
            else -> ProximityZone.FREEZING
        }

        val estimatedMeters = (10.0.pow((-59.0 - smoothed) / 20.0)).toFloat().coerceIn(0.1f, 30.0f)

        _compassState.value = current.copy(
            smoothedRssi = smoothed,
            proximityPercent = proximity,
            zone = zone,
            distanceEstimateMeters = estimatedMeters
        )
    }

    private fun startHapticPulsing() {
        // Continuous vibration disabled to prevent battery drain and user annoyance
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            var azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
            if (azimuth < 0) azimuth += 360f
            _compassState.value = _compassState.value.copy(azimuthDegrees = azimuth)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
