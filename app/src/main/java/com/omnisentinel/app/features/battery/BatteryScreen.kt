package com.omnisentinel.app.features.battery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnisentinel.app.ui.components.*
import com.omnisentinel.app.ui.theme.*

enum class BatterySubTab(val label: String) {
    COCKPIT("Power Cockpit"),
    CABLE_TEST("Cable Tester"),
    HEALTH("Health & Cycles")
}

@Composable
fun BatteryScreen(
    batteryEngine: BatteryEngine
) {
    val metrics by batteryEngine.metrics.collectAsState()
    val isBenchmarking by batteryEngine.isBenchmarking.collectAsState()
    val benchmarkProgress by batteryEngine.benchmarkProgress.collectAsState()
    val benchmarkResult by batteryEngine.benchmarkResult.collectAsState()
    val is80Alarm by batteryEngine.is80AlarmEnabled.collectAsState()
    val isOverheatAlarm by batteryEngine.isOverheatAlarmEnabled.collectAsState()
    val alarmMessage by batteryEngine.alarmTriggeredMessage.collectAsState()

    var selectedTab by remember { mutableStateOf(BatterySubTab.COCKPIT) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TacticalBlack)
    ) {
        TacticalHeader(
            title = "CableTester & Battery",
            subtitle = "Wattage Benchmarker & IC Health Cockpit",
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (metrics.isSimulation) "SIM" else "HW",
                        color = if (metrics.isSimulation) AlertAmber else NeonCyan,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Switch(
                        checked = metrics.isSimulation,
                        onCheckedChange = { batteryEngine.toggleSimulation(it) },
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
            BatterySubTab.values().forEach { tab ->
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

        // Active Alarm Banner
        AnimatedVisibility(visible = alarmMessage != null) {
            Surface(
                color = ThreatCrimson.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, ThreatCrimson),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = ThreatCrimson)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "BATTERY PROTECTION ALARM",
                            color = ThreatCrimson,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(text = alarmMessage ?: "", color = TextPrimary, fontSize = 11.sp)
                    }
                    IconButton(onClick = { batteryEngine.dismissAlarm() }) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = ThreatCrimson)
                    }
                }
            }
        }

        when (selectedTab) {
            BatterySubTab.COCKPIT -> CockpitTabContent(metrics, is80Alarm, isOverheatAlarm, batteryEngine)
            BatterySubTab.CABLE_TEST -> CableTestTabContent(
                metrics = metrics,
                isBenchmarking = isBenchmarking,
                progress = benchmarkProgress,
                result = benchmarkResult,
                onRunTest = { batteryEngine.runCableBenchmark() }
            )
            BatterySubTab.HEALTH -> HealthTabContent(metrics)
        }
    }
}

@Composable
private fun CockpitTabContent(
    metrics: BatteryMetrics,
    is80Alarm: Boolean,
    isOverheat: Boolean,
    engine: BatteryEngine
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // Main Wattage Gauge
        item {
            TacticalCard(
                title = "REAL-TIME POWER COCKPIT",
                badgeText = if (metrics.isCharging) "CHARGING" else "DISCHARGING",
                badgeColor = if (metrics.isCharging) RadarGreen else AlertAmber
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${"%.2f".format(metrics.powerWatts)} W",
                        color = if (metrics.isCharging) RadarGreen else ElectricGold,
                        fontSize = 50.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${metrics.plugType} | ${metrics.batteryPct}% BATTERY",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MetricSmallBox("CURRENT", "${metrics.currentMa} mA", NeonCyan)
                        MetricSmallBox("VOLTAGE", "${"%.2f".format(metrics.voltageMv / 1000.0)} V", ElectricGold)
                        MetricSmallBox("TEMP", "${"%.1f".format(metrics.temperatureC)} °C", if (metrics.temperatureC > 40f) ThreatCrimson else RadarGreen)
                    }
                }
            }
        }

        // Charge Protector Settings
        item {
            TacticalCard(
                title = "SMART CHARGE & OVERHEAT ALARM",
                badgeText = "PROTECTION ACTIVE",
                badgeColor = RadarGreen
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("80% Charge Lifespan Alarm", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Halts lithium stress to double long-term cycle lifespan", color = TextSecondary, fontSize = 10.sp)
                    }
                    Switch(
                        checked = is80Alarm,
                        onCheckedChange = { engine.set80Alarm(it) },
                        modifier = Modifier.scale(0.75f)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = DarkSurface)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("42°C Overheat Warning", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Protects against thermal throttling and battery swelling during intense gaming", color = TextSecondary, fontSize = 10.sp)
                    }
                    Switch(
                        checked = isOverheat,
                        onCheckedChange = { engine.setOverheatAlarm(it) },
                        modifier = Modifier.scale(0.75f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CableTestTabContent(
    metrics: BatteryMetrics,
    isBenchmarking: Boolean,
    progress: Float,
    result: CableBenchmarkResult?,
    onRunTest: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            TacticalCard(
                title = "CABLE QUALITY & WATTAGE BENCHMARKER",
                badgeText = "10s LOAD TEST",
                badgeColor = NeonCyan
            ) {
                Text(
                    text = "Tests your USB cable under active load to measure resistance, voltage drops, and maximum power delivery. Tells you whether your cable supports true 25W+ Super Fast Charging or is damaged/counterfeit.",
                    color = TextSecondary,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (isBenchmarking) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            color = NeonCyan,
                            trackColor = DarkSurfaceElevated
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "SAMPLING POWER DELIVERY: ${(progress * 100).toInt()}%",
                            color = NeonCyan,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Button(
                        onClick = onRunTest,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = TacticalBlack)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "START 10-SEC CABLE BENCHMARK",
                            color = TacticalBlack,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        if (result != null) {
            item {
                TacticalCard(
                    borderColor = Color(result.gradeColorHex),
                    title = "CABLE CERTIFICATE: GRADE ${result.grade}",
                    badgeText = result.speedCategory,
                    badgeColor = Color(result.gradeColorHex)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "GRADE ${result.grade}",
                                color = Color(result.gradeColorHex),
                                fontSize = 42.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "STABILITY: ${result.stabilityPercent}%",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "AVG: ${"%.1f".format(result.avgWatts)} W",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "PEAK: ${"%.1f".format(result.peakWatts)} W",
                                color = NeonCyan,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = DarkSurface)

                    Text(
                        text = result.diagnosticSummary,
                        color = TextPrimary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun HealthTabContent(metrics: BatteryMetrics) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            TacticalCard(
                title = "BATTERY TELEMETRY",
                badgeText = metrics.healthPercent?.let { "$it% CAPACITY" } ?: "HEALTH N/A",
                badgeColor = if ((metrics.healthPercent ?: 0) > 80) RadarGreen else AlertAmber
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = metrics.cycleCount?.let { "$it CYCLES" } ?: "CYCLES: NOT AVAILABLE",
                            color = if (metrics.cycleCount != null) ElectricGold else TextMuted,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (metrics.cycleCount != null)
                                "Read from Battery Management IC (Android 14+)"
                            else
                                "Your device does not expose cycle count to apps. Check Settings → Battery.",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }

                    Surface(
                        color = DarkSurface,
                        border = BorderStroke(1.dp, RadarGreen),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "STATE: ${metrics.healthString.uppercase()}",
                            color = RadarGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (metrics.designCapacityMah != null && metrics.estimatedCapacityMah != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Design Capacity: ${metrics.designCapacityMah} mAh", color = TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text("Estimated Remaining: ${metrics.estimatedCapacityMah} mAh", color = NeonCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (metrics.healthPercent != null) {
                    LinearProgressIndicator(
                        progress = { (metrics.healthPercent ?: 0) / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = RadarGreen,
                        trackColor = DarkSurfaceElevated
                    )
                } else {
                    Text(
                        text = "Battery capacity percentage is not readable on this device.",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }

        item {
            TacticalCard(
                title = "LITHIUM-ION DEGRADATION ANALYSIS",
                badgeText = "HARDWARE TELEMETRY",
                badgeColor = NeonCyan
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("• Typical Smartphone cells retain 80% capacity at 500 charge cycles (~2 years).", color = TextPrimary, fontSize = 11.sp)
                    Text("• Operating above 40°C degrades electrolyte 3x faster.", color = AlertAmber, fontSize = 11.sp)
                    Text("• Keeping charge bounded between 20% and 80% can increase cycle life to over 1,200 cycles.", color = RadarGreen, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun MetricSmallBox(label: String, value: String, color: Color) {
    Surface(
        color = DarkSurface,
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Text(value, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }
}
