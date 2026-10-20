package com.omnisentinel.app.features.rf

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.pow
import kotlin.math.roundToInt

enum class RfHardwareStatus {
    READY,
    BLUETOOTH_OFF,
    PERMISSION_REQUIRED,
    UNSUPPORTED,
    SCANNING,
    ERROR
}

class RfScannerEngine(private val context: Context) {

    private val bluetoothManager: BluetoothManager? =
        ContextCompat.getSystemService(context, BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var bleScanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null

    private val prefs = context.getSharedPreferences("rf_scanner_persist", Context.MODE_PRIVATE)

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _hardwareStatus = MutableStateFlow(RfHardwareStatus.READY)
    val hardwareStatus: StateFlow<RfHardwareStatus> = _hardwareStatus.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val devices: StateFlow<List<DiscoveredDevice>> = _devices.asStateFlow()

    private val _stalkerAlerts = MutableStateFlow<List<StalkerAlert>>(emptyList())
    val stalkerAlerts: StateFlow<List<StalkerAlert>> = _stalkerAlerts.asStateFlow()

    private val _skimmerReports = MutableStateFlow<List<SkimmerThreatReport>>(emptyList())
    val skimmerReports: StateFlow<List<SkimmerThreatReport>> = _skimmerReports.asStateFlow()

    private val _isSimulationMode = MutableStateFlow(false)
    val isSimulationMode: StateFlow<Boolean> = _isSimulationMode.asStateFlow()

    private var simulationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Known skimmer MAC address prefixes (OUIs)
    private val skimmerMacPrefixes = listOf(
        "00:14:03", "20:13:00", "98:D3:31", "00:18:E4", "00:0E:EA", "30:14:4A", "B8:27:EB"
    )

    // Known skimmer module name signatures
    private val skimmerNameKeywords = listOf(
        "hc-05", "hc-06", "hc-08", "jdy-30", "jdy-31", "spp-ca", "linvor",
        "bolutek", "bt-serial", "bt04", "atm-", "pump-", "skimmer"
    )

    val isBluetoothSupported: Boolean
        get() = bluetoothAdapter != null

    val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    init {
        val persisted = loadPersistedHistory().values.toList()
        if (persisted.isNotEmpty()) {
            _devices.value = persisted.sortedByDescending { it.threatLevel.ordinal * 1000 + it.rssi }
        }
    }

    private fun persistDetectionHistory(devices: List<DiscoveredDevice>) {
        try {
            val json = JSONArray()
            devices.forEach { d ->
                val obj = JSONObject()
                obj.put("mac", d.macAddress)
                obj.put("name", d.name)
                obj.put("type", d.deviceType.name)
                obj.put("firstSeen", d.firstSeen)
                obj.put("lastSeen", d.lastSeen)
                obj.put("count", d.detectionCount)
                json.put(obj)
            }
            prefs.edit().putString("history", json.toString()).apply()
        } catch (e: Exception) {
            Log.e("RfScannerEngine", "Persist failed: ${e.message}")
        }
    }

    private fun loadPersistedHistory(): Map<String, DiscoveredDevice> {
        val map = mutableMapOf<String, DiscoveredDevice>()
        try {
            val raw = prefs.getString("history", null) ?: return map
            val json = JSONArray(raw)
            for (i in 0 until json.length()) {
                val obj = json.getJSONObject(i)
                val mac = obj.getString("mac")
                val devType = try {
                    DeviceType.valueOf(obj.getString("type"))
                } catch (e: Exception) {
                    DeviceType.STANDARD_BLE
                }
                map[mac] = DiscoveredDevice(
                    macAddress = mac,
                    name = obj.optString("name").takeIf { it.isNotEmpty() },
                    rssi = -90,
                    deviceType = devType,
                    firstSeen = obj.getLong("firstSeen"),
                    lastSeen = obj.getLong("lastSeen"),
                    detectionCount = obj.getInt("count")
                )
            }
        } catch (e: Exception) {
            Log.e("RfScannerEngine", "Load failed: ${e.message}")
        }
        return map
    }

    fun checkHardwareState() {
        if (_isSimulationMode.value) {
            _hardwareStatus.value = if (_isScanning.value) RfHardwareStatus.SCANNING else RfHardwareStatus.READY
            _statusMessage.value = "Demo simulation mode active"
            return
        }

        if (bluetoothAdapter == null) {
            _hardwareStatus.value = RfHardwareStatus.UNSUPPORTED
            _statusMessage.value = "Bluetooth hardware not detected on this device"
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            _hardwareStatus.value = RfHardwareStatus.BLUETOOTH_OFF
            _statusMessage.value = "Bluetooth is turned off. Turn on Bluetooth to scan."
            return
        }

        _hardwareStatus.value = if (_isScanning.value) RfHardwareStatus.SCANNING else RfHardwareStatus.READY
        _statusMessage.value = null
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (_isScanning.value) return

        if (_isSimulationMode.value) {
            _isScanning.value = true
            _hardwareStatus.value = RfHardwareStatus.SCANNING
            startSimulationScan()
            return
        }

        if (bluetoothAdapter == null) {
            _hardwareStatus.value = RfHardwareStatus.UNSUPPORTED
            _statusMessage.value = "Bluetooth hardware unavailable on device"
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            _hardwareStatus.value = RfHardwareStatus.BLUETOOTH_OFF
            _statusMessage.value = "Bluetooth is currently disabled. Enable Bluetooth in Android settings to scan."
            return
        }

        try {
            bleScanner = bluetoothAdapter.bluetoothLeScanner
            if (bleScanner == null) {
                _hardwareStatus.value = RfHardwareStatus.BLUETOOTH_OFF
                _statusMessage.value = "Bluetooth LE scanner not ready. Please toggle Bluetooth."
                return
            }

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0)
                .build()

            scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    processScanResult(result)
                }

                override fun onBatchScanResults(results: MutableList<ScanResult>) {
                    results.forEach { processScanResult(it) }
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.e("RfScannerEngine", "Scan failed: $errorCode")
                    _isScanning.value = false
                    _hardwareStatus.value = RfHardwareStatus.ERROR
                    _statusMessage.value = "BLE Scan failed with code: $errorCode"
                }
            }

            _isScanning.value = true
            _hardwareStatus.value = RfHardwareStatus.SCANNING
            _statusMessage.value = null
            bleScanner?.startScan(null, settings, scanCallback)
        } catch (e: SecurityException) {
            Log.e("RfScannerEngine", "Security exception during scan: ${e.message}")
            _isScanning.value = false
            _hardwareStatus.value = RfHardwareStatus.PERMISSION_REQUIRED
            _statusMessage.value = "Location or Nearby Devices permission required to scan."
        } catch (e: Exception) {
            Log.e("RfScannerEngine", "Error starting scan: ${e.message}")
            _isScanning.value = false
            _hardwareStatus.value = RfHardwareStatus.ERROR
            _statusMessage.value = "Could not initialize BLE scanner: ${e.message}"
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        _isScanning.value = false
        try {
            if (scanCallback != null && bleScanner != null) {
                bleScanner?.stopScan(scanCallback)
            }
        } catch (e: Exception) {
            Log.e("RfScannerEngine", "Error stopping scan: ${e.message}")
        }
        scanCallback = null
        simulationJob?.cancel()
        checkHardwareState()
    }

    fun toggleSimulationMode(enabled: Boolean) {
        _isSimulationMode.value = enabled
        clearDevices()
        if (_isScanning.value) {
            stopScan()
            startScan()
        } else {
            checkHardwareState()
        }
    }

    fun clearDevices() {
        _devices.value = emptyList()
        _stalkerAlerts.value = emptyList()
        _skimmerReports.value = emptyList()
        prefs.edit().remove("history").apply()
    }

    @SuppressLint("MissingPermission")
    private fun processScanResult(result: ScanResult) {
        val device = result.device ?: return
        val address = device.address ?: "00:00:00:00:00:00"
        val name = device.name ?: result.scanRecord?.deviceName
        val rssi = result.rssi
        val txPower = if (result.txPower != ScanResult.TX_POWER_NOT_PRESENT) result.txPower else -59
        val distance = calculateDistance(rssi, txPower)

        val (deviceType, threatLevel, threatReason) = classifyDevice(result, name, address)

        val currentList = _devices.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.macAddress == address }
        val persisted = loadPersistedHistory()[address]

        val updatedDevice: DiscoveredDevice
        if (existingIndex >= 0) {
            val existing = currentList[existingIndex]
            val effectiveFirstSeen = minOf(existing.firstSeen, persisted?.firstSeen ?: existing.firstSeen)
            val effectiveCount = maxOf(existing.detectionCount, persisted?.detectionCount ?: 0) + 1
            val lastSeen = System.currentTimeMillis()

            updatedDevice = existing.copy(
                name = name ?: existing.name,
                rssi = (existing.rssi * 0.7 + rssi * 0.3).roundToInt(),
                estimatedDistanceMeters = distance,
                deviceType = if (deviceType != DeviceType.STANDARD_BLE) deviceType else existing.deviceType,
                threatLevel = if (threatLevel.ordinal > existing.threatLevel.ordinal) threatLevel else existing.threatLevel,
                firstSeen = effectiveFirstSeen,
                detectionCount = effectiveCount,
                lastSeen = lastSeen,
                threatReason = threatReason ?: existing.threatReason
            )
            currentList[existingIndex] = updatedDevice
        } else {
            val firstSeen = persisted?.firstSeen ?: System.currentTimeMillis()
            val initialCount = (persisted?.detectionCount ?: 0) + 1
            updatedDevice = DiscoveredDevice(
                macAddress = address,
                name = name ?: persisted?.name,
                rssi = rssi,
                txPower = txPower,
                estimatedDistanceMeters = distance,
                deviceType = deviceType,
                threatLevel = threatLevel,
                firstSeen = firstSeen,
                lastSeen = System.currentTimeMillis(),
                detectionCount = initialCount,
                threatReason = threatReason
            )
            currentList.add(updatedDevice)
        }

        _devices.value = currentList.sortedByDescending { it.threatLevel.ordinal * 1000 + it.rssi }
        persistDetectionHistory(_devices.value)

        // Evaluate Stalker Alerts
        checkStalkerThreats(updatedDevice)

        // Evaluate Skimmer Reports
        if (updatedDevice.deviceType == DeviceType.SKIMMER_SUSPECT) {
            generateSkimmerReport(updatedDevice)
        }
    }

    private fun classifyDevice(
        result: ScanResult,
        name: String?,
        address: String
    ): Triple<DeviceType, ThreatLevel, String?> {
        val scanRecord = result.scanRecord
        val mfgData = scanRecord?.manufacturerSpecificData

        // 1. Check Skimmer signatures (HC-05, HC-06, open SPP serial)
        val lowerName = name?.lowercase() ?: ""
        val matchesSkimmerName = skimmerNameKeywords.any { lowerName.contains(it) }
        val matchesSkimmerMac = skimmerMacPrefixes.any { address.uppercase().startsWith(it) }
        val hasSppUuid = scanRecord?.serviceUuids?.any {
            it.uuid.toString().equals("00001101-0000-1000-8000-00805f9b34fb", ignoreCase = true)
        } == true

        val signalCount = listOf(
            matchesSkimmerName,
            matchesSkimmerMac,
            hasSppUuid
        ).count { it }

        // Require either 2+ independent signals OR very close proximity + 1 signal
        val isNearby = result.rssi > -60  // roughly within 1-2 meters
        if (signalCount >= 2 || (signalCount >= 1 && isNearby)) {
            val reason = buildString {
                if (matchesSkimmerName) append("Bluetooth serial bridge name pattern ($name). ")
                if (matchesSkimmerMac) append("MAC OUI matches known skimmer transceiver. ")
                if (hasSppUuid) append("Exposes unauthenticated SPP RFCOMM profile. ")
                append("Confirm by visually inspecting the terminal before use.")
            }
            val level = if (signalCount >= 2) ThreatLevel.CRITICAL_ALERT else ThreatLevel.SUSPICIOUS
            return Triple(DeviceType.SKIMMER_SUSPECT, level, reason)
        }

        // 2. Apple AirTag / FindMy Network
        val appleBytes = mfgData?.get(0x004C)
        if (appleBytes != null && appleBytes.isNotEmpty()) {
            if (appleBytes.size >= 2 && appleBytes[0] == 0x12.toByte()) {
                return Triple(
                    DeviceType.AIRTAG,
                    ThreatLevel.SUSPICIOUS,
                    "Apple FindMy / AirTag beacon detected in offline beacon mode"
                )
            }
            if (appleBytes.size >= 2 && appleBytes[0] == 0x10.toByte()) {
                return Triple(
                    DeviceType.AIRTAG,
                    ThreatLevel.SUSPICIOUS,
                    "Apple Continuity/FindMy accessory payload active"
                )
            }
        }

        // 3. Samsung SmartTag
        val samsungBytes = mfgData?.get(0x0075)
        if (samsungBytes != null && samsungBytes.size >= 3) {
            return Triple(
                DeviceType.SMARTTAG,
                ThreatLevel.SUSPICIOUS,
                "Samsung SmartThings Find beacon tracking frame detected"
            )
        }

        // 4. Tile Tracker
        val hasTileUuid = scanRecord?.serviceUuids?.any {
            it.uuid.toString().startsWith("0000feed", ignoreCase = true)
        } == true
        if (hasTileUuid || lowerName.contains("tile")) {
            return Triple(
                DeviceType.TILE,
                ThreatLevel.SUSPICIOUS,
                "Tile BLE tracking beacon advertising continuous presence"
            )
        }

        // 5. Generic Beacons (AltBeacon, iBeacon)
        if (scanRecord?.serviceUuids?.isNotEmpty() == true || mfgData?.size() ?: 0 > 0) {
            return Triple(DeviceType.GENERIC_BEACON, ThreatLevel.BENIGN, null)
        }

        return Triple(DeviceType.STANDARD_BLE, ThreatLevel.BENIGN, null)
    }

    private fun checkStalkerThreats(device: DiscoveredDevice) {
        if (device.deviceType in listOf(DeviceType.AIRTAG, DeviceType.SMARTTAG, DeviceType.TILE)) {
            val observationSpanMs = device.lastSeen - device.firstSeen
            val minutesFollowing = (observationSpanMs / 60000).toInt()

            // Legitimate stalker warning criteria: observed over at least 5 minutes and 6+ scan hits
            if (device.detectionCount >= 6 && minutesFollowing >= 5) {
                val existing = _stalkerAlerts.value.find { it.targetMac == device.macAddress }
                if (existing == null) {
                    val alert = StalkerAlert(
                        targetMac = device.macAddress,
                        deviceType = device.deviceType,
                        trackingDurationMinutes = maxOf(5, minutesFollowing),
                        sightingCount = device.detectionCount,
                        message = "Suspicious ${device.deviceType.name} observed following across ${maxOf(5, minutesFollowing)} minutes (${device.detectionCount} hits, RSSI ${device.rssi} dBm)"
                    )
                    _stalkerAlerts.value = _stalkerAlerts.value + alert
                }
            }
        }
    }

    private fun generateSkimmerReport(device: DiscoveredDevice) {
        val existing = _skimmerReports.value.find { it.macAddress == device.macAddress }
        if (existing == null) {
            val report = SkimmerThreatReport(
                moduleName = device.name ?: "Unidentified Bluetooth serial module",
                macAddress = device.macAddress,
                rssi = device.rssi,
                confidenceScore = when {
                    device.threatLevel == ThreatLevel.CRITICAL_ALERT -> 88
                    else -> 62
                },
                matchedPattern = device.threatReason ?: "Possible serial bridge module",
                pinVulnerability = "Some HC-05/06 modules ship with default PINs (1234 / 0000). If this is a skimmer, that PIN could expose card data.",
                recommendedAction = "This is a precautionary alert, not proof of a skimmer. Inspect any nearby card terminal or fuel pump for loose bezels or broken security seals before inserting your card."
            )
            _skimmerReports.value = _skimmerReports.value + report
        }
    }

    private fun calculateDistance(rssi: Int, txPower: Int): Double {
        if (rssi == 0) return -1.0
        val ratio = rssi * 1.0 / txPower
        return if (ratio < 1.0) {
            ratio.pow(10.0)
        } else {
            val accuracy = 0.89976 * ratio.pow(7.7095) + 0.111
            (accuracy * 100).roundToInt() / 100.0
        }
    }

    private fun startSimulationScan() {
        simulationJob?.cancel()
        simulationJob = scope.launch {
            val simulatedMocks = listOf(
                DiscoveredDevice(
                    macAddress = "38:F9:D3:A1:8B:20",
                    name = "Apple AirTag [SIMULATED]",
                    rssi = -64,
                    txPower = -59,
                    estimatedDistanceMeters = 1.8,
                    deviceType = DeviceType.AIRTAG,
                    threatLevel = ThreatLevel.SUSPICIOUS,
                    detectionCount = 1,
                    firstSeen = System.currentTimeMillis(),
                    lastSeen = System.currentTimeMillis(),
                    threatReason = "[Demo] Apple FindMy beacon frame detected"
                ),
                DiscoveredDevice(
                    macAddress = "98:D3:31:FD:44:B2",
                    name = "HC-05 Skimmer [SIMULATED]",
                    rssi = -58,
                    txPower = -55,
                    estimatedDistanceMeters = 1.1,
                    deviceType = DeviceType.SKIMMER_SUSPECT,
                    threatLevel = ThreatLevel.CRITICAL_ALERT,
                    detectionCount = 1,
                    threatReason = "[Demo] Matches HC-05 rogue ATM serial bridge"
                )
            )

            _devices.value = simulatedMocks
            generateSkimmerReport(simulatedMocks[1])

            while (isActive && _isScanning.value) {
                delay(1500)
                val updated = _devices.value.map { dev ->
                    val jitter = ((-2..2).random())
                    val newRssi = (dev.rssi + jitter).coerceIn(-90, -40)
                    dev.copy(
                        rssi = newRssi,
                        estimatedDistanceMeters = calculateDistance(newRssi, dev.txPower),
                        detectionCount = dev.detectionCount + 1,
                        lastSeen = System.currentTimeMillis()
                    )
                }
                _devices.value = updated
            }
        }
    }
}
