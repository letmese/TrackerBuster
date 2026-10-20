package com.omnisentinel.app.features.rf

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnisentinel.app.ui.components.*
import com.omnisentinel.app.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

enum class RfSubTab(val label: String) {
    RADAR("Radar / Trackers"),
    SKIMMER("Skimmer Hunter"),
    COMPASS("Hot/Cold Compass")
}

@Composable
fun RfScreen(
    scannerEngine: RfScannerEngine,
    compassEngine: DirectionalCompassEngine
) {
    val context = LocalContext.current
    val isScanning by scannerEngine.isScanning.collectAsState()
    val isSimulated by scannerEngine.isSimulationMode.collectAsState()
    val hardwareStatus by scannerEngine.hardwareStatus.collectAsState()
    val statusMessage by scannerEngine.statusMessage.collectAsState()
    val devices by scannerEngine.devices.collectAsState()
    val stalkerAlerts by scannerEngine.stalkerAlerts.collectAsState()
    val skimmerReports by scannerEngine.skimmerReports.collectAsState()
    val compassState by compassEngine.compassState.collectAsState()

    var selectedTab by remember { mutableStateOf(RfSubTab.RADAR) }

    LaunchedEffect(Unit) {
        scannerEngine.checkHardwareState()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TacticalBlack)
    ) {
        TacticalHeader(
            title = "Tracker Buster",
            subtitle = "RF Sentinel & Skimmer Radar",
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isSimulated) "DEMO" else "LIVE",
                        color = if (isSimulated) AlertAmber else NeonCyan,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Switch(
                        checked = isSimulated,
                        onCheckedChange = { scannerEngine.toggleSimulationMode(it) },
                        modifier = Modifier.scale(0.75f),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AlertAmber,
                            checkedTrackColor = AlertAmber.copy(alpha = 0.3f)
                        )
                    )
                }
            }
        )

        // Sub Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurfaceElevated),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            RfSubTab.values().forEach { tab ->
                val isSelected = selectedTab == tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) NeonCyan.copy(alpha = 0.2f) else Color.Transparent)
                        .border(
                            if (isSelected) BorderStroke(1.dp, NeonCyan) else BorderStroke(0.dp, Color.Transparent),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedTab = tab }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tab.label,
                        color = if (isSelected) NeonCyan else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Hardware Status Notice (Bluetooth Disabled or Permissions)
        if (hardwareStatus == RfHardwareStatus.BLUETOOTH_OFF && !isSimulated) {
            Surface(
                color = AlertAmber.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, AlertAmber),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.BluetoothDisabled, contentDescription = null, tint = AlertAmber)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "BLUETOOTH IS DISABLED",
                            color = AlertAmber,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Turn on Bluetooth to scan for AirTags, SmartTags, and skimmers in the air.",
                            color = TextPrimary,
                            fontSize = 11.sp
                        )
                    }
                    Button(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AlertAmber),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("ENABLE", color = TacticalBlack, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else if (hardwareStatus == RfHardwareStatus.PERMISSION_REQUIRED && !isSimulated) {
            Surface(
                color = AlertAmber.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, AlertAmber),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = AlertAmber)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "PERMISSIONS REQUIRED",
                            color = AlertAmber,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Android requires Nearby Devices / Location permissions to scan BLE frequencies.",
                            color = TextPrimary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Global Scan Controller
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TacticalPulseIndicator(active = isScanning, color = if (isScanning) RadarGreen else Color.Gray)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        isScanning -> "RADAR SCANNING (${devices.size} BEACONS)"
                        hardwareStatus == RfHardwareStatus.BLUETOOTH_OFF -> "BLUETOOTH OFF"
                        else -> "SCANNER IDLE"
                    },
                    color = if (isScanning) RadarGreen else TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = {
                    if (isScanning) scannerEngine.stopScan() else scannerEngine.startScan()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isScanning) ThreatCrimson.copy(alpha = 0.8f) else NeonCyan
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = if (isScanning) "STOP SCAN" else "START SCAN",
                    color = if (isScanning) Color.White else TacticalBlack,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
        }

        // Real Stalker Threat Alert Banner (Only shown if a persistent tracker was legitimately confirmed)
        AnimatedVisibility(visible = stalkerAlerts.isNotEmpty()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                stalkerAlerts.forEach { alert ->
                    Surface(
                        color = ThreatCrimson.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, ThreatCrimson),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = ThreatCrimson,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "ALERT: SUSPICIOUS TRACKER NEARBY",
                                    color = ThreatCrimson,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = alert.message,
                                    color = TextPrimary,
                                    fontSize = 11.sp
                                )
                            }
                            IconButton(onClick = { scannerEngine.clearDevices() }) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = ThreatCrimson)
                            }
                        }
                    }
                }
            }
        }

        when (selectedTab) {
            RfSubTab.RADAR -> RadarTabContent(
                devices = devices,
                isScanning = isScanning,
                hardwareStatus = hardwareStatus,
                stalkerAlerts = stalkerAlerts,
                onTrackDevice = {
                    compassEngine.startTracking(it)
                    selectedTab = RfSubTab.COMPASS
                }
            )
            RfSubTab.SKIMMER -> SkimmerTabContent(skimmerReports, devices)
            RfSubTab.COMPASS -> CompassTabContent(compassState, compassEngine, devices)
        }
    }
}

@Composable
private fun RadarTabContent(
    devices: List<DiscoveredDevice>,
    isScanning: Boolean,
    hardwareStatus: RfHardwareStatus,
    stalkerAlerts: List<StalkerAlert>,
    onTrackDevice: (DiscoveredDevice) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            TacticalRadarHud(devices = devices, isScanning = isScanning)
        }

        // Reassuring Clean Airspace Card when safe
        item {
            if (stalkerAlerts.isEmpty()) {
                Surface(
                    color = DarkSurfaceElevated,
                    border = BorderStroke(1.dp, if (isScanning) RadarGreen.copy(alpha = 0.5f) else Color.Transparent),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = null,
                            tint = if (isScanning) RadarGreen else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isScanning) "NO TRACKERS DETECTED IN THIS SCAN" else "SURVEILLANCE STANDBY",
                                color = if (isScanning) RadarGreen else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Continuous BLE monitoring for Apple FindMy, SmartTag, Tile, and skimmer signatures.",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "SURROUNDING BLE SIGNALS (${devices.size})",
                color = TextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        if (devices.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isScanning)
                            "Listening for nearby Bluetooth broadcasts..."
                        else
                            "No signals captured. Press 'START SCAN' above.",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        } else {
            items(devices, key = { it.macAddress }) { device ->
                DiscoveredDeviceCard(device = device, onTrack = { onTrackDevice(device) })
            }
        }
    }
}

@Composable
fun TacticalRadarHud(devices: List<DiscoveredDevice>, isScanning: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "radarSweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweepAngle"
    )

    TacticalCard(
        title = "360° RF AIRSPACE RADAR",
        badgeText = if (isScanning) "LIVE SWEEP" else "STANDBY",
        badgeColor = if (isScanning) RadarGreen else TextMuted
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(190.dp)) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.width / 2

                drawCircle(color = BorderCyan.copy(alpha = 0.15f), radius = radius, center = center)
                drawCircle(color = BorderCyan.copy(alpha = 0.25f), radius = radius * 0.66f, center = center)
                drawCircle(color = BorderCyan.copy(alpha = 0.35f), radius = radius * 0.33f, center = center)

                drawLine(
                    color = BorderCyan.copy(alpha = 0.25f),
                    start = Offset(0f, center.y),
                    end = Offset(size.width, center.y),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = BorderCyan.copy(alpha = 0.25f),
                    start = Offset(center.x, 0f),
                    end = Offset(center.x, size.height),
                    strokeWidth = 1.dp.toPx()
                )

                if (isScanning) {
                    val sweepRad = Math.toRadians(sweepAngle.toDouble())
                    val beamEnd = Offset(
                        center.x + radius * cos(sweepRad).toFloat(),
                        center.y + radius * sin(sweepRad).toFloat()
                    )
                    drawLine(
                        brush = Brush.radialGradient(
                            colors = listOf(NeonCyan, Color.Transparent),
                            center = center,
                            radius = radius
                        ),
                        start = center,
                        end = beamEnd,
                        strokeWidth = 2.dp.toPx()
                    )
                }

                devices.take(12).forEachIndexed { index, dev ->
                    val distanceFactor = ((dev.rssi + 100).coerceIn(10, 80) / 80f)
                    val r = (1f - distanceFactor) * radius * 0.9f
                    val angle = (index * 67f) % 360f
                    val rad = Math.toRadians(angle.toDouble())
                    val blipCenter = Offset(
                        center.x + r * cos(rad).toFloat(),
                        center.y + r * sin(rad).toFloat()
                    )

                    val blipColor = when (dev.deviceType) {
                        DeviceType.SKIMMER_SUSPECT -> ThreatCrimson
                        DeviceType.AIRTAG -> AlertAmber
                        DeviceType.SMARTTAG -> ElectricGold
                        DeviceType.TILE -> Color(0xFF00E5FF)
                        else -> RadarGreen
                    }
                    drawCircle(color = blipColor, radius = 5.dp.toPx(), center = blipCenter)
                }
            }
        }
    }
}

@Composable
fun DiscoveredDeviceCard(
    device: DiscoveredDevice,
    onTrack: () -> Unit
) {
    val badgeColor = when (device.deviceType) {
        DeviceType.SKIMMER_SUSPECT -> ThreatCrimson
        DeviceType.AIRTAG -> AlertAmber
        DeviceType.SMARTTAG -> ElectricGold
        DeviceType.TILE -> NeonCyan
        else -> TextSecondary
    }

    val typeLabel = when (device.deviceType) {
        DeviceType.SKIMMER_SUSPECT -> "ROGUE SKIMMER"
        DeviceType.AIRTAG -> "APPLE AIRTAG"
        DeviceType.SMARTTAG -> "SAMSUNG SMARTTAG"
        DeviceType.TILE -> "TILE TRACKER"
        DeviceType.GENERIC_BEACON -> "BEACON"
        DeviceType.STANDARD_BLE -> "BLE DEVICE"
    }

    TacticalCard(
        borderColor = if (device.threatLevel == ThreatLevel.CRITICAL_ALERT) ThreatCrimson else DarkSurfaceElevated,
        title = device.name ?: "Unnamed Device",
        badgeText = typeLabel,
        badgeColor = badgeColor
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "MAC: ${device.macAddress}",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "RSSI: ${device.rssi} dBm  |  Dist: ~${"%.1f".format(device.estimatedDistanceMeters)}m",
                    color = if (device.rssi > -65) RadarGreen else TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                if (device.threatReason != null) {
                    Text(
                        text = device.threatReason,
                        color = ThreatCrimson,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Button(
                onClick = onTrack,
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(alpha = 0.2f)),
                border = BorderStroke(1.dp, NeonCyan),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "LOCATE",
                    color = NeonCyan,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SkimmerTabContent(
    reports: List<SkimmerThreatReport>,
    devices: List<DiscoveredDevice>
) {
    val skimmers = devices.filter { it.deviceType == DeviceType.SKIMMER_SUSPECT }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            TacticalCard(
                title = "ATM / PUMP SKIMMER SURVEILLANCE",
                badgeText = if (skimmers.isNotEmpty()) "${skimmers.size} SUSPECT MODULES" else "CLEAR",
                badgeColor = if (skimmers.isNotEmpty()) ThreatCrimson else RadarGreen
            ) {
                Text(
                    text = "Criminal card skimmers inside gas pumps and ATMs use unauthenticated HC-05/06 or SPP serial Bluetooth chips. These bridge magnetic stripe read heads directly to wireless criminals parked nearby.",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        if (skimmers.isNotEmpty()) {
            items(skimmers) { skimmer ->
                TacticalCard(
                    borderColor = ThreatCrimson,
                    title = "POSSIBLE MODULE: ${skimmer.name}",
                    badgeText = "CRITICAL",
                    badgeColor = ThreatCrimson
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "MAC: ${skimmer.macAddress}  |  Signal: ${skimmer.rssi} dBm",
                            color = ThreatCrimson,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Pattern Match: ${skimmer.threatReason ?: "HC-05 rogue bridge"}",
                            color = TextPrimary,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "Vulnerability: Default PIN 1234 / 0000 allows wireless dumping of magnetic tracks 1 & 2 without cardholder knowledge.",
                            color = AlertAmber,
                            fontSize = 11.sp
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = ThreatCrimson.copy(alpha = 0.3f)
                        )
                        Text(
                            text = "ACTION: Do NOT insert your card into this terminal. Alert station attendant.",
                            color = ThreatCrimson,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        item {
            TacticalCard(
                title = "PHYSICAL INSPECTION CHECKLIST",
                badgeText = "BEST PRACTICES",
                badgeColor = NeonCyan
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("1. Wiggle the card slot: Legitimate ATM slots do not flex or have loose bezels.", color = TextPrimary, fontSize = 11.sp)
                    Text("2. Check for broken security seals on pump cabinet doors ('VOID' watermark).", color = TextPrimary, fontSize = 11.sp)
                    Text("3. Look above keypad for pinhole hidden cameras aimed at your PIN.", color = TextPrimary, fontSize = 11.sp)
                    Text("4. Shield PIN with your second hand — completely thwarts overhead cameras.", color = TextPrimary, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun CompassTabContent(
    compassState: CompassTargetState,
    compassEngine: DirectionalCompassEngine,
    allDevices: List<DiscoveredDevice>
) {
    var expandedDropdown by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TacticalCard(
            title = "TARGET BEACON LOCK",
            badgeText = if (compassState.isTracking) "LOCKED" else "STANDBY",
            badgeColor = if (compassState.isTracking) RadarGreen else AlertAmber
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedDropdown = true }
                    .background(DarkSurface, RoundedCornerShape(6.dp))
                    .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = compassState.targetDevice?.name ?: "Select a Bluetooth Device...",
                    color = if (compassState.targetDevice != null) TextPrimary else TextMuted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = NeonCyan)
            }

            DropdownMenu(
                expanded = expandedDropdown,
                onDismissRequest = { expandedDropdown = false }
            ) {
                if (allDevices.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No devices in range yet. Start scan first.", fontSize = 11.sp) },
                        onClick = { expandedDropdown = false }
                    )
                } else {
                    allDevices.forEach { dev ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "${dev.name ?: "Unknown"} (${dev.macAddress})",
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            },
                            onClick = {
                                compassEngine.startTracking(dev)
                                expandedDropdown = false
                            }
                        )
                    }
                }
            }
        }

        TacticalCard(
            title = "DIRECTIONAL HUD & DISTANCE",
            badgeText = compassState.zone.label,
            badgeColor = Color(compassState.zone.thermalColorHex)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(240.dp)) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val radius = size.width / 2

                    drawCircle(color = DarkSurface, radius = radius, center = center)
                    drawCircle(
                        color = Color(compassState.zone.thermalColorHex).copy(alpha = 0.2f),
                        radius = radius * (compassState.proximityPercent / 100f).coerceIn(0.1f, 1f),
                        center = center
                    )
                    drawCircle(
                        color = BorderCyan.copy(alpha = 0.4f),
                        radius = radius,
                        center = center,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                    )

                    for (i in 0 until 360 step 30) {
                        val rad = Math.toRadians(i.toDouble())
                        val p1 = Offset(
                            center.x + (radius - 12.dp.toPx()) * cos(rad).toFloat(),
                            center.y + (radius - 12.dp.toPx()) * sin(rad).toFloat()
                        )
                        val p2 = Offset(
                            center.x + radius * cos(rad).toFloat(),
                            center.y + radius * sin(rad).toFloat()
                        )
                        drawLine(
                            color = BorderCyan.copy(alpha = 0.6f),
                            start = p1,
                            end = p2,
                            strokeWidth = 2.dp.toPx()
                        )
                    }

                    val needleRad = Math.toRadians(compassState.azimuthDegrees.toDouble())
                    val needleEnd = Offset(
                        center.x + (radius - 24.dp.toPx()) * sin(needleRad).toFloat(),
                        center.y - (radius - 24.dp.toPx()) * cos(needleRad).toFloat()
                    )
                    drawLine(
                        color = Color(compassState.zone.thermalColorHex),
                        start = center,
                        end = needleEnd,
                        strokeWidth = 4.dp.toPx()
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "${compassState.proximityPercent}%",
                        color = Color(compassState.zone.thermalColorHex),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "~${"%.1f".format(compassState.distanceEstimateMeters)} METERS",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${compassState.smoothedRssi.toInt()} dBm",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
