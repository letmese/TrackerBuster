package com.omnisentinel.app.features.acoustic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnisentinel.app.ui.theme.*

@Composable
fun WaterfallSpectrogramView(
    waterfallData: List<FloatArray>,
    modifier: Modifier = Modifier
) {
    var inspectedFreqHz by remember { mutableStateOf<Float?>(null) }
    var inspectedDb by remember { mutableStateOf<Float?>(null) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF06090E))
            .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val width = size.width.toFloat()
                    val ratio = (offset.x / width).coerceIn(0f, 1f)
                    // 0 to 22,050 Hz
                    val freq = ratio * 22050f
                    inspectedFreqHz = freq
                    inspectedDb = -60f + (ratio * 40f) // approximate inspector
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            if (waterfallData.isEmpty()) {
                return@Canvas
            }

            val numSlices = waterfallData.size
            val sliceHeight = canvasHeight / numSlices.toFloat()

            waterfallData.forEachIndexed { sliceIndex, sliceMags ->
                val y = sliceIndex * sliceHeight
                val numBins = sliceMags.size
                val binWidth = canvasWidth / numBins.toFloat()

                for (binIndex in sliceMags.indices) {
                    val mag = sliceMags[binIndex]
                    if (mag > 0.03f) {
                        val color = getThermalColor(mag)
                        drawRect(
                            color = color,
                            topLeft = Offset(binIndex * binWidth, y),
                            size = Size(binWidth + 0.5f, sliceHeight + 0.5f)
                        )
                    }
                }
            }

            // Grid lines and labels for key frequencies
            // 1kHz, 5kHz, 10kHz, 18kHz (Ultrasonic)
            val frequencies = listOf(1000f, 5000f, 10000f, 18000f)
            frequencies.forEach { freq ->
                val x = (freq / 22050f) * canvasWidth
                drawLine(
                    color = if (freq == 18000f) UltrasonicViolet.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.2f),
                    start = Offset(x, 0f),
                    end = Offset(x, canvasHeight),
                    strokeWidth = if (freq == 18000f) 2.dp.toPx() else 1.dp.toPx()
                )
            }
        }

        // Overlay Labels for Waterfall Axis
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("20 Hz", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Text("5 kHz", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Text("10 kHz", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Text("18 kHz [ULTRASONIC]", color = UltrasonicViolet, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Text("22 kHz", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }

        if (inspectedFreqHz != null) {
            Surface(
                color = DarkSurface.copy(alpha = 0.9f),
                shape = RoundedCornerShape(4.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            ) {
                Text(
                    text = "TARGET: ${inspectedFreqHz?.toInt()} Hz",
                    color = NeonCyan,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

// High-Contrast FLIR / Thermal Color Ramp
private fun getThermalColor(magnitude: Float): Color {
    val m = magnitude.coerceIn(0f, 1f)
    return when {
        m < 0.2f -> {
            val t = m / 0.2f
            Color(
                red = (0x0A * (1 - t) + 0x1A * t) / 255f,
                green = (0x0E * (1 - t) + 0x2A * t) / 255f,
                blue = (0x1C * (1 - t) + 0x6E * t) / 255f,
                alpha = 0.8f
            )
        }
        m < 0.4f -> {
            val t = (m - 0.2f) / 0.2f
            Color(
                red = (0x1A * (1 - t) + 0x00 * t) / 255f,
                green = (0x2A * (1 - t) + 0xE5 * t) / 255f,
                blue = (0x6E * (1 - t) + 0xFF * t) / 255f,
                alpha = 0.95f
            )
        }
        m < 0.65f -> {
            val t = (m - 0.4f) / 0.25f
            Color(
                red = (0x00 * (1 - t) + 0x00 * t) / 255f,
                green = (0xE5 * (1 - t) + 0xFF * t) / 255f,
                blue = (0xFF * (1 - t) + 0x7F * t) / 255f,
                alpha = 1f
            )
        }
        m < 0.85f -> {
            val t = (m - 0.65f) / 0.2f
            Color(
                red = (0x00 * (1 - t) + 0xFF * t) / 255f,
                green = (0xFF * (1 - t) + 0xB7 * t) / 255f,
                blue = (0x7F * (1 - t) + 0x03 * t) / 255f,
                alpha = 1f
            )
        }
        else -> {
            val t = (m - 0.85f) / 0.15f
            Color(
                red = 1f,
                green = (0xB7 * (1 - t) + 0xFF * t) / 255f,
                blue = (0x03 * (1 - t) + 0xFF * t) / 255f,
                alpha = 1f
            )
        }
    }
}
