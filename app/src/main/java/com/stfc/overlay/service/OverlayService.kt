package com.stfc.overlay.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.stfc.overlay.MainActivity
import com.stfc.overlay.R
import com.stfc.overlay.model.StfcSection
import com.stfc.overlay.ui.overlay.FloatingDatabaseWindow
import com.stfc.overlay.ui.overlay.FloatingDeltaBubble
import com.stfc.overlay.ui.theme.StfcOverlayTheme
import kotlin.math.roundToInt

/**
 * Foreground Service that hosts the floating Starfleet Delta Bubble and
 * the resizable STFC Database (`stfc.space`) window directly over Star Trek Fleet Command.
 * Automatically adapts to Portrait and Landscape screen rotations and passes live
 * window dimensions (in dp) to Compose so font size can scale with the window.
 */
class OverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private lateinit var layoutParams: WindowManager.LayoutParams

    // Reactive state observed by ComposeView
    private var isExpanded by mutableStateOf(false)
    private var isKeyboardFocusable by mutableStateOf(false)
    private var targetUrl by mutableStateOf(StfcSection.HOME.url)
    private var isLandscape by mutableStateOf(false)

    // Saved coordinates & dimensions for Bubble vs Expanded Window
    private var bubbleX = 24
    private var bubbleY = 220

    // Separate saved bounds for Portrait vs Landscape so rotating feels natural
    private var portraitWidthPx = 0
    private var portraitHeightPx = 0
    private var portraitX = 0
    private var portraitY = 0

    private var landscapeWidthPx = 0
    private var landscapeHeightPx = 0
    private var landscapeX = 0
    private var landscapeY = 0

    private var panelX = 40
    private var panelY = 80
    private var panelWidthPx by mutableIntStateOf(800)
    private var panelHeightPx by mutableIntStateOf(900)

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        recalculateBoundsForCurrentOrientation(resetToDefaultForOrientation = true)

        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        isRunning = true

        if (Settings.canDrawOverlays(this)) {
            attachFloatingComposeView()
        } else {
            stopSelf()
        }
    }

    /**
     * Triggered automatically by Android whenever the phone or game rotates between
     * Portrait and Landscape.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        recalculateBoundsForCurrentOrientation(resetToDefaultForOrientation = false)

        val (screenW, screenH) = getScreenDimensions()
        val bubbleSizePx = dpToPx(52f)
        bubbleX = bubbleX.coerceIn(0, (screenW - bubbleSizePx).coerceAtLeast(0))
        bubbleY = bubbleY.coerceIn(0, (screenH - bubbleSizePx).coerceAtLeast(0))

        if (composeView != null) {
            if (isExpanded) {
                layoutParams.width = panelWidthPx
                layoutParams.height = panelHeightPx
                layoutParams.x = panelX
                layoutParams.y = panelY
            } else {
                layoutParams.x = bubbleX
                layoutParams.y = bubbleY
            }
            safeUpdateViewLayout()
        }
    }

    private fun getScreenDimensions(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val dm = resources.displayMetrics
            dm.widthPixels to dm.heightPixels
        }
    }

    private fun recalculateBoundsForCurrentOrientation(resetToDefaultForOrientation: Boolean) {
        val (screenW, screenH) = getScreenDimensions()
        val currentlyLandscape = screenW > screenH
        isLandscape = currentlyLandscape

        if (currentlyLandscape) {
            if (landscapeWidthPx == 0 || resetToDefaultForOrientation) {
                landscapeWidthPx = (screenW * 0.68f).roundToInt().coerceIn(dpToPx(320f), screenW - dpToPx(24f))
                landscapeHeightPx = (screenH * 0.84f).roundToInt().coerceIn(dpToPx(220f), screenH - dpToPx(16f))
                landscapeX = ((screenW - landscapeWidthPx) / 2).coerceAtLeast(8)
                landscapeY = ((screenH - landscapeHeightPx) / 2).coerceAtLeast(8)
            }
            panelWidthPx = landscapeWidthPx.coerceIn(dpToPx(240f), screenW - 8)
            panelHeightPx = landscapeHeightPx.coerceIn(dpToPx(200f), screenH - 8)
            panelX = landscapeX.coerceIn(0, (screenW - panelWidthPx).coerceAtLeast(0))
            panelY = landscapeY.coerceIn(0, (screenH - panelHeightPx).coerceAtLeast(0))
        } else {
            if (portraitWidthPx == 0 || resetToDefaultForOrientation) {
                portraitWidthPx = (screenW * 0.90f).roundToInt().coerceIn(dpToPx(260f), screenW - dpToPx(16f))
                portraitHeightPx = (screenH * 0.68f).roundToInt().coerceIn(dpToPx(280f), screenH - dpToPx(32f))
                portraitX = ((screenW - portraitWidthPx) / 2).coerceAtLeast(8)
                portraitY = (screenH * 0.10f).roundToInt().coerceAtLeast(8)
            }
            panelWidthPx = portraitWidthPx.coerceIn(dpToPx(220f), screenW - 8)
            panelHeightPx = portraitHeightPx.coerceIn(dpToPx(240f), screenH - 8)
            panelX = portraitX.coerceIn(0, (screenW - panelWidthPx).coerceAtLeast(0))
            panelY = portraitY.coerceIn(0, (screenH - panelHeightPx).coerceAtLeast(0))
        }
    }

    private fun saveCurrentOrientationBounds() {
        if (isLandscape) {
            landscapeWidthPx = panelWidthPx
            landscapeHeightPx = panelHeightPx
            landscapeX = panelX
            landscapeY = panelY
        } else {
            portraitWidthPx = panelWidthPx
            portraitHeightPx = panelHeightPx
            portraitX = panelX
            portraitY = panelY
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_EXPAND -> {
                val url = intent.getStringExtra(EXTRA_URL)
                if (!url.isNullOrBlank()) {
                    targetUrl = url
                }
                setExpandedMode(true)
            }
            ACTION_MINIMIZE -> {
                setExpandedMode(false)
            }
            else -> {
                val url = intent?.getStringExtra(EXTRA_URL)
                if (!url.isNullOrBlank()) {
                    targetUrl = url
                }
                if (intent?.getBooleanExtra(EXTRA_START_EXPANDED, false) == true) {
                    setExpandedMode(true)
                }
            }
        }
        return START_STICKY
    }

    private fun attachFloatingComposeView() {
        if (composeView != null) return

        val bubbleSizePx = dpToPx(52f)
        layoutParams = WindowManager.LayoutParams(
            bubbleSizePx,
            bubbleSizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            screenOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            x = bubbleX
            y = bubbleY
        }

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)

            setContent {
                StfcOverlayTheme {
                    if (!isExpanded) {
                        FloatingDeltaBubble(
                            onExpand = { setExpandedMode(true) },
                            onDrag = { dx, dy ->
                                val (screenW, screenH) = getScreenDimensions()
                                bubbleX = (bubbleX + dx.roundToInt())
                                    .coerceIn(0, (screenW - bubbleSizePx).coerceAtLeast(0))
                                bubbleY = (bubbleY + dy.roundToInt())
                                    .coerceIn(0, (screenH - bubbleSizePx).coerceAtLeast(0))
                                this@OverlayService.layoutParams.x = bubbleX
                                this@OverlayService.layoutParams.y = bubbleY
                                safeUpdateViewLayout()
                            }
                        )
                    } else {
                        val density = resources.displayMetrics.density.coerceAtLeast(1f)
                        val widthDp = (panelWidthPx / density).roundToInt()
                        val heightDp = (panelHeightPx / density).roundToInt()

                        FloatingDatabaseWindow(
                            initialUrl = targetUrl,
                            windowWidthDp = widthDp,
                            windowHeightDp = heightDp,
                            isWindowFocusable = isKeyboardFocusable,
                            onRequestFocusable = { focusable ->
                                setWindowFocusable(focusable)
                            },
                            onDragWindow = { dx, dy ->
                                val (screenW, screenH) = getScreenDimensions()
                                panelX = (panelX + dx.roundToInt())
                                    .coerceIn(0, (screenW - dpToPx(80f)).coerceAtLeast(0))
                                panelY = (panelY + dy.roundToInt())
                                    .coerceIn(0, (screenH - dpToPx(60f)).coerceAtLeast(0))
                                this@OverlayService.layoutParams.x = panelX
                                this@OverlayService.layoutParams.y = panelY
                                saveCurrentOrientationBounds()
                                safeUpdateViewLayout()
                            },
                            onResizeWindow = { dw, dh ->
                                val (screenW, screenH) = getScreenDimensions()
                                val minW = dpToPx(220f)
                                val minH = dpToPx(200f)
                                panelWidthPx = (panelWidthPx + dw.roundToInt())
                                    .coerceIn(minW, screenW - 8)
                                panelHeightPx = (panelHeightPx + dh.roundToInt())
                                    .coerceIn(minH, screenH - 16)
                                this@OverlayService.layoutParams.width = panelWidthPx
                                this@OverlayService.layoutParams.height = panelHeightPx
                                saveCurrentOrientationBounds()
                                safeUpdateViewLayout()
                            },
                            onMinimizeToBubble = {
                                setExpandedMode(false)
                            },
                            onCloseService = {
                                stopSelf()
                            }
                        )
                    }
                }
            }
        }

        composeView = view
        windowManager.addView(view, layoutParams)
    }

    private fun setExpandedMode(expanded: Boolean) {
        if (composeView == null) return
        recalculateBoundsForCurrentOrientation(resetToDefaultForOrientation = false)
        isExpanded = expanded
        if (expanded) {
            layoutParams.width = panelWidthPx
            layoutParams.height = panelHeightPx
            layoutParams.x = panelX
            layoutParams.y = panelY
            setWindowFocusable(isKeyboardFocusable)
        } else {
            val bubbleSizePx = dpToPx(52f)
            isKeyboardFocusable = false
            layoutParams.width = bubbleSizePx
            layoutParams.height = bubbleSizePx
            layoutParams.x = bubbleX
            layoutParams.y = bubbleY
            layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            safeUpdateViewLayout()
        }
    }

    private fun setWindowFocusable(focusable: Boolean) {
        isKeyboardFocusable = focusable
        if (focusable) {
            layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            layoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        } else {
            layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }
        safeUpdateViewLayout()
    }

    private fun safeUpdateViewLayout() {
        val view = composeView ?: return
        if (view.isAttachedToWindow) {
            try {
                windowManager.updateViewLayout(view, layoutParams)
            } catch (_: IllegalArgumentException) {
                // Ignore if window is detaching
            }
        }
    }

    private fun dpToPx(dp: Float): Int {
        return (dp * resources.displayMetrics.density).roundToInt()
    }

    private fun buildForegroundNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "STFC Tactical Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the floating STFC Database bubble active over the game"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val expandIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, OverlayService::class.java).apply { action = ACTION_EXPAND },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, OverlayService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_delta_badge)
            .setContentTitle("STFC Tactical Database Active")
            .setContentText("Tap the floating Starfleet badge in-game to browse stfc.space")
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .addAction(0, "Expand DB", expandIntent)
            .addAction(0, "Stop Overlay", stopIntent)
            .build()
        }

    override fun onDestroy() {
        isRunning = false
        composeView?.let { view ->
            if (view.isAttachedToWindow) {
                windowManager.removeView(view)
            }
        }
        composeView = null
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "stfc_overlay_channel"
        private const val NOTIFICATION_ID = 1701

        const val ACTION_EXPAND = "com.stfc.overlay.ACTION_EXPAND"
        const val ACTION_MINIMIZE = "com.stfc.overlay.ACTION_MINIMIZE"
        const val ACTION_STOP = "com.stfc.overlay.ACTION_STOP"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_START_EXPANDED = "extra_start_expanded"

        const val STFC_PACKAGE_NAME = "com.scopely.startrek"

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
