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
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * My body: a big friendly child. Tapping a part lights it up and says its name ("Nose!"). After
 * a few taps the voice asks for parts ("Where's the nose?"): the right one sparkles and gets a
 * cheer, any other one says what it is ("That's the ear!"). Then it's back to tapping freely.
 * The parts are the `<title>`s inside `assets/learn/body.svg`.
 */
@SuppressLint("ViewConstructor")
class BodyView(context: Context, private val host: GameHost) : View(context) {

    /** Every shape with the same name, lit up together: both eyes, both hands. */
    private class Part(val name: String, val outlines: List<Path>, val pieces: List<ColoringPage.Part>)

    private val density = resources.displayMetrics.density
    private val random = Random(System.nanoTime())
    private val page = host.page(BODY_TITLE)

    private var picture: Bitmap? = null
    private val box = RectF()
    private var parts = emptyList<Part>()

    private var lit: Part? = null
    private var litAt = 0L
    private var taps = 0

    private var quiz = false
    private var target: Part? = null
    private var lastTarget: Part? = null
    private var found = false
    private var round = 0
    private var asks = 0

    private val askAgain = Runnable { ask() }
    private val cheerLater = Runnable { host.cheer() }
    private val nextRound = Runnable { newRound() }

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val glowFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val sparkle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFC107.toInt() }

    init {
        contentDescription = context.getString(R.string.lesson_body)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val p = page ?: return
        if (w <= 0 || h <= 0) return
        // The picture as big as fits at 4:3, in the middle.
        val pw = min(w.toFloat(), h * 4f / 3f)
        val ph = pw * 3f / 4f
        box.set((w - pw) / 2f, (h - ph) / 2f, (w + pw) / 2f, (h + ph) / 2f)
        val bw = pw.roundToInt()
        val bh = ph.roundToInt()
        val pieces = p.partsFor(bw, bh)
        parts = pieces.groupBy { it.name }.map { (name, same) ->
            Part(name, same.map { it.area.boundaryPath }, same)
        }
        picture = colorIn(p, pieces, bw, bh)
        invalidate()
    }

    /**
     * The child colored in like a child, not like a crayon box: skin, brown hair, a red shirt,
     * blue shorts and yellow shoes. Bigger parts go first, so eyes and noses sit on top.
     */
    private fun colorIn(p: ColoringPage, pieces: List<ColoringPage.Part>, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Palette.PAPER)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val biggestEye = pieces.filter { it.name == "Eye" }.maxOfOrNull { it.size } ?: 0L
        for (piece in pieces.sortedByDescending { it.size }) {
            fill.color = when (piece.name) {
                "Hair" -> 0xFF8D5524.toInt()
                "Tummy" -> 0xFFE53935.toInt()
                "Shorts" -> 0xFF2962FF.toInt()
                "Foot" -> 0xFFFDD835.toInt()
                "Mouth" -> 0xFFFF6FB0.toInt()
                "Nose" -> 0xFFF4B183.toInt()
                // An eye is a white with a dark middle: the smaller circle inside.
                "Eye" -> if (piece.size * 2 < biggestEye) Palette.INK else Palette.PAPER
                else -> SKIN
            }
            c.drawPath(piece.area.boundaryPath, fill)
        }
        p.drawOutline(c, w, h)
        return bmp
    }

    /** The smallest part under (x, y), so the nose wins over the head around it. */
    private fun partAt(x: Float, y: Float): Part? {
        val px = (x - box.left).roundToInt()
        val py = (y - box.top).roundToInt()
        var best: Part? = null
        var bestSize = Long.MAX_VALUE
        for (part in parts) {
            for (piece in part.pieces) {
                if (piece.size < bestSize && piece.area.contains(px, py)) {
                    best = part
                    bestSize = piece.size
                }
            }
        }
        return best
    }

    /** True if [part] is under (x, y) or within a fingertip of it: little fingers aren't precise. */
    private fun near(part: Part, x: Float, y: Float): Boolean {
        val reach = FINGER_DP * density
        val px = x - box.left
        val py = y - box.top
        for (k in 0..8) {
            val r = if (k == 0) 0f else reach
            val a = k * PI / 4
            val qx = (px + r * cos(a)).roundToInt()
            val qy = (py + r * sin(a)).roundToInt()
            if (part.pieces.any { it.area.contains(qx, qy) }) return true
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val m = e.actionMasked
        if (m != MotionEvent.ACTION_DOWN && m != MotionEvent.ACTION_POINTER_DOWN) return true
        val x = e.getX(e.actionIndex)
        val y = e.getY(e.actionIndex)
        val now = SystemClock.uptimeMillis()
        if (!quiz) {
            val part = partAt(x, y) ?: return true
            light(part, now)
            host.sounds.pop(1.1f)
            host.say("${part.name}!")
            if (++taps >= LOOK_TAPS) {
                taps = 0
                quiz = true
                round = 0
                postDelayed(nextRound, QUIZ_DELAY_MS)
            }
            return true
        }
        val t = target ?: return true
        if (found) return true
        removeCallbacks(askAgain)
        if (near(t, x, y)) {
            found = true
            light(t, now)
            round++
            host.sounds.sparkle()
            host.say("${t.name}!")
            postDelayed(cheerLater, CHEER_DELAY_MS)
            postDelayed(nextRound, NEXT_ROUND_MS)
        } else {
            val part = partAt(x, y)
            if (part != null) {
                light(part, now)
                host.sounds.pop(0.8f)
                host.say(context.getString(R.string.say_thats_the, part.name.lowercase()))
            }
            asks = 0
            postDelayed(askAgain, ASK_AFTER_OTHER_MS)
        }
        return true
    }

    private fun light(part: Part, now: Long) {
        lit = part
        litAt = now
        invalidate()
    }

    private fun newRound() {
        removeCallbacks(askAgain)
        if (round >= QUIZ_ROUNDS || parts.isEmpty()) {
            quiz = false
            target = null
            return
        }
        val choices = parts.filter { it != lastTarget && it.name !in NOT_ASKED }.ifEmpty { parts }
        target = choices.random(random).also { lastTarget = it }
        found = false
        asks = 0
        ask()
    }

    /** "Where's the nose?", again after a while if nothing is tapped, but not forever. */
    private fun ask() {
        val t = target ?: return
        if (!quiz || found) return
        host.say(context.getString(R.string.say_where_your, t.name.lowercase()))
        asks++
        removeCallbacks(askAgain)
        if (asks < 3) postDelayed(askAgain, ASK_AGAIN_MS)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(askAgain)
        removeCallbacks(cheerLater)
        removeCallbacks(nextRound)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val pic = picture ?: return
        canvas.drawBitmap(pic, null, box, bitmapPaint)
        val part = lit ?: return
        val now = SystemClock.uptimeMillis()
        val k = (now - litAt).toFloat() / LIGHT_MS
        if (k >= 1f) return
        // A soft golden glow that pulses once and fades.
        val strength = sin(k * PI.toFloat()).coerceAtLeast(0f)
        canvas.save()
        canvas.translate(box.left, box.top)
        glowFill.color = 0xFFFFF59D.toInt()
        glowFill.alpha = (110 * strength).roundToInt()
        glowLine.color = 0xFFFFC107.toInt()
        glowLine.alpha = (255 * strength).roundToInt()
        glowLine.strokeWidth = 7f * density
        for (outline in part.outlines) {
            canvas.drawPath(outline, glowFill)
            canvas.drawPath(outline, glowLine)
        }
        canvas.restore()
        if (found && part == target) {
            // Sparkles around the found part.
            val b = part.pieces.first().area.bounds
            val cx = box.left + b.exactCenterX()
            val cy = box.top + b.exactCenterY()
            val r = (b.width().coerceAtLeast(b.height()) / 2f + 24f * density) * (0.8f + 0.4f * k)
            for (i in 0 until 8) {
                val a = i * PI / 4 + k * 1.5
                sparkle.alpha = (255 * (1f - k)).roundToInt()
                canvas.drawCircle(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat(), 6f * density * (1f - k / 2f), sparkle)
            }
        }
        postInvalidateOnAnimation()
    }

    private companion object {
        const val BODY_TITLE = "My body"
        const val SKIN = 0xFFFFCC99.toInt()
        /** Clothes can be tapped and named, but the quiz only asks for the body. */
        val NOT_ASKED = setOf("Shorts")
        /** Taps on the picture before the voice starts asking. */
        const val LOOK_TAPS = 6
        const val QUIZ_ROUNDS = 3
        const val FINGER_DP = 18f
        const val LIGHT_MS = 1400L
        const val QUIZ_DELAY_MS = 1600L
        const val ASK_AGAIN_MS = 7000L
        const val ASK_AFTER_OTHER_MS = 1800L
        const val CHEER_DELAY_MS = 900L
        const val NEXT_ROUND_MS = 2800L
    }
}
