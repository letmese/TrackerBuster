package com.omnisentinel.app.features.battery

data class BatteryMetrics(
    val currentMa: Int = 0,
    val voltageMv: Int = 4100,
    val powerWatts: Float = 0.0f,
    val batteryPct: Int = 85,
    val temperatureC: Float = 28.5f,
    val healthString: String = "Unknown",
    val isCharging: Boolean = false,
    val plugType: String = "Unplugged",
    // Nullable: real value only when the OS/driver exposes it
    val cycleCount: Int? = null,
    val designCapacityMah: Int? = null,
    val estimatedCapacityMah: Int? = null,
    val healthPercent: Int? = null,
    val isSimulation: Boolean = false
)

data class CableBenchmarkResult(
    val grade: String = "A",
    val gradeColorHex: Long = 0xFF00FF7F,
    val avgWatts: Float = 18.4f,
    val peakWatts: Float = 22.1f,
    val stabilityPercent: Int = 96,
    val speedCategory: String = "USB-PD 25W Fast Charge",
    val diagnosticSummary: String = "High quality cable with minimal voltage drop. Recommended for full-speed turbo charging."
)
