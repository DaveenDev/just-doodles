package com.daveendev.justdoodles

import android.content.res.AssetManager
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Region
import android.graphics.RegionIterator
import android.util.Log
import kotlin.math.max
import kotlin.math.min

/** One outline picture from `assets/pages`, ready to be drawn at any size. */
class ColoringPage(val id: String, private val drawing: SvgParser.Drawing) {

    /** What the picture is ("Fish"), said out loud when the page is picked. */
    val title: String? get() = drawing.title

    /** A named part of the picture, where it lies in a [partsFor] area, and how big it is. */
    class Part(val name: String, val area: Region, val size: Long)

    /** Draws the outline centered in a [width] x [height] area, with lines in proportion to its size. */
    fun drawOutline(canvas: Canvas, width: Int, height: Int) {
        if (!fit(width, height)) return
        linePaint.strokeWidth = lineWidthFor(width, height)
        for (shape in drawing.shapes) {
            shape.transform(matrix, scratch)
            canvas.drawPath(scratch, linePaint)
        }
    }

    /**
     * The named parts of the picture (from the `<title>`s inside its shapes), placed as
     * [drawOutline] places them in a [width] x [height] area. An open shape counts as closed
     * by a straight line between its ends, which suits a fin or an ear drawn onto a body.
     */
    fun partsFor(width: Int, height: Int): List<Part> {
        if (!fit(width, height)) return emptyList()
        val clip = Region(0, 0, width, height)
        val parts = ArrayList<Part>()
        drawing.shapes.forEachIndexed { i, shape ->
            val name = drawing.names.getOrNull(i) ?: return@forEachIndexed
            shape.transform(matrix, scratch)
            val area = Region()
            if (!area.setPath(scratch, clip)) return@forEachIndexed
            var size = 0L
            val rect = Rect()
            val it = RegionIterator(area)
            while (it.next(rect)) size += rect.width().toLong() * rect.height()
            parts += Part(name, area, size)
        }
        return parts
    }

    /** Sets [matrix] to center the picture in a [width] x [height] area. False if it doesn't fit at all. */
    private fun fit(width: Int, height: Int): Boolean {
        val stroke = lineWidthFor(width, height)
        val vb = drawing.viewBox
        val margin = min(width, height) * 0.04f + stroke
        val scale = min((width - 2 * margin) / vb.width(), (height - 2 * margin) / vb.height())
        if (scale <= 0f) return false
        matrix.setTranslate(-vb.left, -vb.top)
        matrix.postScale(scale, scale)
        matrix.postTranslate((width - vb.width() * scale) / 2f, (height - vb.height() * scale) / 2f)
        return true
    }

    companion object {
        /** Id of the first sheet of white paper (see [Paper]), from before there could be more. */
        const val BLANK = "blank"

        private val matrix = Matrix()
        private val scratch = Path()
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Palette.INK
        }

        fun lineWidthFor(width: Int, height: Int) = max(2f, min(width, height) * 0.013f)

        /**
         * Loads the categories: one folder in `assets/pages` each ("01-animals"), with its pages
         * inside, all in file-name order. Broken files and empty folders are skipped.
         */
        fun loadCategories(assets: AssetManager): List<Category> =
            (assets.list("pages") ?: emptyArray()).sorted().mapNotNull { folder ->
                val pages = (assets.list("pages/$folder") ?: emptyArray())
                    .filter { it.endsWith(".svg") }
                    // By the number at the start of the name, so "91-cow" comes before "100-scarecrow".
                    .sortedWith(compareBy({ it.takeWhile(Char::isDigit).toIntOrNull() ?: Int.MAX_VALUE }, { it }))
                    .mapNotNull { name -> load(assets, "pages/$folder/$name") }
                if (pages.isEmpty()) null else Category(folder, Category.titleFor(folder), pages)
            }

        /** Every page in one assets folder (such as `learn`), in file-name order. */
        fun loadFolder(assets: AssetManager, folder: String): List<ColoringPage> =
            (assets.list(folder) ?: emptyArray()).filter { it.endsWith(".svg") }.sorted()
                .mapNotNull { name -> load(assets, "$folder/$name") }

        private fun load(assets: AssetManager, file: String): ColoringPage? = try {
            val drawing = assets.open(file).use(SvgParser::parse)
            // The id is just the file name, so saved pictures don't depend on the folder.
            ColoringPage(file.substringAfterLast('/').removeSuffix(".svg"), drawing)
        } catch (e: Exception) {
            Log.w("JustDoodles", "Skipping coloring page $file", e)
            null
        }
    }
}

/** A group of coloring pages, such as the animals. [title] is said out loud and shown on its card. */
class Category(val id: String, val title: String, val pages: List<ColoringPage>) {
    companion object {
        /** "04-things-that-go" becomes "Things that go". */
        fun titleFor(folder: String) = folder.substringAfter('-').replace('-', ' ')
            .replaceFirstChar { it.uppercaseChar() }
    }
}
