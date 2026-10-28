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
 * Count with me: one to five of the same picture pop up on white cards. Each tap lights one up with
 * a ring and a dice-dot badge, plays the next note up the scale and says its number ("One", "Two"...).
 * Tapping one that is already counted just says its number again. After the last one the voice
 * says the total ("Three apples!"), everything hops, there is a cheer, and a new set arrives.
 * Nothing can be wrong, and every finger can count at once.
 */
@SuppressLint("ViewConstructor")
class TapCountView(context: Context, private val host: GameHost) : View(context) {

    /** A picture to count, with the words for one of it and for several. */
    private class Thing(val page: ColoringPage, val one: String, val many: String)

    private class Item(val rect: RectF, val picture: Bitmap) {
        /** 0 until it is counted, then the number it was given. */
        var order = 0
        var tapAt = -1L
    }

    private val density = resources.displayMetrics.density
    private val random = Random(System.nanoTime())
    private val numberWords = resources.getStringArray(R.array.number_words)
    private val things = loadThings()
    /** Each picture is colored once per size, then reused for every copy of it. */
    private val pictures = HashMap<String, Bitmap>()
    /** Pictures from the last few sets, kept out of the next ones. */
    private val recent = ArrayDeque<String>()

    private var items = emptyList<Item>()
    private var thing: Thing? = null
    private var counted = 0
    private var done = false
    private var doneAt = 0L
    private var roundAt = 0L
    private var round = 0
    private var lastCount = 0

    private val sayTotal = Runnable {
        val t = thing ?: return@Runnable
        val n = items.size
        if (n in 1..numberWords.size) {
            host.say(context.getString(R.string.say_count_total, numberWords[n - 1], if (n == 1) t.one else t.many))
        }
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
    private val clip = Path()

    init {
        contentDescription = context.getString(R.string.game_count)
    }

    /** The listed things whose pictures exist; if almost none do, any picture the games use. */
    private fun loadThings(): List<Thing> {
        val listed = resources.getStringArray(R.array.g_count_things).mapNotNull { line ->
            val page = host.page(line.substringBefore('=')) ?: return@mapNotNull null
            val words = line.substringAfter('=', "")
            val one = words.substringBefore('|')
            Thing(page, one, words.substringAfter('|', one + "s"))
        }
        if (listed.size >= 3) return listed
        return host.pictureGroups.flatMap { it.pages }.mapNotNull { p ->
            p.title?.let { Thing(p, it.lowercase(), plural(it.lowercase())) }
        }
    }

    private fun plural(word: String) = when {
        word.endsWith("s") || word.endsWith("x") || word.endsWith("ch") || word.endsWith("sh") -> word + "es"
        word.length > 1 && word.endsWith("y") && word[word.length - 2] !in "aeiou" -> word.dropLast(1) + "ies"
        else -> word + "s"
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        pictures.clear()
        if (w > 0 && h > 0) newRound()
    }

    private fun newRound() {
        removeCallbacks(sayTotal)
        removeCallbacks(cheerLater)
        removeCallbacks(nextRound)
        if (things.isEmpty() || width == 0 || height == 0) return

        val pool = things.filter { it.page.id !in recent }.ifEmpty { things }
        val t = pool.random(random)
        recent.addLast(t.page.id)
        if (recent.size > RECENT) recent.removeFirst()
        thing = t

        // Starts with one to three, then up to five.
        val most = min(MAX_COUNT, 2 + round)
        var n = 1 + random.nextInt(most)
        if (n == lastCount && most > 1) n = n % most + 1
        lastCount = n

        // Rows of cards as big as fit, centered.
        val gap = 20f * density
        var columns = 1
        var best = 0f
        for (c in 1..n) {
            val rows = (n + c - 1) / c
            val s = min((width - (c + 1) * gap) / c, (height - (rows + 1) * gap) / rows)
            if (s > best) {
                best = s
                columns = c
            }
        }
        val size = min(best, min(width, height) * 0.62f).coerceAtLeast(1f)
        val rows = (n + columns - 1) / columns
        val top = (height - rows * size - (rows - 1) * gap) / 2f
        val picture = pictures.getOrPut(t.page.id) { host.picture(t.page, size.toInt().coerceAtLeast(1), size.toInt().coerceAtLeast(1)) }
        items = List(n) { i ->
            val row = i / columns
            val inRow = min(columns, n - row * columns)
            val left = (width - inRow * size - (inRow - 1) * gap) / 2f
            val x = left + (i % columns) * (size + gap)
            val y = top + row * (size + gap)
            Item(RectF(x, y, x + size, y + size), picture)
        }
        counted = 0
        done = false
        roundAt = SystemClock.uptimeMillis()
        host.sounds.whoosh()
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val m = e.actionMasked
        if (m != MotionEvent.ACTION_DOWN && m != MotionEvent.ACTION_POINTER_DOWN) return true
        val now = SystemClock.uptimeMillis()
        if (done) return true
        val x = e.getX(e.actionIndex)
        val y = e.getY(e.actionIndex)
        val index = items.indexOfFirst { it.rect.contains(x, y) }
        if (index < 0 || now - roundAt - index * STAGGER_MS < TOUCHABLE_MS) return true
        val item = items[index]
        item.tapAt = now
        if (item.order == 0) {
            counted++
            item.order = counted
            host.sounds.note((counted - 1) * 2)
        } else {
            host.sounds.pop(1.1f)
        }
        host.say(numberWords[(item.order - 1).coerceIn(0, numberWords.size - 1)])
        if (counted == items.size) {
            done = true
            doneAt = now + DONE_DELAY_MS
            postDelayed(sayTotal, DONE_DELAY_MS)
            postDelayed(cheerLater, DONE_DELAY_MS + 500L)
            postDelayed(nextRound, NEXT_ROUND_MS)
            round++
        }
        invalidate()
        return true
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(sayTotal)
        removeCallbacks(cheerLater)
        removeCallbacks(nextRound)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val corner = 20f * density
        items.forEachIndexed { i, item ->
            val r = item.rect
            val enter = ((now - roundAt - i * STAGGER_MS).toFloat() / ENTER_MS).coerceIn(0f, 1f)
            if (enter <= 0f) return@forEachIndexed
            // Pops in with a little overshoot.
            val back = enter - 1f
            var scale = 1f + 2.70158f * back * back * back + 1.70158f * back * back
            if (item.tapAt >= 0 && now - item.tapAt < BOUNCE_MS) {
                val k = (now - item.tapAt).toFloat() / BOUNCE_MS
                scale *= 1f + 0.12f * sin(k * PI.toFloat())
            }
            if (done) {
                val k = (now - doneAt - i * HOP_STAGGER_MS).toFloat() / BOUNCE_MS
                if (k in 0f..1f) scale *= 1f + 0.14f * sin(k * PI.toFloat())
            }
            // Waiting ones float gently; counted ones settle.
            val bob = sin(now / 500f + i * 1.3f) * 4f * density * (if (item.order == 0) 1f else 0.3f)

            canvas.save()
            canvas.translate(0f, bob)
            canvas.scale(scale, scale, r.centerX(), r.centerY())
            fill.color = 0x26000000
            canvas.drawRoundRect(r.left, r.top + 6f * density, r.right, r.bottom + 6f * density, corner, corner, fill)
            canvas.save()
            clip.rewind()
            clip.addRoundRect(r, corner, corner, Path.Direction.CW)
            canvas.clipPath(clip)
            canvas.drawBitmap(item.picture, null, r, bitmapPaint)
            canvas.restore()
            if (item.order > 0) canvas.drawRoundRect(r, corner, corner, glow)
            canvas.drawRoundRect(r, corner, corner, ink)
            if (item.order > 0) drawBadge(canvas, r, item.order)
            canvas.restore()
        }
        if (isAttachedToWindow) postInvalidateOnAnimation()
    }

    /** A round badge on the card's corner with [n] dots like a dice, so the number can be seen too. */
    private fun drawBadge(canvas: Canvas, r: RectF, n: Int) {
        val br = r.width() * 0.17f
        val cx = r.right - br * 0.55f
        val cy = r.top + br * 0.55f
        fill.color = Palette.colors[((n - 1) * 2) % Palette.colors.size]
        canvas.drawCircle(cx, cy, br, fill)
        fill.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(cx, cy, br * 0.8f, fill)
        canvas.drawCircle(cx, cy, br, ink)
        fill.color = Palette.INK
        val d = br * 0.4f
        val dots = when (n) {
            1 -> listOf(0f to 0f)
            2 -> listOf(-1f to -1f, 1f to 1f)
            3 -> listOf(-1f to -1f, 0f to 0f, 1f to 1f)
            4 -> listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f)
            else -> listOf(-1f to -1f, 1f to -1f, 0f to 0f, -1f to 1f, 1f to 1f)
        }
        for ((dx, dy) in dots) canvas.drawCircle(cx + dx * d, cy + dy * d, br * 0.16f, fill)
    }

    private companion object {
        const val MAX_COUNT = 5
        const val RECENT = 8
        const val ENTER_MS = 400L
        const val STAGGER_MS = 110L
        /** How long after a card starts to appear before it can be tapped. */
        const val TOUCHABLE_MS = 250L
        const val BOUNCE_MS = 450L
        const val HOP_STAGGER_MS = 120L
        const val DONE_DELAY_MS = 1000L
        const val NEXT_ROUND_MS = 4200L
    }
}
