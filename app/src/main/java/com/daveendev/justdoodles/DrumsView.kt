package com.daveendev.justdoodles

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Drums: four big colored drums and a golden cymbal, with a little cat drummer on top. Every tap on
 * a pad bangs it with a soft, round sound at its own pitch (the lower drums are deeper), and the
 * pad squashes and sends out a ripple. Many fingers can play at once, and nothing is ever loud.
 * Now and then a pad says its sound out loud ("Boom!").
 */
@SuppressLint("ViewConstructor")
class DrumsView(context: Context, private val host: GameHost) : View(context) {

    private class Pad(val color: Int, val pitch: Float) {
        /** The whole square of screen that counts as this pad, so it is easy to hit. */
        val cell = RectF()
        var cx = 0f
        var cy = 0f
        /** Half the pad's width. */
        var s = 0f
        var hitAt = -10_000L
    }

    private val density = resources.displayMetrics.density
    private val words: Array<String> = resources.getStringArray(R.array.g_drums_words)
    private val pads = Array(PADS) { i -> Pad(PAD_COLORS[i], PAD_PITCHES[i]) }
    private var drummerX = 0f
    private var drummerY = 0f
    private var drummerR = 0f
    private var drummerAt = -10_000L
    private var drummerSide = 0
    private var lastSaidAt = 0L

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f * density
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = Palette.INK
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val box = RectF()
    private val path = Path()

    init {
        contentDescription = context.getString(R.string.game_drums)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val top = if (w > h) h * 0.24f else h * 0.2f // Room for the drummer.
        drummerX = w / 2f
        drummerY = top * 0.52f
        drummerR = min(top * 0.36f, w * 0.12f)
        val cols = if (w > h) PADS else 2
        val rows = (PADS + cols - 1) / cols
        val cw = w / cols.toFloat()
        val ch = (h - top) / rows
        for (i in 0 until PADS) {
            val row = i / cols
            val col = i % cols
            val inRow = min(cols, PADS - row * cols)
            val left = (cols - inRow) * cw / 2f + col * cw
            val pad = pads[i]
            pad.cell.set(left, top + row * ch, left + cw, top + (row + 1) * ch)
            pad.s = min(cw * 0.42f, ch * 0.5f)
            pad.cx = pad.cell.centerX()
            pad.cy = pad.cell.centerY() - pad.s * 0.17f
        }
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val m = e.actionMasked
        if (m != MotionEvent.ACTION_DOWN && m != MotionEvent.ACTION_POINTER_DOWN) return true
        val x = e.getX(e.actionIndex)
        val y = e.getY(e.actionIndex)
        val i = pads.indexOfFirst { it.cell.contains(x, y) }
        if (i >= 0) bang(i) else tickle()
        return true
    }

    private fun bang(i: Int) {
        val now = SystemClock.uptimeMillis()
        val pad = pads[i]
        pad.hitAt = now
        drummerAt = now
        drummerSide = 1 - drummerSide
        if (i == PADS - 1) {
            host.sounds.sparkle()
            host.sounds.pop(1.6f)
        } else {
            host.sounds.pop(pad.pitch)
            host.sounds.note(i, 0.3f)
        }
        // The word only after a pause, so quick drumming is never talked over.
        if (now - lastSaidAt > SAY_PAUSE_MS) {
            host.say(words[i.coerceAtMost(words.size - 1)])
            lastSaidAt = now
        }
        invalidate()
    }

    /** A tap on the drummer or the gaps: the cat bounces and gives a little pop. */
    private fun tickle() {
        drummerAt = SystemClock.uptimeMillis()
        drummerSide = 1 - drummerSide
        host.sounds.pop(1.3f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        canvas.drawColor(0xFFFFF3E0.toInt())
        var busy = false

        for (pad in pads) {
            val age = now - pad.hitAt
            val hit = if (age < HIT_MS) 1f - age.toFloat() / HIT_MS else 0f
            val ripple = if (age < RIPPLE_MS) age.toFloat() / RIPPLE_MS else -1f
            if (age < RIPPLE_MS) busy = true
            // A soft shadow on the floor.
            fill.color = 0x22000000
            canvas.drawOval(pad.cx - pad.s * 1.05f, pad.cy + pad.s * 0.75f, pad.cx + pad.s * 1.05f, pad.cy + pad.s * 1.2f, fill)
            if (pad === pads.last()) drawCymbal(canvas, pad, hit, age) else drawDrum(canvas, pad, hit)
            if (ripple >= 0f) {
                val headY = if (pad === pads.last()) pad.cy - pad.s * 0.1f else pad.cy - pad.s * 0.35f
                line.color = pad.color
                line.alpha = ((1f - ripple) * 200).toInt()
                line.strokeWidth = 5f * density
                val grow = 1f + 0.7f * ripple
                canvas.drawOval(pad.cx - pad.s * grow, headY - pad.s * 0.32f * grow, pad.cx + pad.s * grow, headY + pad.s * 0.32f * grow, line)
            }
        }

        if (now - drummerAt < DRUMMER_MS) busy = true
        drawDrummer(canvas, now)
        if (busy && isAttachedToWindow) postInvalidateOnAnimation()
    }

    private fun drawDrum(c: Canvas, pad: Pad, hit: Float) {
        val s = pad.s
        val rx = s
        val ry = s * 0.32f
        val headY = pad.cy - s * 0.35f
        val bottomY = pad.cy + s * 0.7f
        c.save()
        // Squashes down when it is banged, and springs back.
        c.scale(1f + 0.07f * hit, 1f - 0.14f * hit, pad.cx, bottomY + ry)

        // The shell: sides and a rounded bottom.
        path.rewind()
        path.moveTo(pad.cx - rx, headY)
        path.lineTo(pad.cx - rx, bottomY)
        box.set(pad.cx - rx, bottomY - ry, pad.cx + rx, bottomY + ry)
        path.arcTo(box, 180f, -180f)
        path.lineTo(pad.cx + rx, headY)
        path.close()
        fill.color = pad.color
        c.drawPath(path, fill)
        // Lighter strips down the shell, and a shine.
        line.color = 0x66FFFFFF
        line.strokeWidth = 4f * density
        for (k in floatArrayOf(-0.6f, -0.2f, 0.2f, 0.6f)) {
            val x = pad.cx + rx * k
            c.drawLine(x, headY, x, bottomY + ry * sqrt(1f - k * k) - 6f * density, line)
        }
        c.drawPath(path, ink)

        // The skin on top, tinted with the drum's color when it is hit, with a colored rim.
        box.set(pad.cx - rx, headY - ry, pad.cx + rx, headY + ry)
        fill.color = Palette.mix(0xFFFFF8E1.toInt(), pad.color, 0.45f * hit)
        c.drawOval(box, fill)
        line.color = pad.color
        line.alpha = 255
        line.strokeWidth = s * 0.14f
        c.drawOval(box, line)
        c.drawOval(box, ink)
        fill.color = 0x88FFFFFF.toInt()
        c.drawOval(pad.cx - rx * 0.5f, headY - ry * 0.55f, pad.cx - rx * 0.1f, headY - ry * 0.2f, fill)
        c.restore()
    }

    private fun drawCymbal(c: Canvas, pad: Pad, hit: Float, age: Long) {
        val s = pad.s
        val y = pad.cy - s * 0.1f
        val foot = pad.cy + s * 0.95f
        // The stand and its little feet.
        line.color = Palette.INK
        line.alpha = 255
        line.strokeWidth = 5f * density
        c.drawLine(pad.cx, y, pad.cx, foot, line)
        c.drawLine(pad.cx, foot, pad.cx - s * 0.4f, foot + s * 0.1f, line)
        c.drawLine(pad.cx, foot, pad.cx + s * 0.4f, foot + s * 0.1f, line)

        // The disc wobbles when it is hit.
        c.save()
        c.rotate(sin(age / 38f) * 8f * hit, pad.cx, y)
        val ry = s * 0.28f
        box.set(pad.cx - s, y - ry, pad.cx + s, y + ry)
        fill.color = Palette.mix(0xFFFFC107.toInt(), 0xFFFFFFFF.toInt(), 0.5f * hit)
        c.drawOval(box, fill)
        // Rings around the middle.
        line.color = 0x55FF8F00
        line.strokeWidth = 2.5f * density
        for (k in floatArrayOf(0.72f, 0.46f)) {
            c.drawOval(pad.cx - s * k, y - ry * k, pad.cx + s * k, y + ry * k, line)
        }
        c.drawOval(box, ink)
        // The dome in the middle.
        fill.color = 0xFFFFE082.toInt()
        c.drawOval(pad.cx - s * 0.2f, y - ry * 0.45f, pad.cx + s * 0.2f, y + ry * 0.45f, fill)
        c.drawOval(pad.cx - s * 0.2f, y - ry * 0.45f, pad.cx + s * 0.2f, y + ry * 0.45f, ink)
        fill.color = 0x99FFFFFF.toInt()
        c.drawOval(pad.cx - s * 0.7f, y - ry * 0.6f, pad.cx - s * 0.35f, y - ry * 0.25f, fill)
        c.restore()
    }

    /** A little cat on top with a stick in each paw: it bounces and drums along with every tap. */
    private fun drawDrummer(c: Canvas, now: Long) {
        val r = drummerR
        val age = now - drummerAt
        val hit = if (age < DRUMMER_MS) 1f - age.toFloat() / DRUMMER_MS else 0f
        val bounce = sin(hit * Math.PI.toFloat()) * r * 0.18f
        val cx = drummerX
        val cy = drummerY - bounce

        // The paws and sticks: the one that is playing comes down.
        for (side in 0..1) {
            val sign = if (side == 0) -1f else 1f
            val down = if (side == drummerSide) hit else 0f
            val px = cx + sign * r * 1.15f
            val py = cy + r * (0.55f + 0.45f * down)
            line.color = 0xFF8D5524.toInt()
            line.alpha = 255
            line.strokeWidth = 5f * density
            c.drawLine(px, py, px + sign * r * 0.7f, py - r * (0.9f - 0.5f * down), line)
            fill.color = 0xFFFFCC80.toInt()
            c.drawCircle(px + sign * r * 0.7f, py - r * (0.9f - 0.5f * down), r * 0.12f, fill)
            fill.color = 0xFFFFB74D.toInt()
            c.drawCircle(px, py, r * 0.26f, fill)
            c.drawCircle(px, py, r * 0.26f, ink)
        }

        // Ears.
        for (sign in floatArrayOf(-1f, 1f)) {
            path.rewind()
            path.moveTo(cx + sign * r * 0.9f, cy - r * 0.2f)
            path.lineTo(cx + sign * r * 0.75f, cy - r * 1.2f)
            path.lineTo(cx + sign * r * 0.2f, cy - r * 0.85f)
            path.close()
            fill.color = 0xFFFFB74D.toInt()
            c.drawPath(path, fill)
            c.drawPath(path, ink)
        }
        // Face.
        fill.color = 0xFFFFB74D.toInt()
        c.drawCircle(cx, cy, r, fill)
        c.drawCircle(cx, cy, r, ink)
        fill.color = 0x66FFFFFF
        c.drawOval(cx - r * 0.6f, cy - r * 0.8f, cx - r * 0.2f, cy - r * 0.5f, fill)
        // Eyes: wide open, or squeezed shut with joy when drumming.
        for (sign in floatArrayOf(-1f, 1f)) {
            val ex = cx + sign * r * 0.38f
            val ey = cy - r * 0.1f
            if (hit > 0.1f) {
                box.set(ex - r * 0.16f, ey - r * 0.1f, ex + r * 0.16f, ey + r * 0.22f)
                c.drawArc(box, 200f, 140f, false, ink)
            } else {
                fill.color = Palette.INK
                c.drawCircle(ex, ey, r * 0.1f, fill)
            }
            fill.color = 0x55FF6FB0
            c.drawCircle(cx + sign * r * 0.62f, cy + r * 0.26f, r * 0.14f, fill)
        }
        // Nose and smile.
        fill.color = 0xFFFF6FB0.toInt()
        c.drawCircle(cx, cy + r * 0.12f, r * 0.09f, fill)
        box.set(cx - r * 0.25f, cy + r * 0.15f, cx + r * 0.25f, cy + r * 0.55f)
        c.drawArc(box, 20f, 140f, false, ink)
    }

    private companion object {
        const val PADS = 5
        const val HIT_MS = 320L
        const val RIPPLE_MS = 600L
        const val DRUMMER_MS = 260L
        const val SAY_PAUSE_MS = 1800L

        /** Red, yellow, green and blue drums, then the golden cymbal (its color is the ripple's). */
        val PAD_COLORS = intArrayOf(0xFFE53935.toInt(), 0xFFFDD835.toInt(), 0xFF2E9D4A.toInt(), 0xFF2962FF.toInt(), 0xFFFFC107.toInt())

        /** How high each drum sounds: [Sounds.pop] plays slower and deeper below 1. */
        val PAD_PITCHES = floatArrayOf(0.5f, 0.62f, 0.78f, 0.95f, 1.6f)
    }
}
