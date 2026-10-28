package com.daveendev.justdoodles

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Blocks: colored blocks (squares, bricks, arches and, last, a roof) wait above the ground, one at
 * a time as a ghost. Any tap drops the next one onto the tower with a soft squash and a note, each
 * a step higher up the scale. After eight, the tower sways, cheers and tumbles gently into a pile,
 * then it all starts again. Nothing can be built wrong; the tower only ever goes up.
 */
@SuppressLint("ViewConstructor")
class BlocksView(context: Context, private val host: GameHost) : View(context) {

    private enum class Shape { SQUARE, WIDE, TALL, ARCH, ROOF }
    private enum class Phase { BUILD, SWAY, TUMBLE, CLEAR }

    private class Block(val shape: Shape, val color: Int, val w: Float, val h: Float, val jitter: Float, val restY: Float) {
        var x = 0f
        var y = 0f
        var vx = 0f
        var vy = 0f
        var rot = 0f
        var vrot = 0f
        var tilt = 0f
        var falling = false
        var landedAt = -1L
        var onGround = false
        var settled = false
        var bounces = 0
    }

    private val density = resources.displayMetrics.density
    private val random = Random(System.nanoTime())

    private var blocks = emptyList<Block>()
    private var phase = Phase.BUILD
    private var phaseT = 0f
    private var settledT = 0f
    private var nextIndex = 0
    private var landed = 0
    private var pending = 0
    private var lastFrame = 0L
    private var lastPopAt = 0L
    private var unit = 0f
    private var groundY = 0f
    private var centreX = 0f
    private var hoverY = 0f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f * density
        color = Palette.INK
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val hole = Path()
    private val rect = RectF()

    init {
        contentDescription = context.getString(R.string.game_blocks)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w <= 0 || h <= 0) return
        groundY = h * 0.86f
        centreX = w / 2f
        hoverY = h * 0.12f
        newTower()
        invalidate()
    }

    /** Plans the eight blocks, sized so the finished tower fits the screen, and starts building. */
    private fun newTower() {
        val shapes = ArrayList<Shape>()
        shapes += Shape.WIDE
        val pool = listOf(Shape.SQUARE, Shape.WIDE, Shape.ARCH, Shape.SQUARE, Shape.TALL, Shape.ARCH)
        repeat(BLOCK_COUNT - 2) { shapes += pool[random.nextInt(pool.size)] }
        shapes += Shape.ROOF
        val total = shapes.sumOf { unitHeight(it).toDouble() }.toFloat()
        unit = min(width * 0.14f, groundY * 0.66f / total)
        val colors = TOWER_COLORS.shuffled(random)
        var top = groundY
        blocks = shapes.mapIndexed { i, shape ->
            val bw = unitWidth(shape) * unit
            val bh = unitHeight(shape) * unit
            top -= bh
            Block(shape, Palette.colors[colors[i % colors.size]], bw, bh, (random.nextFloat() - 0.5f) * unit * 0.2f, top + bh / 2f)
        }
        phase = Phase.BUILD
        phaseT = 0f
        settledT = 0f
        nextIndex = 0
        landed = 0
        pending = 0
    }

    private fun unitWidth(s: Shape) = when (s) {
        Shape.SQUARE -> 1.5f
        Shape.WIDE -> 2.4f
        Shape.TALL -> 1.1f
        Shape.ARCH -> 2.0f
        Shape.ROOF -> 2.4f
    }

    private fun unitHeight(s: Shape) = when (s) {
        Shape.SQUARE -> 1.5f
        Shape.WIDE -> 1.0f
        Shape.TALL -> 2.0f
        Shape.ARCH -> 1.3f
        Shape.ROOF -> 1.3f
    }

    // ---- Touch ------------------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val m = e.actionMasked
        if (m != MotionEvent.ACTION_DOWN && m != MotionEvent.ACTION_POINTER_DOWN) return true
        if (phase == Phase.BUILD) {
            // Taps while a block is still falling wait their turn (a couple at most).
            if (nextIndex + pending < BLOCK_COUNT && pending < 2) pending++
        } else {
            // The tower is busy tumbling: a tap still gets a soft sound.
            host.sounds.pop(0.8f + random.nextFloat() * 0.6f)
        }
        return true
    }

    // ---- Moving -----------------------------------------------------------------------------

    private fun update(dt: Float, now: Long) {
        when (phase) {
            Phase.BUILD -> {
                for (b in blocks) {
                    if (!b.falling) continue
                    b.vy += height * 5f * dt
                    b.y += b.vy * dt
                    if (b.y >= b.restY) {
                        b.y = b.restY
                        b.vy = 0f
                        b.falling = false
                        b.landedAt = now
                        host.sounds.note(landed)
                        landed++
                    }
                }
                if (blocks.none { it.falling } && pending > 0 && nextIndex < BLOCK_COUNT) {
                    val b = blocks[nextIndex++]
                    pending--
                    b.falling = true
                    b.x = centreX + b.jitter
                    b.y = hoverY
                }
                if (landed == BLOCK_COUNT) {
                    phaseT += dt
                    if (phaseT > 0.7f) {
                        phase = Phase.SWAY
                        phaseT = 0f
                        host.sounds.sparkle()
                        host.cheer()
                        host.say(context.getString(R.string.g_blocks_tall))
                    }
                }
            }
            Phase.SWAY -> {
                phaseT += dt
                if (phaseT > SWAY_SECONDS) startTumble(now)
            }
            Phase.TUMBLE -> {
                var all = true
                for (b in blocks) {
                    stepPiece(b, dt, now)
                    if (!b.settled) all = false
                }
                if (all) settledT += dt
                if (settledT > 1.6f) {
                    phase = Phase.CLEAR
                    phaseT = 0f
                }
            }
            Phase.CLEAR -> {
                phaseT += dt
                if (phaseT >= CLEAR_SECONDS) newTower()
            }
        }
    }

    /** Where block number [k] is on the swaying tower: the tower leans, and its upper blocks lag. */
    private fun swayX(b: Block, k: Int): Float {
        val amp = min(1f, phaseT / 0.6f)
        val theta = amp * 0.09f * sin(phaseT * 5.5f)
        val relX = b.x - centreX
        val relY = b.restY - groundY
        val snake = amp * unit * 0.25f * sin(phaseT * 5.5f - k * 0.45f) * k / (BLOCK_COUNT - 1)
        return centreX + relX * cos(theta) - relY * sin(theta) + snake
    }

    private fun swayY(b: Block): Float {
        val amp = min(1f, phaseT / 0.6f)
        val theta = amp * 0.09f * sin(phaseT * 5.5f)
        val relX = b.x - centreX
        val relY = b.restY - groundY
        return groundY + relX * sin(theta) + relY * cos(theta)
    }

    private fun swayRotation(): Float = Math.toDegrees((min(1f, phaseT / 0.6f) * 0.09f * sin(phaseT * 5.5f)).toDouble()).toFloat()

    private fun startTumble(now: Long) {
        val lean = if (sin(phaseT * 5.5f) < 0f) -1f else 1f
        blocks.forEachIndexed { k, b ->
            val x = swayX(b, k)
            val y = swayY(b)
            b.x = x
            b.y = y
            b.rot = swayRotation()
            b.vx = lean * unit * (0.4f + 2.2f * k / BLOCK_COUNT) + (random.nextFloat() - 0.5f) * unit * 2f
            b.vy = -unit * (0.6f + random.nextFloat() * 1.6f)
            b.vrot = (random.nextFloat() - 0.5f) * 3f
            b.tilt = (random.nextFloat() - 0.5f) * 0.4f * 57.3f
            b.onGround = false
            b.settled = false
            b.bounces = 0
        }
        phase = Phase.TUMBLE
        phaseT = 0f
        settledT = 0f
        host.sounds.boing()
        host.say(context.getString(R.string.g_blocks_tumble))
    }

    private fun stepPiece(b: Block, dt: Float, now: Long) {
        if (b.settled) return
        b.vy += height * 2.2f * dt
        b.x += b.vx * dt
        b.y += b.vy * dt
        b.rot += b.vrot * 57.3f * dt
        val half = b.w / 2f
        if (b.x < half) { b.x = half; b.vx = abs(b.vx) * 0.5f }
        if (b.x > width - half) { b.x = width - half; b.vx = -abs(b.vx) * 0.5f }
        val floor = groundY - b.h / 2f
        if (b.y >= floor) {
            b.y = floor
            if (b.vy > unit * 1.2f && b.bounces < 2) {
                b.vy = -b.vy * 0.35f
                b.bounces++
                if (now - lastPopAt > 90L) {
                    lastPopAt = now
                    host.sounds.pop(0.7f + random.nextFloat() * 0.6f)
                }
            } else {
                b.vy = 0f
                // However many times it spun, turn the short way back to its resting tilt.
                if (!b.onGround) b.rot = ((b.rot % 360f) + 540f) % 360f - 180f
                b.onGround = true
            }
        }
        if (b.onGround) {
            b.vx *= (1f - min(1f, dt * 4f))
            b.vrot *= (1f - min(1f, dt * 5f))
            b.rot += (b.tilt - b.rot) * min(1f, dt * 6f)
            if (abs(b.vx) < 4f && abs(b.vrot) < 0.05f && abs(b.tilt - b.rot) < 0.6f) b.settled = true
        }
    }

    // ---- Drawing ----------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1000f).coerceAtMost(0.05f)
        lastFrame = now
        if (blocks.isEmpty()) return
        update(dt, now)
        drawScenery(canvas, now)

        // A ghost of the next block hovers above the tower, waiting to be dropped.
        if (phase == Phase.BUILD && nextIndex < BLOCK_COUNT && blocks.none { it.falling }) {
            val g = blocks[nextIndex]
            val gy = hoverY + sin(now / 400f) * height * 0.012f
            canvas.saveLayerAlpha(centreX - g.w, gy - g.h, centreX + g.w, gy + g.h, 110)
            canvas.translate(centreX + g.jitter, gy)
            drawBlock(canvas, g)
            canvas.restore()
        }

        blocks.forEachIndexed { k, b ->
            when (phase) {
                Phase.BUILD -> {
                    if (!b.falling && b.landedAt < 0L) return@forEachIndexed
                    canvas.save()
                    canvas.translate(b.x, b.y)
                    if (b.landedAt >= 0L) {
                        // Squash on landing, anchored at the bottom, then spring back.
                        val dtl = (now - b.landedAt) / 1000f
                        val s = exp(-6f * dtl) * cos(dtl * 24f) * 0.14f
                        canvas.translate(0f, b.h / 2f)
                        canvas.scale(1f + s * 0.7f, 1f - s)
                        canvas.translate(0f, -b.h / 2f)
                    }
                    drawBlock(canvas, b)
                    canvas.restore()
                }
                Phase.SWAY -> {
                    canvas.save()
                    canvas.translate(swayX(b, k), swayY(b))
                    canvas.rotate(swayRotation())
                    drawBlock(canvas, b)
                    canvas.restore()
                }
                Phase.TUMBLE -> {
                    canvas.save()
                    canvas.translate(b.x, b.y)
                    canvas.rotate(b.rot)
                    drawBlock(canvas, b)
                    canvas.restore()
                }
                Phase.CLEAR -> {
                    val s = (1f - phaseT / CLEAR_SECONDS).coerceIn(0f, 1f)
                    canvas.save()
                    canvas.translate(b.x, b.y)
                    canvas.rotate(b.rot)
                    canvas.scale(s, s)
                    drawBlock(canvas, b)
                    canvas.restore()
                }
            }
        }
        if (isAttachedToWindow) postInvalidateOnAnimation()
    }

    private fun drawScenery(c: Canvas, now: Long) {
        val w = width.toFloat()
        val h = height.toFloat()
        c.drawColor(0xFFE3F2FD.toInt())
        // Soft hills behind the ground.
        fill.color = 0xFFC5E1A5.toInt()
        c.drawCircle(w * 0.2f, groundY + h * 0.12f, h * 0.3f, fill)
        c.drawCircle(w * 0.85f, groundY + h * 0.14f, h * 0.34f, fill)
        // Two clouds drifting.
        cloud(c, ((now / 45f) % (w * 1.4f)) - w * 0.2f, h * 0.2f, min(w, h) * 0.06f)
        cloud(c, ((now / 70f + w * 0.7f) % (w * 1.4f)) - w * 0.2f, h * 0.4f, min(w, h) * 0.05f)
        // The ground.
        fill.color = 0xFF81C784.toInt()
        c.drawRect(0f, groundY, w, h, fill)
        fill.color = 0xFF66BB6A.toInt()
        c.drawRect(0f, groundY, w, groundY + h * 0.02f, fill)
        fill.color = 0xFFA5D6A7.toInt()
        for (k in 0 until 7) c.drawCircle(w * (0.08f + k * 0.14f), groundY + h * (0.06f + 0.03f * (k % 3)), h * 0.012f, fill)
    }

    private fun cloud(c: Canvas, x: Float, y: Float, r: Float) {
        fill.color = Color.WHITE
        c.drawCircle(x, y, r, fill)
        c.drawCircle(x + r * 1.1f, y + r * 0.2f, r * 0.85f, fill)
        c.drawCircle(x - r * 1.1f, y + r * 0.3f, r * 0.75f, fill)
        rect.set(x - r * 1.5f, y + r * 0.2f, x + r * 1.7f, y + r * 1.05f)
        c.drawRoundRect(rect, r * 0.4f, r * 0.4f, fill)
    }

    /** Draws [b] centred on (0, 0). */
    private fun drawBlock(c: Canvas, b: Block) {
        val hw = b.w / 2f
        val hh = b.h / 2f
        var faceY = 0f
        var faceSize = min(b.w, b.h) * 0.55f
        when (b.shape) {
            Shape.ROOF -> {
                path.rewind()
                path.moveTo(-hw, hh)
                path.lineTo(hw, hh)
                path.lineTo(0f, -hh)
                path.close()
                fill.color = b.color
                c.drawPath(path, fill)
                c.drawPath(path, ink)
                faceY = hh * 0.35f
                faceSize = b.h * 0.5f
            }
            Shape.ARCH -> {
                val iw = b.w * 0.45f
                val ih = b.h * 0.62f
                val top = hh - ih + iw / 2f
                path.rewind()
                rect.set(-hw, -hh, hw, hh)
                path.addRoundRect(rect, b.h * 0.14f, b.h * 0.14f, Path.Direction.CW)
                hole.rewind()
                hole.moveTo(-iw / 2f, hh)
                hole.lineTo(-iw / 2f, top)
                rect.set(-iw / 2f, top - iw / 2f, iw / 2f, top + iw / 2f)
                hole.arcTo(rect, 180f, 180f, false)
                hole.lineTo(iw / 2f, hh)
                hole.close()
                path.op(hole, Path.Op.DIFFERENCE)
                fill.color = b.color
                c.drawPath(path, fill)
                c.drawPath(path, ink)
                faceY = -hh + (b.h - ih) / 2f
                faceSize = (b.h - ih) * 0.95f
            }
            else -> {
                rect.set(-hw, -hh, hw, hh)
                fill.color = b.color
                c.drawRoundRect(rect, b.h * 0.14f, b.h * 0.14f, fill)
                // A lighter bar along the top, like a shine.
                fill.color = 0x55FFFFFF
                rect.set(-hw + b.w * 0.1f, -hh + b.h * 0.1f, hw - b.w * 0.1f, -hh + b.h * 0.2f)
                c.drawRoundRect(rect, b.h * 0.05f, b.h * 0.05f, fill)
                rect.set(-hw, -hh, hw, hh)
                c.drawRoundRect(rect, b.h * 0.14f, b.h * 0.14f, ink)
                if (b.shape == Shape.TALL) faceY = -hh * 0.35f
            }
        }
        face(c, faceY, faceSize)
    }

    /** Two dots, rosy cheeks and a smile, [size] wide, centred on height [y]. */
    private fun face(c: Canvas, y: Float, size: Float) {
        fill.color = Palette.INK
        c.drawCircle(-size * 0.2f, y, size * 0.07f, fill)
        c.drawCircle(size * 0.2f, y, size * 0.07f, fill)
        fill.color = 0x66FF6F91
        c.drawCircle(-size * 0.36f, y + size * 0.14f, size * 0.07f, fill)
        c.drawCircle(size * 0.36f, y + size * 0.14f, size * 0.07f, fill)
        val old = ink.strokeWidth
        ink.strokeWidth = 2.5f * density
        c.drawArc(-size * 0.17f, y - size * 0.02f, size * 0.17f, y + size * 0.22f, 20f, 140f, false, ink)
        ink.strokeWidth = old
    }

    private companion object {
        const val BLOCK_COUNT = 8
        const val SWAY_SECONDS = 1.8f
        const val CLEAR_SECONDS = 0.5f

        /** Positions in [Palette.colors]: every bright crayon but black, brown and peach. */
        val TOWER_COLORS = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8)
    }
}
