package com.daveendev.justdoodles

import android.graphics.Bitmap
import android.graphics.Canvas
import kotlin.math.min
import kotlin.random.Random

/**
 * Colors in a coloring page the way a child would with tap-to-fill: every closed shape gets a
 * bright crayon color, the background stays white, and the outline goes on top. Used by the
 * games for pictures the child hasn't colored yet. A page always comes out in the same colors.
 */
object AutoColor {

    /** Crayon colors a picture gets, all but black. */
    private val COLORS = Palette.colors.copyOfRange(0, Palette.colors.size - 1)

    fun render(page: ColoringPage, width: Int, height: Int): Bitmap {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val art = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        page.drawOutline(Canvas(art), w, h)
        val px = IntArray(w * h)
        art.getPixels(px, 0, w, 0, 0, w, h)
        val line = BooleanArray(w * h) { (px[it] ushr 24) >= 128 }

        // Number the open areas: -2 is the background (reachable from the edge), 0.. are shapes.
        val shape = IntArray(w * h) { -1 }
        val queue = IntArray(w * h)
        fun flood(start: Int, id: Int): Int {
            var head = 0
            var tail = 0
            shape[start] = id
            queue[tail++] = start
            while (head < tail) {
                val i = queue[head++]
                val x = i % w
                if (x > 0 && !line[i - 1] && shape[i - 1] == -1) { shape[i - 1] = id; queue[tail++] = i - 1 }
                if (x < w - 1 && !line[i + 1] && shape[i + 1] == -1) { shape[i + 1] = id; queue[tail++] = i + 1 }
                if (i >= w && !line[i - w] && shape[i - w] == -1) { shape[i - w] = id; queue[tail++] = i - w }
                if (i < w * (h - 1) && !line[i + w] && shape[i + w] == -1) { shape[i + w] = id; queue[tail++] = i + w }
            }
            return tail
        }
        fun edge(i: Int) {
            if (!line[i] && shape[i] == -1) flood(i, BACKGROUND)
        }
        for (x in 0 until w) { edge(x); edge((h - 1) * w + x) }
        for (y in 0 until h) { edge(y * w); edge(y * w + w - 1) }
        val sizes = ArrayList<Int>()
        for (i in shape.indices) {
            if (!line[i] && shape[i] == -1) sizes += flood(i, sizes.size)
        }

        // A color for each shape, never the same as the one before. Specks too small for a
        // finger (eye twinkles, dots) stay white, like they would on a page colored by hand.
        val speck = min(w, h).let { it * it } / 600
        val random = Random(page.id.hashCode())
        var last = -1
        val colorOf = IntArray(sizes.size) { s ->
            if (sizes[s] < speck) Palette.PAPER
            else {
                var k = random.nextInt(COLORS.size)
                if (k == last) k = (k + 1 + random.nextInt(COLORS.size - 1)) % COLORS.size
                last = k
                COLORS[k]
            }
        }

        for (i in px.indices) px[i] = shape[i].let { if (it >= 0) colorOf[it] else Palette.PAPER }
        // Spread the colors a little under the outline, so no pale seam shows along its soft edge.
        repeat(2) {
            val before = shape.copyOf()
            for (i in px.indices) {
                if (!line[i] || before[i] != -1) continue
                val x = i % w
                val from = when {
                    x > 0 && before[i - 1] >= 0 -> i - 1
                    x < w - 1 && before[i + 1] >= 0 -> i + 1
                    i >= w && before[i - w] >= 0 -> i - w
                    i < w * (h - 1) && before[i + w] >= 0 -> i + w
                    else -> continue
                }
                shape[i] = before[from]
                px[i] = px[from]
            }
        }
        art.setPixels(px, 0, w, 0, 0, w, h)
        page.drawOutline(Canvas(art), w, h)
        return art
    }

    private const val BACKGROUND = -2
}
