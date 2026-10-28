package com.daveendev.justdoodles

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Match the shadows: a dark silhouette sits at the top and three colored pictures wait below. The
 * voice asks whose shadow it is. Tapping the matching picture sends it flying up onto the shadow,
 * which lights up with a sparkle while the voice says the name. Tapping another picture is no
 * failure: it wobbles and the voice says "Try again!" (after two tries the right one starts to
 * bounce a little, as a hint). Then a cheer and a new shadow.
 */
@SuppressLint("ViewConstructor")
class ShadowsView(context: Context, private val host: GameHost) : View(context) {

    private class Choice(val title: String, val picture: Bitmap, val home: RectF, val answer: Boolean) {
        var wobbleAt = -1L
    }

    private val density = resources.displayMetrics.density
    private val random = Random(System.nanoTime())
    /** Pictures from the last few rounds, kept out of the next ones. */
    private val recent = ArrayDeque<String>()

    private var choices = emptyList<Choice>()
    private var answer: Choice? = null
    private val shadowRect = RectF()
    private var shadow: Bitmap? = null
    private var solved = false
    private var solvedAt = 0L
    private var roundAt = 0L
    private var round = 0
    private var wrongs = 0
    private var asks = 0

    private val askAgain = Runnable { ask() }
    /** The picture has landed on the shadow: sparkle and say who it is. */
    private val landed = Runnable {
        host.sounds.sparkle()
        answer?.let { host.say(context.getString(R.string.g_shadows_yes, it.title.lowercase())) }
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
        strokeWidth = 10f * density
        color = 0xFFFFC107.toInt()
    }
    private val clip = Path()
    private val box = RectF()

    init {
        contentDescription = context.getString(R.string.game_shadows)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w > 0 && h > 0) newRound()
    }

    private fun newRound() {
        removeCallbacks(askAgain)
        removeCallbacks(landed)
        removeCallbacks(cheerLater)
        removeCallbacks(nextRound)
        if (width == 0 || height == 0) return

        // One picture from each of three different groups, so they look nothing alike.
        val picks = ArrayList<ColoringPage>()
        for (g in host.pictureGroups.shuffled(random)) {
            if (picks.size == CHOICES) break
            val named = g.pages.filter { it.title != null }
            val fresh = named.filter { it.id !in recent }.ifEmpty { named }
            val page = fresh.randomOrNull(random) ?: continue
            if (picks.none { it.title == page.title }) picks += page
        }
        if (picks.size < 2) return
        for (p in picks) {
            recent.addLast(p.id)
            if (recent.size > RECENT) recent.removeFirst()
        }

        // The shadow on top, the choices in a row underneath, the whole lot centered.
        val gap = 16f * density
        val avail = height - 3 * gap
        val shadowSide = min(avail * 0.55f, width * 0.5f)
        val choiceSide = min((width - (picks.size + 1) * gap) / picks.size, avail - shadowSide)
        val top = (height - (shadowSide + gap + choiceSide)) / 2f
        val shadowSize = shadowSide.toInt().coerceAtLeast(1)
        shadowRect.set((width - shadowSide) / 2f, top, (width + shadowSide) / 2f, top + shadowSide)

        val target = picks.random(random)
        val targetPicture = host.picture(target, shadowSize, shadowSize)
        shadow = silhouette(targetPicture)
        val choiceSize = choiceSide.toInt().coerceAtLeast(1)
        val left = (width - picks.size * choiceSide - (picks.size - 1) * gap) / 2f
        val choiceTop = top + shadowSide + gap
        choices = picks.shuffled(random).mapIndexed { i, p ->
            val x = left + i * (choiceSide + gap)
            val isTarget = p === target
            Choice(
                p.title ?: "",
                // The right one is drawn from the big picture, so it doesn't blur as it flies up.
                if (isTarget) targetPicture else host.picture(p, choiceSize, choiceSize),
                RectF(x, choiceTop, x + choiceSide, choiceTop + choiceSide),
                isTarget,
            )
        }
        answer = choices.first { it.answer }
        solved = false
        wrongs = 0
        asks = 0
        roundAt = SystemClock.uptimeMillis()
        host.sounds.whoosh()
        postDelayed(askAgain, ENTER_MS)
        invalidate()
    }

    /**
     * The picture as a flat dark shape. The paper around it (found by flooding in from the edges,
     * so white eyes and belly patches inside the outline stay part of the shape) becomes clear.
     */
    private fun silhouette(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        fun lightest(i: Int) = min(min(Color.red(px[i]), Color.green(px[i])), Color.blue(px[i]))

        val outside = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var head = 0
        var tail = 0
        fun seed(i: Int) {
            if (!outside[i] && lightest(i) >= PAPER_MIN) {
                outside[i] = true
                queue[tail++] = i
            }
        }
        for (x in 0 until w) {
            seed(x)
            seed((h - 1) * w + x)
        }
        for (y in 0 until h) {
            seed(y * w)
            seed(y * w + w - 1)
        }
        while (head < tail) {
            val i = queue[head++]
            val x = i % w
            if (x > 0) seed(i - 1)
            if (x < w - 1) seed(i + 1)
            if (i >= w) seed(i - w)
            if (i < w * (h - 1)) seed(i + w)
        }
        val out = IntArray(w * h)
        for (i in out.indices) {
            // Outside: only the faint fringe of the outline's soft edge is kept, so the edge is smooth.
            val alpha = if (outside[i]) ((255 - lightest(i)) * 255 / (255 - PAPER_MIN)).coerceIn(0, 255) else 255
            out[i] = (alpha shl 24) or SHADOW_RGB
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    /** "Whose shadow is this?", again after a while if nothing is tapped, but not forever. */
    private fun ask() {
        if (solved || choices.isEmpty()) return
        host.say(context.getString(R.string.g_shadows_whose))
        asks++
        removeCallbacks(askAgain)
        if (asks < 3) postDelayed(askAgain, ASK_AGAIN_MS)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val m = e.actionMasked
        if (m != MotionEvent.ACTION_DOWN && m != MotionEvent.ACTION_POINTER_DOWN) return true
        val now = SystemClock.uptimeMillis()
        if (solved || now - roundAt < ENTER_MS) return true
        val x = e.getX(e.actionIndex)
        val y = e.getY(e.actionIndex)
        val choice = choices.firstOrNull { it.home.contains(x, y) } ?: return true
        removeCallbacks(askAgain)
        if (choice.answer) {
            solved = true
            solvedAt = now
            round++
            host.sounds.whoosh()
            postDelayed(landed, FLY_MS)
            postDelayed(cheerLater, FLY_MS + CHEER_AFTER_MS)
            postDelayed(nextRound, NEXT_ROUND_MS)
        } else {
            choice.wobbleAt = now
            wrongs++
            host.sounds.pop(0.8f)
            host.say(context.getString(R.string.g_shadows_try_again))
        }
        invalidate()
        return true
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(askAgain)
        removeCallbacks(landed)
        removeCallbacks(cheerLater)
        removeCallbacks(nextRound)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val corner = 20f * density
        val fly = if (solved) ((now - solvedAt).toFloat() / FLY_MS).coerceIn(0f, 1f) else 0f
        val ease = 1f - (1f - fly) * (1f - fly) * (1f - fly)

        // The shadow: a soft gray-blue panel with the dark shape on it, fading as the picture lands.
        val enterShadow = ((now - roundAt).toFloat() / ENTER_MS).coerceIn(0f, 1f)
        if (enterShadow > 0f) {
            val s = 0.85f + 0.15f * enterShadow
            canvas.save()
            canvas.scale(s, s, shadowRect.centerX(), shadowRect.centerY())
            fill.color = SHADOW_PANEL
            canvas.drawRoundRect(shadowRect, corner, corner, fill)
            shadow?.let {
                bitmapPaint.alpha = ((1f - ease) * 255).toInt()
                canvas.drawBitmap(it, null, shadowRect, bitmapPaint)
                bitmapPaint.alpha = 255
            }
            canvas.drawRoundRect(shadowRect, corner, corner, ink)
            canvas.restore()
        }

        choices.forEachIndexed { i, c ->
            val enter = ((now - roundAt - i * 110L).toFloat() / ENTER_MS).coerceIn(0f, 1f)
            if (enter <= 0f) return@forEachIndexed
            val back = enter - 1f
            val pop = 1f + 2.70158f * back * back * back + 1.70158f * back * back
            val r = c.home
            val isAnswer = c.answer
            if (solved && isAnswer) return@forEachIndexed // Drawn last, on top.

            canvas.save()
            if (c.wobbleAt >= 0 && now - c.wobbleAt < WOBBLE_MS) {
                val k = (now - c.wobbleAt).toFloat() / WOBBLE_MS
                canvas.rotate(sin(k * PI.toFloat() * 6f) * 7f * (1f - k), r.centerX(), r.centerY())
            }
            var scale = pop
            // After a couple of wrong tries the right one gives a gentle hint.
            if (!solved && isAnswer && wrongs >= HINT_AFTER) scale *= 1f + 0.05f * sin(now / 220f)
            canvas.scale(scale, scale, r.centerX(), r.centerY())
            val dim = solved
            if (dim) canvas.saveLayerAlpha(r.left - corner, r.top - corner, r.right + corner, r.bottom + corner, 90)
            drawCard(canvas, c.picture, r, corner)
            if (dim) canvas.restore()
            canvas.restore()
        }

        // The matching picture flies up onto the shadow, then glows.
        val a = answer
        if (solved && a != null) {
            val h = a.home
            box.set(
                h.left + (shadowRect.left - h.left) * ease,
                h.top + (shadowRect.top - h.top) * ease,
                h.right + (shadowRect.right - h.right) * ease,
                h.bottom + (shadowRect.bottom - h.bottom) * ease,
            )
            // A little hop on the way up.
            val hop = sin(fly * PI.toFloat()) * 18f * density
            canvas.save()
            canvas.translate(0f, -hop)
            drawCard(canvas, a.picture, box, corner)
            if (fly >= 1f) {
                canvas.drawRoundRect(box, corner, corner, glow)
                drawSparkles(canvas, now - solvedAt - FLY_MS)
            }
            canvas.restore()
        }
        if (isAttachedToWindow) postInvalidateOnAnimation()
    }

    /** A white card with [picture] in it, a soft shadow beneath and the ink outline. */
    private fun drawCard(canvas: Canvas, picture: Bitmap, r: RectF, corner: Float) {
        fill.color = 0x26000000
        canvas.drawRoundRect(r.left, r.top + 6f * density, r.right, r.bottom + 6f * density, corner, corner, fill)
        canvas.save()
        clip.rewind()
        clip.addRoundRect(r, corner, corner, Path.Direction.CW)
        canvas.clipPath(clip)
        canvas.drawBitmap(picture, null, r, bitmapPaint)
        canvas.restore()
        canvas.drawRoundRect(r, corner, corner, ink)
    }

    /** Little golden dots that burst out of the lit-up shadow and fade. */
    private fun drawSparkles(canvas: Canvas, sinceLanded: Long) {
        val t = sinceLanded / SPARKLE_MS.toFloat()
        if (t < 0f || t >= 1f) return
        val cx = shadowRect.centerX()
        val cy = shadowRect.centerY()
        val reach = shadowRect.width() * (0.55f + 0.35f * t)
        fill.color = Color.argb(((1f - t) * 255).toInt(), 0xFF, 0xC1, 0x07)
        for (k in 0 until 10) {
            val ang = k * 2.0 * PI / 10
            canvas.drawCircle(
                cx + reach * cos(ang).toFloat(),
                cy + reach * 0.8f * sin(ang).toFloat(),
                shadowRect.width() * 0.035f * (1f - t),
                fill,
            )
        }
    }

    private companion object {
        const val CHOICES = 3
        const val RECENT = 12
        /** Pixels this light in every color count as paper. */
        const val PAPER_MIN = 200
        const val SHADOW_RGB = 0x37474F
        const val SHADOW_PANEL = 0xFFCFD8DC.toInt()
        const val HINT_AFTER = 2
        const val ENTER_MS = 450L
        const val FLY_MS = 650L
        const val WOBBLE_MS = 550L
        const val SPARKLE_MS = 900L
        const val CHEER_AFTER_MS = 500L
        const val ASK_AGAIN_MS = 8000L
        const val NEXT_ROUND_MS = 4000L
    }
}
