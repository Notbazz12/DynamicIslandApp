package com.example.dynamicisland.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.random.Random

class WaveformVisualizerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF2D55.toInt() // Color rosado de Apple Music
        style = Paint.Style.FILL
    }
    
    private val numBars = 4
    private val barHeights = FloatArray(numBars) { 0.2f }
    private val animators = mutableListOf<ValueAnimator>()
    private var isPlaying = false

    fun setPlaying(playing: Boolean) {
        if (this.isPlaying == playing) return
        this.isPlaying = playing
        stopAnimation()
        if (playing) {
            startAnimation()
        } else {
            for (i in 0 until numBars) {
                barHeights[i] = 0.2f
            }
            invalidate()
        }
    }

    fun setColor(color: Int) {
        paint.color = color
        invalidate()
    }

    private fun startAnimation() {
        for (i in 0 until numBars) {
            val animator = ValueAnimator.ofFloat(0.1f, 0.9f).apply {
                duration = Random.nextLong(250, 450)
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = LinearInterpolator()
                addUpdateListener { va ->
                    barHeights[i] = va.animatedValue as Float
                    invalidate()
                }
                start()
            }
            animators.add(animator)
        }
    }

    private fun stopAnimation() {
        animators.forEach { it.cancel() }
        animators.clear()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val barWidth = w / (numBars * 2 - 1)
        val cornerRadius = barWidth / 2f

        for (i in 0 until numBars) {
            val barHeight = h * barHeights[i]
            val left = i * 2 * barWidth
            val top = h - barHeight
            val right = left + barWidth
            val bottom = h
            canvas.drawRoundRect(left, top, right, bottom, cornerRadius, cornerRadius, paint)
        }
    }
}
