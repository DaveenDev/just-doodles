package com.daveendev.justdoodles

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

/**
 * Memory pairs: cards lie face down. Tapping one turns it over and says what's on it. Two the same
 * stay up with a sparkle; two different ones turn back over after a moment, with no buzzer. Once
 * every pair is found there's a cheer, and the next round has one more pair (2, then 3, then 4).
 */
@SuppressLint("ViewConstructor")
class MemoryView(context: Context, private val host: GameHost) : View(context) {

    private class Card(val id: String, val title: String, val picture: Bitmap, val rect: RectF) {
        var up = false
        var matched = false
        /** When it last started turning over, for the flip. */
        var flipAt = -1L
    }

    private val density = resources.displayMetrics.density
    private val random = Random(System.nanoTime())

    private var cards = emptyList<Card>()
    private var first: Card? = null
    /** True while two different cards are showing, before they turn back. */
    private var busy = false
    private var round = 0

    private val turnBack = Runnable {
        for (c in cards) if (c.up && !c.matched) flip(c, up = false)
        first = null
        busy = false
    }
    private val cheerLater = Runnable { host.cheer() }
    private val nextRound = Runnable { newRound() }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        color = Palette.INK
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f * density
        color = 0xFFFFC107.toInt()
    }
    private val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val box = RectF()

    init {
        contentDescription = context.getString(R.string.game_memory)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w > 0 && h > 0) newRound()
    }

    private fun newRound() {
        removeCallbacks(turnBack)
        first = null
        busy = false
        val pairs = min(START_PAIRS + round, MAX_PAIRS)
        // One picture from each of a few different groups, so the pairs look nothing alike.
        val picks = host.pictureGroups.shuffled(random).mapNotNull { g ->
            g.pages.filter { it.title != null }.randomOrNull(random)
        }.take(pairs)
        if (picks.size < 2) return

        val n = picks.size * 2
        val columns = if (n <= 4) n else n / 2
        val rows = (n + columns - 1) / columns
        val gap = 16f * density
        var cw = (width - (columns + 1) * gap) / columns
        var ch = cw * 4f / 3f
        val maxH = (height - (rows + 1) * gap) / rows
        if (ch > maxH) {
            ch = maxH
            cw = ch * 3f / 4f
        }
        val left = (width - columns * cw - (columns - 1) * gap) / 2f
        val top = (height - rows * ch - (rows - 1) * gap) / 2f
        val pictureSize = (cw * 0.86f).toInt().coerceAtLeast(1)
        val faces = picks.flatMap { p ->
            val picture = host.picture(p, pictureSize, pictureSize)
            listOf(p to picture, p to picture)
        }.shuffled(random)
        cards = faces.mapIndexed { i, (p, picture) ->
            val x = left + (i % columns) * (cw + gap)
            val y = top + (i / columns) * (ch + gap)
            Card(p.id, p.title ?: "", picture, RectF(x, y, x + cw, y + ch))
        }
        host.sounds.whoosh()
        invalidate()
    }

    private fun flip(card: Card, up: Boolean) {
        card.up = up
        card.flipAt = SystemClock.uptimeMillis()
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val m = e.actionMasked
        if (m != MotionEvent.ACTION_DOWN && m != MotionEvent.ACTION_POINTER_DOWN) return true
        if (busy) return true
        val x = e.getX(e.actionIndex)
        val y = e.getY(e.actionIndex)
        val card = cards.firstOrNull { it.rect.contains(x, y) } ?: return true
        if (card.up) return true
        flip(card, up = true)
        host.sounds.pop(1.1f)
        if (card.title.isNotEmpty()) host.say("${card.title}!")

        val other = first
        if (other == null) {
            first = card
        } else if (other.id == card.id) {
            other.matched = true
            card.matched = true
            first = null
            host.sounds.sparkle()
            if (cards.all { it.matched }) {
                round++
                postDelayed(cheerLater, 600L)
                postDelayed(nextRound, NEXT_ROUND_MS)
            }
        } else {
            busy = true
            postDelayed(turnBack, TURN_BACK_MS)
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        var turning = false
        for (card in cards) {
            val t = if (card.flipAt < 0) 1f else ((now - card.flipAt) / FLIP_MS.toFloat()).coerceIn(0f, 1f)
            if (t < 1f) turning = true
            // Squeeze to nothing and back out, showing the new side for the second half.
            val squeeze = abs(1f - 2f * t)
            val faceUp = if (t < 0.5f) !card.up else card.up
            val r = card.rect
            val cx = r.centerX()
            box.set(cx - r.width() / 2f * squeeze, r.top, cx + r.width() / 2f * squeeze, r.bottom)
            val corner = 14f * density
            if (faceUp) {
                fill.color = Color.WHITE
                canvas.drawRoundRect(box, corner, corner, fill)
                val s = card.picture.width * squeeze
                val pTop = r.centerY() - card.picture.height / 2f
                canvas.drawBitmap(card.picture, null, RectF(cx - s / 2f, pTop, cx + s / 2f, pTop + card.picture.height), bitmapPaint)
                canvas.drawRoundRect(box, corner, corner, if (card.matched && t >= 1f) glow else ink)
            } else {
                fill.color = 0xFF5C6BC0.toInt()
                canvas.drawRoundRect(box, corner, corner, fill)
                canvas.drawRoundRect(box, corner, corner, ink)
                if (squeeze > 0.3f) {
                    mark.textSize = r.width() * 0.45f * squeeze
                    canvas.drawText("?", cx, r.centerY() - (mark.descent() + mark.ascent()) / 2f, mark)
                }
            }
        }
        if (turning) postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(turnBack)
        removeCallbacks(cheerLater)
        removeCallbacks(nextRound)
        super.onDetachedFromWindow()
    }

    private companion object {
        const val START_PAIRS = 2
        const val MAX_PAIRS = 4
        const val FLIP_MS = 260L
        const val TURN_BACK_MS = 1100L
        const val NEXT_ROUND_MS = 2600L
    }
}
