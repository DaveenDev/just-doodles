package com.daveendev.justdoodles

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * "Where's the cow?": two pictures (three, once that's easy), and the voice asks for one of them.
 * The right one bounces and gets a cheer. Tapping another one isn't wrong, it just wiggles and
 * says what it is ("That's the pig!"), then the question comes again.
 */
@SuppressLint("ViewConstructor")
class FindItView(context: Context, private val host: GameHost) : View(context) {

    private class Card(val page: ColoringPage, val title: String, val picture: Bitmap, val rect: RectF) {
        var wiggleAt = -1L
    }

    private val density = resources.displayMetrics.density
    private val random = Random(System.nanoTime())

    private var cards = emptyList<Card>()
    private var target: Card? = null
    private var found = false
    private var round = 0
    private var roundAt = 0L
    private var foundAt = 0L
    /** How many times this round's question has been asked since the last tap. */
    private var asks = 0
    /** Pictures from the last few rounds, kept out of the next ones. */
    private val recent = ArrayDeque<String>()

    private val askAgain = Runnable { ask() }
    private val cheerLater = Runnable { host.cheer() }
    private val nextRound = Runnable { newRound() }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        color = Palette.INK
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f * density
        color = 0xFFFFC107.toInt()
    }
    private val clip = Path()

    init {
        contentDescription = context.getString(R.string.game_find_it)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w > 0 && h > 0) newRound()
    }

    private fun newRound() {
        removeCallbacks(askAgain)
        val n = if (round < EASY_ROUNDS) 2 else 3
        // One picture from each of n different groups, so they look nothing alike.
        val groups = host.pictureGroups.shuffled(random)
        val picks = ArrayList<ColoringPage>()
        for (g in groups) {
            if (picks.size == n) break
            val choices = g.pages.filter { it.title != null && it.id !in recent }.ifEmpty { g.pages.filter { it.title != null } }
            if (choices.isNotEmpty()) picks += choices.random(random)
        }
        if (picks.size < 2) return
        for (p in picks) {
            recent.addLast(p.id)
            if (recent.size > RECENT) recent.removeFirst()
        }

        val gap = 18f * density
        val cw = min((width - (picks.size + 1) * gap) / picks.size, (height - 2 * gap) * 4f / 3f)
        val ch = cw * 3f / 4f
        val left = (width - picks.size * cw - (picks.size - 1) * gap) / 2f
        val top = (height - ch) / 2f
        cards = picks.mapIndexed { i, p ->
            val x = left + i * (cw + gap)
            Card(p, p.title ?: "", host.picture(p, cw.toInt(), ch.toInt()), RectF(x, top, x + cw, top + ch))
        }
        target = cards.random(random)
        found = false
        roundAt = SystemClock.uptimeMillis()
        asks = 0
        host.sounds.whoosh()
        postDelayed(askAgain, ENTER_MS)
        invalidate()
    }

    /** "Where's the cow?", again after a while if nothing is tapped, but not forever. */
    private fun ask() {
        val t = target ?: return
        if (found) return
        host.say(context.getString(R.string.say_where_is, t.title.lowercase()))
        asks++
        removeCallbacks(askAgain)
        if (asks < 3) postDelayed(askAgain, ASK_AGAIN_MS)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val m = e.actionMasked
        if (m != MotionEvent.ACTION_DOWN && m != MotionEvent.ACTION_POINTER_DOWN) return true
        val now = SystemClock.uptimeMillis()
        if (found || now - roundAt < ENTER_MS) return true
        val x = e.getX(e.actionIndex)
        val y = e.getY(e.actionIndex)
        val card = cards.firstOrNull { it.rect.contains(x, y) } ?: return true
        removeCallbacks(askAgain)
        if (card == target) {
            found = true
            foundAt = now
            round++
            host.sounds.sparkle()
            host.say(card.title)
            postDelayed(cheerLater, CHEER_DELAY_MS)
            postDelayed(nextRound, NEXT_ROUND_MS)
        } else {
            card.wiggleAt = now
            host.sounds.pop(0.8f)
            host.say(context.getString(R.string.say_thats_the, card.title.lowercase()))
            asks = 0
            postDelayed(askAgain, ASK_AFTER_OTHER_MS)
        }
        invalidate()
        return true
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(askAgain)
        removeCallbacks(cheerLater)
        removeCallbacks(nextRound)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        var busy = false
        val corner = 20f * density
        cards.forEachIndexed { i, c ->
            val r = c.rect
            // Slide in from the right, one after the other.
            val enter = ((now - roundAt - i * 90L).toFloat() / ENTER_MS).coerceIn(0f, 1f)
            if (enter < 1f) busy = true
            val ease = 1f - (1f - enter) * (1f - enter) * (1f - enter)
            canvas.save()
            canvas.translate((1f - ease) * width, 0f)

            if (c.wiggleAt >= 0 && now - c.wiggleAt < WIGGLE_MS) {
                val k = (now - c.wiggleAt).toFloat() / WIGGLE_MS
                canvas.rotate(sin(k * PI.toFloat() * 6f) * 6f * (1f - k), r.centerX(), r.centerY())
                busy = true
            }
            val isTarget = c == target
            if (found && isTarget && now - foundAt < BOUNCE_MS) {
                val k = (now - foundAt).toFloat() / BOUNCE_MS
                val s = 1f + 0.1f * sin(k * PI.toFloat() * 3f) * (1f - k)
                canvas.scale(s, s, r.centerX(), r.centerY())
                busy = true
            }
            // Once the right one is found, the others step back.
            val dim = found && !isTarget
            if (dim) canvas.saveLayerAlpha(r.left - corner, r.top - corner, r.right + corner, r.bottom + corner, 90)

            fill.color = 0x26000000
            canvas.drawRoundRect(r.left, r.top + 6f * density, r.right, r.bottom + 6f * density, corner, corner, fill)
            canvas.save()
            clip.rewind()
            clip.addRoundRect(r, corner, corner, Path.Direction.CW)
            canvas.clipPath(clip)
            canvas.drawBitmap(c.picture, null, r, bitmapPaint)
            canvas.restore()
            if (found && isTarget) canvas.drawRoundRect(r, corner, corner, glow)
            canvas.drawRoundRect(r, corner, corner, ink)

            if (dim) canvas.restore()
            canvas.restore()
        }
        if (busy) postInvalidateOnAnimation()
    }

    private companion object {
        /** Rounds with two pictures, before there are three. */
        const val EASY_ROUNDS = 5
        const val RECENT = 12
        const val ENTER_MS = 450L
        const val WIGGLE_MS = 550L
        const val BOUNCE_MS = 900L
        const val ASK_AGAIN_MS = 7000L
        const val ASK_AFTER_OTHER_MS = 1800L
        const val CHEER_DELAY_MS = 900L
        const val NEXT_ROUND_MS = 2800L
    }
}
