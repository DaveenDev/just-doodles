package com.daveendev.justdoodles

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface

enum class Icon(val label: String) {
    BRUSH("Crayon"),
    MAGIC("Magic crayon: rainbow, glitter, glow or mirror"),
    STAMP("Stamps: tap again for another one"),
    PATTERN("Pattern crayon: tap again to pick a pattern"),
    STICKER("Stickers: tap again to pick a sticker"),
    ERASER("Eraser"),
    SIZE("Crayon size"),
    UNDO("Undo"),
    PAGES("Pictures"),
    CLEAR("Hold for a clean page"),
    CLOSE("Close"),
    GEAR("Grown-up settings (hold for 2 seconds)"),
    BACK("Back"),
    FREE_DRAW("Free draw"),
    COLORING("Coloring"),
    PLAY("Games"),
    PEEKABOO("Peekaboo"),
    FIND_IT("Where is it?"),
    XYLOPHONE("Xylophone"),
    COLOR_SORT("Sort the colors"),
    FEED("Feed the animals"),
    TRACE("Follow the road"),
    LISTEN("Play a song"),
    LEARN("Learn"),
    LEARN_COLORS("Colors"),
    LEARN_SHAPES("Shapes"),
    LEARN_COUNT("Counting"),
    LEARN_ANIMALS("Animal sounds"),
    LEARN_ABC("ABC"),
    LEARN_BODY("My body"),
    LEARN_FOOD("Food"),
    LEARN_SEA("Sea animals"),
    LEARN_BUGS("Bugs"),
    LEARN_SKY("Sky"),
    GAME_BUBBLES("Bubble pop"),
    GAME_MEMORY("Memory"),
    GAME_SHAPE_SORT("Sort the shapes"),
    GAME_JIGSAW("Jigsaw"),
    LOCK("Locked"),
}

/**
 * Button pictures, drawn in code so they look the same on every phone and need no image files.
 * Every icon is drawn into a 100 x 100 box; scale the canvas to the size you need first.
 */
object Icons {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Palette.INK
    }
    private val thick = Paint(line).apply { strokeWidth = 10f }
    private val band = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 7.5f
    }
    private val glowLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    /** Round dots in a row, like a road drawn for tracing. */
    private val dots = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 9f
        strokeCap = Paint.Cap.ROUND
        color = 0xFF90A4AE.toInt()
        pathEffect = DashPathEffect(floatArrayOf(0.1f, 14f), 0f)
    }
    private val letter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        strokeJoin = Paint.Join.ROUND
    }
    /** The padlock's shackle: a band drawn twice, ink then gold, so it's outlined like everything else. */
    private val shackle = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    private val matrix = Matrix()

    private val rainbow = intArrayOf(
        0xFFE53935.toInt(), 0xFFFF9800.toInt(), 0xFFFDD835.toInt(), 0xFF43A047.toInt(), 0xFF1E88E5.toInt(),
    )
    private const val LIGHT_GRAY = 0xFFCFD8DC.toInt()
    private const val GOLD = 0xFFFFC107.toInt()

    fun draw(
        c: Canvas, icon: Icon, tint: Int, sizeIndex: Int, magic: Magic = Magic.RAINBOW, stamp: StampKind = StampKind.STAR,
        pattern: Pattern = Pattern.HEARTS, sticker: Sticker = Sticker.SUN,
    ) {
        when (icon) {
            Icon.BRUSH -> crayon(c, tint)
            Icon.MAGIC -> when (magic) {
                Magic.RAINBOW -> rainbow(c)
                Magic.GLITTER -> wand(c, tint)
                Magic.GLOW -> glow(c, tint)
                Magic.MIRROR -> butterfly(c, tint)
            }
            Icon.STAMP -> stamp(c, stamp, tint)
            Icon.PATTERN -> PatternPen.drawSample(c, pattern, tint)
            Icon.STICKER -> Stickers.draw(c, sticker, tint)
            Icon.ERASER -> eraser(c)
            Icon.SIZE -> sizes(c, tint, sizeIndex)
            Icon.UNDO -> undo(c)
            Icon.PAGES -> picture(c)
            Icon.CLEAR -> cleanPage(c)
            Icon.CLOSE -> close(c)
            Icon.GEAR -> gear(c)
            Icon.BACK -> back(c)
            Icon.FREE_DRAW -> freeDraw(c)
            Icon.COLORING -> coloring(c)
            Icon.PLAY -> play(c)
            Icon.PEEKABOO -> peekaboo(c)
            Icon.FIND_IT -> findIt(c)
            Icon.XYLOPHONE -> xylophone(c)
            Icon.COLOR_SORT -> colorSort(c)
            Icon.FEED -> feed(c)
            Icon.TRACE -> trace(c)
            Icon.LISTEN -> listen(c)
            Icon.LEARN -> learn(c)
            Icon.LEARN_COLORS -> paints(c)
            Icon.LEARN_SHAPES -> shapes(c)
            Icon.LEARN_COUNT -> counting(c)
            Icon.LEARN_ANIMALS -> animalSounds(c)
            Icon.LEARN_ABC -> abc(c)
            Icon.LEARN_BODY -> body(c)
            Icon.LEARN_FOOD -> food(c)
            Icon.LEARN_SEA -> sea(c)
            Icon.LEARN_BUGS -> bugs(c)
            Icon.LEARN_SKY -> sky(c)
            Icon.GAME_BUBBLES -> bubbles(c)
            Icon.GAME_MEMORY -> memory(c)
            Icon.GAME_SHAPE_SORT -> shapeSort(c)
            Icon.GAME_JIGSAW -> jigsaw(c)
            Icon.LOCK -> lock(c)
        }
    }

    /**
     * The little padlock in the corner of a card or button that needs PLUS, [size] across and
     * centered on ([cx], [cy]). It sits on a white disc so it shows up on any picture.
     */
    fun lockBadge(c: Canvas, cx: Float, cy: Float, size: Float) {
        c.save()
        c.translate(cx - size / 2f, cy - size / 2f)
        c.scale(size / 100f, size / 100f)
        fill.color = 0x22263238
        c.drawCircle(50f, 50f, 50f, fill)
        fill.color = Color.WHITE
        c.drawCircle(50f, 50f, 46f, fill)
        c.translate(14f, 13f)
        c.scale(0.72f, 0.72f)
        lock(c)
        c.restore()
    }

    /** A gold padlock with a smile: something for later, nothing to be sad about. */
    private fun lock(c: Canvas) {
        path.rewind()
        path.moveTo(31f, 50f)
        path.lineTo(31f, 36f)
        path.arcTo(31f, 17f, 69f, 55f, 180f, 180f, false)
        path.lineTo(69f, 50f)
        shackle.color = Palette.INK
        shackle.strokeWidth = 14f
        c.drawPath(path, shackle)
        shackle.color = GOLD
        shackle.strokeWidth = 7f
        c.drawPath(path, shackle)

        fill.color = GOLD
        c.drawRoundRect(16f, 44f, 84f, 92f, 14f, 14f, fill)
        fill.color = 0x55FFFFFF
        c.drawRoundRect(22f, 48f, 78f, 57f, 4.5f, 4.5f, fill)
        c.drawRoundRect(16f, 44f, 84f, 92f, 14f, 14f, line)

        fill.color = Palette.INK
        c.drawCircle(39f, 66f, 4.5f, fill)
        c.drawCircle(61f, 66f, 4.5f, fill)
        c.drawArc(40f, 64f, 60f, 82f, 25f, 130f, false, line)
        fill.color = 0x66FF6FB0
        c.drawCircle(28f, 76f, 5f, fill)
        c.drawCircle(72f, 76f, 5f, fill)
    }

    /** One bold glyph centered on (x, y), filled with [color] and outlined in ink. */
    private fun glyph(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int) {
        letter.textSize = size
        val base = y - (letter.descent() + letter.ascent()) / 2f
        letter.style = Paint.Style.FILL
        letter.color = color
        c.drawText(s, x, base, letter)
        letter.style = Paint.Style.STROKE
        letter.strokeWidth = size * 0.05f
        letter.color = Palette.INK
        c.drawText(s, x, base, letter)
    }

    private fun block(c: Canvas, l: Float, t: Float, size: Float, color: Int, s: String) {
        fill.color = color
        c.drawRoundRect(l, t, l + size, t + size, 6f, 6f, fill)
        c.drawRoundRect(l, t, l + size, t + size, 6f, 6f, line)
        glyph(c, s, l + size / 2f, t + size / 2f, size * 0.72f, Color.WHITE)
    }

    /** The start menu's "learn" picture: a stack of A B C blocks. */
    private fun learn(c: Canvas) {
        block(c, 8f, 52f, 40f, 0xFFE53935.toInt(), "A")
        block(c, 52f, 52f, 40f, 0xFF29B6F6.toInt(), "B")
        c.save()
        c.rotate(-8f, 50f, 30f)
        block(c, 30f, 10f, 40f, 0xFFFDD835.toInt(), "C")
        c.restore()
    }

    /** Colors: a painter's palette with blobs of paint. */
    private fun paints(c: Canvas) {
        path.rewind()
        path.addOval(6f, 16f, 94f, 86f, Path.Direction.CW)
        val hole = Path().apply { addCircle(30f, 66f, 9f, Path.Direction.CW) }
        path.op(hole, Path.Op.DIFFERENCE)
        fill.color = 0xFFFFE0B2.toInt()
        c.drawPath(path, fill)
        c.drawPath(path, line)
        val blobs = arrayOf(
            floatArrayOf(28f, 38f) to 0xFFE53935.toInt(), floatArrayOf(50f, 30f) to 0xFFFDD835.toInt(),
            floatArrayOf(72f, 38f) to 0xFF2E9D4A.toInt(), floatArrayOf(74f, 62f) to 0xFF2962FF.toInt(),
            floatArrayOf(52f, 68f) to 0xFF8E24AA.toInt(),
        )
        for ((p, color) in blobs) {
            fill.color = color
            c.drawCircle(p[0], p[1], 9f, fill)
            c.drawCircle(p[0], p[1], 9f, line)
        }
    }

    /** Shapes: a circle, a square and a triangle. */
    private fun shapes(c: Canvas) {
        fill.color = 0xFFE53935.toInt()
        c.drawCircle(30f, 32f, 20f, fill)
        c.drawCircle(30f, 32f, 20f, line)
        fill.color = 0xFF29B6F6.toInt()
        c.drawRoundRect(56f, 14f, 92f, 50f, 3f, 3f, fill)
        c.drawRoundRect(56f, 14f, 92f, 50f, 3f, 3f, line)
        path.rewind()
        path.moveTo(50f, 54f)
        path.lineTo(76f, 92f)
        path.lineTo(24f, 92f)
        path.close()
        fill.color = 0xFFFDD835.toInt()
        c.drawPath(path, fill)
        c.drawPath(path, line)
    }

    /** Counting: 1 2 3, each with that many dots. */
    private fun counting(c: Canvas) {
        val colors = intArrayOf(0xFFE53935.toInt(), 0xFF2E9D4A.toInt(), 0xFF2962FF.toInt())
        for (i in 0 until 3) {
            val x = 20f + i * 30f
            glyph(c, (i + 1).toString(), x, 36f, 44f, colors[i])
            fill.color = colors[i]
            for (k in 0..i) {
                c.drawCircle(x + (k - i / 2f) * 9f, 80f, 4f, fill)
            }
        }
    }

    /** Animal sounds: a cat saying "Meow" in a speech bubble. */
    private fun animalSounds(c: Canvas) {
        c.save()
        c.translate(0f, 30f)
        c.scale(0.68f, 0.68f)
        Stickers.draw(c, Sticker.CAT, 0xFFFF8F00.toInt())
        c.restore()
        path.rewind()
        path.addRoundRect(40f, 6f, 98f, 40f, 14f, 14f, Path.Direction.CW)
        val tail = Path().apply {
            moveTo(54f, 34f)
            lineTo(46f, 52f)
            lineTo(66f, 38f)
            close()
        }
        path.op(tail, Path.Op.UNION)
        fill.color = Color.WHITE
        c.drawPath(path, fill)
        c.drawPath(path, line)
        letter.textSize = 15f
        letter.style = Paint.Style.FILL
        letter.color = Palette.INK
        c.drawText("Meow", 69f, 28f, letter)
    }

    /** ABC: a big "A" with an apple. */
    private fun abc(c: Canvas) {
        glyph(c, "A", 36f, 50f, 78f, 0xFFE53935.toInt())
        fill.color = 0xFFE53935.toInt()
        c.drawCircle(76f, 70f, 16f, fill)
        c.drawCircle(76f, 70f, 16f, line)
        fill.color = 0xFF2E9D4A.toInt()
        c.drawOval(76f, 42f, 92f, 52f, fill)
        c.drawOval(76f, 42f, 92f, 52f, line)
        c.drawLine(76f, 54f, 74f, 44f, line)
    }

    /** My body: a little person with arms out. */
    private fun body(c: Canvas) {
        val skin = 0xFFFFCC99.toInt()
        fill.color = 0xFFF06292.toInt()
        path.rewind()
        path.moveTo(36f, 46f)
        path.lineTo(64f, 46f)
        path.lineTo(68f, 76f)
        path.lineTo(32f, 76f)
        path.close()
        c.drawPath(path, fill)
        c.drawPath(path, line)
        thick.color = skin
        for (s in floatArrayOf(-1f, 1f)) {
            c.drawLine(50f + s * 16f, 50f, 50f + s * 34f, 64f, thick)
            c.drawLine(50f + s * 9f, 78f, 50f + s * 10f, 94f, thick)
        }
        thick.color = Palette.INK
        fill.color = skin
        c.drawCircle(50f, 28f, 18f, fill)
        c.drawCircle(50f, 28f, 18f, line)
        fill.color = Palette.INK
        c.drawCircle(44f, 26f, 2.5f, fill)
        c.drawCircle(56f, 26f, 2.5f, fill)
        c.drawArc(43f, 26f, 57f, 38f, 20f, 140f, false, line)
    }

    /** A big arrow pointing left in an orange disc: "go back". Unlike undo, it's straight. */
    private fun back(c: Canvas) {
        fill.color = 0xFFFF8F00.toInt()
        c.drawCircle(50f, 50f, 40f, fill)
        c.drawCircle(50f, 50f, 40f, line)
        path.rewind()
        path.moveTo(25f, 50f)
        path.lineTo(48f, 28f)
        path.lineTo(48f, 41f)
        path.lineTo(73f, 41f)
        path.lineTo(73f, 59f)
        path.lineTo(48f, 59f)
        path.lineTo(48f, 72f)
        path.close()
        fill.color = Color.WHITE
        c.drawPath(path, fill)
        c.drawPath(path, line)
    }

    /** The start menu's "draw anything" picture: a sheet of paper with doodles and a crayon. */
    private fun freeDraw(c: Canvas) {
        c.save()
        c.rotate(-5f, 50f, 50f)
        fill.color = Color.WHITE
        c.drawRoundRect(8f, 12f, 88f, 88f, 6f, 6f, fill)
        c.drawRoundRect(8f, 12f, 88f, 88f, 6f, 6f, line)
        path.rewind()
        path.moveTo(16f, 46f)
        path.cubicTo(22f, 22f, 36f, 22f, 36f, 40f)
        path.cubicTo(36f, 58f, 52f, 58f, 54f, 36f)
        glowLine.color = 0xFFE53935.toInt()
        glowLine.alpha = 0xFF
        glowLine.strokeWidth = 6f
        c.drawPath(path, glowLine)
        PatternPen(Pattern.HEARTS, 0xFFFF6FB0.toInt(), 9f).apply {
            start(c, 18f, 76f)
            lineTo(c, 58f, 76f)
        }
        c.restore()
        c.save()
        c.translate(58f, 14f)
        c.scale(0.3f, 0.3f)
        Stickers.draw(c, Sticker.SUN, 0xFFFDD835.toInt())
        c.restore()
        c.save()
        c.translate(48f, 42f)
        c.scale(0.55f, 0.55f)
        crayon(c, 0xFF2962FF.toInt())
        c.restore()
    }

    /** The start menu's "color in a picture" picture: a flower, half colored in, and a crayon. */
    private fun coloring(c: Canvas) {
        fill.color = Color.WHITE
        c.drawRoundRect(8f, 8f, 92f, 92f, 6f, 6f, fill)
        c.drawRoundRect(8f, 8f, 92f, 92f, 6f, 6f, line)
        c.drawLine(44f, 56f, 44f, 86f, line)
        val petals = intArrayOf(0xFFE53935.toInt(), 0xFFFDD835.toInt(), 0xFF29B6F6.toInt(), Color.WHITE, Color.WHITE)
        for (i in petals.indices) {
            val a = Math.toRadians(-90.0 + i * 72)
            val x = 44f + 17f * Math.cos(a).toFloat()
            val y = 40f + 17f * Math.sin(a).toFloat()
            fill.color = petals[i]
            c.drawCircle(x, y, 11f, fill)
            c.drawCircle(x, y, 11f, line)
        }
        fill.color = 0xFFFF8F00.toInt()
        c.drawCircle(44f, 40f, 9f, fill)
        c.drawCircle(44f, 40f, 9f, line)
        c.save()
        c.translate(52f, 44f)
        c.scale(0.5f, 0.5f)
        crayon(c, 0xFF8E24AA.toInt())
        c.restore()
    }

    /** The start menu's "games" picture: a beach ball and a toy block. */
    private fun play(c: Canvas) {
        fill.color = 0xFF2962FF.toInt()
        c.drawRoundRect(54f, 34f, 92f, 72f, 6f, 6f, fill)
        fill.color = 0x55FFFFFF
        c.drawRoundRect(61f, 41f, 85f, 65f, 4f, 4f, fill)
        c.drawRoundRect(54f, 34f, 92f, 72f, 6f, 6f, line)
        val wedges = intArrayOf(0xFFE53935.toInt(), 0xFFFDD835.toInt(), 0xFF29B6F6.toInt())
        for (i in 0 until 6) {
            fill.color = if (i % 2 == 0) wedges[i / 2] else Color.WHITE
            c.drawArc(10f, 40f, 66f, 96f, -90f + i * 60f, 60f, true, fill)
        }
        c.drawCircle(38f, 68f, 28f, line)
        fill.color = Color.WHITE
        c.drawCircle(38f, 68f, 6f, fill)
        c.drawCircle(38f, 68f, 6f, line)
        sparkle(c, 28f, 22f, 12f)
    }

    /** Peekaboo: a sunny face peeking over a cloud. */
    private fun peekaboo(c: Canvas) {
        fill.color = 0xFFFDD835.toInt()
        c.drawCircle(50f, 44f, 30f, fill)
        c.drawCircle(50f, 44f, 30f, line)
        for (x in floatArrayOf(39f, 61f)) {
            fill.color = Color.WHITE
            c.drawCircle(x, 38f, 7f, fill)
            c.drawCircle(x, 38f, 7f, line)
            fill.color = Palette.INK
            c.drawCircle(x + 1f, 39f, 3.5f, fill)
        }
        path.rewind()
        val puff = Path()
        for (p in arrayOf(floatArrayOf(24f, 70f, 16f), floatArrayOf(45f, 62f, 20f), floatArrayOf(68f, 64f, 18f), floatArrayOf(84f, 76f, 12f))) {
            puff.rewind()
            puff.addCircle(p[0], p[1], p[2], Path.Direction.CW)
            path.op(puff, Path.Op.UNION)
        }
        puff.rewind()
        puff.addRoundRect(10f, 70f, 94f, 90f, 10f, 10f, Path.Direction.CW)
        path.op(puff, Path.Op.UNION)
        fill.color = Color.WHITE
        c.drawPath(path, fill)
        c.drawPath(path, line)
    }

    /** Where is it?: a magnifying glass with a duck in it. */
    private fun findIt(c: Canvas) {
        path.rewind()
        path.moveTo(64f, 56f)
        path.lineTo(92f, 84f)
        path.quadTo(92f, 92f, 84f, 92f)
        path.lineTo(56f, 64f)
        path.close()
        fill.color = 0xFF8D5524.toInt()
        c.drawPath(path, fill)
        c.drawPath(path, line)
        fill.color = 0xFF90A4AE.toInt()
        c.drawCircle(40f, 40f, 33f, fill)
        c.drawCircle(40f, 40f, 33f, line)
        fill.color = 0xFFB3E5FC.toInt()
        c.drawCircle(40f, 40f, 26f, fill)
        c.save()
        path.rewind()
        path.addCircle(40f, 40f, 26f, Path.Direction.CW)
        c.clipPath(path)
        c.translate(17f, 19f)
        c.scale(0.46f, 0.46f)
        Stickers.draw(c, Sticker.DUCK, 0xFFFDD835.toInt())
        c.restore()
        c.drawCircle(40f, 40f, 26f, line)
        glowLine.color = Color.WHITE
        glowLine.alpha = 0xCC
        glowLine.strokeWidth = 4f
        c.drawArc(22f, 22f, 58f, 58f, 195f, 55f, false, glowLine)
    }

    /** Rainbow bars on two rails, long to short, with a mallet. */
    private fun xylophone(c: Canvas) {
        fill.color = 0xFF8D5524.toInt()
        for (y in floatArrayOf(35f, 58f)) {
            c.drawRoundRect(6f, y, 94f, y + 7f, 3f, 3f, fill)
            c.drawRoundRect(6f, y, 94f, y + 7f, 3f, 3f, line)
        }
        for (i in 0 until 6) {
            val x = 9f + i * 14f
            val half = 34f - i * 4f
            fill.color = if (i < rainbow.size) rainbow[i] else 0xFF8E24AA.toInt()
            c.drawRoundRect(x, 50f - half, x + 11f, 50f + half, 4f, 4f, fill)
            c.drawRoundRect(x, 50f - half, x + 11f, 50f + half, 4f, 4f, line)
            fill.color = Color.WHITE
            c.drawCircle(x + 5.5f, 38.5f, 2f, fill)
            c.drawCircle(x + 5.5f, 61.5f, 2f, fill)
        }
        c.drawLine(74f, 18f, 94f, 4f, thick)
        fill.color = 0xFFE53935.toInt()
        c.drawCircle(70f, 21f, 8f, fill)
        c.drawCircle(70f, 21f, 8f, line)
    }

    /** Sort the colors: balls dropping into a bucket. */
    private fun colorSort(c: Canvas) {
        fun ball(x: Float, y: Float, r: Float, color: Int) {
            fill.color = color
            c.drawCircle(x, y, r, fill)
            c.drawCircle(x, y, r, line)
            fill.color = 0xAAFFFFFF.toInt()
            c.drawCircle(x - r * 0.35f, y - r * 0.35f, r * 0.28f, fill)
        }
        ball(50f, 48f, 12f, 0xFF2962FF.toInt())
        path.rewind()
        path.moveTo(20f, 54f)
        path.lineTo(80f, 54f)
        path.lineTo(72f, 94f)
        path.lineTo(28f, 94f)
        path.close()
        fill.color = 0xFF2962FF.toInt()
        c.drawPath(path, fill)
        c.drawPath(path, line)
        fill.color = 0x44FFFFFF
        c.drawRect(24f, 66f, 76f, 74f, fill)
        fill.color = 0xFF5C85FF.toInt()
        c.drawRoundRect(15f, 48f, 85f, 58f, 5f, 5f, fill)
        c.drawRoundRect(15f, 48f, 85f, 58f, 5f, 5f, line)
        ball(24f, 22f, 12f, 0xFFE53935.toInt())
        ball(74f, 20f, 12f, 0xFFFDD835.toInt())
    }

    /** Feed the animals: a hungry frog with its mouth wide open. */
    private fun feed(c: Canvas) {
        val green = 0xFF7CB342.toInt()
        fill.color = green
        for (x in floatArrayOf(28f, 72f)) {
            c.drawCircle(x, 30f, 15f, fill)
            c.drawCircle(x, 30f, 15f, line)
        }
        c.drawOval(6f, 32f, 94f, 92f, fill)
        c.drawOval(6f, 32f, 94f, 92f, line)
        for (x in floatArrayOf(28f, 72f)) {
            fill.color = Color.WHITE
            c.drawCircle(x, 29f, 9f, fill)
            c.drawCircle(x, 29f, 9f, line)
            fill.color = Palette.INK
            c.drawCircle(x, 30f, 4.5f, fill)
        }
        fill.color = 0xFFC62828.toInt()
        c.drawOval(26f, 52f, 74f, 84f, fill)
        fill.color = 0xFFFF8A80.toInt()
        c.drawOval(36f, 68f, 64f, 84f, fill)
        c.drawOval(26f, 52f, 74f, 84f, line)
        fill.color = 0x66FF6FB0
        c.drawCircle(16f, 60f, 6f, fill)
        c.drawCircle(84f, 60f, 6f, fill)
    }

    /** Follow the road: a dotted road from a car to a house. */
    /** A sticker [s] in [tint], shrunk to [scale] with its box's top-left at ([x], [y]). */
    private fun sticker(c: Canvas, s: Sticker, x: Float, y: Float, scale: Float, tint: Int) {
        c.save()
        c.translate(x, y)
        c.scale(scale, scale)
        Stickers.draw(c, s, tint)
        c.restore()
    }

    /** Learn, Food: an apple and a banana. */
    private fun food(c: Canvas) {
        path.rewind()
        path.moveTo(58f, 30f)
        path.cubicTo(90f, 30f, 96f, 60f, 82f, 78f)
        path.cubicTo(74f, 88f, 66f, 84f, 60f, 82f)
        path.cubicTo(54f, 84f, 46f, 88f, 38f, 78f)
        path.cubicTo(24f, 60f, 30f, 30f, 58f, 30f)
        path.close()
        fill.color = 0xFFE53935.toInt()
        c.drawPath(path, fill)
        c.drawPath(path, line)
        fill.color = 0x55FFFFFF
        c.drawOval(44f, 40f, 54f, 52f, fill)
        c.drawLine(58f, 31f, 60f, 18f, line)
        path.rewind()
        path.moveTo(61f, 22f)
        path.quadTo(72f, 10f, 82f, 16f)
        path.quadTo(72f, 28f, 61f, 22f)
        fill.color = 0xFF66BB6A.toInt()
        c.drawPath(path, fill)
        c.drawPath(path, line)
        path.rewind()
        path.moveTo(8f, 50f)
        path.quadTo(14f, 88f, 50f, 92f)
        path.quadTo(30f, 80f, 20f, 48f)
        path.close()
        fill.color = 0xFFFDD835.toInt()
        c.drawPath(path, fill)
        c.drawPath(path, line)
    }

    /** Learn, Sea animals: a fish over the waves. */
    private fun sea(c: Canvas) {
        path.rewind()
        path.moveTo(4f, 74f)
        for (i in 0 until 4) path.quadTo(16f + i * 24f, if (i % 2 == 0) 64f else 84f, 28f + i * 24f, 74f)
        path.lineTo(96f, 96f)
        path.lineTo(4f, 96f)
        path.close()
        fill.color = 0xFF29B6F6.toInt()
        c.drawPath(path, fill)
        c.drawPath(path, line)
        sticker(c, Sticker.FISH, 14f, 4f, 0.72f, 0xFFFF8F00.toInt())
    }

    /** Learn, Bugs: a butterfly and a ladybug. */
    private fun bugs(c: Canvas) {
        sticker(c, Sticker.BUTTERFLY, 2f, 2f, 0.7f, 0xFFBA68C8.toInt())
        fill.color = Palette.INK
        c.drawCircle(70f, 62f, 9f, fill)
        fill.color = 0xFFE53935.toInt()
        c.drawCircle(72f, 76f, 18f, fill)
        c.drawCircle(72f, 76f, 18f, line)
        c.drawLine(72f, 59f, 72f, 94f, line)
        fill.color = Palette.INK
        c.drawCircle(64f, 72f, 3.5f, fill)
        c.drawCircle(80f, 72f, 3.5f, fill)
        c.drawCircle(66f, 84f, 3.5f, fill)
        c.drawCircle(78f, 84f, 3.5f, fill)
    }

    /** Learn, Sky: the sun peeking out behind a cloud. */
    private fun sky(c: Canvas) {
        sticker(c, Sticker.SUN, 2f, 0f, 0.66f, 0xFFFDD835.toInt())
        sticker(c, Sticker.CLOUD, 30f, 36f, 0.68f, Color.WHITE)
    }

    /** Play, Bubble pop: three bubbles with shines. */
    private fun bubbles(c: Canvas) {
        for ((x, y, r) in listOf(Triple(34f, 62f, 26f), Triple(70f, 36f, 20f), Triple(76f, 78f, 14f))) {
            fill.color = 0x664FC3F7
            c.drawCircle(x, y, r, fill)
            c.drawCircle(x, y, r, line)
            fill.color = 0xCCFFFFFF.toInt()
            c.drawOval(x - r * 0.6f, y - r * 0.65f, x - r * 0.15f, y - r * 0.3f, fill)
        }
    }

    /** Play, Memory: one card face down, one face up with a star. */
    private fun memory(c: Canvas) {
        fill.color = 0xFF1E88E5.toInt()
        c.drawRoundRect(8f, 18f, 46f, 82f, 7f, 7f, fill)
        c.drawRoundRect(8f, 18f, 46f, 82f, 7f, 7f, line)
        glyph(c, "?", 27f, 50f, 30f, Color.WHITE)
        fill.color = Color.WHITE
        c.drawRoundRect(54f, 18f, 92f, 82f, 7f, 7f, fill)
        c.drawRoundRect(54f, 18f, 92f, 82f, 7f, 7f, line)
        matrix.setScale(14f, 14f)
        matrix.postTranslate(73f, 51f)
        Stamps.draw(c, StampKind.STAR, matrix, 0xFFFDD835.toInt(), fill, path)
    }

    /** Play, Shape sorter: a board with holes and a square on its way in. */
    private fun shapeSort(c: Canvas) {
        fill.color = 0xFFFFB74D.toInt()
        c.drawRoundRect(6f, 56f, 94f, 94f, 8f, 8f, fill)
        c.drawRoundRect(6f, 56f, 94f, 94f, 8f, 8f, line)
        fill.color = 0xFF5D4037.toInt()
        c.drawCircle(26f, 75f, 11f, fill)
        path.rewind()
        path.moveTo(50f, 64f)
        path.lineTo(62f, 86f)
        path.lineTo(38f, 86f)
        path.close()
        c.drawPath(path, fill)
        c.drawRect(66f, 65f, 86f, 85f, fill)
        fill.color = 0xFFE53935.toInt()
        c.drawRoundRect(62f, 12f, 88f, 38f, 3f, 3f, fill)
        c.drawRoundRect(62f, 12f, 88f, 38f, 3f, 3f, line)
        fill.color = 0xFF29B6F6.toInt()
        c.drawCircle(28f, 28f, 14f, fill)
        c.drawCircle(28f, 28f, 14f, line)
    }

    /** Play, Jigsaw: a picture in four pieces, one of them on its way in. */
    private fun jigsaw(c: Canvas) {
        val colors = intArrayOf(0xFF81D4FA.toInt(), 0xFFFFF59D.toInt(), 0xFFA5D6A7.toInt())
        val cells = listOf(floatArrayOf(10f, 22f), floatArrayOf(46f, 22f), floatArrayOf(10f, 58f))
        cells.forEachIndexed { i, (x, y) ->
            fill.color = colors[i]
            c.drawRect(x, y, x + 36f, y + 36f, fill)
            c.drawRect(x, y, x + 36f, y + 36f, line)
        }
        fill.color = 0xFFF48FB1.toInt()
        c.save()
        c.rotate(12f, 70f, 66f)
        c.drawRect(52f, 48f, 88f, 84f, fill)
        c.drawRect(52f, 48f, 88f, 84f, line)
        c.restore()
        fill.color = 0xFFFDD835.toInt()
        c.drawCircle(28f, 40f, 8f, fill)
    }

    private fun trace(c: Canvas) {
        path.rewind()
        path.moveTo(18f, 84f)
        path.cubicTo(56f, 98f, 30f, 40f, 72f, 36f)
        c.drawPath(path, dots)
        c.save()
        c.translate(54f, 0f)
        c.scale(0.42f, 0.42f)
        Stickers.draw(c, Sticker.HOUSE, 0xFFE53935.toInt())
        c.restore()
        c.save()
        c.translate(0f, 56f)
        c.scale(0.4f, 0.4f)
        Stickers.draw(c, Sticker.CAR, 0xFF29B6F6.toInt())
        c.restore()
    }

    /** A music note on a purple disc: "play me a song". */
    private fun listen(c: Canvas) {
        fill.color = 0xFF8E24AA.toInt()
        c.drawCircle(50f, 50f, 40f, fill)
        c.drawCircle(50f, 50f, 40f, line)
        fill.color = Color.WHITE
        c.drawOval(26f, 60f, 46f, 74f, fill)
        c.drawOval(52f, 54f, 72f, 68f, fill)
        c.drawRect(41f, 32f, 46f, 67f, fill)
        c.drawRect(67f, 26f, 72f, 61f, fill)
        path.rewind()
        path.moveTo(41f, 30f)
        path.lineTo(72f, 22f)
        path.lineTo(72f, 32f)
        path.lineTo(41f, 40f)
        path.close()
        c.drawPath(path, fill)
    }

    /** A magic wand with a star on the tip, for the glitter crayon. */
    private fun wand(c: Canvas, tint: Int) {
        c.drawLine(22f, 86f, 56f, 48f, thick)
        matrix.setScale(26f, 26f)
        matrix.postRotate(-12f)
        matrix.postTranslate(63f, 38f)
        path.rewind()
        Stamps.star.transform(matrix, path)
        fill.color = tint
        c.drawPath(path, fill)
        c.drawPath(path, line)
        sparkle(c, 22f, 30f, 10f)
        sparkle(c, 86f, 74f, 9f)
    }

    /** A glowing squiggle, for the glow crayon. */
    private fun glow(c: Canvas, tint: Int) {
        path.rewind()
        path.moveTo(16f, 72f)
        path.cubicTo(26f, 22f, 52f, 18f, 50f, 50f)
        path.cubicTo(48f, 82f, 76f, 84f, 84f, 30f)
        glowLine.color = tint
        glowLine.alpha = 0x45
        glowLine.strokeWidth = 26f
        c.drawPath(path, glowLine)
        glowLine.alpha = 0x99
        glowLine.strokeWidth = 15f
        c.drawPath(path, glowLine)
        glowLine.color = Color.WHITE
        glowLine.alpha = 0xE6
        glowLine.strokeWidth = 6f
        c.drawPath(path, glowLine)
    }

    /** A butterfly, the same on both sides, for the mirror crayon. */
    private fun butterfly(c: Canvas, tint: Int) {
        fill.color = tint
        for (side in 0..1) {
            c.save()
            if (side == 1) c.scale(-1f, 1f, 50f, 50f)
            path.rewind()
            path.moveTo(47f, 46f)
            path.cubicTo(32f, 12f, 4f, 16f, 10f, 38f)
            path.cubicTo(14f, 54f, 34f, 56f, 47f, 52f)
            path.close()
            c.drawPath(path, fill)
            c.drawPath(path, line)
            path.rewind()
            path.moveTo(47f, 56f)
            path.cubicTo(30f, 58f, 14f, 72f, 22f, 83f)
            path.cubicTo(31f, 93f, 45f, 78f, 47f, 62f)
            path.close()
            c.drawPath(path, fill)
            c.drawPath(path, line)
            c.drawLine(49f, 28f, 40f, 14f, line)
            c.restore()
        }
        fill.color = Palette.INK
        c.drawRoundRect(45f, 26f, 55f, 84f, 5f, 5f, fill)
    }

    /** A plain gray gear, meant to look boring to little ones. */
    private fun gear(c: Canvas) {
        fill.color = 0xFF90A4AE.toInt()
        for (i in 0 until 8) {
            c.save()
            c.rotate(i * 45f, 50f, 50f)
            c.drawRoundRect(42f, 8f, 58f, 28f, 4f, 4f, fill)
            c.restore()
        }
        c.drawCircle(50f, 50f, 30f, fill)
        fill.color = Color.WHITE
        c.drawCircle(50f, 50f, 12f, fill)
    }

    private fun crayon(c: Canvas, tint: Int) {
        c.save()
        c.rotate(40f, 50f, 50f)
        path.rewind()
        path.moveTo(38f, 34f)
        path.lineTo(46f, 12f)
        path.quadTo(50f, 7f, 54f, 12f)
        path.lineTo(62f, 34f)
        path.close()
        fill.color = tint
        c.drawPath(path, fill)
        c.drawPath(path, line)
        c.drawRoundRect(36f, 34f, 64f, 90f, 6f, 6f, fill)
        fill.color = 0x66FFFFFF
        c.drawRect(36f, 48f, 64f, 74f, fill)
        c.drawRoundRect(36f, 34f, 64f, 90f, 6f, 6f, line)
        c.drawLine(36f, 48f, 64f, 48f, line)
        c.drawLine(36f, 74f, 64f, 74f, line)
        c.restore()
    }

    private fun rainbow(c: Canvas) {
        for (i in rainbow.indices) {
            val r = 40f - i * 7f
            band.color = rainbow[i]
            c.drawArc(50f - r, 70f - r, 50f + r, 70f + r, 180f, 180f, false, band)
        }
        cloud(c, 18f, 72f, flip = false)
        cloud(c, 82f, 72f, flip = true)
    }

    private fun cloud(c: Canvas, cx: Float, cy: Float, flip: Boolean) {
        val s = if (flip) -1f else 1f
        path.rewind()
        val puffs = Path()
        puffs.addCircle(cx - 7f * s, cy + 3f, 8f, Path.Direction.CW)
        path.addCircle(cx + 4f * s, cy - 2f, 10f, Path.Direction.CW)
        path.op(puffs, Path.Op.UNION)
        puffs.rewind()
        puffs.addCircle(cx + 11f * s, cy + 5f, 7f, Path.Direction.CW)
        path.op(puffs, Path.Op.UNION)
        fill.color = Color.WHITE
        c.drawPath(path, fill)
        c.drawPath(path, line)
    }

    /** The stamp that's chosen, in the crayon color, with an outline so a pale one still shows. */
    private fun stamp(c: Canvas, kind: StampKind, tint: Int) {
        matrix.setScale(42f, 42f)
        matrix.postTranslate(50f, 52f)
        Stamps.draw(c, kind, matrix, tint, fill, path)
        path.rewind()
        Stamps.shape(kind).transform(matrix, path)
        c.drawPath(path, line)
    }

    private fun eraser(c: Canvas) {
        c.save()
        c.rotate(40f, 50f, 50f)
        fill.color = 0xFFF8BBD0.toInt()
        c.drawRoundRect(32f, 18f, 68f, 84f, 8f, 8f, fill)
        c.save()
        c.clipRect(32f, 18f, 68f, 48f)
        fill.color = 0xFF90CAF9.toInt()
        c.drawRoundRect(32f, 18f, 68f, 84f, 8f, 8f, fill)
        c.restore()
        c.drawRoundRect(32f, 18f, 68f, 84f, 8f, 8f, line)
        c.drawLine(32f, 48f, 68f, 48f, line)
        c.restore()
    }

    private fun sizes(c: Canvas, tint: Int, sizeIndex: Int) {
        val xs = floatArrayOf(17f, 43f, 76f)
        val rs = floatArrayOf(7f, 11f, 17f)
        for (i in xs.indices) {
            if (i == sizeIndex) {
                fill.color = tint
                c.drawCircle(xs[i], 50f, rs[i], fill)
                c.drawCircle(xs[i], 50f, rs[i], line)
            } else {
                fill.color = LIGHT_GRAY
                c.drawCircle(xs[i], 50f, rs[i], fill)
            }
        }
    }

    private fun undo(c: Canvas) {
        path.rewind()
        path.moveTo(36f, 38f)
        path.lineTo(60f, 38f)
        path.cubicTo(86f, 38f, 86f, 80f, 60f, 80f)
        path.lineTo(44f, 80f)
        c.drawPath(path, thick)
        path.rewind()
        path.moveTo(14f, 38f)
        path.lineTo(38f, 20f)
        path.lineTo(38f, 56f)
        path.close()
        fill.color = Palette.INK
        c.drawPath(path, fill)
        c.drawPath(path, line)
    }

    private fun picture(c: Canvas) {
        fill.color = Color.WHITE
        c.drawRoundRect(12f, 20f, 88f, 80f, 9f, 9f, fill)
        fill.color = 0xFFFDD835.toInt()
        c.drawCircle(32f, 38f, 8f, fill)
        path.rewind()
        path.moveTo(16f, 78f)
        path.lineTo(40f, 48f)
        path.lineTo(56f, 64f)
        path.lineTo(68f, 52f)
        path.lineTo(84f, 78f)
        path.close()
        fill.color = 0xFF66BB6A.toInt()
        c.drawPath(path, fill)
        c.drawRoundRect(12f, 20f, 88f, 80f, 9f, 9f, line)
    }

    private fun cleanPage(c: Canvas) {
        path.rewind()
        path.moveTo(28f, 14f)
        path.lineTo(58f, 14f)
        path.lineTo(74f, 30f)
        path.lineTo(74f, 86f)
        path.lineTo(28f, 86f)
        path.close()
        fill.color = Color.WHITE
        c.drawPath(path, fill)
        c.drawPath(path, line)
        path.rewind()
        path.moveTo(58f, 14f)
        path.lineTo(58f, 30f)
        path.lineTo(74f, 30f)
        c.drawPath(path, line)
        sparkle(c, 22f, 32f, 13f)
        sparkle(c, 80f, 70f, 11f)
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

    private fun close(c: Canvas) {
        fill.color = Color.WHITE
        c.drawCircle(50f, 50f, 38f, fill)
        c.drawCircle(50f, 50f, 38f, line)
        c.drawLine(36f, 36f, 64f, 64f, thick)
        c.drawLine(64f, 36f, 36f, 64f, thick)
    }
}
