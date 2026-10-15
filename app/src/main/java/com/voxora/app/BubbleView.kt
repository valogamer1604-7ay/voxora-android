package com.voxora.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.*

/** The floating Voxora bubble, drawn entirely in code (no image files needed). */
class BubbleView(context: Context) : View(context) {

    enum class Mode { IDLE, LISTENING, PROCESSING, DONE, ERROR }

    var mode: Mode = Mode.IDLE
        set(value) { field = value; invalidate() }

    private var level = 0f          // live microphone loudness 0..1
    private var phase = 0f          // animation clock
    private val d = resources.displayMetrics.density

    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val core = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.5f * d; color = 0x66FFFFFF
    }
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; strokeCap = Paint.Cap.ROUND; strokeWidth = 3.2f * d
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f * d; strokeCap = Paint.Cap.ROUND; color = Color.WHITE
    }

    private val ticker = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { phase += 0.06f; invalidate() }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); ticker.start() }
    override fun onDetachedFromWindow() { ticker.cancel(); super.onDetachedFromWindow() }

    fun setLevel(rmsDb: Float) {
        // SpeechRecognizer gives roughly -2..10 dB
        val v = ((rmsDb + 2f) / 12f).coerceIn(0f, 1f)
        level = level * 0.6f + v * 0.4f
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - 6f * d

        val (a, b) = when (mode) {
            Mode.LISTENING -> 0xFFFF6FB5.toInt() to 0xFF9B5CFF.toInt()
            Mode.PROCESSING -> 0xFF8E7BFF.toInt() to 0xFF4B2FD6.toInt()
            Mode.DONE -> 0xFF7BE0B5.toInt() to 0xFF3D9E86.toInt()
            Mode.ERROR -> 0xFFFF8A8A.toInt() to 0xFFB53B5C.toInt()
            Mode.IDLE -> 0xFFC9B8FF.toInt() to 0xFF6B4BF0.toInt()
        }

        // soft outer glow, pulses with your voice
        val pulse = if (mode == Mode.LISTENING) 1f + level * 0.35f + 0.05f * sin(phase * 2) else 1f
        glow.shader = RadialGradient(cx, cy, r * 1.25f * pulse + 1f,
            intArrayOf((a and 0x00FFFFFF) or 0x77000000, 0x00000000), null, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r * 1.25f * pulse, glow)

        core.shader = LinearGradient(cx - r, cy - r, cx + r, cy + r, a, b, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r * 0.82f, core)
        c.drawCircle(cx, cy, r * 0.82f, ring)

        when (mode) {
            Mode.PROCESSING -> {
                val s = r * 0.42f
                c.drawArc(cx - s, cy - s, cx + s, cy + s, (phase * 120f) % 360f, 260f, false, arc)
            }
            Mode.DONE -> {
                val p = Path().apply {
                    moveTo(cx - r * 0.28f, cy); lineTo(cx - r * 0.06f, cy + r * 0.22f); lineTo(cx + r * 0.3f, cy - r * 0.2f)
                }
                c.drawPath(p, arc)
            }
            else -> {
                // 5 waveform bars
                val heights = floatArrayOf(0.35f, 0.65f, 1f, 0.65f, 0.35f)
                val gap = r * 0.2f
                for (i in 0 until 5) {
                    val x = cx + (i - 2) * gap
                    val live = if (mode == Mode.LISTENING)
                        (0.35f + level * 0.9f) * (0.75f + 0.25f * sin(phase * 3 + i))
                    else 0.55f + 0.08f * sin(phase + i)
                    val h = r * 0.62f * heights[i] * live.coerceAtMost(1.2f)
                    c.drawLine(x, cy - h / 2, x, cy + h / 2, bar)
                }
            }
        }
    }
}
