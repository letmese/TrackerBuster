package com.omnisentinel.app.features.acoustic

data class AcousticState(
    val currentDb: Float = 35.0f,
    val peakDb: Float = 42.0f,
    val averageDb: Float = 38.0f,
    val isUltrasonicDetected: Boolean = false,
    val ultrasonicFreqHz: Float = 0f,
    val oshaStatus: OshaStatus = OshaStatus.SAFE,
    val fftMagnitudes: FloatArray = FloatArray(256),
    val isRecording: Boolean = false,
    val isSimulation: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AcousticState
        return currentDb == other.currentDb &&
                peakDb == other.peakDb &&
                isUltrasonicDetected == other.isUltrasonicDetected &&
                ultrasonicFreqHz == other.ultrasonicFreqHz &&
                isRecording == other.isRecording
    }

    override fun hashCode(): Int {
        var result = currentDb.hashCode()
        result = 31 * result + peakDb.hashCode()
        result = 31 * result + isUltrasonicDetected.hashCode()
        result = 31 * result + ultrasonicFreqHz.hashCode()
        return result
    }
}

enum class OshaStatus(val label: String, val maxSafeExposure: String, val colorHex: Long) {
    SAFE("SAFE (<70 dB)", "Unlimited safe exposure", 0xFF00FF7F),
    MODERATE("MODERATE (70-84 dB)", "Safe for normal daily periods", 0xFFFFD600),
    WARNING("LOUD (85-99 dB) — OSHA DANGER ZONE IF CALIBRATED", "Max 8 hours / day (Hearing loss risk)", 0xFFFFB703),
    HAZARD("CRITICAL HAZARD (100+ dB)", "Immediate ear protection required (<15 min safe)", 0xFFFF1744)
}

data class UltrasonicTrackerEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val frequencyHz: Float,
    val amplitudeDb: Float,
    val matchedProtocol: String,
    val description: String
)
