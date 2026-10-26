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
import kotlin.random.Random

/**
 * Peekaboo: a picture hides behind a cloud. Rubbing wipes the cloud away; once most of it is
 * gone, the rest melts, the picture bounces and its name is said ("Peekaboo! Cow!"). A tap
 * then brings the cloud back over the next picture.
 */
@SuppressLint("ViewConstructor")
class PeekabooView(context: Context, private val host: GameHost) : View(context) {

    private enum class State { HIDDEN, REVEALED, COVERING }

    private val density = resources.displayMetrics.density
    private val random = Random(System.nanoTime())

    private val deck = ArrayList<ColoringPage>()
    private var page: ColoringPage? = null
    private var picture: Bitmap? = null
    private var fog: Bitmap? = null
    private var fogCanvas: Canvas? = null
    private val card = RectF()

    /** Which cells of a coarse grid over the picture have been rubbed clear. */
    private val cleared = BooleanArray(GRID_W * GRID_H)
    private var clearedCount = 0

    private var state = State.HIDDEN
    private var stateAt = 0L

    /** Where each finger was last, to rub along the way it moved. */
    private val fingers = SparseArray<FloatArray>()
    private var lastMoveAt = 0L

    private val eraser = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = BRUSH_DP * density
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val eraserDot = Paint(eraser).apply { style = Paint.Style.FILL }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        color = Palette.INK
    }
    private val clip = Path()

    private val cheerLater = Runnable { host.cheer() }
    private val swapPicture = Runnable {
        nextPicture()
        state = State.HIDDEN
        stateAt = SystemClock.uptimeMillis()
        invalidate()
    }

    init {
        contentDescription = context.getString(R.string.game_peekaboo)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val m = 12f * density
        val scale = min((w - 2 * m) / 4f, (h - 2 * m) / 3f)
        card.set(w / 2f - 2f * scale, h / 2f - 1.5f * scale, w / 2f + 2f * scale, h / 2f + 1.5f * scale)
        if (card.width() < 1f || card.height() < 1f) return
        makeFog()
        val current = page
        if (current == null) nextPicture()
        else picture = host.picture(current, card.width().toInt(), card.height().toInt())
        state = State.HIDDEN
    }

    /** The next picture from a shuffled pile, so none comes back until all have been seen. */
    private fun nextPicture() {
        if (deck.isEmpty()) {
            deck += host.pictureGroups.flatMap { it.pages }
            deck.shuffle(random)
            // Not the one just seen, straight after a reshuffle.
            if (deck.size > 1 && deck.last() == page) deck.add(0, deck.removeAt(deck.lastIndex))
        }
        val next = deck.removeAt(deck.lastIndex)
        page = next
        picture = host.picture(next, card.width().toInt(), card.height().toInt())
    }

    /** A fresh cloud over the whole picture: soft blue, with lighter puffs and a few twinkles. */
    private fun makeFog() {
        val w = card.width().toInt()
        val h = card.height().toInt()
        val bmp = fog?.takeIf { it.width == w && it.height == h } ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(FOG, PorterDuff.Mode.SRC)
        fill.color = 0x55FFFFFF
        repeat(16) {
            c.drawCircle(random.nextFloat() * w, random.nextFloat() * h, (0.08f + random.nextFloat() * 0.12f) * h, fill)
        }
        fill.color = 0xCCFFFFFF.toInt()
        repeat(7) {
            val x = random.nextFloat() * w
            val y = random.nextFloat() * h
            val r = (6f + random.nextFloat() * 8f) * density
            clip.rewind()
            clip.moveTo(x, y - r)
            clip.lineTo(x + r * 0.25f, y - r * 0.25f)
            clip.lineTo(x + r, y)
            clip.lineTo(x + r * 0.25f, y + r * 0.25f)
            clip.lineTo(x, y + r)
            clip.lineTo(x - r * 0.25f, y + r * 0.25f)
            clip.lineTo(x - r, y)
            clip.lineTo(x - r * 0.25f, y - r * 0.25f)
            clip.close()
            c.drawPath(clip, fill)
        }
        fog = bmp
        fogCanvas = c
        cleared.fill(false)
        clearedCount = 0
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val now = SystemClock.uptimeMillis()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val x = e.getX(i)
                val y = e.getY(i)
                when (state) {
                    State.HIDDEN -> {
                        fingers.put(e.getPointerId(i), floatArrayOf(x, y))
                        lastMoveAt = now
                        rub(x, y, x, y)
                    }
                    State.REVEALED -> if (now - stateAt > NEXT_PAUSE_MS) coverUp(now)
                    State.COVERING -> Unit
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

    /** Wipes the cloud along a finger's way from (x0, y0) to (x1, y1), in view coordinates. */
    private fun rub(x0: Float, y0: Float, x1: Float, y1: Float) {
        val c = fogCanvas ?: return
        val ax = x0 - card.left
        val ay = y0 - card.top
        val bx = x1 - card.left
        val by = y1 - card.top
        if (ax == bx && ay == by) c.drawCircle(ax, ay, eraser.strokeWidth / 2f, eraserDot)
        else c.drawLine(ax, ay, bx, by, eraser)
        markCleared(ax, ay, bx, by)
        if (clearedCount >= cleared.size * REVEAL_SHARE) reveal()
        invalidate()
    }

    /** Counts the grid cells whose middle the brush passed over. */
    private fun markCleared(ax: Float, ay: Float, bx: Float, by: Float) {
        val cw = card.width() / GRID_W
        val ch = card.height() / GRID_H
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

    private fun reveal() {
        state = State.REVEALED
        stateAt = SystemClock.uptimeMillis()
        fingers.clear()
        host.sounds.stopScribble()
        host.sounds.sparkle()
        val title = page?.title
        host.say(if (title != null) context.getString(R.string.say_peekaboo, title) else context.getString(R.string.say_game_peekaboo))
        postDelayed(cheerLater, CHEER_DELAY_MS)
    }

    /** The cloud rolls back in over the picture, and the next one is swapped in underneath. */
    private fun coverUp(now: Long) {
        removeCallbacks(cheerLater)
        host.sounds.whoosh()
        makeFog()
        state = State.COVERING
        stateAt = now
        postDelayed(swapPicture, COVER_MS)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(cheerLater)
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
            s = 1f + 0.07f * sin(k * PI.toFloat() * 3f) * (1f - k)
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

        if (busy) postInvalidateOnAnimation()
    }

    private companion object {
        const val GRID_W = 24
        const val GRID_H = 18
        const val BRUSH_DP = 76f
        /** How much of the cloud has to be rubbed away before the rest melts. */
        const val REVEAL_SHARE = 0.55f
        const val MELT_MS = 600L
        const val BOUNCE_MS = 900L
        const val COVER_MS = 350L
        const val CHEER_DELAY_MS = 1500L
        /** After a picture is found, taps wait this long before the next one, so a rub can't skip it. */
        const val NEXT_PAUSE_MS = 1200L
        const val FOG = 0xFF90CAF9.toInt()
    }
}
