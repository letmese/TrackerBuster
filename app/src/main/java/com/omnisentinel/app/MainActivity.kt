package com.omnisentinel.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.omnisentinel.app.features.acoustic.AcousticEngine
import com.omnisentinel.app.features.acoustic.AcousticScreen
import com.omnisentinel.app.features.acoustic.ToneGeneratorEngine
import com.omnisentinel.app.features.battery.BatteryEngine
import com.omnisentinel.app.features.battery.BatteryScreen
import com.omnisentinel.app.features.camguard.CamGuardScreen
import com.omnisentinel.app.features.camguard.MagneticScannerEngine
import com.omnisentinel.app.features.rf.DirectionalCompassEngine
import com.omnisentinel.app.features.rf.RfScannerEngine
import com.omnisentinel.app.features.rf.RfScreen
import com.omnisentinel.app.ui.theme.*

enum class OmniSentinelNav(val label: String, val icon: ImageVector) {
    RF_SENTINEL("RF Sentinel", Icons.Default.Radar),
    SONIC_SPY("Sonic Spy", Icons.Default.GraphicEq),
    CAM_GUARD("CamGuard", Icons.Default.CenterFocusStrong),
    POWER_LAB("PowerLab", Icons.Default.Bolt)
}

class MainActivity : ComponentActivity() {

    private lateinit var rfScannerEngine: RfScannerEngine
    private lateinit var compassEngine: DirectionalCompassEngine
    private lateinit var acousticEngine: AcousticEngine
    private lateinit var toneGenerator: ToneGeneratorEngine
    private lateinit var magneticEngine: MagneticScannerEngine
    private lateinit var batteryEngine: BatteryEngine

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        rfScannerEngine.checkHardwareState()
    }

    override fun onResume() {
        super.onResume()
        rfScannerEngine.checkHardwareState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize hardware engines
        rfScannerEngine = RfScannerEngine(applicationContext)
        compassEngine = DirectionalCompassEngine(applicationContext)
        acousticEngine = AcousticEngine(applicationContext)
        toneGenerator = ToneGeneratorEngine()
        magneticEngine = MagneticScannerEngine(applicationContext)
        batteryEngine = BatteryEngine(applicationContext)

        WindowCompat.setDecorFitsSystemWindows(window, true)
        batteryEngine.start()
        checkAndRequestPermissions()

        setContent {
            OmniSentinelTheme {
                MainAppScreen(
                    rfScannerEngine = rfScannerEngine,
                    compassEngine = compassEngine,
                    acousticEngine = acousticEngine,
                    toneGenerator = toneGenerator,
                    magneticEngine = magneticEngine,
                    batteryEngine = batteryEngine
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        rfScannerEngine.stopScan()
        compassEngine.stopTracking()
        acousticEngine.stop()
        toneGenerator.stopTone()
        magneticEngine.stop()
        batteryEngine.stop()
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
}

@Composable
fun MainAppScreen(
    rfScannerEngine: RfScannerEngine,
    compassEngine: DirectionalCompassEngine,
    acousticEngine: AcousticEngine,
    toneGenerator: ToneGeneratorEngine,
    magneticEngine: MagneticScannerEngine,
    batteryEngine: BatteryEngine
) {
    var currentNav by remember { mutableStateOf(OmniSentinelNav.RF_SENTINEL) }

    BackHandler(enabled = currentNav != OmniSentinelNav.RF_SENTINEL) {
        currentNav = OmniSentinelNav.RF_SENTINEL
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurfaceElevated,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, BorderCyan.copy(alpha = 0.2f)))
            ) {
                OmniSentinelNav.values().forEach { nav ->
                    val isSelected = currentNav == nav
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentNav = nav },
                        icon = {
                            Icon(
                                nav.icon,
                                contentDescription = nav.label,
                                tint = if (isSelected) NeonCyan else TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                text = nav.label,
                                color = if (isSelected) NeonCyan else TextSecondary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = NeonCyan.copy(alpha = 0.18f),
                            selectedIconColor = NeonCyan,
                            unselectedIconColor = TextSecondary,
                            selectedTextColor = NeonCyan,
                            unselectedTextColor = TextSecondary
                        )
                    )
                }
            }
        },
        containerColor = TacticalBlack
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (currentNav) {
                OmniSentinelNav.RF_SENTINEL -> RfScreen(
                    scannerEngine = rfScannerEngine,
                    compassEngine = compassEngine
                )
                OmniSentinelNav.SONIC_SPY -> AcousticScreen(
                    acousticEngine = acousticEngine,
                    toneGenerator = toneGenerator
                )
                OmniSentinelNav.CAM_GUARD -> CamGuardScreen(
                    magneticEngine = magneticEngine
                )
                OmniSentinelNav.POWER_LAB -> BatteryScreen(
                    batteryEngine = batteryEngine
                )
            }
        }
    }
}
