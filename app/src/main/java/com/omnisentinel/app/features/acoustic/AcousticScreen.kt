package com.omnisentinel.app.features.acoustic

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnisentinel.app.ui.components.*
import com.omnisentinel.app.ui.theme.*

enum class AcousticSubTab(val label: String) {
    WATERFALL("Waterfall & Spectrum"),
    ULTRASONIC("Ultrasonic Spy"),
    METER("Sound Meter (dB)"),
    GENERATOR("Tone & Cleaner")
}

@Composable
fun AcousticScreen(
    acousticEngine: AcousticEngine,
    toneGenerator: ToneGeneratorEngine
) {
    val state by acousticEngine.acousticState.collectAsState()
    val waterfallData by acousticEngine.waterfallBuffer.collectAsState()
    val ultrasonicEvents by acousticEngine.ultrasonicHistory.collectAsState()
    val isTonePlaying by toneGenerator.isPlaying.collectAsState()
    val currentToneFreq by toneGenerator.currentFrequency.collectAsState()
    val isWaterEjectMode by toneGenerator.isWaterEjectMode.collectAsState()

    var selectedTab by remember { mutableStateOf(AcousticSubTab.WATERFALL) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TacticalBlack)
    ) {
        TacticalHeader(
            title = "AcousticLab / Sonic Spy",
            subtitle = "DSP Spectrogram & Ultrasonic Hunter",
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (state.isSimulation) "SIM" else "MIC",
                        color = if (state.isSimulation) AlertAmber else NeonCyan,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Switch(
                        checked = state.isSimulation,
                        onCheckedChange = { acousticEngine.toggleSimulation(it) },
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
            AcousticSubTab.values().forEach { tab ->
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
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Recording Controller Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TacticalPulseIndicator(active = state.isRecording, color = RadarGreen)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (state.isRecording) "DSP STREAM ACTIVE (44.1 kHz)" else "DSP ENGINE IDLE",
                    color = if (state.isRecording) RadarGreen else TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = {
                    if (state.isRecording) acousticEngine.stop() else acousticEngine.start()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isRecording) ThreatCrimson.copy(alpha = 0.8f) else NeonCyan
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = if (state.isRecording) "STOP DSP" else "START DSP",
                    color = if (state.isRecording) Color.White else TacticalBlack,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
        }

        // Global Ultrasonic Threat Banner
        AnimatedVisibility(visible = state.isUltrasonicDetected) {
            Surface(
                color = UltrasonicViolet.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, UltrasonicViolet),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = UltrasonicViolet,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "INAUDIBLE ULTRASONIC BEACON ACTIVE!",
                            color = UltrasonicViolet,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Emitting carrier at ${state.ultrasonicFreqHz.toInt()} Hz (TV ad / cross-device tracker)",
                            color = TextPrimary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        when (selectedTab) {
            AcousticSubTab.WATERFALL -> WaterfallTabContent(state, waterfallData)
            AcousticSubTab.ULTRASONIC -> UltrasonicTabContent(state, ultrasonicEvents)
            AcousticSubTab.METER -> DecibelMeterTabContent(state)
            AcousticSubTab.GENERATOR -> GeneratorTabContent(toneGenerator, isTonePlaying, currentToneFreq, isWaterEjectMode)
        }
    }
}

@Composable
private fun WaterfallTabContent(state: AcousticState, waterfallData: List<FloatArray>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            TacticalCard(
                title = "60 FPS SCI-FI WATERFALL SPECTROGRAM",
                badgeText = "20 Hz - 22 kHz",
                badgeColor = NeonCyan
            ) {
                WaterfallSpectrogramView(waterfallData = waterfallData)
            }
        }

        item {
            TacticalCard(
                title = "INSTANTANEOUS SPECTRUM BINS",
                badgeText = "FFT 512 RADIX-2",
                badgeColor = RadarGreen
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    val step = (state.fftMagnitudes.size / 64).coerceAtLeast(1)
                    for (i in 0 until state.fftMagnitudes.size step step) {
                        val mag = state.fftMagnitudes[i].coerceIn(0.02f, 1f)
                        val freq = i * (44100f / 512f)
                        val barColor = when {
                            freq >= 18000f -> UltrasonicViolet
                            mag > 0.7f -> ThreatCrimson
                            mag > 0.4f -> AlertAmber
                            else -> NeonCyan
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(mag)
                                .background(barColor, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Sub-Bass", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    Text("Mids", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    Text("Highs", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    Text("Ultrasonic", color = UltrasonicViolet, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun UltrasonicTabContent(state: AcousticState, history: List<UltrasonicTrackerEvent>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            TacticalCard(
                title = "CROSS-DEVICE AD TRACKER MONITOR",
                badgeText = if (state.isUltrasonicDetected) "BEACON DETECTED" else "SURVEILLANCE CLEAR",
                badgeColor = if (state.isUltrasonicDetected) ThreatCrimson else RadarGreen
            ) {
                Text(
                    text = "Smart TVs, web ads, and store audio systems secretly broadcast high-frequency inaudible tones (18,000 Hz – 22,000 Hz). Apps with microphone access capture these to link your phone to the TV you are watching.",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        item {
            TacticalCard(
                title = "ULTRASONIC SPECTRUM STATUS",
                badgeText = "18 kHz - 22 kHz",
                badgeColor = UltrasonicViolet
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (state.isUltrasonicDetected) "${state.ultrasonicFreqHz.toInt()} Hz" else "NO CARRIER",
                            color = if (state.isUltrasonicDetected) UltrasonicViolet else TextMuted,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (state.isUltrasonicDetected) "Active Beacon Modulated Tone" else "Ambient Noise Floor Quiet",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    Surface(
                        color = (if (state.isUltrasonicDetected) UltrasonicViolet else DarkSurfaceElevated).copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, if (state.isUltrasonicDetected) UltrasonicViolet else Color.Gray),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (state.isUltrasonicDetected) "BEACON DETECTED" else "MONITORING",
                            color = if (state.isUltrasonicDetected) UltrasonicViolet else RadarGreen,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Note: This app detects but does not block ultrasonic beacons. Blocking would require audio jamming, which is not implemented.",
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }
        }

        item {
            Text(
                text = "DETECTION LOG",
                color = TextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        if (history.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No ultrasonic ad beacons detected in recent audio buffers",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        } else {
            items(history) { event ->
                TacticalCard(
                    borderColor = UltrasonicViolet,
                    title = event.matchedProtocol,
                    badgeText = "${event.frequencyHz.toInt()} Hz",
                    badgeColor = UltrasonicViolet
                ) {
                    Text(text = event.description, color = TextPrimary, fontSize = 11.sp)
                    Text(
                        text = "Detected at ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(event.timestamp))} | Level: ${"%.1f".format(event.amplitudeDb)} dB",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DecibelMeterTabContent(state: AcousticState) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            TacticalCard(
                title = "RELATIVE LOUDNESS METER (dBFS)",
                badgeText = state.oshaStatus.label,
                badgeColor = Color(state.oshaStatus.colorHex)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${"%.1f".format(state.currentDb)} dB",
                        color = Color(state.oshaStatus.colorHex),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "MAX: ${"%.1f".format(state.peakDb)} dB  |  AVG: ${"%.1f".format(state.averageDb)} dB",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Progress Bar for dB
                    val meterProgress = ((state.currentDb - 30f) / (110f - 30f)).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { meterProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        color = Color(state.oshaStatus.colorHex),
                        trackColor = DarkSurfaceElevated,
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("30 dB (Whisper)", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        Text("70 dB", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        Text("85 dB (Danger)", color = AlertAmber, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        Text("110+ dB", color = ThreatCrimson, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Note: Measurements are relative to phone microphone full-scale (dBFS), not calibrated dB SPL. For certified OSHA compliance, use a Type 2 sound level meter.",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }
            }
        }

        item {
            TacticalCard(
                title = "OSHA & NIOSH HEARING PROTECTION GUIDELINES",
                badgeText = "HEALTH SAFETY",
                badgeColor = AlertAmber
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("• Safe Zone (<70 dB): Normal conversation, no risk of hearing loss.", color = TextPrimary, fontSize = 11.sp)
                    Text("• Caution Zone (70-84 dB): City traffic, lawnmower at distance.", color = TextPrimary, fontSize = 11.sp)
                    Text("• Hazard Zone (85-99 dB): OSHA max 8 hours daily. Permanent hair cell destruction begins without earplugs.", color = AlertAmber, fontSize = 11.sp)
                    Text("• Critical Danger (100+ dB): Rock concerts, sirens, jackhammers. Hearing damage in under 15 minutes!", color = ThreatCrimson, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun GeneratorTabContent(
    toneGenerator: ToneGeneratorEngine,
    isPlaying: Boolean,
    frequency: Float,
    isWaterEject: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Water Ejection Speaker Cleaner
        TacticalCard(
            title = "ACOUSTIC SPEAKER WATER EJECTOR",
            badgeText = if (isPlaying && isWaterEject) "PUMPING WATER" else "READY",
            badgeColor = if (isPlaying && isWaterEject) NeonCyan else RadarGreen
        ) {
            Text(
                text = "Generates a 165 Hz pulsing sine wave. This precisely matches the Helmholtz acoustic resonance of phone speaker enclosures to vibrate the diaphragm at max excursion and blast water droplets out of the mesh.",
                color = TextSecondary,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = {
                    if (isPlaying && isWaterEject) {
                        toneGenerator.stopTone()
                    } else {
                        toneGenerator.startTone(165f, isWaterEject = true)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPlaying && isWaterEject) ThreatCrimson else NeonCyan
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    if (isPlaying && isWaterEject) Icons.Default.Stop else Icons.Default.WaterDrop,
                    contentDescription = null,
                    tint = if (isPlaying && isWaterEject) Color.White else TacticalBlack
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isPlaying && isWaterEject) "HALT WATER EJECTION" else "START 165Hz WATER EJECTION",
                    color = if (isPlaying && isWaterEject) Color.White else TacticalBlack,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "⚠ Do not run for more than 30 seconds at a time. Extended playback at maximum volume can overheat or damage the speaker.",
                color = AlertAmber,
                fontSize = 10.sp
            )
        }

        // Variable Tone Generator & Hearing Threshold Tester
        TacticalCard(
            title = "TONE GENERATOR & HEARING THRESHOLD",
            badgeText = "${frequency.toInt()} Hz",
            badgeColor = ElectricGold
        ) {
            Text(
                text = "Auditory frequency test & equipment calibrator. Test the highest frequency your ears can detect (teenagers can hear ~17-18kHz, adults usually top out at 14-16kHz).",
                color = TextSecondary,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "FREQUENCY: ${frequency.toInt()} Hz",
                color = NeonCyan,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            Slider(
                value = frequency,
                onValueChange = {
                    toneGenerator.setFrequency(it)
                    if (isPlaying && !isWaterEject) {
                        toneGenerator.startTone(it, isWaterEject = false)
                    }
                },
                valueRange = 20f..22000f,
                colors = SliderDefaults.colors(
                    thumbColor = NeonCyan,
                    activeTrackColor = NeonCyan,
                    inactiveTrackColor = DarkSurfaceElevated
                )
            )

            // Preset Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    Pair("1 kHz", 1000f),
                    Pair("14 kHz", 14000f),
                    Pair("17.5 kHz", 17500f),
                    Pair("19.5 kHz", 19500f)
                ).forEach { (label, freq) ->
                    OutlinedButton(
                        onClick = {
                            toneGenerator.setFrequency(freq)
                            if (isPlaying && !isWaterEject) {
                                toneGenerator.startTone(freq, isWaterEject = false)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f))
                    ) {
                        Text(text = label, color = NeonCyan, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    if (isPlaying && !isWaterEject) {
                        toneGenerator.stopTone()
                    } else {
                        toneGenerator.startTone(frequency, isWaterEject = false)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPlaying && !isWaterEject) ThreatCrimson else RadarGreen
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    if (isPlaying && !isWaterEject) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    tint = TacticalBlack
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isPlaying && !isWaterEject) "HALT SINE WAVE" else "EMIT SINE WAVE",
                    color = TacticalBlack,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
        }
    }
}
