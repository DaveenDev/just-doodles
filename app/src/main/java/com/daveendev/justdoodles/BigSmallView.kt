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
 * Big and small: the same picture twice, one big and one small. The voice asks "Where is the big
 * cow?" (or the small one, turn and turn about). The right one bounces, lights up and gets a
 * sparkle; the other one is not wrong, it just wiggles and says what it is ("That is the small
 * one."), then the question comes again. Every few rounds there is a cheer, and there is never a
 * fail screen.
 */
@SuppressLint("ViewConstructor")
class BigSmallView(context: Context, private val host: GameHost) : View(context) {

    private class Card(val picture: Bitmap, val rect: RectF, val big: Boolean) {
        var wiggleAt = -1L
    }

    private val density = resources.displayMetrics.density
    private val random = Random(System.nanoTime())
    private val pages = host.pictureGroups.flatMap { it.pages }.filter { it.title != null }
    /** Pictures from the last few rounds, kept out of the next ones. */
    private val recent = ArrayDeque<String>()

    private var cards = emptyList<Card>()
    private var title = ""
    private var askBig = true
    private var found = false
    private var foundAt = 0L
    private var roundAt = 0L
    private var round = 0
    private var wins = 0
    /** How many times this round's question has been asked since the last tap. */
    private var asks = 0

    private val askAgain = Runnable { ask() }
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
        strokeWidth = 10f * density
        color = 0xFFFFC107.toInt()
    }
    private val clip = Path()

    init {
        contentDescription = context.getString(R.string.game_big_small)
        askBig = random.nextBoolean()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w > 0 && h > 0) newRound()
    }

    private fun newRound() {
        removeCallbacks(askAgain)
        removeCallbacks(cheerLater)
        removeCallbacks(nextRound)
        if (pages.isEmpty() || width == 0 || height == 0) return

        val page = pages.filter { it.id !in recent }.ifEmpty { pages }.random(random)
        recent.addLast(page.id)
        if (recent.size > RECENT) recent.removeFirst()
        title = (page.title ?: "").lowercase()
        // Big and small take turns, so both words get used.
        askBig = !askBig

        val bigSide = min(height * 0.82f, width * 0.44f)
        val smallSide = bigSide * SMALL_SHARE
        val bigLeft = random.nextBoolean()
        val bigCx = width * (if (bigLeft) 0.27f else 0.73f)
        val smallCx = width * (if (bigLeft) 0.75f else 0.25f)
        // Both stand on the same ground line.
        val bottom = height / 2f + bigSide / 2f
        val big = Card(
            host.picture(page, bigSide.toInt().coerceAtLeast(1), bigSide.toInt().coerceAtLeast(1)),
            RectF(bigCx - bigSide / 2f, bottom - bigSide, bigCx + bigSide / 2f, bottom),
            big = true,
        )
        val small = Card(
            host.picture(page, smallSide.toInt().coerceAtLeast(1), smallSide.toInt().coerceAtLeast(1)),
            RectF(smallCx - smallSide / 2f, bottom - smallSide, smallCx + smallSide / 2f, bottom),
            big = false,
        )
        cards = if (bigLeft) listOf(big, small) else listOf(small, big)
        found = false
        asks = 0
        roundAt = SystemClock.uptimeMillis()
        host.sounds.whoosh()
        postDelayed(askAgain, ENTER_MS)
        invalidate()
    }

    /** "Where is the big cow?", again after a while if nothing is tapped, but not forever. */
    private fun ask() {
        if (found || cards.isEmpty()) return
        val line = if (askBig) R.string.g_big_small_where_big else R.string.g_big_small_where_small
        host.say(context.getString(line, title))
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
        // The small one gets a little extra room around it: it's a small target for a small finger.
        val slop = 12f * density
        val card = cards.firstOrNull { it.rect.contains(x, y) }
            ?: cards.firstOrNull { RectF(it.rect).apply { inset(-slop, -slop) }.contains(x, y) }
            ?: return true
        removeCallbacks(askAgain)
        if (card.big == askBig) {
            found = true
            foundAt = now
            round++
            wins++
            host.sounds.sparkle()
            host.sounds.note(if (card.big) 2 else 7)
            val line = if (card.big) R.string.g_big_small_yes_big else R.string.g_big_small_yes_small
            host.say(context.getString(line, title))
            if (wins % CHEER_EVERY == 0) postDelayed(cheerLater, CHEER_DELAY_MS)
            postDelayed(nextRound, NEXT_ROUND_MS)
        } else {
            card.wiggleAt = now
            host.sounds.pop(0.8f)
            val line = if (card.big) R.string.g_big_small_that_big else R.string.g_big_small_that_small
            host.say(context.getString(line))
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
        val corner = 20f * density
        cards.forEachIndexed { i, c ->
            val r = c.rect
            val enter = ((now - roundAt - i * 120L).toFloat() / ENTER_MS).coerceIn(0f, 1f)
            if (enter <= 0f) return@forEachIndexed
            // Pops up from the ground line with a little overshoot.
            val back = enter - 1f
            var scale = 1f + 2.70158f * back * back * back + 1.70158f * back * back
            canvas.save()
            if (c.wiggleAt >= 0 && now - c.wiggleAt < WIGGLE_MS) {
                val k = (now - c.wiggleAt).toFloat() / WIGGLE_MS
                canvas.rotate(sin(k * PI.toFloat() * 6f) * 6f * (1f - k), r.centerX(), r.bottom)
            }
            val isTarget = c.big == askBig
            if (found && isTarget && now - foundAt < BOUNCE_MS) {
                val k = (now - foundAt).toFloat() / BOUNCE_MS
                scale *= 1f + 0.1f * sin(k * PI.toFloat() * 3f) * (1f - k)
            }
            canvas.scale(scale, scale, r.centerX(), r.bottom)
            // Once the right one is found, the other steps back.
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
        if (isAttachedToWindow) postInvalidateOnAnimation()
    }

    private companion object {
        /** How wide the small one is next to the big one. */
        const val SMALL_SHARE = 0.4f
        const val RECENT = 12
        const val ENTER_MS = 450L
        const val WIGGLE_MS = 550L
        const val BOUNCE_MS = 900L
        const val ASK_AGAIN_MS = 7000L
        const val ASK_AFTER_OTHER_MS = 2200L
        const val CHEER_EVERY = 4
        const val CHEER_DELAY_MS = 900L
        const val NEXT_ROUND_MS = 3200L
    }
}
