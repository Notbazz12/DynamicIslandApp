package com.example.dynamicisland.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.dynamicisland.MainActivity
import com.example.dynamicisland.R
import com.example.dynamicisland.media.IslandMediaSessionManager
import com.example.dynamicisland.state.IslandEventBus
import com.example.dynamicisland.state.IslandState
import com.example.dynamicisland.ui.IslandOverlayView
import kotlinx.coroutines.*

class DynamicIslandService : Service() {
    companion object {
        const val ACTION_START             = "action.START"
        const val ACTION_STOP              = "action.STOP"
        const val ACTION_DEMO_CALL         = "action.DEMO_CALL"
        const val ACTION_DEMO_MUSIC        = "action.DEMO_MUSIC"
        const val ACTION_DEMO_NOTIFICATION = "action.DEMO_NOTIFICATION"
        const val ACTION_DEMO_TIMER        = "action.DEMO_TIMER"
        const val ACTION_CLEAR             = "action.CLEAR"
        const val ACTION_TOGGLE_GAMING     = "action.TOGGLE_GAMING"
        const val ACTION_SOCIAL_MODE       = "action.SOCIAL_MODE"
        const val PREFS_NAME               = "island_prefs"
        const val PREF_ALWAYS_ON           = "always_on"
        const val PREF_GAMING_MODE         = "gaming_mode"
        private const val NOTIF_CHANNEL_ID = "dynamic_island_channel"
        private const val NOTIF_ID         = 1337
        private const val DEMO_TIMER_TOTAL = 60L
        // Apps de redes sociales que activan mini mode
        private val SOCIAL_PACKAGES = setOf(
            "com.instagram.android",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.twitter.android",
            "com.x.android",
            "com.facebook.katana",
            "com.facebook.lite",
            "com.snapchat.android",
            "com.reddit.frontpage",
            "com.pinterest",
            "com.linkedin.android",
            "com.google.android.youtube",
        )
    }

    private lateinit var windowManager: WindowManager
    private var islandView: IslandOverlayView? = null   // nullable para resetear
    private lateinit var mediaSessionManager: IslandMediaSessionManager
    private lateinit var prefs: SharedPreferences
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var stateCollectorJob: Job? = null
    private var socialDetectorJob: Job? = null
    private var timerJob: Job? = null
    private var islandStarted = false
    private var gamingModeActive = false

    private val systemStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    val level = getBatteryLevel(context)
                    IslandEventBus.tryEmit(IslandState.BatteryCharging(level, true))
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    val level = getBatteryLevel(context)
                    IslandEventBus.tryEmit(IslandState.BatteryCharging(level, false))
                }
                AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                    val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    IslandEventBus.tryEmit(IslandState.RingerMode(am.ringerMode))
                }
            }
        }
    }

    private fun getBatteryLevel(context: Context): Int {
        val batIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else 0
    }


    override fun onCreate() {
        super.onCreate()
        windowManager       = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        mediaSessionManager = IslandMediaSessionManager(this)
        prefs               = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        createNotificationChannel()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(systemStatusReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(systemStatusReceiver, filter)
        }
    }

    // BUG-001 FIX: intent=null → tratar como ACTION_START (reinicio del sistema)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, null    -> startIsland()
            ACTION_STOP           -> stopIsland()
            ACTION_DEMO_CALL      -> handleDemoCall()
            ACTION_DEMO_MUSIC     -> handleDemoMusic()
            ACTION_DEMO_NOTIFICATION -> handleDemoNotification()
            ACTION_DEMO_TIMER     -> handleDemoTimer()
            ACTION_CLEAR          -> clearIsland()
            ACTION_TOGGLE_GAMING  -> toggleGamingMode()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // BUG-003 FIX: onDestroy solo hace cleanup mínimo
    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(systemStatusReceiver) }
        stopEventCollection()
        socialDetectorJob?.cancel()
        if (::mediaSessionManager.isInitialized) mediaSessionManager.stop()
        removeOverlayView()
        serviceScope.cancel()
    }

    private fun startIsland() {
        if (islandStarted) return
        islandStarted = true
        // Guardar estado para BootReceiver (BUG-009 FIX)
        prefs.edit().putBoolean("service_running", true).apply()
        val notification = buildForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notification)
        }
        addOverlayView()
        startEventCollection()
        startSocialMediaDetection()   // detector de redes sociales
        mediaSessionManager.start()
        // Aplicar gaming mode si estaba activo
        gamingModeActive = prefs.getBoolean(PREF_GAMING_MODE, false)
        islandView?.gamingMode = gamingModeActive
    }

    private fun stopIsland() {
        islandStarted = false
        prefs.edit().putBoolean("service_running", false).apply()
        stopEventCollection()
        if (::mediaSessionManager.isInitialized) mediaSessionManager.stop()
        removeOverlayView()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun clearIsland() {
        timerJob?.cancel()
        IslandEventBus.tryEmit(IslandState.Dismissing)
    }

    private fun toggleGamingMode() {
        gamingModeActive = !gamingModeActive
        prefs.edit().putBoolean(PREF_GAMING_MODE, gamingModeActive).apply()
        islandView?.gamingMode = gamingModeActive
    }

    private fun addOverlayView() {
        if (islandView != null) return
        val themedContext = android.view.ContextThemeWrapper(this, R.style.Theme_DynamicIsland)
        islandView = IslandOverlayView(themedContext).also { view ->
            view.wm = windowManager
            view.onDismiss = { clearIsland() }
            view.onPlayPause = { dispatchPlayPause() }
            view.onSkipPrev  = { dispatchPrev() }
            view.onSkipNext  = { dispatchNext() }
            view.onAnswerCall = { answerIncomingCall() }
            view.onDeclineCall = { declineIncomingCall() }
            runCatching { windowManager.addView(view, buildLayoutParams()) }
        }
    }

    private fun answerIncomingCall() {
        val tm = getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ANSWER_PHONE_CALLS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            runCatching { tm.acceptRingingCall() }
        }
    }

    private fun declineIncomingCall() {
        val tm = getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ANSWER_PHONE_CALLS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            runCatching { tm.endCall() }
        }
    }

    // BUG-002 + BUG-004 FIX
    private fun removeOverlayView() {
        islandView?.let { view ->
            view.cleanup()
            runCatching { windowManager.removeView(view) }
        }
        islandView = null
    }

    /** Envía KEYCODE_MEDIA_PLAY_PAUSE al reproductor activo (Spotify, YT Music, etc.) */
    private fun dispatchPlayPause() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP,   KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
    }

    private fun dispatchPrev() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP,   KeyEvent.KEYCODE_MEDIA_PREVIOUS))
    }

    private fun dispatchNext() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP,   KeyEvent.KEYCODE_MEDIA_NEXT))
    }

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = getStatusBarHeight() + 4
        }
    }

    // BUG-013 FIX: WindowInsets en Android 11+
    private fun getStatusBarHeight(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                windowManager.currentWindowMetrics.windowInsets
                    .getInsets(android.view.WindowInsets.Type.statusBars()).top
            }.getOrElse { 80 }
        } else {
            val id = resources.getIdentifier("status_bar_height", "dimen", "android")
            if (id > 0) resources.getDimensionPixelSize(id) else 60
        }
    }

    private fun startEventCollection() {
        stateCollectorJob = serviceScope.launch {
            // BUG-018 FIX: emitir Idle al reconectar para limpiar estado fantasma
            IslandEventBus.tryEmit(IslandState.Idle)
            IslandEventBus.events.collect { state -> islandView?.updateState(state) }
        }
    }

    private fun stopEventCollection() {
        stateCollectorJob?.cancel(); stateCollectorJob = null
    }

    /** Detecta foreground app con UsageStatsManager cada 1.5s */
    private fun startSocialMediaDetection() {
        socialDetectorJob?.cancel()
        socialDetectorJob = serviceScope.launch(Dispatchers.IO) {
            var lastPkg = ""
            while (isActive) {
                val pkg = getForegroundPackage()
                if (pkg != lastPkg) {
                    lastPkg = pkg
                    val isSocial = pkg in SOCIAL_PACKAGES
                    withContext(Dispatchers.Main) {
                        islandView?.miniMode = isSocial
                    }
                }
                delay(1500L)
            }
        }
    }

    private fun getForegroundPackage(): String = runCatching {
        val um = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val eventsResult = um.queryEvents(now - 5000, now)
        val event = UsageEvents.Event()
        var lastPkg = ""
        while (eventsResult.hasNextEvent()) {
            eventsResult.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastPkg = event.packageName
            }
        }
        lastPkg
    }.getOrDefault("")

    // ── Demos ───────────────────────────────────────────────────────────────
    private fun handleDemoCall() {
        timerJob?.cancel()
        IslandEventBus.tryEmit(IslandState.IncomingCall("María García", "+52 55 1234 5678"))
    }

    // BUG-012 FIX: loop con condición de parada
    private fun handleDemoMusic() {
        timerJob?.cancel()
        var progress = 0f
        timerJob = serviceScope.launch {
            while (isActive && progress < 1f) {
                IslandEventBus.tryEmit(IslandState.MusicPlaying(
                    trackTitle = "Blinding Lights", artistName = "The Weeknd",
                    isPlaying = true, progress = progress
                ))
                delay(1000L)
                progress = (progress + 0.01f).coerceAtMost(1f)
            }
            IslandEventBus.tryEmit(IslandState.Idle)
        }
    }

    private fun handleDemoNotification() {
        timerJob?.cancel()
        IslandEventBus.tryEmit(IslandState.Notification(
            appName = "WhatsApp", title = "Juan Pérez",
            text = "¿Nos vemos a las 7? 🎉", packageName = "com.whatsapp"
        ))
    }

    private fun handleDemoTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            var remaining = DEMO_TIMER_TOTAL
            while (isActive && remaining >= 0) {
                IslandEventBus.tryEmit(IslandState.Timer("Demo", remaining, DEMO_TIMER_TOTAL))
                delay(1000L); remaining--
            }
            IslandEventBus.tryEmit(IslandState.Dismissing)
        }
    }

    private fun createNotificationChannel() {
        val ch = NotificationChannel(NOTIF_CHANNEL_ID, "Dynamic Island",
            NotificationManager.IMPORTANCE_LOW).apply {
            description = "Servicio activo"; setShowBadge(false)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
    }

    private fun buildForegroundNotification(): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle("Dynamic Island activa")
            .setContentText("Toca para gestionar")
            .setSmallIcon(R.drawable.ic_island_notif)
            .setContentIntent(pi).setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW).build()
    }
}
