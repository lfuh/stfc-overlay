package com.stfc.overlay.ui.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.stfc.overlay.model.StfcBookmark
import com.stfc.overlay.model.StfcPresets
import com.stfc.overlay.model.StfcSection
import com.stfc.overlay.ui.theme.LcarsCyan
import com.stfc.overlay.ui.theme.LcarsGold
import com.stfc.overlay.ui.theme.LcarsRed
import com.stfc.overlay.ui.theme.SpaceCard
import com.stfc.overlay.ui.theme.SpaceNavyDark
import com.stfc.overlay.ui.theme.SpaceSurface
import com.stfc.overlay.ui.theme.TextPrimary
import com.stfc.overlay.ui.theme.TextSecondary
import kotlin.math.roundToInt

private const val PREFS_NAME = "stfc_overlay_prefs"
private const val KEY_AUTO_SCALE = "auto_scale_with_window"
private const val KEY_MANUAL_FONT_ZOOM = "manual_font_zoom"
private const val KEY_OPACITY = "hud_opacity"

/**
 * Floating Starfleet Delta Bubble that sits above Star Trek Fleet Command.
 * Supports drag-to-reposition and single-tap to expand the STFC Database HUD.
 */
@Composable
fun FloatingDeltaBubble(
    onExpand: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(52.dp)
            .shadow(8.dp, CircleShape, ambientColor = LcarsCyan, spotColor = LcarsCyan)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF132743),
                        Color(0xFF060B16)
                    )
                )
            )
            .border(
                width = 1.8.dp,
                brush = Brush.sweepGradient(
                    listOf(LcarsCyan, LcarsGold, LcarsCyan)
                ),
                shape = CircleShape
            )
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
            .clickable { onExpand() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(28.dp)) {
            val w = size.width
            val h = size.height
            val deltaPath = Path().apply {
                moveTo(w * 0.5f, h * 0.08f)
                lineTo(w * 0.86f, h * 0.88f)
                lineTo(w * 0.5f, h * 0.66f)
                lineTo(w * 0.14f, h * 0.88f)
                close()
            }
            drawPath(
                path = deltaPath,
                brush = Brush.linearGradient(
                    colors = listOf(LcarsCyan, Color(0xFF2979FF))
                )
            )
            drawPath(
                path = deltaPath,
                color = LcarsGold,
                style = Stroke(width = 2.4f)
            )
        }
        Text(
            text = "DB",
            color = TextPrimary,
            fontSize = 8.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 3.dp)
        )
    }
}

private const val MOBILE_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

/**
 * Computes a comfortable, readable font zoom percentage (75% .. 115%) automatically
 * based on the current floating window width in dp.
 */
fun computeAutoTextZoomForWidthDp(windowWidthDp: Int): Int {
    // At 260dp wide -> ~78%, at 380dp wide -> ~92%, at 550dp+ wide -> ~105%
    val ratio = ((windowWidthDp - 240f) / 360f).coerceIn(0f, 1.2f)
    return (78f + ratio * 24f).roundToInt().coerceIn(75, 115)
}

/**
 * Expanded LCARS Tactical Database Window that floats above STFC.
 * Supports both Automatic Window-Based Font Scaling and Manual Text Size Adjustments.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FloatingDatabaseWindow(
    initialUrl: String,
    windowWidthDp: Int,
    windowHeightDp: Int,
    isWindowFocusable: Boolean,
    onRequestFocusable: (Boolean) -> Unit,
    onDragWindow: (Float, Float) -> Unit,
    onResizeWindow: (Float, Float) -> Unit,
    onMinimizeToBubble: () -> Unit,
    onCloseService: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    var currentUrl by remember { mutableStateOf(initialUrl) }
    var pageTitle by remember { mutableStateOf("stfc.space") }
    var activeSection by remember {
        mutableStateOf(
            StfcSection.entries.find { initialUrl.startsWith(it.url) } ?: StfcSection.HOME
        )
    }
    var isLoading by remember { mutableStateOf(true) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var panelAlpha by remember { mutableFloatStateOf(prefs.getFloat(KEY_OPACITY, 0.96f)) }

    // Auto-scale vs Manual font size state (persisted in SharedPreferences)
    var autoScaleWithWindow by remember {
        mutableStateOf(prefs.getBoolean(KEY_AUTO_SCALE, true))
    }
    var manualTextZoom by remember {
        mutableIntStateOf(prefs.getInt(KEY_MANUAL_FONT_ZOOM, 92))
    }

    val effectiveTextZoom = if (autoScaleWithWindow) {
        computeAutoTextZoomForWidthDp(windowWidthDp)
    } else {
        manualTextZoom
    }

    // Scale UI chrome text slightly with window width so buttons stay readable
    val uiScale = (windowWidthDp / 360f).coerceIn(0.88f, 1.20f)

    var desktopWideMode by remember { mutableStateOf(false) }
    // Show the Font Size & Opacity bar right away when user taps "Aa"
    var showFontAndToolsBar by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var searchInput by remember { mutableStateOf("") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val bookmarks = remember {
        mutableStateListOf<StfcBookmark>().apply {
            addAll(StfcPresets.defaultBookmarks)
        }
    }

    // Whenever windowWidthDp or manualTextZoom changes, immediately update WebView font size!
    LaunchedEffect(effectiveTextZoom, desktopWideMode) {
        webViewRef?.let { wv ->
            wv.settings.textZoom = effectiveTextZoom
            applyReadableWebScaling(wv, effectiveTextZoom, desktopWideMode)
        }
    }

    // Sync initialUrl changes if user clicks a deep link from MainActivity while service is alive
    DisposableEffect(initialUrl) {
        if (initialUrl != currentUrl) {
            currentUrl = initialUrl
            webViewRef?.loadUrl(initialUrl)
        }
        onDispose { }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .alpha(panelAlpha)
            .shadow(14.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = 1.2.dp,
                brush = Brush.linearGradient(listOf(LcarsCyan, LcarsGold, LcarsCyan)),
                shape = RoundedCornerShape(14.dp)
            ),
        color = SpaceNavyDark
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. LCARS Drag Handle & Top Control Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF0E1B31), Color(0xFF091221))
                        )
                    )
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDragWindow(dragAmount.x, dragAmount.y)
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LCARS Accent Pill
                Box(
                    modifier = Modifier
                        .width(8.dp)
                        .height(18.dp)
                        .clip(RoundedCornerShape(topStart = 5.dp, bottomStart = 5.dp))
                        .background(LcarsGold)
                )
                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = pageTitle,
                    color = LcarsCyan,
                    fontSize = (10.5f * uiScale).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Back button
                HudHeaderButton(label = "◀", color = TextPrimary, uiScale = uiScale) {
                    if (webViewRef?.canGoBack() == true) {
                        webViewRef?.goBack()
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))

                // Quick Text Zoom Out (A-)
                HudHeaderButton(label = "A-", color = LcarsCyan, uiScale = uiScale) {
                    autoScaleWithWindow = false
                    manualTextZoom = (effectiveTextZoom - 8).coerceIn(55, 160)
                    prefs.edit()
                        .putBoolean(KEY_AUTO_SCALE, false)
                        .putInt(KEY_MANUAL_FONT_ZOOM, manualTextZoom)
                        .apply()
                }
                Spacer(modifier = Modifier.width(3.dp))

                // Font Size Indicator / Toggle Font Settings Bar ("Aa 92%" or "AUTO 90%")
                HudHeaderButton(
                    label = if (autoScaleWithWindow) "Aa AUTO (${effectiveTextZoom}%)" else "Aa ${effectiveTextZoom}%",
                    color = if (showFontAndToolsBar) LcarsGold else TextPrimary,
                    uiScale = uiScale
                ) {
                    showFontAndToolsBar = !showFontAndToolsBar
                }
                Spacer(modifier = Modifier.width(3.dp))

                // Quick Text Zoom In (A+)
                HudHeaderButton(label = "A+", color = LcarsCyan, uiScale = uiScale) {
                    autoScaleWithWindow = false
                    manualTextZoom = (effectiveTextZoom + 8).coerceIn(55, 160)
                    prefs.edit()
                        .putBoolean(KEY_AUTO_SCALE, false)
                        .putInt(KEY_MANUAL_FONT_ZOOM, manualTextZoom)
                        .apply()
                }
                Spacer(modifier = Modifier.width(4.dp))

                // Keyboard focus toggle
                HudHeaderButton(
                    label = if (isWindowFocusable) "⌨ ON" else "⌨",
                    color = if (isWindowFocusable) LcarsGold else TextSecondary,
                    uiScale = uiScale
                ) {
                    onRequestFocusable(!isWindowFocusable)
                }
                Spacer(modifier = Modifier.width(4.dp))

                // Minimize to Bubble
                HudHeaderButton(label = "—", color = LcarsCyan, uiScale = uiScale) {
                    onRequestFocusable(false)
                    onMinimizeToBubble()
                }
                Spacer(modifier = Modifier.width(4.dp))

                // Close overlay service
                HudHeaderButton(label = "✕", color = LcarsRed, uiScale = uiScale) {
                    onCloseService()
                }
            }

            // 2. Category Quick-Switch Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SpaceSurface)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StfcSection.entries.forEach { section ->
                    val isSelected = activeSection == section
                    val chipColor = Color(section.badgeColorHex)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (isSelected) chipColor.copy(alpha = 0.24f)
                                else SpaceCard
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) chipColor else Color(0xFF233554),
                                shape = RoundedCornerShape(50)
                            )
                            .clickable {
                                activeSection = section
                                currentUrl = section.url
                                webViewRef?.loadUrl(section.url)
                            }
                            .padding(horizontal = 9.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = section.title,
                            color = if (isSelected) chipColor else TextPrimary,
                            fontSize = (10f * uiScale).sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            // 3. Font Size, Auto-Scale & Opacity Control Drawer (toggled via "Aa" button)
            AnimatedVisibility(visible = showFontAndToolsBar) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0A1324))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    // Row A: Auto-Scale Toggle + Quick Preset Chips (75%, 90%, 100%, 115%, 130%)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "Text Size:",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        // Auto-Fit to Window Size chip
                        HudHeaderButton(
                            label = if (autoScaleWithWindow) "✓ Auto-Fit Window" else "Auto-Fit Window",
                            color = if (autoScaleWithWindow) LcarsGold else TextSecondary,
                            uiScale = uiScale
                        ) {
                            autoScaleWithWindow = !autoScaleWithWindow
                            prefs.edit().putBoolean(KEY_AUTO_SCALE, autoScaleWithWindow).apply()
                        }

                        // Preset percentage buttons
                        listOf(75, 90, 100, 115, 130).forEach { preset ->
                            val isActive = !autoScaleWithWindow && manualTextZoom == preset
                            HudHeaderButton(
                                label = "${preset}%",
                                color = if (isActive) LcarsCyan else TextPrimary,
                                uiScale = uiScale
                            ) {
                                autoScaleWithWindow = false
                                manualTextZoom = preset
                                prefs.edit()
                                    .putBoolean(KEY_AUTO_SCALE, false)
                                    .putInt(KEY_MANUAL_FONT_ZOOM, preset)
                                    .apply()
                            }
                        }

                        // Desktop vs Mobile layout toggle
                        HudHeaderButton(
                            label = if (desktopWideMode) "🖥 Desktop" else "📱 Mobile",
                            color = if (desktopWideMode) LcarsGold else TextSecondary,
                            uiScale = uiScale
                        ) {
                            desktopWideMode = !desktopWideMode
                            webViewRef?.let { wv ->
                                configureViewportMode(wv, desktopWideMode)
                                applyReadableWebScaling(wv, effectiveTextZoom, desktopWideMode)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Row B: Continuous Font Size Slider (55% .. 160%)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Font Zoom",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            modifier = Modifier.width(64.dp)
                        )
                        Slider(
                            value = effectiveTextZoom.toFloat(),
                            onValueChange = {
                                autoScaleWithWindow = false
                                manualTextZoom = it.roundToInt().coerceIn(55, 160)
                                prefs.edit()
                                    .putBoolean(KEY_AUTO_SCALE, false)
                                    .putInt(KEY_MANUAL_FONT_ZOOM, manualTextZoom)
                                    .apply()
                            },
                            valueRange = 55f..160f,
                            colors = SliderDefaults.colors(
                                thumbColor = LcarsGold,
                                activeTrackColor = LcarsGold
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(22.dp)
                        )
                        Text(
                            text = "${effectiveTextZoom}%",
                            color = LcarsGold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(40.dp)
                        )
                    }

                    // Row C: Opacity Slider
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Opacity",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            modifier = Modifier.width(64.dp)
                        )
                        Slider(
                            value = panelAlpha,
                            onValueChange = {
                                panelAlpha = it.coerceIn(0.35f, 1.0f)
                                prefs.edit().putFloat(KEY_OPACITY, panelAlpha).apply()
                            },
                            valueRange = 0.35f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = LcarsCyan,
                                activeTrackColor = LcarsCyan
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(22.dp)
                        )
                        Text(
                            text = "${(panelAlpha * 100).toInt()}%",
                            color = LcarsCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Row D: Direct URL / Reload / Bookmarks
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(SpaceCard)
                                .border(1.dp, Color(0xFF294066), RoundedCornerShape(6.dp))
                                .clickable {
                                    if (!isWindowFocusable) onRequestFocusable(true)
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            if (searchInput.isEmpty()) {
                                Text(
                                    text = "Custom URL or tap search bar inside page...",
                                    color = TextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                            BasicTextField(
                                value = searchInput,
                                onValueChange = {
                                    if (!isWindowFocusable) onRequestFocusable(true)
                                    searchInput = it
                                },
                                singleLine = true,
                                textStyle = TextStyle(color = TextPrimary, fontSize = 10.sp),
                                cursorBrush = SolidColor(LcarsCyan),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        val q = searchInput.trim()
                                        if (q.isNotEmpty()) {
                                            val target = if (q.startsWith("http")) q
                                            else "https://stfc.space/"
                                            webViewRef?.loadUrl(target)
                                        }
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(modifier = Modifier.width(5.dp))
                        HudHeaderButton(label = "↻", color = TextPrimary, uiScale = uiScale) {
                            webViewRef?.reload()
                        }
                        Spacer(modifier = Modifier.width(5.dp))
                        HudHeaderButton(
                            label = "★ Pins",
                            color = if (showBookmarks) LcarsGold else TextPrimary,
                            uiScale = uiScale
                        ) {
                            showBookmarks = !showBookmarks
                        }
                        Spacer(modifier = Modifier.width(5.dp))
                        HudHeaderButton(label = "+Pin", color = LcarsGold, uiScale = uiScale) {
                            val urlToSave = webViewRef?.url ?: currentUrl
                            val titleToSave = pageTitle.ifBlank { urlToSave }
                            if (bookmarks.none { it.url == urlToSave }) {
                                bookmarks.add(0, StfcBookmark(titleToSave, urlToSave, activeSection.title))
                            }
                            showBookmarks = true
                        }
                    }
                }
            }

            // 4. Optional Pinned Bookmarks Drawer
            AnimatedVisibility(visible = showBookmarks) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF101C33))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PINNED:",
                        color = LcarsGold,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    bookmarks.forEach { bm ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(SpaceCard)
                                .border(1.dp, LcarsGold.copy(alpha = 0.5f), RoundedCornerShape(5.dp))
                                .clickable {
                                    currentUrl = bm.url
                                    webViewRef?.loadUrl(bm.url)
                                    showBookmarks = false
                                }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = bm.title,
                                color = TextPrimary,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Loading progress bar
            if (isLoading) {
                LinearProgressIndicator(
                    progress = { (loadProgress / 100f).coerceIn(0.05f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = LcarsCyan,
                    trackColor = SpaceSurface
                )
            }

            // 5. Embedded Hardware-Accelerated STFC Database WebView
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(android.graphics.Color.parseColor("#060B16"))

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                cacheMode = WebSettings.LOAD_DEFAULT
                                textZoom = effectiveTextZoom
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                builtInZoomControls = true
                                displayZoomControls = false
                                setSupportZoom(true)
                                mediaPlaybackRequiresUserGesture = false
                                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                userAgentString = MOBILE_USER_AGENT
                            }

                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                            // Auto-enable keyboard focus when user taps inside the WebView
                            setOnTouchListener { _, _ ->
                                if (!isWindowFocusable) {
                                    onRequestFocusable(true)
                                }
                                false
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(
                                    view: WebView?,
                                    url: String?,
                                    favicon: Bitmap?
                                ) {
                                    isLoading = true
                                    url?.let { currentUrl = it }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false
                                    pageTitle = view?.title?.takeIf { it.isNotBlank() } ?: "stfc.space"
                                    view?.let {
                                        it.settings.textZoom = effectiveTextZoom
                                        applyReadableWebScaling(it, effectiveTextZoom, desktopWideMode)
                                    }
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    return false
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    loadProgress = newProgress
                                    if (newProgress >= 95) {
                                        isLoading = false
                                    }
                                }

                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    if (!title.isNullOrBlank()) {
                                        pageTitle = title
                                    }
                                }
                            }

                            loadUrl(currentUrl)
                            webViewRef = this
                        }
                    }
                )

                // Bottom-Right Corner Resize Grip
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(26.dp)
                        .clip(RoundedCornerShape(topStart = 9.dp))
                        .background(Color(0xCC0D1627))
                        .border(
                            width = 1.dp,
                            color = LcarsCyan.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(topStart = 9.dp)
                        )
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                onResizeWindow(dragAmount.x, dragAmount.y)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⋱",
                        color = LcarsCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Toggles between standard mobile viewport and wide desktop/multi-column viewport.
 */
private fun configureViewportMode(webView: WebView, desktopWideMode: Boolean) {
    webView.settings.userAgentString = if (desktopWideMode) DESKTOP_USER_AGENT else MOBILE_USER_AGENT
    webView.reload()
}

/**
 * Applies clean, proportional font scaling (without double-shrinking via body zoom)
 * so text is crisp, legible, and scales smoothly from 55% to 160%.
 */
private fun applyReadableWebScaling(
    webView: WebView,
    textZoomPercent: Int,
    desktopWideMode: Boolean
) {
    val viewportContent = if (desktopWideMode) {
        "width=1024, initial-scale=0.80, minimum-scale=0.4, maximum-scale=3.0, user-scalable=yes"
    } else {
        "width=device-width, initial-scale=1.0, minimum-scale=0.5, maximum-scale=3.0, user-scalable=yes"
    }

    val js = """
        (function() {
            try {
                var meta = document.querySelector('meta[name="viewport"]');
                if (!meta) {
                    meta = document.createElement('meta');
                    meta.name = 'viewport';
                    document.head.appendChild(meta);
                }
                meta.content = '$viewportContent';

                var styleId = 'stfc-overlay-compact-style';
                var style = document.getElementById(styleId);
                if (!style) {
                    style = document.createElement('style');
                    style.id = styleId;
                    document.head.appendChild(style);
                }
                style.innerHTML = `
                    html {
                        font-size: ${textZoomPercent}% !important;
                        -webkit-text-size-adjust: ${textZoomPercent}% !important;
                    }
                    body {
                        zoom: 1.0 !important;
                        line-height: 1.35 !important;
                    }
                    table th, table td {
                        padding: 4px 6px !important;
                    }
                `;
            } catch (e) {}
        })();
    """.trimIndent()

    webView.evaluateJavascript(js, null)
}

@Composable
private fun HudHeaderButton(
    label: String,
    color: Color,
    uiScale: Float = 1f,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(SpaceCard)
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(5.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = color,
            fontSize = (9.5f * uiScale).sp,
            fontWeight = FontWeight.Bold
        )
    }
}
