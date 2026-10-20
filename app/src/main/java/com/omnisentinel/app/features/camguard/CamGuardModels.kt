package com.omnisentinel.app.features.camguard

enum class CamGuardMode(val title: String) {
    OPTICAL_GLINT("Optical Glint Finder"),
    INFRARED_EYE("Infrared (IR) Night Eye"),
    MAGNETIC_EMF("EMF Anomaly Scanner")
}

data class EmfReading(
    val magnitudeUf: Float = 42.0f,
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 42.0f,
    val baselineUf: Float = 42.0f,
    val anomalyDeltaUf: Float = 0.0f,
    val isAnomaly: Boolean = false,
    val anomalyLevel: AnomalyLevel = AnomalyLevel.NORMAL
)

enum class AnomalyLevel(val label: String, val colorHex: Long) {
    NORMAL("NORMAL AMBIENT", 0xFF00FF7F),
    ELEVATED("ELEVATED FIELD", 0xFFFFD600),
    HIGH("MAGNETIC ANOMALY", 0xFFFFB703),
    CRITICAL("CRITICAL EMF / COIL DETECTED", 0xFFFF1744)
}

enum class StrobeRate(val hz: Int, val label: String) {
    OFF(0, "STEADY TORCH"),
    SLOW(2, "2 Hz STROBE"),
    MEDIUM(4, "4 Hz STROBE"),
    FAST(8, "8 Hz STROBE")
}
