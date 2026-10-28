package com.daveendev.justdoodles

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

/**
 * The pictures on the Play cards for the newer games, drawn in code like [Icons]: a 100 x 100 box,
 * ink outline, bright fills with a white shine, round friendly shapes, a little face where it fits.
 * [Icons.draw] hands these icons over, so scale the canvas to the size you need first.
 */
object GameIcons {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Palette.INK
    }
    /** Thin ink line, for balloon strings. */
    private val thin = Paint(line).apply { strokeWidth = 2.5f }
    /** Soft white stripe for shine and fog swirls. */
    private val hi = Paint(line).apply {
        strokeWidth = 3.5f
        color = Color.WHITE
        alpha = 0xAA
    }
    /** Sticks, stems and fingers: drawn twice (ink, then color) so they get an outline too. */
    private val bar = Paint(line)
    private val path = Path()
    private val tmp = Path()

    private const val RED = 0xFFE53935.toInt()
    private const val ORANGE = 0xFFFF8F00.toInt()
    private const val YELLOW = 0xFFFDD835.toInt()
    private const val GREEN = 0xFF43A047.toInt()
    private const val BLUE = 0xFF1E88E5.toInt()
    private const val SKY = 0xFF29B6F6.toInt()
    private const val PURPLE = 0xFF8E24AA.toInt()
    private const val PINK = 0xFFF06292.toInt()
    private const val WOOD = 0xFFD7A86E.toInt()
    private const val DARK = 0xFF455A64.toInt()
    private const val SKIN = 0xFFFFCC99.toInt()

    fun draw(c: Canvas, icon: Icon) {
        when (icon) {
            Icon.GAME_BALLOONS -> balloons(c)
            Icon.GAME_DRUMS -> drums(c)
            Icon.GAME_FACES -> faces(c)
            Icon.GAME_FISHING -> fishing(c)
            Icon.GAME_STARS -> stars(c)
            Icon.GAME_COUNT -> count(c)
            Icon.GAME_BIG_SMALL -> bigSmall(c)
            Icon.GAME_SHADOWS -> shadows(c)
            Icon.GAME_TRAIN -> train(c)
            Icon.GAME_WIPE -> wipe(c)
            Icon.GAME_FLOWERS -> flowers(c)
            Icon.GAME_BLOCKS -> blocks(c)
            else -> {}
        }
    }

    // ---- small helpers -------------------------------------------------------------------------

    private fun oval(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
        fill.color = color
        c.drawOval(l, t, r, b, fill)
        c.drawOval(l, t, r, b, line)
    }

    private fun disc(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        fill.color = color
        c.drawCircle(x, y, r, fill)
        c.drawCircle(x, y, r, line)
    }

    private fun rrect(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, color: Int) {
        fill.color = color
        c.drawRoundRect(l, t, r, b, rad, rad, fill)
        c.drawRoundRect(l, t, r, b, rad, rad, line)
    }

    /** A filled, outlined polygon through the x, y pairs in [pts]. */
    private fun poly(c: Canvas, color: Int, vararg pts: Float) {
        path.rewind()
        path.moveTo(pts[0], pts[1])
        var i = 2
        while (i < pts.size) {
            path.lineTo(pts[i], pts[i + 1])
            i += 2
        }
        path.close()
        fill.color = color
        c.drawPath(path, fill)
        c.drawPath(path, line)
    }

    /** A soft white glint on the upper left of an oval centered on (cx, cy). */
    private fun shine(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float) {
        fill.color = 0xAAFFFFFF.toInt()
        c.drawOval(cx - rx * 0.62f, cy - ry * 0.66f, cx - rx * 0.2f, cy - ry * 0.26f, fill)
    }

    private fun ball(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        disc(c, x, y, r, color)
        shine(c, x, y, r, r)
    }

    private fun eyes(c: Canvas, x1: Float, x2: Float, y: Float, r: Float) {
        fill.color = Palette.INK
        c.drawCircle(x1, y, r, fill)
        c.drawCircle(x2, y, r, fill)
    }

    /** A smile whose oval is centered on (cx, cy) and [w] wide on each side. */
    private fun smile(c: Canvas, cx: Float, cy: Float, w: Float) {
        c.drawArc(cx - w, cy - w * 0.9f, cx + w, cy + w * 0.9f, 25f, 130f, false, line)
    }

    private fun cheeks(c: Canvas, x1: Float, x2: Float, y: Float, r: Float) {
        fill.color = 0x66FF6FB0
        c.drawCircle(x1, y, r, fill)
        c.drawCircle(x2, y, r, fill)
    }

    private fun sparkle(c: Canvas, cx: Float, cy: Float, r: Float) {
        val k = r * 0.28f
        path.rewind()
        path.moveTo(cx, cy - r)
        path.lineTo(cx + k, cy - k)
        path.lineTo(cx + r, cy)
        path.lineTo(cx + k, cy + k)
        path.lineTo(cx, cy + r)
        path.lineTo(cx - k, cy + k)
        path.lineTo(cx - r, cy)
        path.lineTo(cx - k, cy - k)
        path.close()
        fill.color = 0xFFFFC107.toInt()
        c.drawPath(path, fill)
        c.drawPath(path, line)
    }

    private fun star(c: Canvas, cx: Float, cy: Float, big: Float, small: Float, color: Int) {
        path.rewind()
        for (i in 0 until 10) {
            val a = Math.toRadians(-90.0 + i * 36.0)
            val r = if (i % 2 == 0) big else small
            val x = cx + r * Math.cos(a).toFloat()
            val y = cy + r * Math.sin(a).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        fill.color = color
        c.drawPath(path, fill)
        c.drawPath(path, line)
    }

    /** A straight stick from (x1, y1) to (x2, y2): ink underneath, [color] on top. */
    private fun stick(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int, w: Float) {
        bar.color = Palette.INK
        bar.strokeWidth = w + 5f
        c.drawLine(x1, y1, x2, y2, bar)
        bar.color = color
        bar.strokeWidth = w
        c.drawLine(x1, y1, x2, y2, bar)
    }

    /** Whatever is in [path] as a thick stroke, ink underneath, [color] on top. */
    private fun stem(c: Canvas, color: Int, w: Float) {
        bar.color = Palette.INK
        bar.strokeWidth = w + 5f
        c.drawPath(path, bar)
        bar.color = color
        bar.strokeWidth = w
        c.drawPath(path, bar)
    }

    // ---- the icons -----------------------------------------------------------------------------

    /** Balloons: three, tied together, the middle one smiling. */
    private fun balloons(c: Canvas) {
        path.rewind()
        path.moveTo(30f, 66f)
        path.quadTo(36f, 84f, 50f, 97f)
        c.drawPath(path, thin)
        path.rewind()
        path.moveTo(70f, 62f)
        path.quadTo(64f, 82f, 50f, 97f)
        c.drawPath(path, thin)
        c.drawLine(50f, 84f, 50f, 97f, thin)
        balloon(c, 30f, 36f, 20f, 24f, RED)
        balloon(c, 70f, 32f, 20f, 24f, SKY)
        balloon(c, 50f, 56f, 19f, 22f, YELLOW)
        eyes(c, 43f, 57f, 54f, 2.6f)
        smile(c, 50f, 58f, 6f)
        cheeks(c, 38f, 62f, 61f, 4f)
    }

    private fun balloon(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, color: Int) {
        poly(c, color, cx, cy + ry - 3f, cx - 5f, cy + ry + 6f, cx + 5f, cy + ry + 6f)
        oval(c, cx - rx, cy - ry, cx + rx, cy + ry, color)
        shine(c, cx, cy, rx, ry)
    }

    /** Drums: two happy drums and a pair of sticks tapping them. */
    private fun drums(c: Canvas) {
        drum(c, 6f, 40f, 88f, 40f, RED, 0xFFFFCDD2.toInt())
        drum(c, 54f, 40f, 88f, 40f, BLUE, 0xFFBBDEFB.toInt())
        for (x in floatArrayOf(26f, 74f)) {
            eyes(c, x - 6f, x + 6f, 66f, 2.6f)
            smile(c, x, 69f, 6f)
            cheeks(c, x - 12f, x + 12f, 72f, 3.5f)
        }
        stick(c, 8f, 6f, 28f, 44f, WOOD, 5f)
        stick(c, 92f, 6f, 72f, 44f, WOOD, 5f)
        disc(c, 28f, 44f, 5f, 0xFFFFF3E0.toInt())
        disc(c, 72f, 44f, 5f, 0xFFFFF3E0.toInt())
    }

    private fun drum(c: Canvas, x: Float, top: Float, bottom: Float, w: Float, color: Int, rim: Int) {
        path.rewind()
        path.moveTo(x, top + 9f)
        path.lineTo(x, bottom - 9f)
        path.arcTo(x, bottom - 18f, x + w, bottom, 180f, -180f, false)
        path.lineTo(x + w, top + 9f)
        path.close()
        fill.color = color
        c.drawPath(path, fill)
        for (k in 1..3) {
            val sx = x + w * k / 4f
            c.drawLine(sx, top + 16f, sx, bottom - 6f, hi)
        }
        c.drawPath(path, line)
        oval(c, x, top, x + w, top + 18f, rim)
    }

    /** Funny faces: one eye open, one winking, tongue out, hair on top. */
    private fun faces(c: Canvas) {
        disc(c, 13f, 56f, 9f, SKIN)
        disc(c, 87f, 56f, 9f, SKIN)
        val hair = 0xFF6D4C41.toInt()
        disc(c, 35f, 19f, 9f, hair)
        disc(c, 65f, 19f, 9f, hair)
        disc(c, 50f, 14f, 10f, hair)
        disc(c, 50f, 54f, 38f, 0xFFFFD54F.toInt())
        // open eye
        disc(c, 35f, 48f, 8f, Color.WHITE)
        fill.color = Palette.INK
        c.drawCircle(36f, 49f, 4f, fill)
        // winking eye, a happy upside-down U
        c.drawArc(54f, 44f, 74f, 58f, 200f, 140f, false, line)
        cheeks(c, 24f, 76f, 64f, 6f)
        // big open mouth with a tongue
        path.rewind()
        path.moveTo(32f, 64f)
        path.quadTo(50f, 94f, 68f, 64f)
        path.close()
        fill.color = 0xFFC62828.toInt()
        c.drawPath(path, fill)
        fill.color = 0xFFFF8A80.toInt()
        c.drawCircle(50f, 73f, 5.5f, fill)
        c.drawPath(path, line)
        sparkle(c, 86f, 24f, 9f)
    }

    /** Fishing: a fish jumping out of the waves toward a bobber on a rod's line. */
    private fun fishing(c: Canvas) {
        // the fish, tilted as if it's leaping
        c.save()
        c.rotate(-25f, 30f, 38f)
        poly(c, ORANGE, 14f, 38f, 1f, 25f, 1f, 51f)
        poly(c, 0xFFFFB300.toInt(), 24f, 28f, 32f, 15f, 42f, 29f)
        oval(c, 8f, 26f, 54f, 50f, ORANGE)
        shine(c, 31f, 38f, 23f, 12f)
        disc(c, 43f, 35f, 5f, Color.WHITE)
        fill.color = Palette.INK
        c.drawCircle(44f, 35.5f, 2.4f, fill)
        c.drawArc(44f, 38f, 54f, 46f, 20f, 120f, false, line)
        c.restore()
        // rod, line and bobber
        stick(c, 96f, 30f, 58f, 6f, 0xFF8D5524.toInt(), 5f)
        c.drawLine(58f, 6f, 62f, 50f, thin)
        disc(c, 62f, 58f, 8f, Color.WHITE)
        fill.color = RED
        c.drawArc(54f, 50f, 70f, 66f, 180f, 180f, true, fill)
        c.drawCircle(62f, 58f, 8f, line)
        // waves
        path.rewind()
        path.moveTo(4f, 68f)
        for (i in 0 until 4) {
            path.quadTo(15.5f + i * 23f, if (i % 2 == 0) 76f else 60f, 27f + i * 23f, 68f)
        }
        path.lineTo(96f, 96f)
        path.lineTo(4f, 96f)
        path.close()
        fill.color = SKY
        c.drawPath(path, fill)
        c.drawPath(path, line)
        c.drawLine(18f, 82f, 34f, 82f, hi)
        c.drawLine(52f, 88f, 72f, 88f, hi)
        // splash
        for (p in arrayOf(floatArrayOf(10f, 56f, 3.5f), floatArrayOf(22f, 62f, 3f), floatArrayOf(4f, 66f, 2.5f))) {
            disc(c, p[0], p[1], p[2], 0xFFB3E5FC.toInt())
        }
    }

    /** Catch the stars: a night sky with a moon, a smiling star and a few small ones. */
    private fun stars(c: Canvas) {
        rrect(c, 4f, 4f, 96f, 96f, 16f, 0xFF303F9F.toInt())
        path.rewind()
        path.addCircle(74f, 28f, 15f, Path.Direction.CW)
        tmp.rewind()
        tmp.addCircle(81f, 23f, 13f, Path.Direction.CW)
        path.op(tmp, Path.Op.DIFFERENCE)
        fill.color = 0xFFFFF59D.toInt()
        c.drawPath(path, fill)
        c.drawPath(path, line)
        star(c, 46f, 58f, 31f, 15f, YELLOW)
        eyes(c, 39f, 53f, 57f, 2.8f)
        smile(c, 46f, 62f, 6.5f)
        cheeks(c, 33f, 59f, 64f, 3.8f)
        star(c, 20f, 27f, 9f, 4f, 0xFFFFF59D.toInt())
        star(c, 80f, 76f, 11f, 5f, 0xFFFFF59D.toInt())
        fill.color = Color.WHITE
        for (p in arrayOf(floatArrayOf(36f, 14f), floatArrayOf(90f, 56f), floatArrayOf(14f, 62f), floatArrayOf(60f, 88f))) {
            c.drawCircle(p[0], p[1], 2f, fill)
        }
    }

    /** Count with me: three cards holding one, two and three round dots. */
    private fun count(c: Canvas) {
        val tilt = floatArrayOf(-5f, 0f, 5f)
        val left = floatArrayOf(5f, 36f, 67f)
        for (i in 0 until 3) {
            c.save()
            c.rotate(tilt[i], left[i] + 14f, 53f)
            rrect(c, left[i], 22f, left[i] + 28f, 84f, 7f, Color.WHITE)
            c.restore()
        }
        c.save()
        c.rotate(-5f, 19f, 53f)
        ball(c, 19f, 53f, 10f, RED)
        c.restore()
        ball(c, 50f, 42f, 8f, BLUE)
        ball(c, 50f, 64f, 8f, BLUE)
        c.save()
        c.rotate(5f, 81f, 53f)
        ball(c, 81f, 38f, 7f, GREEN)
        ball(c, 72f, 62f, 7f, GREEN)
        ball(c, 90f, 62f, 7f, GREEN)
        c.restore()
        sparkle(c, 50f, 10f, 9f)
    }

    /**
     * A little duck in the 100 x 100 box scaled by [s] with its box's top-left at ([tx], [ty]), or just its
     * flat dark shape when [silhouette] is set. The outline stays 4 wide however small the duck is.
     */
    private fun duck(c: Canvas, tx: Float, ty: Float, s: Float, silhouette: Boolean) {
        c.save()
        c.translate(tx, ty)
        c.scale(s, s)
        line.strokeWidth = 4f / s
        val body = if (silhouette) DARK else YELLOW
        if (silhouette) line.color = DARK
        poly(c, body, 16f, 52f, 4f, 36f, 30f, 46f)
        oval(c, 12f, 44f, 80f, 88f, body)
        disc(c, 62f, 32f, 20f, body)
        oval(c, 76f, 28f, 96f, 40f, if (silhouette) DARK else ORANGE)
        if (!silhouette) {
            shine(c, 46f, 66f, 34f, 22f)
            oval(c, 26f, 58f, 56f, 78f, 0xFFFFEE58.toInt())
            fill.color = Palette.INK
            c.drawCircle(68f, 28f, 3.4f, fill)
            fill.color = 0x66FF6FB0
            c.drawCircle(60f, 38f, 4f, fill)
        }
        line.strokeWidth = 4f
        line.color = Palette.INK
        c.restore()
    }

    /** Big and small: the same duck twice, one big and one small. */
    private fun bigSmall(c: Canvas) {
        duck(c, -3f, 22f, 0.85f, false)
        duck(c, 62f, 62f, 0.38f, false)
        sparkle(c, 80f, 26f, 11f)
        sparkle(c, 30f, 14f, 8f)
    }

    /** Shadows: a yellow duck and, below it, its flat dark shadow. */
    private fun shadows(c: Canvas) {
        duck(c, 42f, 44f, 0.6f, true)
        duck(c, 0f, 0f, 0.6f, false)
        sparkle(c, 82f, 22f, 11f)
    }

    private fun wheel(c: Canvas, x: Float, y: Float, r: Float) {
        disc(c, x, y, r, DARK)
        fill.color = Color.WHITE
        c.drawCircle(x, y, r * 0.35f, fill)
    }

    /** Choo choo train: a smiling engine puffing smoke, pulling a wagon of balls. */
    private fun train(c: Canvas) {
        c.drawLine(2f, 91f, 98f, 91f, line)
        c.drawLine(62f, 70f, 72f, 70f, line)
        // smoke
        disc(c, 20f, 17f, 6f, 0xFFECEFF1.toInt())
        disc(c, 31f, 9f, 7f, 0xFFECEFF1.toInt())
        disc(c, 44f, 12f, 5f, 0xFFECEFF1.toInt())
        // chimney, boiler, cab
        poly(c, DARK, 12f, 30f, 26f, 30f, 28f, 46f, 10f, 46f)
        rrect(c, 8f, 24f, 30f, 32f, 3f, DARK)
        rrect(c, 6f, 44f, 46f, 76f, 8f, RED)
        shine(c, 26f, 60f, 20f, 16f)
        rrect(c, 40f, 32f, 64f, 76f, 4f, BLUE)
        rrect(c, 36f, 26f, 68f, 36f, 3f, 0xFF37474F.toInt())
        rrect(c, 45f, 40f, 59f, 54f, 3f, Color.WHITE)
        // lamp and face
        disc(c, 7f, 58f, 4f, YELLOW)
        eyes(c, 18f, 30f, 57f, 2.6f)
        smile(c, 24f, 62f, 6f)
        cheeks(c, 13f, 35f, 65f, 3.3f)
        wheel(c, 20f, 80f, 9f)
        wheel(c, 38f, 80f, 9f)
        wheel(c, 56f, 80f, 9f)
        // wagon with a load
        ball(c, 79f, 40f, 6.5f, PINK)
        ball(c, 90f, 40f, 6.5f, GREEN)
        rrect(c, 70f, 46f, 97f, 76f, 5f, YELLOW)
        fill.color = 0x55FFFFFF
        c.drawRoundRect(75f, 52f, 92f, 58f, 3f, 3f, fill)
        wheel(c, 79f, 80f, 7f)
        wheel(c, 90f, 80f, 7f)
    }

    /** Rub and see: half of a sunny picture is still under fog, and a hand is rubbing it away. */
    private fun wipe(c: Canvas) {
        c.save()
        path.rewind()
        path.addRoundRect(8f, 12f, 92f, 88f, 10f, 10f, Path.Direction.CW)
        c.clipPath(path)
        fill.color = 0xFF81D4FA.toInt()
        c.drawRect(8f, 12f, 92f, 88f, fill)
        fill.color = YELLOW
        c.drawCircle(72f, 34f, 12f, fill)
        c.drawCircle(72f, 34f, 12f, line)
        path.rewind()
        path.moveTo(8f, 68f)
        path.quadTo(44f, 40f, 92f, 64f)
        path.lineTo(92f, 88f)
        path.lineTo(8f, 88f)
        path.close()
        fill.color = 0xFF66BB6A.toInt()
        c.drawPath(path, fill)
        c.drawPath(path, line)
        // the fog, with a puffy right edge
        path.rewind()
        path.addRect(0f, 0f, 44f, 100f, Path.Direction.CW)
        for (y in floatArrayOf(20f, 38f, 56f, 74f, 92f)) {
            tmp.rewind()
            tmp.addCircle(44f, y, 11f, Path.Direction.CW)
            path.op(tmp, Path.Op.UNION)
        }
        fill.color = 0xFFB0BEC5.toInt()
        c.drawPath(path, fill)
        c.drawPath(path, line)
        c.drawArc(14f, 24f, 34f, 38f, 200f, 200f, false, hi)
        c.drawArc(20f, 44f, 38f, 56f, 200f, 200f, false, hi)
        c.restore()
        rrect(c, 8f, 12f, 92f, 88f, 10f, Color.TRANSPARENT)
        // swipe lines and the hand
        c.drawLine(10f, 62f, 24f, 62f, hi)
        c.drawLine(10f, 72f, 22f, 72f, hi)
        val baseX = floatArrayOf(46f, 52f, 58f, 64f)
        val tipX = floatArrayOf(40f, 50f, 61f, 72f)
        val tipY = floatArrayOf(52f, 46f, 47f, 53f)
        bar.color = Palette.INK
        bar.strokeWidth = 13f
        for (i in 0 until 4) c.drawLine(baseX[i], 74f, tipX[i], tipY[i], bar)
        c.drawLine(45f, 82f, 31f, 71f, bar)
        fill.color = Palette.INK
        c.drawCircle(56f, 80f, 16.5f, fill)
        bar.color = SKIN
        bar.strokeWidth = 7f
        for (i in 0 until 4) c.drawLine(baseX[i], 74f, tipX[i], tipY[i], bar)
        c.drawLine(45f, 82f, 31f, 71f, bar)
        fill.color = SKIN
        c.drawCircle(56f, 80f, 13f, fill)
        sparkle(c, 80f, 20f, 9f)
    }

    private fun flower(c: Canvas, cx: Float, cy: Float, n: Int, d: Float, pr: Float, petal: Int, center: Int, cr: Float) {
        for (i in 0 until n) {
            val a = Math.toRadians(-90.0 + i * 360.0 / n)
            disc(c, cx + d * Math.cos(a).toFloat(), cy + d * Math.sin(a).toFloat(), pr, petal)
        }
        disc(c, cx, cy, cr, center)
    }

    /** Grow flowers: three flowers of different colors and sizes in a strip of soil. */
    private fun flowers(c: Canvas) {
        val green = 0xFF66BB6A.toInt()
        path.rewind()
        path.moveTo(28f, 44f)
        path.quadTo(22f, 66f, 28f, 88f)
        stem(c, green, 5f)
        path.rewind()
        path.moveTo(72f, 34f)
        path.quadTo(78f, 62f, 72f, 88f)
        stem(c, green, 5f)
        path.rewind()
        path.moveTo(52f, 66f)
        path.quadTo(56f, 78f, 52f, 88f)
        stem(c, green, 5f)
        // a leaf
        path.rewind()
        path.moveTo(26f, 76f)
        path.quadTo(40f, 62f, 46f, 72f)
        path.quadTo(36f, 84f, 26f, 76f)
        fill.color = green
        c.drawPath(path, fill)
        c.drawPath(path, line)
        flower(c, 28f, 42f, 5, 13f, 11f, RED, YELLOW, 9f)
        eyes(c, 24.5f, 31.5f, 41f, 1.8f)
        smile(c, 28f, 43f, 3.6f)
        flower(c, 72f, 32f, 6, 11f, 9f, PINK, ORANGE, 7f)
        flower(c, 52f, 66f, 5, 10f, 8f, SKY, YELLOW, 6f)
        // the soil
        rrect(c, 3f, 84f, 97f, 98f, 7f, 0xFF8D6E63.toInt())
        fill.color = 0xFF6D4C41.toInt()
        for (x in floatArrayOf(14f, 40f, 64f, 86f)) c.drawCircle(x, 91f, 2.2f, fill)
    }

    /** Block tower: red, yellow and blue squares, then green and orange, and a purple roof on top. */
    private fun blocks(c: Canvas) {
        cube(c, 8f, 66f, RED)
        cube(c, 36f, 66f, YELLOW)
        cube(c, 64f, 66f, BLUE)
        cube(c, 22f, 38f, GREEN)
        cube(c, 50f, 38f, ORANGE)
        poly(c, PURPLE, 39f, 38f, 67f, 38f, 53f, 10f)
        fill.color = 0x55FFFFFF
        c.drawCircle(49f, 30f, 2.6f, fill)
        eyes(c, 44f, 56f, 78f, 2.6f)
        smile(c, 50f, 82f, 6f)
        cheeks(c, 40f, 60f, 84f, 3.5f)
    }

    private fun cube(c: Canvas, l: Float, t: Float, color: Int) {
        fill.color = color
        c.drawRoundRect(l, t, l + 28f, t + 28f, 4f, 4f, fill)
        fill.color = 0x55FFFFFF
        c.drawRoundRect(l + 5f, t + 5f, l + 16f, t + 10f, 2.5f, 2.5f, fill)
        c.drawRoundRect(l, t, l + 28f, t + 28f, 4f, 4f, line)
    }
}
