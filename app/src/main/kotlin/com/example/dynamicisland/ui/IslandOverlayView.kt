package com.example.dynamicisland.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.dynamicisland.R
import com.example.dynamicisland.state.IslandState
import java.util.Locale
import java.util.concurrent.TimeUnit

class IslandOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // ── State ─────────────────────────────────────────────────────────────
    private var currentState: IslandState = IslandState.Idle
    private var isExpanded = false

    // ── Modes ─────────────────────────────────────────────────────────────
    /** Modo gaming: isla ultra-pequeña, solo un punto */
    var gamingMode: Boolean = false
        set(value) { field = value; applyGamingMode(value) }

    /** Modo mini (redes sociales): no se expande, tamaño reducido */
    var miniMode: Boolean = false

    // ── Sub-views ──────────────────────────────────────────────────────────
    private val collapsedView: View
    private val expandedView: View

    // Compact
    private val ivAlbumArtSmall: ImageView
    private val ivLeadingIcon: ImageView
    private val tvLeadingText: TextView
    private val ivTrailingIcon: ImageView
    private val tvTrailingText: TextView

    // Expanded
    private val ivExpandedIcon: ImageView
    private val tvExpandedTitle: TextView
    private val tvExpandedSubtitle: TextView
    private val tvExpandedExtra: TextView

    // New Apple-style views
    private val waveformVisualizer: WaveformVisualizerView
    private val expandedVisualizer: WaveformVisualizerView
    private val pbMusicProgress: android.widget.ProgressBar
    private val llMusicControls: View
    private val llCallControls: View
    private val btnActionPrev: View
    private val btnAction1: View // Music Play/Pause
    private val btnAction2: View // Music Skip Next
    private val btnCallAction1: View
    private val btnCallAction2: View
    private val ivCallIcon1: ImageView
    private val ivCallIcon2: ImageView

    // ── Metrics ────────────────────────────────────────────────────────────
    private val dp        = context.resources.displayMetrics.density
    private val pillW     = (126 * dp).toInt()
    private val pillH     = (36  * dp).toInt()
    private val miniPillW = (80  * dp).toInt()
    private val miniPillH = (26  * dp).toInt()
    private val dotW      = (12  * dp).toInt()
    private val dotH      = (12  * dp).toInt()
    private val expandW   = (340 * dp).toInt()
    private val expandH   = (116 * dp).toInt() // Aumentado para controles Apple-style sin squish

    // ── Animación ──────────────────────────────────────────────────────────
    private var sizeAnimator: ValueAnimator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var autoCollapseRunnable: Runnable? = null

    /** WindowManager del servicio para updateViewLayout */
    var wm: WindowManager? = null
    var onDismiss: (() -> Unit)? = null
    /** Callback para enviar key de play/pause al AudioManager */
    var onPlayPause: (() -> Unit)? = null
    /** Callback para pista anterior */
    var onSkipPrev: (() -> Unit)? = null
    /** Callback para pista siguiente */
    var onSkipNext: (() -> Unit)? = null

    // ── Fondo con GradientDrawable (fiable con hardware acceleration) ────────
    private val bgDrawable = GradientDrawable().apply {
        setColor(Color.BLACK)
        cornerRadius = 50f * dp
    }
    private var cornerRadius = 50f * dp

    // ── Init ───────────────────────────────────────────────────────────────
    init {
        background = bgDrawable   // fondo negro con esquinas animables
        val inflater = LayoutInflater.from(context)
        collapsedView = inflater.inflate(R.layout.view_island_collapsed, this, false)
        expandedView  = inflater.inflate(R.layout.view_island_expanded,  this, false)
        addView(collapsedView)
        addView(expandedView)
        expandedView.alpha = 0f; expandedView.visibility = INVISIBLE

        ivAlbumArtSmall = collapsedView.findViewById(R.id.iv_album_art_small)
        ivLeadingIcon   = collapsedView.findViewById(R.id.iv_leading_icon)
        tvLeadingText   = collapsedView.findViewById(R.id.tv_leading_text)
        ivTrailingIcon  = collapsedView.findViewById(R.id.iv_trailing_icon)
        tvTrailingText  = collapsedView.findViewById(R.id.tv_trailing_text)

        ivExpandedIcon     = expandedView.findViewById(R.id.iv_expanded_icon)
        tvExpandedTitle    = expandedView.findViewById(R.id.tv_expanded_title)
        tvExpandedSubtitle = expandedView.findViewById(R.id.tv_expanded_subtitle)
        tvExpandedExtra    = expandedView.findViewById(R.id.tv_expanded_extra)

        waveformVisualizer = collapsedView.findViewById(R.id.waveform_visualizer)
        expandedVisualizer = expandedView.findViewById(R.id.iv_expanded_visualizer)
        pbMusicProgress    = expandedView.findViewById(R.id.pb_music_progress)
        llMusicControls    = expandedView.findViewById(R.id.ll_music_controls)
        llCallControls     = expandedView.findViewById(R.id.ll_call_controls)

        btnActionPrev      = expandedView.findViewById(R.id.btn_action_prev)
        btnAction1         = expandedView.findViewById(R.id.btn_action1)
        btnAction2         = expandedView.findViewById(R.id.btn_action2)

        btnCallAction1     = expandedView.findViewById(R.id.btn_call_action1)
        btnCallAction2     = expandedView.findViewById(R.id.btn_call_action2)
        ivCallIcon1        = expandedView.findViewById(R.id.iv_call_icon1)
        ivCallIcon2        = expandedView.findViewById(R.id.iv_call_icon2)

        btnActionPrev.setOnClickListener { onSkipPrev?.invoke() }
        btnAction1.setOnClickListener    { onPlayPause?.invoke() }
        btnAction2.setOnClickListener    { onSkipNext?.invoke() }

        setOnClickListener {
            if (gamingMode || miniMode) return@setOnClickListener
            if (isExpanded) collapseIsland() else expandIsland()
        }
        setOnLongClickListener {
            gamingMode = !gamingMode
            true
        }
    }

    // ── Cleanup ────────────────────────────────────────────────────────────
    fun cleanup() {
        cancelAutoCollapse()
        sizeAnimator?.cancel()
        handler.removeCallbacksAndMessages(null)
        animate().cancel()
        collapsedView.animate().cancel()
        expandedView.animate().cancel()
        wm = null
        onDismiss = null
        onPlayPause = null
        onSkipPrev = null
        onSkipNext = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cleanup()
    }

    // ── Fondo: actualizado en animateShape directamente ───────────────────

    // ── Estado público ─────────────────────────────────────────────────────
    fun updateState(state: IslandState) {
        currentState = state
        when (state) {
            is IslandState.Idle         -> dismissIsland()
            is IslandState.Dismissing   -> dismissIsland()
            is IslandState.IncomingCall  -> showCall(state)
            is IslandState.OngoingCall   -> showOngoingCall(state)
            is IslandState.MusicPlaying  -> showMusic(state)
            is IslandState.Notification  -> showNotification(state)
            is IslandState.Timer         -> showTimer(state)
            is IslandState.BatteryCharging -> showBattery(state)
            is IslandState.RingerMode    -> showRingerMode(state)
        }
    }

    // ── Renderizado de estados ─────────────────────────────────────────────
    private fun showCall(s: IslandState.IncomingCall) {
        cancelAutoCollapse()
        showAlbumArt(null)
        ivLeadingIcon.setImageResource(R.drawable.ic_call_incoming)
        ivLeadingIcon.setColorFilter(0xFF4CD964.toInt())
        tvLeadingText.visibility = GONE
        tvTrailingText.text = s.callerName.substringBefore(" ")
        tvTrailingText.visibility = VISIBLE
        ivTrailingIcon.visibility = GONE
        waveformVisualizer.visibility = GONE

        // Expanded
        ivExpandedIcon.setImageResource(R.drawable.ic_call_incoming)
        ivExpandedIcon.setColorFilter(0xFF4CD964.toInt())
        tvExpandedTitle.text    = s.callerName
        tvExpandedSubtitle.text = s.callerNumber ?: "Llamada entrante"
        tvExpandedExtra.visibility = GONE
        expandedVisualizer.visibility = GONE
        pbMusicProgress.visibility = GONE
        llMusicControls.visibility = GONE

        llCallControls.visibility = VISIBLE
        ivCallIcon1.setImageResource(R.drawable.ic_call_end)
        ivCallIcon1.setColorFilter(0xFFFF3B30.toInt())
        ivCallIcon2.setImageResource(R.drawable.ic_call_answer)
        ivCallIcon2.setColorFilter(0xFF4CD964.toInt())
        btnCallAction1.setOnClickListener { dismissIsland() }
        btnCallAction2.setOnClickListener { dismissIsland() }

        if (!miniMode) expandWithAutoCollapse(15_000L)
    }

    private fun showOngoingCall(s: IslandState.OngoingCall) {
        cancelAutoCollapse()
        showAlbumArt(null)
        val min = s.durationSeconds / 60; val sec = s.durationSeconds % 60
        val dur = String.format(Locale.getDefault(), "%d:%02d", min, sec)
        ivLeadingIcon.setImageResource(R.drawable.ic_call_active)
        ivLeadingIcon.setColorFilter(0xFF4CD964.toInt())
        tvLeadingText.visibility = GONE
        tvTrailingText.text = dur; tvTrailingText.visibility = VISIBLE
        ivTrailingIcon.visibility = GONE
        waveformVisualizer.visibility = GONE

        // Expanded
        ivExpandedIcon.setImageResource(R.drawable.ic_call_active)
        ivExpandedIcon.setColorFilter(0xFF4CD964.toInt())
        tvExpandedTitle.text    = s.callerName
        tvExpandedSubtitle.text = dur
        tvExpandedExtra.visibility = GONE
        expandedVisualizer.visibility = GONE
        pbMusicProgress.visibility = GONE
        llMusicControls.visibility = GONE

        llCallControls.visibility = VISIBLE
        ivCallIcon1.setImageResource(R.drawable.ic_call_mute); ivCallIcon1.clearColorFilter()
        ivCallIcon2.setImageResource(R.drawable.ic_call_end)
        ivCallIcon2.setColorFilter(0xFFFF3B30.toInt())
        btnCallAction1.setOnClickListener { }
        btnCallAction2.setOnClickListener { dismissIsland() }

        if (!isExpanded && !miniMode) expandIsland()
    }

    private fun showMusic(s: IslandState.MusicPlaying) {
        // Compact
        showAlbumArt(s.albumArt)
        
        // ── Extracción dinámica de color del álbum art ────────────────────────
        val themeColor = getVibrantDominantColor(s.albumArt)

        if (s.albumArt == null) {
            ivLeadingIcon.setImageResource(R.drawable.ic_music)
            ivLeadingIcon.setColorFilter(themeColor)
        }
        tvLeadingText.visibility = GONE
        ivTrailingIcon.visibility = GONE
        tvTrailingText.visibility = GONE // El visualizador de onda se muestra en su lugar

        waveformVisualizer.visibility = VISIBLE
        waveformVisualizer.setPlaying(s.isPlaying)
        waveformVisualizer.setColor(themeColor)

        // Expanded
        if (s.albumArt != null) {
            ivExpandedIcon.setImageBitmap(s.albumArt)
            ivExpandedIcon.clearColorFilter()
        } else {
            ivExpandedIcon.setImageResource(R.drawable.ic_music)
            ivExpandedIcon.setColorFilter(themeColor)
        }
        tvExpandedTitle.text    = s.trackTitle
        tvExpandedSubtitle.text = s.artistName
        tvExpandedExtra.visibility = GONE

        expandedVisualizer.visibility = VISIBLE
        expandedVisualizer.setPlaying(s.isPlaying)
        expandedVisualizer.setColor(themeColor)

        pbMusicProgress.visibility = VISIBLE
        pbMusicProgress.progress = (s.progress * 100).toInt()
        pbMusicProgress.progressTintList = android.content.res.ColorStateList.valueOf(themeColor)

        llCallControls.visibility = GONE
        llMusicControls.visibility = VISIBLE

        // Botones de control de música directos
        val playPauseIcon = expandedView.findViewById<ImageView>(R.id.btn_action1)
        playPauseIcon.setImageResource(if (s.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)

        if (miniMode) return
        if (!isExpanded) expandWithAutoCollapse(6_000L)
        else {
            cancelAutoCollapse()
            autoCollapseRunnable = Runnable { collapseIsland() }
            handler.postDelayed(autoCollapseRunnable!!, 6_000L)
        }
    }

    private fun showBattery(s: IslandState.BatteryCharging) {
        cancelAutoCollapse()
        showAlbumArt(null)
        // Compact
        ivLeadingIcon.setImageResource(R.drawable.ic_battery_charging)
        ivLeadingIcon.setColorFilter(0xFF10B981.toInt()) // Emerald charging green
        tvLeadingText.visibility = GONE
        ivTrailingIcon.visibility = GONE
        tvTrailingText.text = "${s.level}%"
        tvTrailingText.visibility = VISIBLE
        waveformVisualizer.visibility = GONE

        // Expanded
        ivExpandedIcon.setImageResource(R.drawable.ic_battery_charging)
        ivExpandedIcon.setColorFilter(0xFF10B981.toInt())
        tvExpandedTitle.text = "Cargador conectado"
        tvExpandedSubtitle.text = "Batería al ${s.level}%"
        tvExpandedExtra.visibility = GONE
        expandedVisualizer.visibility = GONE
        pbMusicProgress.visibility = GONE
        llMusicControls.visibility = GONE
        llCallControls.visibility = GONE

        if (!miniMode) expandWithAutoCollapse(4_000L)
    }

    private fun showRingerMode(s: IslandState.RingerMode) {
        cancelAutoCollapse()
        showAlbumArt(null)
        // Compact
        val (icon, color, label) = when (s.mode) {
            android.media.AudioManager.RINGER_MODE_SILENT -> Triple(R.drawable.ic_silent_mode, 0xFFEF4444.toInt(), "Silencio")
            android.media.AudioManager.RINGER_MODE_VIBRATE -> Triple(R.drawable.ic_vibrate_mode, 0xFF94A3B8.toInt(), "Vibración")
            else -> Triple(R.drawable.ic_notification, 0xFFFFFFFF.toInt(), "Sonido")
        }
        ivLeadingIcon.setImageResource(icon)
        ivLeadingIcon.setColorFilter(color)
        tvLeadingText.visibility = GONE
        ivTrailingIcon.visibility = GONE
        tvTrailingText.text = label
        tvTrailingText.visibility = VISIBLE
        waveformVisualizer.visibility = GONE

        // Expanded
        ivExpandedIcon.setImageResource(icon)
        ivExpandedIcon.setColorFilter(color)
        tvExpandedTitle.text = "Modo de sonido"
        tvExpandedSubtitle.text = label
        tvExpandedExtra.visibility = GONE
        expandedVisualizer.visibility = GONE
        pbMusicProgress.visibility = GONE
        llMusicControls.visibility = GONE
        llCallControls.visibility = GONE

        if (!miniMode) expandWithAutoCollapse(3_000L)
    }

    private fun getDominantColor(bitmap: android.graphics.Bitmap?): Int {
        bitmap ?: return Color.BLACK
        var redBucket = 0
        var greenBucket = 0
        var blueBucket = 0
        var sampleCount = 0
        
        val w = bitmap.width
        val h = bitmap.height
        for (x in 0 until w step (w / 6).coerceAtLeast(1)) {
            for (y in 0 until h step (h / 6).coerceAtLeast(1)) {
                if (x < w && y < h) {
                    val color = bitmap.getPixel(x, y)
                    val r = Color.red(color)
                    val g = Color.green(color)
                    val b = Color.blue(color)
                    val luminance = 0.299f * r + 0.587f * g + 0.114f * b
                    if (luminance in 35f..225f) {
                        redBucket += r
                        greenBucket += g
                        blueBucket += b
                        sampleCount++
                    }
                }
            }
        }
        return if (sampleCount > 0) {
            Color.rgb(redBucket / sampleCount, greenBucket / sampleCount, blueBucket / sampleCount)
        } else {
            0xFF3B82F6.toInt()
        }
    }

    private fun getVibrantDominantColor(bitmap: android.graphics.Bitmap?): Int {
        bitmap ?: return 0xFF3B82F6.toInt()
        val color = getDominantColor(bitmap)
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[1] = hsv[1].coerceAtLeast(0.8f) // Forzar alta saturación
        hsv[2] = 0.95f // Forzar buen brillo
        return Color.HSVToColor(hsv)
    }

    private fun showNotification(s: IslandState.Notification) {
        showAlbumArt(null)
        ivLeadingIcon.setImageResource(R.drawable.ic_notification); ivLeadingIcon.clearColorFilter()
        tvLeadingText.visibility = GONE
        ivTrailingIcon.visibility = GONE
        tvTrailingText.text = s.appName; tvTrailingText.visibility = VISIBLE
        waveformVisualizer.visibility = GONE

        // Expanded
        ivExpandedIcon.setImageResource(R.drawable.ic_notification); ivExpandedIcon.clearColorFilter()
        tvExpandedTitle.text    = s.title
        tvExpandedSubtitle.text = s.text
        tvExpandedExtra.visibility = GONE
        expandedVisualizer.visibility = GONE
        pbMusicProgress.visibility = GONE
        llMusicControls.visibility = GONE
        llCallControls.visibility = GONE

        if (!miniMode) expandWithAutoCollapse(4_000L)
    }

    private fun showTimer(s: IslandState.Timer) {
        showAlbumArt(null)
        val min = TimeUnit.SECONDS.toMinutes(s.remainingSeconds)
        val sec = s.remainingSeconds % 60
        val label = String.format(Locale.getDefault(), "%d:%02d", min, sec)
        ivLeadingIcon.setImageResource(R.drawable.ic_timer)
        ivLeadingIcon.setColorFilter(0xFFFF9500.toInt())
        tvLeadingText.visibility = GONE
        ivTrailingIcon.visibility = GONE
        tvTrailingText.text = label; tvTrailingText.visibility = VISIBLE
        waveformVisualizer.visibility = GONE

        // Expanded
        ivExpandedIcon.setImageResource(R.drawable.ic_timer)
        ivExpandedIcon.setColorFilter(0xFFFF9500.toInt())
        tvExpandedTitle.text    = s.label
        tvExpandedSubtitle.text = label
        tvExpandedExtra.visibility = GONE
        expandedVisualizer.visibility = GONE
        pbMusicProgress.visibility = GONE
        llMusicControls.visibility = GONE
        llCallControls.visibility = GONE

        if (!isExpanded) {
            if (!miniMode) expandWithAutoCollapse(3_000L)
        }
    }

    private fun dismissIsland() {
        cancelAutoCollapse()
        if (isExpanded) collapseIsland()
    }

    // ── Album art helper ───────────────────────────────────────────────────
    private fun showAlbumArt(bitmap: android.graphics.Bitmap?) {
        if (bitmap != null) {
            ivAlbumArtSmall.setImageBitmap(bitmap)
            ivAlbumArtSmall.visibility = VISIBLE
            ivLeadingIcon.visibility = GONE
        } else {
            ivAlbumArtSmall.visibility = GONE
            ivLeadingIcon.visibility = VISIBLE
        }
    }

    // ── Gaming mode ────────────────────────────────────────────────────────
    private fun applyGamingMode(active: Boolean) {
        // Guard: no animar si la vista aun no esta en WindowManager
        if (!isAttachedToWindow) return
        if (active) {
            if (isExpanded) collapseIsland {
                animateShape(dotW, dotH, 50f * dp, DecelerateInterpolator(2f), 250L)
            } else animateShape(dotW, dotH, 50f * dp, DecelerateInterpolator(2f), 250L)
            collapsedView.visibility = INVISIBLE
        } else {
            animateShape(pillW, pillH, 50f * dp, OvershootInterpolator(1.5f), 350L)
            collapsedView.visibility = VISIBLE
        }
    }

    // ── Expand / Collapse ──────────────────────────────────────────────────
    fun expandIsland(onEnd: (() -> Unit)? = null) {
        if (isExpanded || gamingMode || miniMode) { onEnd?.invoke(); return }
        isExpanded = true
        collapsedView.animate().alpha(0f).setDuration(100).withEndAction {
            collapsedView.visibility = INVISIBLE
        }.start()
        animateShape(expandW, expandH, 24f * dp, OvershootInterpolator(1.8f), 420L) { onEnd?.invoke() }
        handler.postDelayed({
            expandedView.visibility = VISIBLE
            expandedView.animate().alpha(1f).setDuration(200)
                .setInterpolator(DecelerateInterpolator()).start()
        }, 200L)
    }

    fun collapseIsland(onEnd: (() -> Unit)? = null) {
        if (!isExpanded) { onEnd?.invoke(); return }
        isExpanded = false
        expandedView.animate().alpha(0f).setDuration(120).withEndAction {
            expandedView.visibility = INVISIBLE
            collapsedView.visibility = VISIBLE
            collapsedView.animate().alpha(1f).setDuration(150).start()
        }.start()
        val targetW = if (miniMode) miniPillW else pillW
        val targetH = if (miniMode) miniPillH else pillH
        animateShape(targetW, targetH, 50f * dp, DecelerateInterpolator(2.5f), 340L) { onEnd?.invoke() }
    }

    private fun expandWithAutoCollapse(delay: Long) {
        expandIsland()
        cancelAutoCollapse()
        autoCollapseRunnable = Runnable { collapseIsland() }
        handler.postDelayed(autoCollapseRunnable!!, delay)
    }

    private fun cancelAutoCollapse() {
        autoCollapseRunnable?.let { handler.removeCallbacks(it) }
        autoCollapseRunnable = null
    }

    // ── Animación de forma ─────────────────────────────────────────────────
    private fun animateShape(
        targetW: Int, targetH: Int, targetCorners: Float,
        interp: android.view.animation.Interpolator,
        duration: Long, onEnd: () -> Unit = {}
    ) {
        sizeAnimator?.cancel()
        val wmLp = layoutParams as? WindowManager.LayoutParams ?: return
        val startW = wmLp.width; val startH = wmLp.height; val startR = cornerRadius

        // FIX: usar variable nombrada para evitar this@apply circular en AnimatorSet
        sizeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator  = interp
            addUpdateListener { va ->
                val t = va.animatedValue as Float
                (layoutParams as? WindowManager.LayoutParams)?.let { lp ->
                    lp.width  = lerp(startW, targetW, t)
                    lp.height = lerp(startH, targetH, t)
                    runCatching { wm?.updateViewLayout(this@IslandOverlayView, lp) }
                }
                cornerRadius = lerp(startR, targetCorners, t)
                bgDrawable.cornerRadius = cornerRadius   // actualizar fondo directamente
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) { onEnd() }
            })
            start()
        }
    }

    private fun lerp(a: Int, b: Int, t: Float) = (a + (b - a) * t).toInt()
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}

// ─── Arc progress view ────────────────────────────────────────────────────
class ArcProgressView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    var progress: Float = 0f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }
    private val trackP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * context.resources.displayMetrics.density
        color = 0x33FFFFFF; strokeCap = Paint.Cap.ROUND
    }
    private val progP = Paint(trackP).apply { color = 0xFFFF2D55.toInt() }
    private val oval = RectF()
    override fun onDraw(canvas: Canvas) {
        val i = trackP.strokeWidth / 2
        oval.set(i, i, width - i, height - i)
        canvas.drawArc(oval, -90f, 360f, false, trackP)
        canvas.drawArc(oval, -90f, 360f * progress, false, progP)
    }
}
