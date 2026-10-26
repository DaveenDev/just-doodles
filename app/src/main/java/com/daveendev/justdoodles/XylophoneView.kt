package com.daveendev.justdoodles

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.SparseIntArray
import android.view.MotionEvent
import android.view.View
import kotlin.math.sin

/**
 * The xylophone: eight rainbow bars, long and low on the left to short and high on the right.
 * Every finger plays the bar it touches, and sliding across plays each bar in turn, like running
 * a mallet along. The notes are on the same five-note scale as everything else, so any tune
 * sounds nice. The music button plays a song, lighting up each bar, to listen to or copy.
 */
@SuppressLint("ViewConstructor")
class XylophoneView(context: Context, private val host: GameHost) : View(context) {

    private class FloatingNote(val bar: Int, val born: Long)

    private val density = resources.displayMetrics.density
    private val colorNames = resources.getStringArray(R.array.color_names)

    private val bars = Array(BARS) { RectF() }
    private val struckAt = LongArray(BARS) { -HIT_MS }
    /** Which bar each finger is on (-1 for none), so sliding plays each bar once as it's reached. */
    private val fingerBar = SparseIntArray()
    private val floating = ArrayList<FloatingNote>()
    private var lastNoteAt = 0L
    private val listen = RectF()

    /** The next note of the song, or -1 when it isn't playing. */
    private var songStep = -1
    private val songTick = Runnable { playSongStep() }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        color = Palette.INK
    }
    private val rail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 16f * density
        color = 0xFF8D5524.toInt()
    }
    private val noteStem = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 3f * density
    }

    init {
        contentDescription = context.getString(R.string.game_xylophone)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val button = 64f * density
        listen.set(w / 2f - button / 2f, 6f * density, w / 2f + button / 2f, 6f * density + button)
        val top = listen.bottom + 18f * density
        val bottom = h - 18f * density
        val cy = (top + bottom) / 2f
        val maxHalf = (bottom - top) / 2f
        val slot = w / BARS.toFloat()
        val barW = slot * 0.78f
        for (i in 0 until BARS) {
            val half = maxHalf * (1f - 0.42f * i / (BARS - 1))
            val cx = slot * (i + 0.5f)
            bars[i].set(cx - barW / 2f, cy - half, cx + barW / 2f, cy + half)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val x = e.getX(i)
                val y = e.getY(i)
                val slack = 12f * density
                if (x in listen.left - slack..listen.right + slack && y in listen.top - slack..listen.bottom + slack) {
                    fingerBar.put(e.getPointerId(i), -1)
                    toggleSong()
                } else {
                    stopSong()
                    val bar = barAt(x, y)
                    fingerBar.put(e.getPointerId(i), bar)
                    if (bar >= 0) strike(bar, byChild = true)
                }
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) {
                val id = e.getPointerId(i)
                val bar = barAt(e.getX(i), e.getY(i))
                if (bar != fingerBar.get(id, -1)) {
                    fingerBar.put(id, bar)
                    if (bar >= 0) {
                        stopSong()
                        strike(bar, byChild = true)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> fingerBar.delete(e.getPointerId(e.actionIndex))
            MotionEvent.ACTION_CANCEL -> fingerBar.clear()
        }
        return true
    }

    /** The bar under (x, y), counting a little above and below it, or -1. */
    private fun barAt(x: Float, y: Float): Int {
        val i = (x / (width / BARS.toFloat())).toInt()
        if (x < 0f || i !in 0 until BARS) return -1
        val reach = 24f * density
        return if (y >= bars[i].top - reach && y <= bars[i].bottom + reach) i else -1
    }

    private fun strike(bar: Int, byChild: Boolean) {
        val now = SystemClock.uptimeMillis()
        host.sounds.note(FIRST_NOTE + bar, 0.8f)
        if (byChild) {
            // The color's name only for a tap after a pause, so it never drowns out a tune.
            if (now - lastNoteAt > NAME_PAUSE_MS) host.say(colorNames[COLORS[bar]])
            lastNoteAt = now
        }
        struckAt[bar] = now
        floating += FloatingNote(bar, now)
        if (floating.size > 40) floating.removeAt(0)
        invalidate()
    }

    private fun toggleSong() {
        if (songStep >= 0) {
            stopSong()
        } else {
            songStep = 0
            playSongStep()
        }
    }

    private fun playSongStep() {
        if (songStep !in SONG.indices) {
            songStep = -1
            invalidate()
            return
        }
        strike(SONG[songStep], byChild = false)
        postDelayed(songTick, BEATS[songStep] * BEAT_MS)
        songStep++
    }

    private fun stopSong() {
        removeCallbacks(songTick)
        if (songStep >= 0) invalidate()
        songStep = -1
    }

    override fun onDetachedFromWindow() {
        stopSong()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        var busy = songStep >= 0

        // Two wooden rails under the bars, through their nails.
        for (sign in intArrayOf(-1, 1)) {
            val first = bars.first()
            val last = bars.last()
            val pad = 10f * density
            canvas.drawLine(
                first.left - pad, nailY(first, sign), last.right + pad, nailY(last, sign), rail,
            )
        }

        for (i in 0 until BARS) {
            val r = bars[i]
            val age = now - struckAt[i]
            val hit = if (age < HIT_MS) 1f - age.toFloat() / HIT_MS else 0f
            if (hit > 0f) busy = true
            val color = Palette.colors[COLORS[i]]
            val corner = r.width() * 0.3f
            canvas.save()
            canvas.rotate(sin(age / 28f) * 4f * hit, r.centerX(), r.centerY())
            canvas.scale(1f - 0.05f * hit, 1f - 0.05f * hit, r.centerX(), r.centerY())
            fill.color = 0x33000000
            canvas.drawRoundRect(r.left, r.top + 5f * density, r.right, r.bottom + 5f * density, corner, corner, fill)
            fill.color = Palette.mix(color, Color.WHITE, 0.6f * hit)
            canvas.drawRoundRect(r, corner, corner, fill)
            fill.color = 0x44FFFFFF
            val shine = r.width() * 0.16f
            canvas.drawRoundRect(r.left + shine, r.top + shine, r.left + 2.2f * shine, r.bottom - shine, shine, shine, fill)
            canvas.drawRoundRect(r, corner, corner, ink)
            for (sign in intArrayOf(-1, 1)) {
                val nr = r.width() * 0.1f
                fill.color = 0xFFCFD8DC.toInt()
                canvas.drawCircle(r.centerX(), nailY(r, sign), nr, fill)
                canvas.drawCircle(r.centerX(), nailY(r, sign), nr, ink)
            }
            canvas.restore()
        }

        // Little notes float up from every bar that's played.
        floating.removeAll { now - it.born > FLOAT_MS }
        for (n in floating) {
            busy = true
            val t = (now - n.born).toFloat() / FLOAT_MS
            val r = bars[n.bar]
            val x = r.centerX() + sin(t * 9f + n.bar) * 10f * density
            val y = r.top - 14f * density - t * 110f * density
            val color = Palette.colors[COLORS[n.bar]]
            val alpha = (255 * (1f - t)).toInt()
            val head = 8f * density
            fill.color = color
            fill.alpha = alpha
            canvas.drawCircle(x, y, head, fill)
            noteStem.color = color
            noteStem.alpha = alpha
            canvas.drawLine(x + head * 0.85f, y, x + head * 0.85f, y - head * 3f, noteStem)
        }

        // The music button, bobbing while its song plays.
        val size = listen.width() * if (songStep >= 0) 1f + 0.06f * sin(now / 120f) else 1f
        canvas.save()
        canvas.translate(listen.centerX() - size / 2f, listen.centerY() - size / 2f)
        canvas.scale(size / 100f, size / 100f)
        Icons.draw(canvas, Icon.LISTEN, 0, 1)
        canvas.restore()

        if (busy) postInvalidateOnAnimation()
    }

    private fun nailY(r: RectF, sign: Int) = r.centerY() + sign * r.height() / 2f * 0.62f

    private companion object {
        const val BARS = 8
        /** The lowest bar's note in [Sounds.note]: C, then up the five-note scale. */
        const val FIRST_NOTE = 2
        /** Each bar's color, as an index into [Palette.colors] and `color_names`: red to purple. */
        val COLORS = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7)
        const val HIT_MS = 450L
        const val FLOAT_MS = 900L
        const val NAME_PAUSE_MS = 1500L
        const val BEAT_MS = 380L

        /** "Mary Had a Little Lamb" (a traditional tune), as bars, with how many beats each lasts. */
        val SONG = intArrayOf(2, 1, 0, 1, 2, 2, 2, 1, 1, 1, 2, 3, 3, 2, 1, 0, 1, 2, 2, 2, 2, 1, 1, 2, 1, 0)
        val BEATS = intArrayOf(1, 1, 1, 1, 1, 1, 2, 1, 1, 2, 1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 4)
    }
}
