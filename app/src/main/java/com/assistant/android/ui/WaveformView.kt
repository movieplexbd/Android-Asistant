package com.assistant.android.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Animated audio waveform — 24 vertical bars that bounce based on the supplied audio level (0..1).
 * Bars in the center react more strongly so it looks like a real waveform.
 */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF22C55E") // listening green
        style = Paint.Style.FILL
    }

    private val barCount = 24
    private val barLevels = FloatArray(barCount) { 0.05f }
    private val barTargets = FloatArray(barCount) { 0.05f }
    private var inputLevel: Float = 0f
    private val rect = RectF()
    private var phase = 0f

    fun setLevel(level: Float) {
        inputLevel = level.coerceIn(0f, 1f)
        // Generate fresh targets using the input level + a per-bar wave so it dances naturally.
        val now = System.currentTimeMillis() / 90.0
        for (i in 0 until barCount) {
            // Center bars react more (Hann-like envelope)
            val centerWeight = 0.4f + 0.6f * (1f - abs(i - barCount / 2f) / (barCount / 2f))
            val noise = (Random.nextFloat() * 0.25f)
            val wave = (sin(now + i * 0.5).toFloat() + 1f) * 0.5f * 0.4f
            val target = (inputLevel * centerWeight + wave * 0.2f * inputLevel + noise * inputLevel)
                .coerceIn(0.05f, 1f)
            barTargets[i] = target
        }
        invalidate()
    }

    fun setColor(color: Int) {
        barPaint.color = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // Smooth-attack / decay toward target so movement looks fluid.
        for (i in 0 until barCount) {
            val target = barTargets[i]
            val cur = barLevels[i]
            val diff = target - cur
            barLevels[i] = (cur + diff * 0.35f).coerceIn(0.05f, 1f)
            // gentle decay so when input==0 bars settle
            if (inputLevel < 0.02f) barTargets[i] = (barTargets[i] * 0.85f).coerceAtLeast(0.05f)
        }

        val gap = w / (barCount * 2f)
        val barWidth = (w - gap * (barCount + 1)) / barCount
        val maxBarH = h * 0.9f
        val minBarH = h * 0.08f
        val cornerR = barWidth / 2f
        val centerY = h / 2f

        for (i in 0 until barCount) {
            val lvl = barLevels[i]
            val barH = (minBarH + (maxBarH - minBarH) * lvl)
            val left = gap + i * (barWidth + gap)
            val top = centerY - barH / 2f
            val right = left + barWidth
            val bottom = centerY + barH / 2f
            rect.set(left, top, right, bottom)
            canvas.drawRoundRect(rect, cornerR, cornerR, barPaint)
        }

        // Continuous redraw if there is any movement to animate decay
        if (inputLevel > 0.02f || barLevels.any { it > 0.06f }) {
            postInvalidateOnAnimation()
        }
    }
}
