package com.stfc.overlay

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.stfc.overlay.model.StfcSection
import com.stfc.overlay.service.OverlayService
import com.stfc.overlay.ui.theme.LcarsCoral
import com.stfc.overlay.ui.theme.LcarsCyan
import com.stfc.overlay.ui.theme.LcarsGold
import com.stfc.overlay.ui.theme.LcarsRed
import com.stfc.overlay.ui.theme.SpaceCard
import com.stfc.overlay.ui.theme.SpaceNavyDark
import com.stfc.overlay.ui.theme.SpaceSurface
import com.stfc.overlay.ui.theme.StfcOverlayTheme
import com.stfc.overlay.ui.theme.TextPrimary
import com.stfc.overlay.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {

    private var hasOverlayPermission by mutableStateOf(false)
    private var isServiceActive by mutableStateOf(false)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        refreshStates()
        requestNotificationPermissionIfNeeded()

        setContent {
            StfcOverlayTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = SpaceNavyDark
                ) { innerPadding ->
                    StfcCommandCenterScreen(
                        modifier = Modifier.padding(innerPadding),
                        hasOverlayPermission = hasOverlayPermission,
                        isServiceActive = isServiceActive,
                        onRequestOverlayPermission = { openOverlayPermissionSettings() },
                        onStartOverlayBubble = { startOverlay(url = null, startExpanded = false) },
                        onStopOverlay = { stopOverlay() },
                        onLaunchStfcWithOverlay = { launchStfcGameWithOverlay() },
                        onOpenSectionInOverlay = { section ->
                            startOverlay(url = section.url, startExpanded = true)
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStates()
    }

    private fun refreshStates() {
        hasOverlayPermission = Settings.canDrawOverlays(this)
        isServiceActive = OverlayService.isRunning
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun openOverlayPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun startOverlay(url: String?, startExpanded: Boolean) {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "Please grant 'Display over other apps' permission first",
                Toast.LENGTH_LONG
            ).show()
            openOverlayPermissionSettings()
            return
        }

        val serviceIntent = Intent(this, OverlayService::class.java).apply {
            if (startExpanded) {
                action = OverlayService.ACTION_EXPAND
            }
            if (!url.isNullOrBlank()) {
                putExtra(OverlayService.EXTRA_URL, url)
            }
            putExtra(OverlayService.EXTRA_START_EXPANDED, startExpanded)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        isServiceActive = true
    }

    private fun stopOverlay() {
        stopService(Intent(this, OverlayService::class.java))
        isServiceActive = false
    }

    private fun launchStfcGameWithOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            openOverlayPermissionSettings()
            return
        }
        startOverlay(url = null, startExpanded = false)

        val launchIntent = packageManager.getLaunchIntentForPackage(OverlayService.STFC_PACKAGE_NAME)
        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            Toast.makeText(
                this,
                "Overlay Bubble started! (Star Trek Fleet Command app not detected on this device; bubble is active over all apps)",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

@Composable
fun StfcCommandCenterScreen(
    modifier: Modifier = Modifier,
    hasOverlayPermission: Boolean,
    isServiceActive: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onStartOverlayBubble: () -> Unit,
    onStopOverlay: () -> Unit,
    onLaunchStfcWithOverlay: () -> Unit,
    onOpenSectionInOverlay: (StfcSection) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 156.dp),
        modifier = modifier
            .fillMaxSize()
            .background(SpaceNavyDark),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. LCARS Top Header Banner
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF10203B), Color(0xFF091224))
                        )
                    )
                    .border(
                        width = 1.5.dp,
                        brush = Brush.horizontalGradient(listOf(LcarsCyan, LcarsGold)),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (isServiceActive) Color(0xFF00E676) else LcarsGold)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "STARFLEET TACTICAL ADD-ON // STFC.SPACE",
                        color = LcarsCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "STFC In-Game Database Overlay",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Attaches a floating Starfleet Delta badge over Star Trek Fleet Command so you can look up Ships, Officers, Hostiles, Systems & Research without leaving the game.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }

        // 2. Permission Check Card (if not granted)
        if (!hasOverlayPermission) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Surface(
                    color = Color(0xFF28151E),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, LcarsCoral, RoundedCornerShape(14.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "ACTION REQUIRED: OVERLAY PERMISSION",
                            color = LcarsCoral,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Android requires 'Display over other apps' permission so the floating database bubble can render on top of Star Trek Fleet Command.",
                            color = TextPrimary,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onRequestOverlayPermission,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LcarsCoral,
                                contentColor = SpaceNavyDark
                            )
                        ) {
                            Text("Grant Overlay Permission", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 3. Primary Launch Controls Card
        item(span = { GridItemSpan(maxLineSpan) }) {
            Surface(
                color = SpaceSurface,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF203558), RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "OVERLAY HUD CONTROLS",
                        color = LcarsGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = onLaunchStfcWithOverlay,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LcarsCyan,
                            contentColor = SpaceNavyDark
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "🚀 Launch STFC + Floating Database Bubble",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onStartOverlayBubble,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "Start Bubble Only",
                                color = LcarsCyan,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        OutlinedButton(
                            onClick = onStopOverlay,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "Stop Overlay",
                                color = LcarsRed,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // 4. Section Header for Direct Database Modules
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(modifier = Modifier.padding(top = 6.dp)) {
                Text(
                    text = "QUICK-OPEN IN FLOATING WINDOW",
                    color = LcarsCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = "Tap any module below to immediately pop open its floating database view:",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        // 5. Grid of STFC Database Modules
        items(StfcSection.entries) { section ->
            val accent = Color(section.badgeColorHex)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SpaceCard)
                    .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .clickable { onOpenSectionInOverlay(section) }
                    .padding(14.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(accent)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = section.title,
                            color = accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = section.description,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        minLines = 2
                    )
                }
            }
        }

        // 6. How-to / Tips Footer
        item(span = { GridItemSpan(maxLineSpan) }) {
            Surface(
                color = SpaceSurface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF1F314F), RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "IN-GAME HUD TIPS",
                        color = LcarsGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Drag the floating Starfleet badge anywhere along the screen edge.\n" +
                            "• Drag the top LCARS bar to move the expanded window, or drag the bottom-right corner grip (⋱) to resize.\n" +
                            "• Tap '◐' in the overlay header to adjust transparency so you can watch your ships in-game while reading stats.\n" +
                            "• Tap '⌨ ON/OFF' when you need the Android keyboard inside the overlay; turn it OFF to pass back-button presses directly to STFC.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }
}
