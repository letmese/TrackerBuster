package com.omnisentinel.app.features.rf

enum class DeviceType {
    AIRTAG,
    SMARTTAG,
    TILE,
    SKIMMER_SUSPECT,
    GENERIC_BEACON,
    STANDARD_BLE
}

enum class ThreatLevel {
    BENIGN,
    SUSPICIOUS,
    CRITICAL_ALERT
}

data class DiscoveredDevice(
    val macAddress: String,
    val name: String?,
    val rssi: Int,
    val txPower: Int = -59,
    val estimatedDistanceMeters: Double = 0.0,
    val deviceType: DeviceType = DeviceType.STANDARD_BLE,
    val threatLevel: ThreatLevel = ThreatLevel.BENIGN,
    val detectionCount: Int = 1,
    val firstSeen: Long = System.currentTimeMillis(),
    val lastSeen: Long = System.currentTimeMillis(),
    val rawManufacturerData: String? = null,
    val threatReason: String? = null
)

data class SkimmerThreatReport(
    val moduleName: String,
    val macAddress: String,
    val rssi: Int,
    val confidenceScore: Int,
    val matchedPattern: String,
    val pinVulnerability: String,
    val recommendedAction: String
)

data class StalkerAlert(
    val targetMac: String,
    val deviceType: DeviceType,
    val trackingDurationMinutes: Int,
    val sightingCount: Int,
    val message: String
)
