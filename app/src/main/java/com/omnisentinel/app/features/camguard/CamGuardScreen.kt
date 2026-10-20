package com.omnisentinel.app.features.camguard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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

@Composable
fun CamGuardScreen(
    magneticEngine: MagneticScannerEngine
) {
    val emfReading by magneticEngine.reading.collectAsState()
    val emfHistory by magneticEngine.history.collectAsState()
    val isEmfScanning by magneticEngine.isScanning.collectAsState()
    val isGeigerSound by magneticEngine.isGeigerSoundEnabled.collectAsState()
    val isSimulated by magneticEngine.isSimulation.collectAsState()

    var selectedMode by remember { mutableStateOf(CamGuardMode.OPTICAL_GLINT) }
    var currentStrobe by remember { mutableStateOf(StrobeRate.MEDIUM) }
    var isFrontCamera by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TacticalBlack)
    ) {
        TacticalHeader(
            title = "CamGuard / Optical & EMF",
            subtitle = "Hidden Camera & Surveillance Detector",
            trailingContent = {
                if (selectedMode == CamGuardMode.MAGNETIC_EMF) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isSimulated) "SIM" else "MAG",
                            color = if (isSimulated) AlertAmber else NeonCyan,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Switch(
                            checked = isSimulated,
                            onCheckedChange = { magneticEngine.toggleSimulation(it) },
                            modifier = Modifier.scale(0.75f),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AlertAmber,
                                checkedTrackColor = AlertAmber.copy(alpha = 0.3f)
                            )
                        )
                    }
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
            CamGuardMode.values().forEach { mode ->
                val isSelected = selectedMode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) NeonCyan.copy(alpha = 0.2f) else Color.Transparent)
                        .border(
                            if (isSelected) BorderStroke(1.dp, NeonCyan) else BorderStroke(0.dp, Color.Transparent),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedMode = mode }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.title,
                        color = if (isSelected) NeonCyan else TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        when (selectedMode) {
            CamGuardMode.OPTICAL_GLINT -> OpticalGlintTab(
                currentStrobe = currentStrobe,
                onSelectStrobe = { currentStrobe = it }
            )
            CamGuardMode.INFRARED_EYE -> InfraredEyeTab(
                isFrontCamera = isFrontCamera,
                onToggleCamera = { isFrontCamera = !isFrontCamera }
            )
            CamGuardMode.MAGNETIC_EMF -> MagneticEmfTab(
                reading = emfReading,
                history = emfHistory,
                isScanning = isEmfScanning,
                isGeiger = isGeigerSound,
                onStart = { magneticEngine.start() },
                onStop = { magneticEngine.stop() },
                onTare = { magneticEngine.tareBaseline() },
                onToggleGeiger = { magneticEngine.toggleGeigerSound(!isGeigerSound) }
            )
        }
    }
}

@Composable
private fun OpticalGlintTab(
    currentStrobe: StrobeRate,
    onSelectStrobe: (StrobeRate) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            CameraViewfinder(
                strobeRate = currentStrobe,
                isFrontCamera = false,
                isNightEyeMode = false
            )
        }

        item {
            TacticalCard(
                title = "FLASH STROBE FREQUENCY",
                badgeText = currentStrobe.label,
                badgeColor = ThreatCrimson
            ) {
                Text(
                    text = "Select strobe pulsing rate. Rapid flashes cause pinhole glass lenses to emit sharp retro-reflective points of light back at the camera.",
                    color = TextSecondary,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StrobeRate.values().forEach { rate ->
                        val isSelected = currentStrobe == rate
                        Button(
                            onClick = { onSelectStrobe(rate) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) ThreatCrimson else DarkSurface
                            ),
                            border = BorderStroke(1.dp, if (isSelected) ThreatCrimson else BorderCyan.copy(alpha = 0.3f)),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = rate.label,
                                color = if (isSelected) Color.White else TextSecondary,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        item {
            TacticalCard(
                title = "OPTICAL RETRO-REFLECTION GUIDE",
                badgeText = "INSPECTION HINTS",
                badgeColor = NeonCyan
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("• Turn off ambient room lights to maximize contrast.", color = TextPrimary, fontSize = 11.sp)
                    Text("• Hold the phone right at eye level as you pan across smoke detectors, electrical outlets, clocks, and paintings.", color = TextPrimary, fontSize = 11.sp)
                    Text("• Pinhole camera lenses reflect a tiny, fierce, pulsing dot of red/white light.", color = AlertAmber, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun InfraredEyeTab(
    isFrontCamera: Boolean,
    onToggleCamera: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            CameraViewfinder(
                strobeRate = StrobeRate.OFF,
                isFrontCamera = isFrontCamera,
                isNightEyeMode = true
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ACTIVE SENSOR: ${if (isFrontCamera) "FRONT CAMERA (NO IR FILTER)" else "REAR CAMERA"}",
                    color = UltrasonicViolet,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                Button(
                    onClick = onToggleCamera,
                    colors = ButtonDefaults.buttonColors(containerColor = UltrasonicViolet.copy(alpha = 0.25f)),
                    border = BorderStroke(1.dp, UltrasonicViolet),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Default.FlipCameraAndroid, contentDescription = null, tint = UltrasonicViolet)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("SWITCH SENSOR", color = UltrasonicViolet, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        item {
            TacticalCard(
                title = "850nm / 940nm INFRARED NIGHT ILLUMINATOR HUNTER",
                badgeText = "IR SPECTRUM",
                badgeColor = UltrasonicViolet
            ) {
                Text(
                    text = "Hidden night-vision surveillance cameras utilize 850nm or 940nm infrared LEDs. While completely invisible to human eyes in the dark, the phone's image sensor reveals them as luminous violet/white spheres of light.",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "PRO TIP: Smartphone FRONT cameras typically lack the thick glass IR cut filters found on main rear cameras, making them far more sensitive to night-vision spy illuminators!",
                    color = UltrasonicViolet,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun MagneticEmfTab(
    reading: EmfReading,
    history: List<Float>,
    isScanning: Boolean,
    isGeiger: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onTare: () -> Unit,
    onToggleGeiger: () -> Unit
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
                title = "MAGNETIC FLUX DENSITY (EMF)",
                badgeText = reading.anomalyLevel.label,
                badgeColor = Color(reading.anomalyLevel.colorHex)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${"%.1f".format(reading.magnitudeUf)} μT",
                        color = Color(reading.anomalyLevel.colorHex),
                        fontSize = 46.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "DELTA FROM TARE: +${"%.1f".format(reading.anomalyDeltaUf)} μT  |  TARE: ${"%.1f".format(reading.baselineUf)} μT",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 3-Axis Vector Breakdown
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Text("X: ${"%.1f".format(reading.x)} μT", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text("Y: ${"%.1f".format(reading.y)} μT", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text("Z: ${"%.1f".format(reading.z)} μT", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        // Oscilloscope History Chart
        item {
            TacticalCard(
                title = "EMF OSCILLOSCOPE WAVEFORM",
                badgeText = "ROLLING 50 SAMPLES",
                badgeColor = NeonCyan
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF070B10))
                        .border(1.dp, BorderCyan.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height

                        if (history.size < 2) return@Canvas

                        val stepX = canvasWidth / (history.size - 1).toFloat()
                        val maxVal = (history.maxOrNull() ?: 100f).coerceAtLeast(100f)
                        val minVal = (history.minOrNull() ?: 0f).coerceAtMost(0f)
                        val range = (maxVal - minVal).coerceAtLeast(1f)

                        for (i in 0 until history.size - 1) {
                            val y1 = canvasHeight - ((history[i] - minVal) / range) * canvasHeight
                            val y2 = canvasHeight - ((history[i + 1] - minVal) / range) * canvasHeight

                            drawLine(
                                color = Color(reading.anomalyLevel.colorHex),
                                start = Offset(i * stepX, y1),
                                end = Offset((i + 1) * stepX, y2),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                    }
                }
            }
        }

        // Controls
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { if (isScanning) onStop() else onStart() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isScanning) ThreatCrimson else RadarGreen
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isScanning) "HALT EMF" else "START EMF",
                        color = TacticalBlack,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onTare,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated),
                    border = BorderStroke(1.dp, NeonCyan),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("TARE / ZERO", color = NeonCyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onToggleGeiger,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, if (isGeiger) ElectricGold else Color.Gray),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    tint = if (isGeiger) ElectricGold else Color.Gray
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isGeiger) "GEIGER AUDIO TICK: ENABLED" else "GEIGER AUDIO TICK: MUTED",
                    color = if (isGeiger) ElectricGold else Color.Gray,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
        }
    }
}
