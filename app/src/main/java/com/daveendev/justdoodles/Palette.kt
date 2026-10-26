package com.daveendev.justdoodles

import android.graphics.Color
import kotlin.math.roundToInt

/** New tools go at the end: the chosen tool is saved by its position in this list. */
enum class Tool { BRUSH, MAGIC, STAMP, ERASER, PATTERN, STICKER }

/** What the magic crayon does. Tapping the magic button again moves on to the next one. */
enum class Magic { RAINBOW, GLITTER, GLOW, MIRROR }

/** The two things to do from the start menu: draw on blank paper, or color in a picture. */
enum class Mode { FREE, COLOR }

object Palette {
    /** Bright crayon colors, two per row, so they flow like a rainbow down the screen. */
    val colors = intArrayOf(
        0xFFE53935.toInt(), // red
        0xFFFF8F00.toInt(), // orange
        0xFFFDD835.toInt(), // yellow
        0xFF8BC34A.toInt(), // lime
        0xFF2E9D4A.toInt(), // green
        0xFF29B6F6.toInt(), // sky blue
        0xFF2962FF.toInt(), // blue
        0xFF8E24AA.toInt(), // purple
        0xFFFF6FB0.toInt(), // pink
        0xFF8D5524.toInt(), // brown
        0xFFFFCC99.toInt(), // peach
        0xFF263238.toInt(), // black
    )

    /** Crayon widths in dp: small, medium, big. Toddler fingers are wide, so even "small" is chunky. */
    val brushSizesDp = floatArrayOf(10f, 24f, 46f)

    /** Size of one heart, dot or zigzag of the pattern crayon, in dp, for each crayon size. */
    val patternSizesDp = floatArrayOf(18f, 30f, 48f)

    /** How wide a sticker is, in dp, for each crayon size. */
    val stickerSizesDp = floatArrayOf(72f, 112f, 160f)

    const val INK = 0xFF263238.toInt()
    const val PAPER = 0xFFFFFFFF.toInt()

    /** Black paper, for bright crayons and glow. */
    const val DARK_PAPER = 0xFF1C2329.toInt()

    /** Blends [a] toward [b] by [t] (0 = all a, 1 = all b). */
    fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).roundToInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).roundToInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).roundToInt(),
    )
}
