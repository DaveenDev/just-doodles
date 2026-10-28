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
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Flowers: sky above, soil along the bottom. A tap drops a seed that sprouts a stem and blooms into
 * a random flower (daisy, tulip, pom-pom, sunflower or blossom, in crayon colors) up near where the
 * finger was, with a note, and sometimes "Pretty!" or its color. A bee and a butterfly flutter from
 * flower to flower. There are about a dozen at most: the oldest one shrinks away to make room.
 */
@SuppressLint("ViewConstructor")
class FlowersView(context: Context, private val host: GameHost) : View(context) {

    private class Flower(
        val x: Float,
        val baseY: Float,
        val bloomY: Float,
        val fallFrom: Float,
        val color: Int,
        val type: Int,
        val size: Float,
        val lean: Float,
        val phase: Float,
        val bornAt: Long,
    ) {
        var dyingAt = -1L
        var bloomed = false
        /** Where the flower head was last drawn, so the insects know where to land. */
        var headX = x
        var headY = bloomY
    }

    private class Bug(val bee: Boolean, var x: Float, var y: Float, val seed: Float) {
        var target: Flower? = null
        var lingerUntil = 0L
        var facing = 1f
    }

    private val density = resources.displayMetrics.density
    private val random = Random(System.nanoTime())
    private val colorNames = resources.getStringArray(R.array.color_names)
    private val flowers = ArrayList<Flower>()
    private val bugs = ArrayList<Bug>()
    private var groundY = 0f
    private var lastFrame = 0L
    private var soilDots = FloatArray(0)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        color = Palette.INK
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val stem = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val path = Path()
    private val rect = RectF()

    init {
        contentDescription = context.getString(R.string.game_flowers)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        groundY = h * 0.84f
        flowers.clear()
        bugs.clear()
        if (w > 0 && h > 0) {
            bugs += Bug(true, w * 0.2f, h * 0.3f, 0f)
            bugs += Bug(false, w * 0.8f, h * 0.35f, 2.5f)
            // Specks in the soil, kept for good so they don't flicker.
            val r = Random(7)
            soilDots = FloatArray(60) { i -> if (i % 2 == 0) r.nextFloat() * w else groundY + h * 0.04f + r.nextFloat() * (h - groundY - h * 0.05f) }
        }
        invalidate()
    }

    // ---- Planting ---------------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val m = e.actionMasked
        if (m != MotionEvent.ACTION_DOWN && m != MotionEvent.ACTION_POINTER_DOWN) return true
        val i = e.actionIndex
        plant(e.getX(i), e.getY(i))
        return true
    }

    private fun plant(tapX: Float, tapY: Float) {
        if (width == 0) return
        val now = SystemClock.uptimeMillis()
        // The oldest flower shrinks away once there are too many.
        if (flowers.count { it.dyingAt < 0L } >= MAX_FLOWERS) flowers.first { it.dyingAt < 0L }.dyingAt = now

        val size = min(width, height) * (0.08f + random.nextFloat() * 0.03f)
        val x = tapX.coerceIn(size, width - size)
        val baseY = groundY + height * 0.025f
        val top = height * 0.14f
        // A tap in the sky blooms right there; a tap low down or in the soil grows to a random height.
        val bloomY = if (tapY < groundY - height * 0.2f) tapY.coerceAtLeast(top) else groundY - height * (0.2f + random.nextFloat() * 0.3f)
        val fallFrom = if (tapY < baseY) tapY.coerceAtLeast(0f) else baseY
        val color = PETAL_COLORS[random.nextInt(PETAL_COLORS.size)]
        flowers += Flower(
            x, baseY, bloomY, fallFrom, color, random.nextInt(TYPES), size,
            (random.nextFloat() - 0.5f) * size * 1.2f, random.nextFloat() * 6f, now,
        )
        host.sounds.pop(0.9f + random.nextFloat() * 0.4f)
    }

    private fun bloomed(f: Flower) {
        f.bloomed = true
        host.sounds.note(f.color)
        host.sounds.sparkle()
        val r = random.nextFloat()
        if (r < 0.3f) host.say(colorNames[f.color]) else if (r < 0.6f) host.say(context.getString(R.string.g_flowers_pretty))
    }

    // ---- Drawing ----------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1000f).coerceAtMost(0.05f)
        lastFrame = now
        val w = width.toFloat()
        val h = height.toFloat()

        drawScenery(canvas, now)

        val it = flowers.iterator()
        while (it.hasNext()) {
            val f = it.next()
            if (f.dyingAt >= 0L && now - f.dyingAt > SHRINK_MS) {
                bugs.forEach { b -> if (b.target === f) b.target = null }
                it.remove()
                continue
            }
            val t = (now - f.bornAt) / 1000f
            if (!f.bloomed && t >= T_STEM) bloomed(f)
            drawFlower(canvas, f, t, now)
        }

        for (b in bugs) {
            moveBug(b, now, dt, w, h)
            if (b.bee) drawBee(canvas, b, now) else drawButterfly(canvas, b, now)
        }
        if (isAttachedToWindow) postInvalidateOnAnimation()
    }

    private fun drawScenery(c: Canvas, now: Long) {
        val w = width.toFloat()
        val h = height.toFloat()
        c.drawColor(0xFFD8F1FF.toInt())

        // The sun, with slowly turning rays.
        val sunX = w * 0.88f
        val sunY = h * 0.14f
        val sunR = min(w, h) * 0.07f
        ink.strokeWidth = 4f * density
        ink.color = 0xFFFFB300.toInt()
        for (k in 0 until 10) {
            val a = now / 4000f + k * Math.PI.toFloat() / 5f
            c.drawLine(sunX + cos(a) * sunR * 1.25f, sunY + sin(a) * sunR * 1.25f, sunX + cos(a) * sunR * 1.65f, sunY + sin(a) * sunR * 1.65f, ink)
        }
        ink.color = Palette.INK
        ink.strokeWidth = 3f * density
        fill.color = 0xFFFFD54F.toInt()
        c.drawCircle(sunX, sunY, sunR, fill)
        c.drawCircle(sunX, sunY, sunR, ink)
        smileFace(c, sunX, sunY, sunR)

        // Two clouds drifting.
        cloud(c, ((now / 40f) % (w * 1.4f)) - w * 0.2f, h * 0.2f, min(w, h) * 0.07f)
        cloud(c, ((now / 65f + w * 0.6f) % (w * 1.4f)) - w * 0.2f, h * 0.38f, min(w, h) * 0.055f)

        // Soil with a grass edge.
        fill.color = 0xFF8D6E4A.toInt()
        c.drawRect(0f, groundY, w, h, fill)
        fill.color = 0xFF6D4C41.toInt()
        var i = 0
        while (i + 1 < soilDots.size) {
            c.drawCircle(soilDots[i], soilDots[i + 1], min(w, h) * 0.008f, fill)
            i += 2
        }
        path.rewind()
        path.moveTo(0f, groundY + h * 0.03f)
        path.lineTo(0f, groundY)
        val bumps = 14
        val step = w / bumps
        for (k in 0 until bumps) {
            path.quadTo((k + 0.5f) * step, groundY - h * 0.025f, (k + 1) * step, groundY)
        }
        path.lineTo(w, groundY + h * 0.03f)
        path.close()
        fill.color = 0xFF7CB342.toInt()
        c.drawPath(path, fill)
    }

    private fun cloud(c: Canvas, x: Float, y: Float, r: Float) {
        fill.color = Color.WHITE
        c.drawCircle(x, y, r, fill)
        c.drawCircle(x + r * 1.1f, y + r * 0.2f, r * 0.85f, fill)
        c.drawCircle(x - r * 1.1f, y + r * 0.3f, r * 0.75f, fill)
        rect.set(x - r * 1.5f, y + r * 0.2f, x + r * 1.7f, y + r * 1.05f)
        c.drawRoundRect(rect, r * 0.4f, r * 0.4f, fill)
    }

    /** A small friendly face (two dots and a smile) in a circle of radius [r] at [x], [y]. */
    private fun smileFace(c: Canvas, x: Float, y: Float, r: Float) {
        fill.color = Palette.INK
        c.drawCircle(x - r * 0.33f, y - r * 0.12f, r * 0.1f, fill)
        c.drawCircle(x + r * 0.33f, y - r * 0.12f, r * 0.1f, fill)
        ink.strokeWidth = 2.5f * density
        c.drawArc(x - r * 0.35f, y - r * 0.15f, x + r * 0.35f, y + r * 0.5f, 25f, 130f, false, ink)
        ink.strokeWidth = 3f * density
    }

    private fun easeOutBack(t: Float): Float {
        val c1 = 1.9f
        val c3 = c1 + 1f
        return 1f + c3 * (t - 1f).pow(3) + c1 * (t - 1f).pow(2)
    }

    private fun drawFlower(c: Canvas, f: Flower, t: Float, now: Long) {
        var alpha = 255
        if (f.dyingAt >= 0L) alpha = ((1f - (now - f.dyingAt) / SHRINK_MS.toFloat()) * 255).toInt().coerceIn(0, 255)
        if (alpha < 255) c.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), alpha)

        // The seed falls and lands, with a little puff of soil.
        if (t < T_SEED) {
            val p = t / T_SEED
            val y = f.fallFrom + (f.baseY - f.fallFrom) * p * p
            fill.color = 0xFF5D4037.toInt()
            c.drawOval(f.x - f.size * 0.1f, y - f.size * 0.14f, f.x + f.size * 0.1f, y + f.size * 0.14f, fill)
        } else if (t < T_SEED + 0.35f) {
            val p = (t - T_SEED) / 0.35f
            fill.color = Color.argb(((1f - p) * 255).toInt(), 0x6D, 0x4C, 0x41)
            for (k in 0 until 5) {
                val a = Math.PI.toFloat() * (1.1f + k * 0.2f)
                c.drawCircle(f.x + cos(a) * f.size * 0.5f * p, f.baseY + sin(a) * f.size * 0.5f * p, f.size * 0.08f, fill)
            }
        }

        val gt = ((t - T_SEED) / (T_STEM - T_SEED)).coerceIn(0f, 1f)
        if (gt > 0f) {
            val g = 1f - (1f - gt) * (1f - gt)
            val sway = sin(now / 700f + f.phase) * f.size * 0.1f * g
            val endX = f.x + f.lean * g + sway
            val endY = f.baseY + (f.bloomY - f.baseY) * g
            val ctrlY = (f.baseY + endY) / 2f
            path.rewind()
            path.moveTo(f.x, f.baseY)
            path.quadTo(f.x, ctrlY, endX, endY)
            val sw = f.size * 0.17f
            stem.strokeWidth = sw + 6f * density
            stem.color = Palette.INK
            c.drawPath(path, stem)
            stem.strokeWidth = sw
            stem.color = 0xFF43A047.toInt()
            c.drawPath(path, stem)

            // Two leaves part way up, once the stem is tall enough.
            val leaf = ((g - 0.35f) / 0.5f).coerceIn(0f, 1f)
            if (leaf > 0f) {
                val u = 0.45f
                val lx = (1 - u) * (1 - u) * f.x + 2 * u * (1 - u) * f.x + u * u * endX
                val ly = (1 - u) * (1 - u) * f.baseY + 2 * u * (1 - u) * ctrlY + u * u * endY
                drawLeaf(c, lx, ly, -30f, f.size * leaf)
                drawLeaf(c, lx, ly + f.size * 0.25f, 210f, f.size * leaf)
            }

            f.headX = endX
            f.headY = endY
            val bt = ((t - T_STEM) / (T_BLOOM - T_STEM)).coerceIn(0f, 1f)
            if (bt <= 0f) {
                // A green bud with a hint of the color coming.
                val r = f.size * 0.28f * g
                fill.color = 0xFF66BB6A.toInt()
                c.drawCircle(endX, endY, r, fill)
                c.drawCircle(endX, endY, r, ink)
                fill.color = Palette.colors[f.color]
                c.drawCircle(endX, endY - r * 0.45f, r * 0.4f, fill)
            } else {
                val s = easeOutBack(bt)
                c.save()
                c.translate(endX, endY)
                c.rotate(sway / f.size * 20f)
                drawHead(c, f, f.size * s)
                c.restore()
            }
        }
        if (alpha < 255) c.restore()
    }

    private fun drawLeaf(c: Canvas, x: Float, y: Float, angle: Float, size: Float) {
        c.save()
        c.translate(x, y)
        c.rotate(angle)
        rect.set(0f, -size * 0.14f, size * 0.6f, size * 0.14f)
        fill.color = 0xFF66BB6A.toInt()
        c.drawOval(rect, fill)
        c.drawOval(rect, ink)
        c.restore()
    }

    /** The bloom, centred on (0, 0), about [r] in radius. */
    private fun drawHead(c: Canvas, f: Flower, r: Float) {
        val color = Palette.colors[f.color]
        when (f.type) {
            0 -> { // Daisy: ten long petals round a smiling face.
                for (k in 0 until 10) {
                    c.save()
                    c.rotate(k * 36f)
                    rect.set(r * 0.25f, -r * 0.2f, r, r * 0.2f)
                    fill.color = color
                    c.drawOval(rect, fill)
                    c.drawOval(rect, ink)
                    c.restore()
                }
                centre(c, r * 0.4f, if (f.color == 2) 0xFF8D5524.toInt() else 0xFFFDD835.toInt(), true)
            }
            1 -> { // Tulip: a cup of three pointed petals.
                for (k in 0 until 3) {
                    c.save()
                    c.translate(0f, r * 0.5f)
                    c.rotate(floatArrayOf(-26f, 26f, 0f)[k])
                    c.translate(0f, -r * 0.5f)
                    path.rewind()
                    path.moveTo(0f, -r)
                    path.cubicTo(r * 0.55f, -r * 0.7f, r * 0.5f, r * 0.3f, 0f, r * 0.5f)
                    path.cubicTo(-r * 0.5f, r * 0.3f, -r * 0.55f, -r * 0.7f, 0f, -r)
                    path.close()
                    fill.color = if (k == 2) color else Palette.mix(color, Palette.INK, 0.12f)
                    c.drawPath(path, fill)
                    c.drawPath(path, ink)
                    c.restore()
                }
                fill.color = 0x55FFFFFF
                c.drawOval(-r * 0.15f, -r * 0.65f, r * 0.02f, -r * 0.1f, fill)
            }
            2 -> { // Pom-pom: a ring of round puffs round a pale middle.
                for (k in 0 until 7) {
                    val a = k * 2 * Math.PI.toFloat() / 7f
                    c.drawCircle(cos(a) * r * 0.6f, sin(a) * r * 0.6f, r * 0.4f, fillWith(color))
                    c.drawCircle(cos(a) * r * 0.6f, sin(a) * r * 0.6f, r * 0.4f, ink)
                }
                c.drawCircle(0f, 0f, r * 0.45f, fillWith(Palette.mix(color, Color.WHITE, 0.45f)))
                c.drawCircle(0f, 0f, r * 0.45f, ink)
            }
            3 -> { // Sunflower: a dozen pointed petals round a big brown middle.
                for (k in 0 until 12) {
                    c.save()
                    c.rotate(k * 30f)
                    path.rewind()
                    path.moveTo(r * 0.3f, -r * 0.17f)
                    path.lineTo(r, 0f)
                    path.lineTo(r * 0.3f, r * 0.17f)
                    path.close()
                    fill.color = color
                    c.drawPath(path, fill)
                    c.drawPath(path, ink)
                    c.restore()
                }
                c.drawCircle(0f, 0f, r * 0.5f, fillWith(0xFF8D5524.toInt()))
                c.drawCircle(0f, 0f, r * 0.5f, ink)
                fill.color = 0xFFBC8A5F.toInt()
                for (k in 0 until 6) {
                    val a = k * Math.PI.toFloat() / 3f
                    c.drawCircle(cos(a) * r * 0.27f, sin(a) * r * 0.27f, r * 0.05f, fill)
                }
                c.drawCircle(0f, 0f, r * 0.05f, fill)
            }
            else -> { // Blossom: five round petals round a smiling face.
                for (k in 0 until 5) {
                    val a = -Math.PI.toFloat() / 2f + k * 2 * Math.PI.toFloat() / 5f
                    c.drawCircle(cos(a) * r * 0.55f, sin(a) * r * 0.55f, r * 0.45f, fillWith(color))
                    c.drawCircle(cos(a) * r * 0.55f, sin(a) * r * 0.55f, r * 0.45f, ink)
                }
                centre(c, r * 0.36f, 0xFFFDD835.toInt(), f.color != 2)
            }
        }
    }

    private fun fillWith(color: Int): Paint {
        fill.color = color
        return fill
    }

    private fun centre(c: Canvas, r: Float, color: Int, face: Boolean) {
        c.drawCircle(0f, 0f, r, fillWith(color))
        c.drawCircle(0f, 0f, r, ink)
        if (face) smileFace(c, 0f, 0f, r)
    }

    // ---- The bee and the butterfly ----------------------------------------------------------

    private fun moveBug(b: Bug, now: Long, dt: Float, w: Float, h: Float) {
        var tx: Float
        var ty: Float
        val target = b.target
        if (target != null && target.bloomed) {
            tx = target.headX + (if (b.bee) -1f else 1f) * target.size * 0.2f
            ty = target.headY - target.size * 0.2f
            if (hypot(tx - b.x, ty - b.y) < target.size * 0.5f && b.lingerUntil == 0L) b.lingerUntil = now + 1200L
            if (b.lingerUntil != 0L && now > b.lingerUntil) {
                b.lingerUntil = 0L
                b.target = null
            }
        } else {
            // Nothing to visit yet: a lazy loop across the sky, or look for a flower.
            val ready = flowers.filter { it.bloomed && it.dyingAt < 0L }
            if (ready.isNotEmpty() && random.nextInt(60) == 0) {
                b.target = ready[random.nextInt(ready.size)]
                b.lingerUntil = 0L
            }
            tx = w * (0.5f + 0.38f * sin(now / 3100f + b.seed))
            ty = h * (0.3f + 0.16f * sin(now / 2300f + b.seed * 1.7f))
        }
        val ease = min(1f, dt * (if (b.target != null) 2.2f else 1.1f))
        val oldX = b.x
        b.x += (tx - b.x) * ease
        b.y += (ty - b.y) * ease
        if (b.x - oldX > 0.2f) b.facing = 1f else if (b.x - oldX < -0.2f) b.facing = -1f
    }

    private fun drawBee(c: Canvas, b: Bug, now: Long) {
        val s = min(width, height) * 0.035f
        val bob = sin(now / 90f + b.seed) * s * 0.15f
        c.save()
        c.translate(b.x, b.y + bob)
        c.scale(b.facing, 1f)
        val flap = 0.45f + 0.55f * kotlin.math.abs(sin(now / 35f))
        fill.color = 0xCCFFFFFF.toInt()
        rect.set(-s * 0.9f, -s * (0.4f + 1.1f * flap), s * 0.1f, -s * 0.3f)
        c.drawOval(rect, fill)
        c.drawOval(rect, ink)
        rect.set(-s * 1.2f, -s * 0.9f, s * 1.1f, s * 0.8f)
        fill.color = 0xFFFDD835.toInt()
        c.drawOval(rect, fill)
        c.save()
        c.clipPath(path.also { it.rewind(); it.addOval(rect, Path.Direction.CW) })
        fill.color = Palette.INK
        c.drawRect(-s * 0.6f, -s, -s * 0.2f, s, fill)
        c.drawRect(s * 0.1f, -s, s * 0.5f, s, fill)
        c.restore()
        c.drawOval(rect, ink)
        fill.color = Palette.INK
        c.drawCircle(s * 0.65f, -s * 0.15f, s * 0.12f, fill)
        c.restore()
    }

    private fun drawButterfly(c: Canvas, b: Bug, now: Long) {
        val s = min(width, height) * 0.04f
        val flap = 0.3f + 0.7f * kotlin.math.abs(sin(now / 110f + b.seed))
        c.save()
        c.translate(b.x, b.y + sin(now / 400f + b.seed) * s * 0.4f)
        for (side in -1..1 step 2) {
            c.save()
            c.scale(side * flap, 1f)
            rect.set(0f, -s * 1.5f, s * 1.5f, s * 0.2f)
            fill.color = 0xFFBA68C8.toInt()
            c.drawOval(rect, fill)
            c.drawOval(rect, ink)
            rect.set(0f, -s * 0.2f, s * 1.1f, s * 1.1f)
            fill.color = 0xFFFF8AB8.toInt()
            c.drawOval(rect, fill)
            c.drawOval(rect, ink)
            fill.color = Color.WHITE
            c.drawCircle(s * 0.8f, -s * 0.8f, s * 0.22f, fill)
            c.restore()
        }
        rect.set(-s * 0.14f, -s * 0.8f, s * 0.14f, s * 0.8f)
        fill.color = Palette.INK
        c.drawOval(rect, fill)
        c.drawLine(0f, -s * 0.75f, -s * 0.3f, -s * 1.2f, ink)
        c.drawLine(0f, -s * 0.75f, s * 0.3f, -s * 1.2f, ink)
        c.restore()
    }

    private companion object {
        const val MAX_FLOWERS = 12
        const val TYPES = 5
        const val SHRINK_MS = 1000L
        const val T_SEED = 0.35f
        const val T_STEM = 1.3f
        const val T_BLOOM = 2.0f

        /** Positions in [Palette.colors]: red, orange, yellow, sky blue, blue, purple, pink. */
        val PETAL_COLORS = intArrayOf(0, 1, 2, 5, 6, 7, 8)
    }
}
