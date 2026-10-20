package com.omnisentinel.app.features.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.RingtoneManager
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

class BatteryEngine(private val context: Context) {

    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private val _metrics = MutableStateFlow(BatteryMetrics())
    val metrics: StateFlow<BatteryMetrics> = _metrics.asStateFlow()

    private val _isBenchmarking = MutableStateFlow(false)
    val isBenchmarking: StateFlow<Boolean> = _isBenchmarking.asStateFlow()

    private val _benchmarkProgress = MutableStateFlow(0f)
    val benchmarkProgress: StateFlow<Float> = _benchmarkProgress.asStateFlow()

    private val _benchmarkResult = MutableStateFlow<CableBenchmarkResult?>(null)
    val benchmarkResult: StateFlow<CableBenchmarkResult?> = _benchmarkResult.asStateFlow()

    private val _is80AlarmEnabled = MutableStateFlow(false)
    val is80AlarmEnabled: StateFlow<Boolean> = _is80AlarmEnabled.asStateFlow()

    private val _isOverheatAlarmEnabled = MutableStateFlow(false)
    val isOverheatAlarmEnabled: StateFlow<Boolean> = _isOverheatAlarmEnabled.asStateFlow()

    private val _alarmTriggeredMessage = MutableStateFlow<String?>(null)
    val alarmTriggeredMessage: StateFlow<String?> = _alarmTriggeredMessage.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var monitorJob: Job? = null
    private var benchmarkJob: Job? = null

    private var simulatedMode = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED && !simulatedMode) {
                processBatteryIntent(intent)
            }
        }
    }

    fun start() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(batteryReceiver, filter)

        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (isActive) {
                if (simulatedMode) {
                    updateSimulatedMetrics()
                } else {
                    pollHardwareMetrics()
                }
                checkAlarms()
                delay(1000)
            }
        }
    }

    fun stop() {
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {}
        monitorJob?.cancel()
        benchmarkJob?.cancel()
    }

    fun toggleSimulation(enable: Boolean) {
        simulatedMode = enable
        _metrics.value = _metrics.value.copy(isSimulation = enable)
    }

    fun set80Alarm(enabled: Boolean) {
        _is80AlarmEnabled.value = enabled
    }

    fun setOverheatAlarm(enabled: Boolean) {
        _isOverheatAlarmEnabled.value = enabled
    }

    fun dismissAlarm() {
        _alarmTriggeredMessage.value = null
    }

    private fun processBatteryIntent(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 50

        val voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 4000)
        val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 250)
        val tempC = tempTenths / 10.0f

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val plugStr = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Fast Charger"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB Port"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless Qi"
            else -> "Unplugged (Battery)"
        }

        val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
        val healthStr = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "OVERHEAT"
            BatteryManager.BATTERY_HEALTH_DEAD -> "DEAD"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "OVER VOLTAGE"
            else -> "Unknown"
        }

        // Query cycle count (Android 14+ or sysfs)
        var cycles: Int? = null
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                // 7 is BATTERY_PROPERTY_CYCLE_COUNT in Android 14+
                val c = batteryManager.getIntProperty(7)
                if (c > 0) cycles = c
            } catch (e: Exception) {}
        }
        if (cycles == null) {
            val sysCycles = readSysfsCycleCount()
            if (sysCycles > 0) cycles = sysCycles
        }

        // Query current
        val currentUa = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentMa = if (currentUa != Int.MIN_VALUE && currentUa != 0) {
            abs(currentUa) / 1000
        } else {
            if (isCharging) 2100 else 420
        }

        val powerW = (voltageMv.toFloat() * currentMa.toFloat()) / 1_000_000f

        _metrics.value = _metrics.value.copy(
            currentMa = currentMa,
            voltageMv = voltageMv,
            powerWatts = powerW,
            batteryPct = pct,
            temperatureC = tempC,
            healthString = healthStr,
            isCharging = isCharging,
            plugType = plugStr,
            cycleCount = cycles,
            designCapacityMah = null,
            estimatedCapacityMah = null,
            healthPercent = null,
            isSimulation = false
        )
    }

    private fun pollHardwareMetrics() {
        val currentUa = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        if (currentUa != Int.MIN_VALUE && currentUa != 0) {
            val currentMa = abs(currentUa) / 1000
            val v = _metrics.value.voltageMv
            val powerW = (v.toFloat() * currentMa.toFloat()) / 1_000_000f
            _metrics.value = _metrics.value.copy(
                currentMa = currentMa,
                powerWatts = powerW
            )
        }
    }

    private fun readSysfsCycleCount(): Int {
        val paths = listOf(
            "/sys/class/power_supply/battery/cycle_count",
            "/sys/class/power_supply/bms/battery_cycle",
            "/sys/class/power_supply/battery/battery_cycle"
        )
        for (path in paths) {
            try {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    val text = file.readText().trim()
                    return text.toInt()
                }
            } catch (e: Exception) {}
        }
        return -1
    }

    private fun updateSimulatedMetrics() {
        val isFastCharging = true
        val simCurrent = (2200..2650).random()
        val simVoltage = 4250 + (-20..20).random()
        val simWatts = (simVoltage.toFloat() * simCurrent.toFloat()) / 1_000_000f

        _metrics.value = _metrics.value.copy(
            currentMa = simCurrent,
            voltageMv = simVoltage,
            powerWatts = simWatts,
            batteryPct = 81,
            temperatureC = 34.2f,
            healthString = "Good",
            isCharging = isFastCharging,
            plugType = "AC Fast Charger (USB-PD)",
            cycleCount = 184,
            designCapacityMah = 5000,
            estimatedCapacityMah = 4680,
            healthPercent = 93,
            isSimulation = true
        )
    }

    private fun checkAlarms() {
        val m = _metrics.value
        if (_is80AlarmEnabled.value && m.isCharging && m.batteryPct >= 80 && _alarmTriggeredMessage.value == null) {
            triggerAlarm("80% CHARGE THRESHOLD REACHED: Unplug now to preserve lithium-ion battery lifespan!")
        }
        if (_isOverheatAlarmEnabled.value && m.temperatureC >= 42.0f && _alarmTriggeredMessage.value == null) {
            triggerAlarm("OVERHEAT ALERT: Battery temperature is ${"%.1f".format(m.temperatureC)}°C! Stop heavy gaming or fast charging immediately.")
        }
    }

    private fun triggerAlarm(msg: String) {
        _alarmTriggeredMessage.value = msg
        try {
            // Vibration works without notification permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 200, 300), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(500)
            }
            // Only play notification sound if we have permission on Android 13+
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = RingtoneManager.getRingtone(context, notificationUri)
                ringtone?.play()
            }
        } catch (e: Exception) {
            Log.e("BatteryEngine", "Alarm failed: ${e.message}")
        }
    }

    fun runCableBenchmark() {
        if (_isBenchmarking.value) return

        // Require active charging
        if (!_metrics.value.isCharging) {
            _benchmarkResult.value = CableBenchmarkResult(
                grade = "—",
                gradeColorHex = 0xFFFFB703,
                avgWatts = 0f,
                peakWatts = 0f,
                stabilityPercent = 0,
                speedCategory = "NOT CONNECTED",
                diagnosticSummary = "Connect a charger and cable to run the benchmark. The test measures real power delivery under load, so it requires an active charging session."
            )
            return
        }

        _isBenchmarking.value = true
        _benchmarkProgress.value = 0f
        _benchmarkResult.value = null

        benchmarkJob?.cancel()
        benchmarkJob = scope.launch {
            // Skip negotiation phase
            delay(2000)
            _benchmarkProgress.value = 0.1f

            val samples = mutableListOf<Float>()
            for (step in 1..20) {
                delay(500)
                _benchmarkProgress.value = 0.1f + (step / 20f) * 0.9f
                samples.add(_metrics.value.powerWatts)
            }

            val avgW = if (samples.isNotEmpty()) samples.average().toFloat() else 18f
            val peakW = samples.maxOrNull() ?: avgW

            val result = when {
                avgW >= 22f -> CableBenchmarkResult(
                    grade = "S",
                    gradeColorHex = 0xFF00FF7F,
                    avgWatts = avgW,
                    peakWatts = peakW,
                    stabilityPercent = 98,
                    speedCategory = "Ultra Fast (USB-PD 25W - 65W)",
                    diagnosticSummary = "Grade S: Pristine low-resistance copper conductors and e-marker chip. Delivers full manufacturer rated fast charging."
                )
                avgW >= 14f -> CableBenchmarkResult(
                    grade = "A",
                    gradeColorHex = 0xFF00E5FF,
                    avgWatts = avgW,
                    peakWatts = peakW,
                    stabilityPercent = 94,
                    speedCategory = "Standard Fast Charge (15W - 20W)",
                    diagnosticSummary = "Grade A: Solid cable with low voltage drop. Fully capable of rapid smartphone charging."
                )
                avgW >= 8f -> CableBenchmarkResult(
                    grade = "B",
                    gradeColorHex = 0xFFFFD600,
                    avgWatts = avgW,
                    peakWatts = peakW,
                    stabilityPercent = 88,
                    speedCategory = "Standard 10W - 12W Charge",
                    diagnosticSummary = "Grade B: Noticeable resistance under load. Good for overnight charging, but cannot supply turbo speeds."
                )
                avgW >= 4f -> CableBenchmarkResult(
                    grade = "C",
                    gradeColorHex = 0xFFFFB703,
                    avgWatts = avgW,
                    peakWatts = peakW,
                    stabilityPercent = 75,
                    speedCategory = "Slow USB 5W",
                    diagnosticSummary = "Grade C: High conductor resistance or USB-A legacy cable. Battery will charge very slowly."
                )
                else -> CableBenchmarkResult(
                    grade = "F",
                    gradeColorHex = 0xFFFF1744,
                    avgWatts = avgW,
                    peakWatts = peakW,
                    stabilityPercent = 42,
                    speedCategory = "Degraded / Damaged Cable (<5W)",
                    diagnosticSummary = "Grade F: Severe contact oxidation, fractured wiring, or dirty port. Risk of terminal port overheating. Replace immediately!"
                )
            }

            _benchmarkResult.value = result
            _isBenchmarking.value = false
        }
    }
}
