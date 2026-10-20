package com.omnisentinel.app.features.camguard

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.omnisentinel.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun CameraViewfinder(
    strobeRate: StrobeRate,
    isFrontCamera: Boolean,
    isNightEyeMode: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var camera by remember { mutableStateOf<Camera?>(null) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }

    // Strobe flashlight effect
    LaunchedEffect(strobeRate, camera) {
        val control = camera?.cameraControl ?: return@LaunchedEffect
        if (strobeRate == StrobeRate.OFF) {
            try {
                control.enableTorch(true)
            } catch (e: Exception) {}
            return@LaunchedEffect
        }

        val intervalMs = (1000L / (strobeRate.hz * 2L)).coerceAtLeast(40L)
        var torchOn = false
        while (isActive) {
            torchOn = !torchOn
            try {
                control.enableTorch(torchOn)
            } catch (e: Exception) {
                break
            }
            delay(intervalMs)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(340.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .border(1.dp, if (isNightEyeMode) UltrasonicViolet else ThreatCrimson, RoundedCornerShape(12.dp))
    ) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val selector = if (isFrontCamera) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }

                    try {
                        cameraProvider.unbindAll()
                        camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            selector,
                            preview
                        )
                    } catch (e: Exception) {
                        Log.e("CameraViewfinder", "Binding camera failed: ${e.message}")
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            update = {
                // If zoom changes, update camera
                camera?.cameraControl?.setZoomRatio(zoomRatio)
            },
            modifier = Modifier.fillMaxSize()
        )

        // Night Vision False-Color IR Tint / Glint Reticle Overlay
        if (isNightEyeMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x334A148C)) // High-contrast purple IR luminescence filter
            )
        }

        // Tactical Reticle HUD
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val reticleColor = if (isNightEyeMode) UltrasonicViolet else NeonCyan

            // Crosshair
            val lineLen = 24.dp.toPx()
            drawLine(reticleColor, Offset(center.x - lineLen, center.y), Offset(center.x + lineLen, center.y), 1.5.dp.toPx())
            drawLine(reticleColor, Offset(center.x, center.y - lineLen), Offset(center.x, center.y + lineLen), 1.5.dp.toPx())

            // Corner brackets
            val bracketLen = 30.dp.toPx()
            val pad = 20.dp.toPx()
            // Top-Left
            drawLine(reticleColor, Offset(pad, pad), Offset(pad + bracketLen, pad), 2.dp.toPx())
            drawLine(reticleColor, Offset(pad, pad), Offset(pad, pad + bracketLen), 2.dp.toPx())
            // Top-Right
            drawLine(reticleColor, Offset(size.width - pad, pad), Offset(size.width - pad - bracketLen, pad), 2.dp.toPx())
            drawLine(reticleColor, Offset(size.width - pad, pad), Offset(size.width - pad, pad + bracketLen), 2.dp.toPx())
            // Bottom-Left
            drawLine(reticleColor, Offset(pad, size.height - pad), Offset(pad + bracketLen, size.height - pad), 2.dp.toPx())
            drawLine(reticleColor, Offset(pad, size.height - pad), Offset(pad, size.height - pad - bracketLen), 2.dp.toPx())
            // Bottom-Right
            drawLine(reticleColor, Offset(size.width - pad, size.height - pad), Offset(size.width - pad - bracketLen, size.height - pad), 2.dp.toPx())
            drawLine(reticleColor, Offset(size.width - pad, size.height - pad), Offset(size.width - pad, size.height - pad - bracketLen), 2.dp.toPx())
        }

        // Live Mode Tag
        Surface(
            color = DarkSurface.copy(alpha = 0.85f),
            shape = RoundedCornerShape(4.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (isNightEyeMode) UltrasonicViolet else ThreatCrimson),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
        ) {
            Text(
                text = if (isNightEyeMode) "IR 850nm/940nm EYE" else "GLINT: ${strobeRate.label}",
                color = if (isNightEyeMode) UltrasonicViolet else ThreatCrimson,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }

        // Digital Zoom Selector Controls
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(DarkSurface.copy(alpha = 0.85f))
                .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(1f, 2f, 4f).forEach { zoom ->
                val isSelected = zoomRatio == zoom
                Text(
                    text = "${zoom.toInt()}X",
                    color = if (isSelected) NeonCyan else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clickable { zoomRatio = zoom }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
