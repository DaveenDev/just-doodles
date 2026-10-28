package com.daveendev.justdoodles

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.os.SystemClock
import android.util.SparseArray
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Rub and see: a big picture is hidden under a gray-blue layer of crayon scribbles. Rubbing with a
 * finger (or several) wipes the scribbles away with a soft rubbing sound. Once about two thirds is
 * clear, the rest melts away by itself with a sparkle, the picture's name is said, and after a
 * moment the scribbles flow back over the next picture. Nothing to fail, nothing to tap.
 */
@SuppressLint("ViewConstructor")
class WipeView(context: Context, private val host: GameHost) : View(context) {

    private enum class State { HIDDEN, REVEALED, COVERING }

    private class Twinkle(val x: Float, val y: Float, val delay: Long, val r: Float, val color: Int)

    private val density = resources.displayMetrics.density
    private val random = Random(System.nanoTime())

    private val deck = ArrayList<ColoringPage>()
    private var page: ColoringPage? = null
    private var picture: Bitmap? = null
    private var fog: Bitmap? = null
    private var fogCanvas: Canvas? = null
    private val card = RectF()
    /** The picture and the scribbles are made at this share of the card's size, to keep them light. */
    private var workScale = 1f

    /** Which cells of a coarse grid over the picture have been rubbed clear. */
    private val cleared = BooleanArray(GRID_W * GRID_H)
    private var clearedCount = 0
    private var milestone = 0

    private var state = State.HIDDEN
    private var stateAt = 0L
    private var found = 0
    private val twinkles = ArrayList<Twinkle>()

    /** Where each finger was last, to rub along the way it moved. */
    private val fingers = SparseArray<FloatArray>()
    private var lastMoveAt = 0L
    private var lastPopAt = 0L

    private val cheerLater = Runnable { host.cheer() }
    private val coverUp = Runnable { cover() }
    private val swapPicture = Runnable {
        nextPicture()
        state = State.HIDDEN
        stateAt = SystemClock.uptimeMillis()
        invalidate()
    }

    private val eraser = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val eraserDot = Paint(eraser).apply { style = Paint.Style.FILL }
    private val scribble = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        color = Palette.INK
    }
    private val clip = Path()
    private val scratch = Path()

    init {
        contentDescription = context.getString(R.string.game_wipe)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        removeCallbacks(cheerLater)
        removeCallbacks(coverUp)
        removeCallbacks(swapPicture)
        val m = 10f * density
        card.set(m, m, w - m, h - m)
        if (card.width() < 1f || card.height() < 1f) return
        workScale = min(1f, sqrt(MAX_PIXELS / (card.width() * card.height())))
        eraser.strokeWidth = BRUSH_DP * density * workScale
        eraserDot.strokeWidth = eraser.strokeWidth
        fingers.clear()
        twinkles.clear()
        makeFog()
        val current = page
        if (current == null) nextPicture() else picture = host.picture(current, workW(), workH())
        state = State.HIDDEN
        invalidate()
    }

    private fun workW() = (card.width() * workScale).toInt().coerceAtLeast(1)
    private fun workH() = (card.height() * workScale).toInt().coerceAtLeast(1)

    /** The next picture from a shuffled pile, so none comes back until all have been seen. */
    private fun nextPicture() {
        if (deck.isEmpty()) {
            deck += host.pictureGroups.flatMap { it.pages }.filter { it.title != null }
            deck.shuffle(random)
            // Not the one just seen, straight after a reshuffle.
            if (deck.size > 1 && deck.last() == page) deck.add(0, deck.removeAt(deck.lastIndex))
        }
        if (deck.isEmpty()) return
        val next = deck.removeAt(deck.lastIndex)
        page = next
        picture = host.picture(next, workW(), workH())
    }

    /** A fresh layer of scribbles over the whole picture, in blues and grays. */
    private fun makeFog() {
        val w = workW()
        val h = workH()
        val bmp = fog?.takeIf { it.width == w && it.height == h } ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(FOG_BASE, PorterDuff.Mode.SRC)
        scribble.strokeWidth = 26f * density * workScale
        val tones = intArrayOf(0xFF9DB1C4.toInt(), 0xFFC5D2DE.toInt(), 0xFFA9BBCB.toInt(), 0xFF8FA5BA.toInt())
        val reach = min(w, h) * 0.22f
        for (n in 0 until 70) {
            scribble.color = tones[n % tones.size]
            var x = random.nextFloat() * w
            var y = random.nextFloat() * h
            scratch.rewind()
            scratch.moveTo(x, y)
            for (k in 0 until 6) {
                val nx = x + (random.nextFloat() - 0.5f) * 2f * reach
                val ny = y + (random.nextFloat() - 0.5f) * 2f * reach
                scratch.quadTo(x + (random.nextFloat() - 0.5f) * 2f * reach, y + (random.nextFloat() - 0.5f) * 2f * reach, nx, ny)
                x = nx
                y = ny
            }
            c.drawPath(scratch, scribble)
        }
        // A few soft white puffs on top, like fog.
        fill.color = 0x30FFFFFF
        repeat(10) {
            c.drawCircle(random.nextFloat() * w, random.nextFloat() * h, (0.08f + random.nextFloat() * 0.1f) * h, fill)
        }
        fog = bmp
        fogCanvas = c
        cleared.fill(false)
        clearedCount = 0
        milestone = 0
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val now = SystemClock.uptimeMillis()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val x = e.getX(i)
                val y = e.getY(i)
                if (state == State.HIDDEN) {
                    fingers.put(e.getPointerId(i), floatArrayOf(x, y))
                    lastMoveAt = now
                    rub(x, y, x, y)
                } else if (now - lastPopAt > 250L) {
                    // Between pictures a tap just plips, so it still answers.
                    lastPopAt = now
                    host.sounds.pop(1.2f)
                }
            }
            MotionEvent.ACTION_MOVE -> if (state == State.HIDDEN) {
                var moved = 0f
                for (i in 0 until e.pointerCount) {
                    val last = fingers[e.getPointerId(i)] ?: continue
                    val x = e.getX(i)
                    val y = e.getY(i)
                    moved = max(moved, hypot(x - last[0], y - last[1]))
                    rub(last[0], last[1], x, y)
                    last[0] = x
                    last[1] = y
                    if (state != State.HIDDEN) break
                }
                val seconds = max(1L, now - lastMoveAt) / 1000f
                lastMoveAt = now
                if (state == State.HIDDEN) host.sounds.scribble(moved / density / seconds / 1200f)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                fingers.remove(e.getPointerId(e.actionIndex))
                if (fingers.size() == 0) host.sounds.stopScribble()
            }
            MotionEvent.ACTION_CANCEL -> {
                fingers.clear()
                host.sounds.stopScribble()
            }
        }
        return true
    }

    /** Wipes the scribbles along a finger's way from (x0, y0) to (x1, y1), in view coordinates. */
    private fun rub(x0: Float, y0: Float, x1: Float, y1: Float) {
        val c = fogCanvas ?: return
        val ax = (x0 - card.left) * workScale
        val ay = (y0 - card.top) * workScale
        val bx = (x1 - card.left) * workScale
        val by = (y1 - card.top) * workScale
        if (ax == bx && ay == by) c.drawCircle(ax, ay, eraser.strokeWidth / 2f, eraserDot)
        else c.drawLine(ax, ay, bx, by, eraser)
        markCleared(ax, ay, bx, by)
        val share = clearedCount.toFloat() / cleared.size
        // A rising plip as the picture comes out, a little reward on the way.
        if (milestone < MILESTONES.size && share >= MILESTONES[milestone]) {
            host.sounds.pop(1f + 0.25f * (milestone + 1))
            milestone++
        }
        if (share >= REVEAL_SHARE) reveal()
        invalidate()
    }

    /** Counts the grid cells whose middle the brush passed over. */
    private fun markCleared(ax: Float, ay: Float, bx: Float, by: Float) {
        val cw = workW().toFloat() / GRID_W
        val ch = workH().toFloat() / GRID_H
        val r = eraser.strokeWidth / 2f
        val gx0 = ((min(ax, bx) - r) / cw).toInt().coerceAtLeast(0)
        val gx1 = ((max(ax, bx) + r) / cw).toInt().coerceAtMost(GRID_W - 1)
        val gy0 = ((min(ay, by) - r) / ch).toInt().coerceAtLeast(0)
        val gy1 = ((max(ay, by) + r) / ch).toInt().coerceAtMost(GRID_H - 1)
        for (gy in gy0..gy1) {
            for (gx in gx0..gx1) {
                val k = gy * GRID_W + gx
                if (cleared[k]) continue
                if (distanceToSegment((gx + 0.5f) * cw, (gy + 0.5f) * ch, ax, ay, bx, by) <= r) {
                    cleared[k] = true
                    clearedCount++
                }
            }
        }
    }

    private fun distanceToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    /** Enough is clear: the rest melts, stars twinkle, the name is said, and soon the next picture comes. */
    private fun reveal() {
        state = State.REVEALED
        stateAt = SystemClock.uptimeMillis()
        fingers.clear()
        host.sounds.stopScribble()
        host.sounds.sparkle()
        val title = page?.title
        if (title != null) host.say(context.getString(R.string.g_wipe_found, title))
        twinkles.clear()
        val colors = Palette.colors
        repeat(14) { n ->
            twinkles += Twinkle(
                card.left + random.nextFloat() * card.width(),
                card.top + random.nextFloat() * card.height(),
                n * 70L,
                (10f + random.nextFloat() * 14f) * density,
                colors[random.nextInt(colors.size - 1)],
            )
        }
        found++
        if (found % CHEER_EVERY == 0) postDelayed(cheerLater, 900L)
        postDelayed(coverUp, REVEAL_HOLD_MS)
    }

    /** The scribbles flow back in over the picture, and the next one is swapped in underneath. */
    private fun cover() {
        host.sounds.whoosh()
        makeFog()
        state = State.COVERING
        stateAt = SystemClock.uptimeMillis()
        postDelayed(swapPicture, COVER_MS)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(cheerLater)
        removeCallbacks(coverUp)
        removeCallbacks(swapPicture)
        host.sounds.stopScribble()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val pic = picture ?: return
        val now = SystemClock.uptimeMillis()
        val t = now - stateAt
        var busy = false

        // The picture does a happy bounce as it's found.
        var s = 1f
        if (state == State.REVEALED && t < BOUNCE_MS) {
            val k = t.toFloat() / BOUNCE_MS
            s = 1f + 0.04f * sin(k * PI.toFloat() * 3f) * (1f - k)
            busy = true
        }
        val corner = 22f * density
        canvas.save()
        canvas.scale(s, s, card.centerX(), card.centerY())
        fill.color = 0x26000000
        canvas.drawRoundRect(card.left, card.top + 6f * density, card.right, card.bottom + 6f * density, corner, corner, fill)
        canvas.save()
        clip.rewind()
        clip.addRoundRect(card, corner, corner, Path.Direction.CW)
        canvas.clipPath(clip)
        fill.color = Palette.PAPER
        canvas.drawRect(card, fill)
        canvas.drawBitmap(pic, null, card, bitmapPaint)
        val fogAlpha = when (state) {
            State.HIDDEN -> 255
            State.REVEALED -> (255 * (1f - t.toFloat() / MELT_MS)).toInt().coerceIn(0, 255)
            State.COVERING -> (255 * t.toFloat() / COVER_MS).toInt().coerceIn(0, 255)
        }
        if (state == State.COVERING || (state == State.REVEALED && fogAlpha > 0)) busy = true
        fog?.let {
            if (fogAlpha > 0) {
                bitmapPaint.alpha = fogAlpha
                canvas.drawBitmap(it, null, card, bitmapPaint)
                bitmapPaint.alpha = 255
            }
        }
        canvas.restore()
        canvas.drawRoundRect(card, corner, corner, ink)
        canvas.restore()

        if (state == State.REVEALED) {
            for (tw in twinkles) {
                val k = (t - tw.delay) / TWINKLE_MS.toFloat()
                if (k < 0f) {
                    busy = true
                    continue
                }
                if (k >= 1f) continue
                busy = true
                star(canvas, tw.x, tw.y, tw.r * sin(k * PI.toFloat()), tw.color)
            }
        }
        if (busy) postInvalidateOnAnimation()
    }

    /** A four-point star, for the sparkles. */
    private fun star(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        if (r <= 0f) return
        scratch.rewind()
        scratch.moveTo(x, y - r)
        scratch.lineTo(x + r * 0.25f, y - r * 0.25f)
        scratch.lineTo(x + r, y)
        scratch.lineTo(x + r * 0.25f, y + r * 0.25f)
        scratch.lineTo(x, y + r)
        scratch.lineTo(x - r * 0.25f, y + r * 0.25f)
        scratch.lineTo(x - r, y)
        scratch.lineTo(x - r * 0.25f, y - r * 0.25f)
        scratch.close()
        fill.color = color
        c.drawPath(scratch, fill)
        c.drawPath(scratch, ink)
    }

    private companion object {
        const val GRID_W = 24
        const val GRID_H = 18
        const val BRUSH_DP = 64f
        /** How much has to be rubbed clear before the rest melts away by itself. */
        const val REVEAL_SHARE = 0.6f
        val MILESTONES = floatArrayOf(0.2f, 0.4f)
        /** The most pixels the picture and the scribbles are made with (a bit over a megapixel). */
        const val MAX_PIXELS = 1_200_000f
        const val MELT_MS = 600L
        const val BOUNCE_MS = 900L
        const val TWINKLE_MS = 900L
        const val COVER_MS = 500L
        /** How long a found picture stays before the scribbles come back. */
        const val REVEAL_HOLD_MS = 3200L
        const val CHEER_EVERY = 3
        const val FOG_BASE = 0xFFB0BEC5.toInt()
    }
}
