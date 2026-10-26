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
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * A Learn topic: first look, then a little quiz. One big card at a time is named out loud; the
 * arrows (or a swipe) go to the next one. After a few cards come three rounds of "Where's red?"
 * about the cards just seen, played like Where is it?: the right one bounces and gets a cheer,
 * any other one wiggles and says what it is. Then back to looking, at the next card.
 */
@SuppressLint("ViewConstructor")
class LearnView(context: Context, private val host: GameHost, private val deck: List<LearnItem>) : View(context) {

    private class Card(val item: LearnItem, val rect: RectF) {
        var wiggleAt = -1L
        var bounceAt = -1L
        /** When counting started on this card, or -1 before it has. */
        var countAt = -1L
        var counted = 0
    }

    private val density = resources.displayMetrics.density
    private val random = Random(System.nanoTime())

    private var quiz = false
    private var index = 0
    /** The cards looked at since the last quiz. */
    private val batch = ArrayList<LearnItem>()
    private var look: Card? = null
    /** Which way the card slid in: 1 from the right, -1 from the left. */
    private var slide = 1
    private var shownAt = 0L

    private var cards = emptyList<Card>()
    private var target: Card? = null
    private var lastTarget: LearnItem? = null
    private var found = false
    private var round = 0
    private var rightAnswers = 0
    private var foundAt = 0L
    private var asks = 0

    private val prevButton = RectF()
    private val nextButton = RectF()
    private var downX = 0f
    private var downY = 0f
    private var tracking = false

    private val sayLook = Runnable { look?.let { present(it) } }
    private val askAgain = Runnable { ask() }
    private val cheerLater = Runnable { host.cheer() }
    private val nextRound = Runnable { newRound() }

    /** Pictures for the cards on screen, so a coloring page is only colored in once. */
    private val pictures = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>) = size > MAX_PICTURES
    }
    private val state = CardState { page, w, h ->
        // Big cards are colored in at a smaller size and drawn scaled up, which keeps it quick.
        val k = min(1f, MAX_PICTURE_WIDTH / w.toFloat())
        val pw = (w * k).roundToInt().coerceAtLeast(1)
        val ph = (h * k).roundToInt().coerceAtLeast(1)
        // Always the neatly colored-in picture, not the child's own coloring of that page:
        // a half-colored or scribbled cow is no good for learning what a cow is.
        pictures.getOrPut("${page.id}@${pw}x$ph") { AutoColor.render(page, pw, ph) }
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        strokeJoin = Paint.Join.ROUND
        color = Palette.INK
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f * density
        color = 0xFFFFC107.toInt()
    }
    private val clip = Path()
    private val arrow = Path()

    init {
        contentDescription = context.getString(R.string.menu_learn)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w <= 0 || h <= 0 || deck.isEmpty()) return
        if (quiz) newRound() else showLook(index, 1)
    }

    // ---- Looking ----------------------------------------------------------------------------

    private fun showLook(i: Int, dir: Int) {
        removeCallbacks(sayLook)
        removeCallbacks(askAgain)
        quiz = false
        index = Math.floorMod(i, deck.size)
        val item = deck[index]
        if (dir > 0 && item !in batch) batch += item

        val gap = 16f * density
        val button = min(height * 0.24f, 96f * density)
        val cw = min((height - 2 * gap) * 4f / 3f, width - 2 * (button + 2 * gap))
        val ch = cw * 3f / 4f
        val rect = RectF((width - cw) / 2f, (height - ch) / 2f, (width + cw) / 2f, (height + ch) / 2f)
        prevButton.set(rect.left - gap - button, height / 2f - button / 2f, rect.left - gap, height / 2f + button / 2f)
        nextButton.set(rect.right + gap, height / 2f - button / 2f, rect.right + gap + button, height / 2f + button / 2f)

        look = Card(item, rect)
        slide = dir
        shownAt = SystemClock.uptimeMillis()
        host.sounds.whoosh()
        postDelayed(sayLook, ENTER_MS)
        invalidate()
    }

    /** Names the card, and counts its things if it has any. */
    private fun present(card: Card) {
        val item = card.item
        val now = SystemClock.uptimeMillis()
        host.say(item.look)
        if (item.note >= 0) host.sounds.note(item.note)
        if (item.count > 0) {
            card.countAt = now
            card.counted = 0
        }
        card.bounceAt = now
        invalidate()
    }

    private fun next() {
        val pool = batch.filter { it.inQuiz }
        if (batch.size >= LOOK_CARDS && pool.isNotEmpty() && deck.count { it.inQuiz } >= 2) startQuiz()
        else showLook(index + 1, 1)
    }

    private fun previous() = showLook(index - 1, -1)

    // ---- The quiz ---------------------------------------------------------------------------

    private fun startQuiz() {
        quiz = true
        round = 0
        newRound()
    }

    private fun newRound() {
        removeCallbacks(askAgain)
        removeCallbacks(sayLook)
        val pool = batch.filter { it.inQuiz }
        if (round >= QUIZ_ROUNDS || pool.isEmpty()) {
            batch.clear()
            showLook(index + 1, 1)
            return
        }
        val n = if (rightAnswers < EASY_ANSWERS) 2 else 3
        val answer = pool.filter { it != lastTarget }.ifEmpty { pool }.random(random)
        lastTarget = answer
        // The other choices: cards just seen first, then any others, but never a look-alike
        // of the answer (green and light green), and no two alike among themselves.
        val others = ((pool - answer).shuffled(random) + (deck.filter { it.inQuiz } - pool.toSet() - answer).shuffled(random))
            .filter { it.kind == null || it.kind != answer.kind }
            .distinctBy { it.kind ?: it.other }
            .take(n - 1)
        val picks = (others + answer).shuffled(random)

        val gap = 18f * density
        val cw = min((width - (picks.size + 1) * gap) / picks.size, (height - 2 * gap) * 4f / 3f)
        val ch = cw * 3f / 4f
        val left = (width - picks.size * cw - (picks.size - 1) * gap) / 2f
        val top = (height - ch) / 2f
        cards = picks.mapIndexed { i, item ->
            val x = left + i * (cw + gap)
            Card(item, RectF(x, top, x + cw, top + ch))
        }
        target = cards.first { it.item == answer }
        found = false
        shownAt = SystemClock.uptimeMillis()
        asks = 0
        host.sounds.whoosh()
        postDelayed(askAgain, ENTER_MS)
        invalidate()
    }

    /** "Where's red?", again after a while if nothing is tapped, but not forever. */
    private fun ask() {
        val t = target ?: return
        if (!quiz || found) return
        host.say(t.item.ask)
        asks++
        removeCallbacks(askAgain)
        if (asks < 3) postDelayed(askAgain, ASK_AGAIN_MS)
    }

    private fun tapQuiz(x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        if (found || now - shownAt < ENTER_MS) return
        val card = cards.firstOrNull { it.rect.contains(x, y) } ?: return
        removeCallbacks(askAgain)
        if (card == target) {
            found = true
            foundAt = now
            round++
            rightAnswers++
            host.sounds.sparkle()
            host.say(card.item.answer)
            if (card.item.note >= 0) host.sounds.note(card.item.note)
            postDelayed(cheerLater, CHEER_DELAY_MS)
            postDelayed(nextRound, NEXT_ROUND_MS)
        } else {
            card.wiggleAt = now
            host.sounds.pop(0.8f)
            host.say(card.item.other)
            asks = 0
            postDelayed(askAgain, ASK_AFTER_OTHER_MS)
        }
        invalidate()
    }

    // ---- Touch ------------------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = e.getX(e.actionIndex)
        val y = e.getY(e.actionIndex)
        if (quiz) {
            val m = e.actionMasked
            if (m == MotionEvent.ACTION_DOWN || m == MotionEvent.ACTION_POINTER_DOWN) tapQuiz(x, y)
            return true
        }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = x
                downY = y
                tracking = true
            }
            MotionEvent.ACTION_POINTER_DOWN -> tracking = false // Several fingers: just a pat on the screen.
            MotionEvent.ACTION_UP -> if (tracking) {
                tracking = false
                val dx = x - downX
                val dy = y - downY
                when {
                    abs(dx) > SWIPE_DP * density && abs(dx) > abs(dy) -> if (dx < 0) next() else previous()
                    SystemClock.uptimeMillis() - shownAt < ENTER_MS / 2 -> Unit
                    prevButton.contains(downX, downY) -> { host.sounds.pop(0.9f); previous() }
                    nextButton.contains(downX, downY) -> { host.sounds.pop(1.1f); next() }
                    look?.rect?.contains(downX, downY) == true -> look?.let { present(it) }
                }
            }
            MotionEvent.ACTION_CANCEL -> tracking = false
        }
        return true
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(sayLook)
        removeCallbacks(askAgain)
        removeCallbacks(cheerLater)
        removeCallbacks(nextRound)
        pictures.clear()
        super.onDetachedFromWindow()
    }

    // ---- Drawing ----------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        var busy = false
        if (quiz) {
            cards.forEachIndexed { i, c ->
                // Slide in from the right, one after the other.
                val enter = ((now - shownAt - i * 90L).toFloat() / ENTER_MS).coerceIn(0f, 1f)
                if (drawCard(canvas, c, enter, 1, now, dim = found && c != target, glowing = found && c == target)) busy = true
            }
        } else {
            drawArrow(canvas, prevButton, left = true)
            drawArrow(canvas, nextButton, left = false)
            look?.let { c ->
                val enter = ((now - shownAt).toFloat() / ENTER_MS).coerceIn(0f, 1f)
                if (drawCard(canvas, c, enter, slide, now, dim = false, glowing = false)) busy = true
            }
        }
        if (busy) postInvalidateOnAnimation()
    }

    /** Draws one card, sliding in from [dir] while [enter] goes from 0 to 1. True while it's still moving. */
    private fun drawCard(canvas: Canvas, c: Card, enter: Float, dir: Int, now: Long, dim: Boolean, glowing: Boolean): Boolean {
        var busy = enter < 1f
        val r = c.rect
        val corner = 20f * density
        val ease = 1f - (1f - enter) * (1f - enter) * (1f - enter)
        canvas.save()
        canvas.translate((1f - ease) * width * dir, 0f)

        if (c.wiggleAt >= 0 && now - c.wiggleAt < WIGGLE_MS) {
            val k = (now - c.wiggleAt).toFloat() / WIGGLE_MS
            canvas.rotate(sin(k * PI.toFloat() * 6f) * 6f * (1f - k), r.centerX(), r.centerY())
            busy = true
        }
        val bounceAt = if (glowing) foundAt else c.bounceAt
        if (bounceAt >= 0 && now - bounceAt < BOUNCE_MS) {
            val k = (now - bounceAt).toFloat() / BOUNCE_MS
            val s = 1f + 0.06f * sin(k * PI.toFloat() * 3f) * (1f - k)
            canvas.scale(s, s, r.centerX(), r.centerY())
            busy = true
        }
        if (dim) canvas.saveLayerAlpha(r.left - corner, r.top - corner, r.right + corner, r.bottom + corner, 90)

        fill.color = 0x26000000
        canvas.drawRoundRect(r.left, r.top + 6f * density, r.right, r.bottom + 6f * density, corner, corner, fill)
        fill.color = Palette.PAPER
        canvas.drawRoundRect(r, corner, corner, fill)
        canvas.save()
        clip.rewind()
        clip.addRoundRect(r, corner, corner, Path.Direction.CW)
        canvas.clipPath(clip)
        state.quiz = quiz
        state.shown = shownFor(c, now)
        if (state.shown < c.item.count) busy = true
        c.item.art(canvas, r, state)
        canvas.restore()
        if (glowing) canvas.drawRoundRect(r, corner, corner, glow)
        canvas.drawRoundRect(r, corner, corner, ink)

        if (dim) canvas.restore()
        canvas.restore()
        return busy
    }

    /** How many of a counting card's things show: one more every beat, each with a pop. */
    private fun shownFor(c: Card, now: Long): Int {
        val n = c.item.count
        if (n == 0 || quiz) return n
        if (c.countAt < 0) return 0
        val shown = (1 + (now - c.countAt) / COUNT_BEAT_MS).toInt().coerceAtMost(n)
        if (shown > c.counted) {
            c.counted = shown
            host.sounds.note(shown - 1, 0.5f)
        }
        return shown
    }

    /** A big round arrow button. */
    private fun drawArrow(canvas: Canvas, r: RectF, left: Boolean) {
        val cx = r.centerX()
        val cy = r.centerY()
        val radius = r.width() / 2f
        fill.color = 0x26000000
        canvas.drawCircle(cx, cy + 4f * density, radius, fill)
        fill.color = 0xFFFF8F00.toInt()
        canvas.drawCircle(cx, cy, radius, fill)
        canvas.drawCircle(cx, cy, radius, ink)
        val s = if (left) -1f else 1f
        arrow.rewind()
        arrow.moveTo(cx + s * radius * 0.5f, cy)
        arrow.lineTo(cx - s * radius * 0.15f, cy - radius * 0.48f)
        arrow.lineTo(cx - s * radius * 0.15f, cy + radius * 0.48f)
        arrow.close()
        fill.color = Palette.PAPER
        canvas.drawPath(arrow, fill)
        canvas.drawPath(arrow, ink)
    }

    private companion object {
        /** Cards looked at before a quiz. */
        const val LOOK_CARDS = 5
        const val QUIZ_ROUNDS = 3
        /** Right answers with two cards to choose from, before there are three. */
        const val EASY_ANSWERS = 5
        const val MAX_PICTURES = 8
        const val MAX_PICTURE_WIDTH = 900f
        const val SWIPE_DP = 60f
        const val ENTER_MS = 450L
        const val WIGGLE_MS = 550L
        const val BOUNCE_MS = 900L
        const val COUNT_BEAT_MS = 600L
        const val ASK_AGAIN_MS = 7000L
        const val ASK_AFTER_OTHER_MS = 1800L
        const val CHEER_DELAY_MS = 900L
        const val NEXT_ROUND_MS = 2800L
    }
}
