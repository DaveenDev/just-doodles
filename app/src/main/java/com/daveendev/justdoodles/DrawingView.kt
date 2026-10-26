package com.daveendev.justdoodles

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.SparseArray
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The paper.
 *
 * Paint lives in [doc], an opaque bitmap. On a coloring page the outline lives in [lineArt],
 * drawn on top so the lines stay crisp however much paint goes over them, and a quick tap
 * fills the shape under the finger. Every finger paints its own line, because toddlers
 * rarely use just one.
 *
 * Blank paper has two more tools: the pattern crayon, which draws hearts, zigzags, paw prints
 * and so on along the finger, and stickers, which follow the finger while it's down and drop
 * onto the paper when it lifts.
 *
 * The document keeps its size when the view changes size (rotation, split screen); it is
 * scaled to fit instead, so a picture never gets stretched or cropped.
 */
class DrawingView(context: Context) : View(context) {

    // Setting the tool or the magic redraws, since the magic mirror shows a guide line.
    var tool = Tool.BRUSH
        set(value) { field = value; invalidate() }
    var magic = Magic.RAINBOW
        set(value) { field = value; invalidate() }
    var color = Palette.colors[0]
    var sizeIndex = 1
    var stamp = StampKind.STAR
    var pattern = Pattern.HEARTS
    var sticker = Sticker.SUN
    var sounds: Sounds? = null

    /** The paper's color: white, or black for black paper. Coloring pages are always white. */
    var paper = Palette.PAPER
        private set

    /** True when the picture changed since it was last saved. */
    var dirty = false

    /** Called whenever undo becomes available or unavailable. */
    var onHistoryChanged: (() -> Unit)? = null

    /** Called once when most of a coloring page's shapes have been colored in. */
    var onPageComplete: (() -> Unit)? = null

    /** Called with a part's name ("Wheel") when a tap fills a named part of a coloring page. */
    var onPartTapped: ((String) -> Unit)? = null

    val canUndo get() = undoStack.isNotEmpty()

    private val density = resources.displayMetrics.density
    private val tapSlop = ViewConfiguration.get(context).scaledTouchSlop * 1.5f

    private var doc: Bitmap? = null
    private var docCanvas: Canvas? = null
    private var page: ColoringPage? = null
    private var lineArt: Bitmap? = null
    /** One entry per document pixel: true where the outline is, so fills stop there. */
    private var lineMask: BooleanArray? = null
    /** The coloring page's named parts, in document pixels. */
    private var parts: List<ColoringPage.Part> = emptyList()
    /** Saved picture waiting for the document to be created. */
    private var pending: Bitmap? = null

    // Document -> view transform: uniform scale plus centering offset.
    private var viewScale = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private val toView = Matrix()

    private val undoStack = ArrayDeque<Bitmap>()
    private var maxUndo = 5
    /** True while fingers are down and an undo step was already saved for this touch. */
    private var actionOpen = false

    private val strokes = SparseArray<Stroke>()
    private var rainbowHue = 0f
    /** The rainbow's hue where the segment being drawn starts; it shades smoothly to [rainbowHue]. */
    private var rainbowFrom = 0f
    private val random = Random(System.nanoTime())
    private var lastMoveTime = 0L

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    /**
     * The glow's halo is see-through, so wherever two pieces of it overlap it gets darker. Its
     * segments therefore have flat ends, which meet edge to edge because the line is smoothed
     * through midpoints; only the two ends of the stroke get a round cap ([glowCapPaint]).
     */
    private val glowPaint = Paint(strokePaint).apply { strokeCap = Paint.Cap.BUTT }
    private val glowCapPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val capPath = Path()
    private var glowBlurFor = -1f
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val segment = Path()
    private val corePath = Path()
    private val stampPath = Path()
    private val stampMatrix = Matrix()
    private val mirrored = Path()
    private val mirrorMatrix = Matrix()
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 3f * density
        color = 0x66909090
        pathEffect = DashPathEffect(floatArrayOf(10f * density, 12f * density), 0f)
    }

    /** One finger on the paper, in document coordinates. */
    private class Stroke(val downX: Float, val downY: Float) {
        /** False until we know this is a drag rather than a tap. */
        var drawing = false
        var lastX = downX
        var lastY = downY
        var midX = downX
        var midY = downY
        var sinceStamp = 0f

        /** The last few points of a glow stroke, to redraw its bright core over the newest halo. */
        val coreX = FloatArray(CORE_POINTS)
        val coreY = FloatArray(CORE_POINTS)
        var coreCount = 0

        /** Whether the glow's start cap is drawn yet, and which way the line was last heading. */
        var glowCapped = false
        var headingX = 0f
        var headingY = 0f

        /** Glitter twinkles waiting for the line to move past them, so it doesn't paint over them. */
        val twinkles = ArrayList<FloatArray>()

        /** Draws this finger's line when the pattern crayon is in use. */
        var pen: PatternPen? = null

        fun addCorePoint(x: Float, y: Float) {
            if (coreCount == CORE_POINTS) {
                System.arraycopy(coreX, 1, coreX, 0, CORE_POINTS - 1)
                System.arraycopy(coreY, 1, coreY, 0, CORE_POINTS - 1)
                coreCount--
            }
            coreX[coreCount] = x
            coreY[coreCount] = y
            coreCount++
        }
    }

    // ---- Pages ------------------------------------------------------------------------------

    /**
     * Shows [newPage], or blank paper in [paperColor] when it is null, starting from [content]
     * if it was saved before.
     */
    fun setPage(newPage: ColoringPage?, content: Bitmap?, paperColor: Int = Palette.PAPER) {
        page = newPage
        paper = if (newPage != null) Palette.PAPER else paperColor
        strokes.clear()
        clearUndo()
        dirty = false
        pending?.recycle()
        pending = content
        createDocument()
    }

    /** Wipes the page clean. The previous picture can still be brought back with undo. */
    fun clearPage() {
        val c = docCanvas ?: return
        pushUndo()
        c.drawColor(paper)
        dirty = true
        celebrated = false
        invalidate()
    }

    fun undo() {
        val snapshot = undoStack.removeLastOrNull() ?: return
        // Both bitmaps are opaque, so drawing one over the other replaces every pixel.
        docCanvas?.drawBitmap(snapshot, 0f, 0f, null)
        snapshot.recycle()
        dirty = true
        invalidate()
        onHistoryChanged?.invoke()
    }

    /** A copy of the picture for saving, or null before the view has a size. */
    fun snapshot(): Bitmap? = doc?.copy(Bitmap.Config.ARGB_8888, false)

    /** A small copy of the picture, [width] pixels wide. */
    fun thumbnail(width: Int): Bitmap? = doc?.let {
        Bitmap.createScaledBitmap(it, width, max(1, it.height * width / it.width), true)
    }

    private fun createDocument() {
        val content = pending
        val w = content?.width ?: width
        val h = content?.height ?: height
        if (w <= 0 || h <= 0) return // Not laid out yet; onSizeChanged comes back here.
        pending = null

        val bmp = if (content != null && content.isMutable && content.config == Bitmap.Config.ARGB_8888) {
            content
        } else {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b ->
                val c = Canvas(b)
                c.drawColor(paper)
                if (content != null) {
                    c.drawBitmap(content, 0f, 0f, null)
                    content.recycle()
                }
            }
        }
        doc?.recycle()
        doc = bmp
        docCanvas = Canvas(bmp)
        maxUndo = (Runtime.getRuntime().maxMemory() / 4 / (w.toLong() * h * 4)).toInt().coerceIn(1, 10)
        buildLineArt(w, h)
        // A page that was already finished last time doesn't celebrate again straight away.
        celebrated = isPageComplete()
        updateTransform()
        invalidate()
    }

    private fun buildLineArt(w: Int, h: Int) {
        lineArt?.recycle()
        lineArt = null
        lineMask = null
        insideGrid = null
        shapeOf = null
        parts = emptyList()
        val p = page ?: return
        val art = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        p.drawOutline(Canvas(art), w, h)
        parts = p.partsFor(w, h)
        val px = IntArray(w * h)
        art.getPixels(px, 0, w, 0, 0, w, h)
        val mask = BooleanArray(w * h) { (px[it] ushr 24) >= 128 }
        lineMask = mask
        lineArt = art
        findInside(mask, w, h)
    }

    // ---- Layout and drawing -----------------------------------------------------------------

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (doc == null) createDocument() else updateTransform()
    }

    private fun updateTransform() {
        val d = doc ?: return
        if (width == 0 || height == 0) return
        viewScale = min(width / d.width.toFloat(), height / d.height.toFloat())
        offsetX = (width - d.width * viewScale) / 2f
        offsetY = (height - d.height * viewScale) / 2f
        toView.setScale(viewScale, viewScale)
        toView.postTranslate(offsetX, offsetY)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(paper)
        val d = doc ?: return
        canvas.save()
        canvas.concat(toView)
        canvas.drawBitmap(d, 0f, 0f, bitmapPaint)
        lineArt?.let { canvas.drawBitmap(it, 0f, 0f, bitmapPaint) }
        if (tool == Tool.STICKER) {
            // Stickers still under a finger, a little bigger, as if lifted off the paper.
            for (i in 0 until strokes.size()) {
                val s = strokes.valueAt(i)
                drawSticker(canvas, s.lastX, s.lastY, stickerSize() * LIFTED)
            }
        }
        canvas.restore()
        if (isMirror) {
            // A dotted line down the middle, where the mirror is. It's never part of the picture.
            val x = offsetX + d.width * viewScale / 2f
            canvas.drawLine(x, offsetY + 12f * density, x, offsetY + d.height * viewScale - 12f * density, guidePaint)
        }
    }

    // ---- Touch ------------------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val s = Stroke(docX(e.getX(i)), docY(e.getY(i)))
                strokes.put(e.getPointerId(i), s)
                // Blank paper has nothing to fill, so start painting right away.
                // A sticker only shows under the finger until it lifts.
                if (page == null && tool != Tool.STICKER) startStroke(s)
                lastMoveTime = e.eventTime
            }
            MotionEvent.ACTION_MOVE -> {
                var moved = 0f
                for (i in 0 until e.pointerCount) {
                    val s = strokes[e.getPointerId(i)] ?: continue
                    for (h in 0 until e.historySize) {
                        moved += moveStroke(s, docX(e.getHistoricalX(i, h)), docY(e.getHistoricalY(i, h)))
                    }
                    moved += moveStroke(s, docX(e.getX(i)), docY(e.getY(i)))
                }
                updateScribble(moved, e.eventTime)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = e.actionIndex
                val id = e.getPointerId(i)
                strokes[id]?.let { endStroke(it, docX(e.getX(i)), docY(e.getY(i))) }
                strokes.remove(id)
                if (e.actionMasked == MotionEvent.ACTION_UP) touchFinished()
            }
            MotionEvent.ACTION_CANCEL -> {
                strokes.clear()
                touchFinished()
            }
        }
        invalidate()
        return true
    }

    private fun touchFinished() {
        actionOpen = false
        sounds?.stopScribble()
        // Check whether the page is finished once the little artist pauses.
        removeCallbacks(completionCheck)
        if (page != null && !celebrated) postDelayed(completionCheck, 350)
    }

    /** Crayon scratch sound, louder the faster the fingers move. */
    private fun updateScribble(movedDoc: Float, time: Long) {
        val dt = (time - lastMoveTime).coerceAtLeast(8L) / 1000f
        lastMoveTime = time
        if (tool == Tool.STAMP || tool == Tool.STICKER) return
        val dpPerSecond = movedDoc * viewScale / density / dt
        sounds?.scribble(dpPerSecond / 1200f)
    }

    private fun docX(x: Float) = (x - offsetX) / viewScale
    private fun docY(y: Float) = (y - offsetY) / viewScale

    /** Sizes are chosen in dp on screen; this converts them to document pixels. */
    private fun dpToDoc(dp: Float) = dp * density / viewScale

    private fun brushWidth() = dpToDoc(Palette.brushSizesDp[sizeIndex] * if (tool == Tool.ERASER) 1.6f else 1f)

    private fun stampRadius() = dpToDoc(12f + Palette.brushSizesDp[sizeIndex] * 0.9f)

    private fun patternUnit() = dpToDoc(Palette.patternSizesDp[sizeIndex])

    private fun stickerSize() = dpToDoc(Palette.stickerSizesDp[sizeIndex])

    private val isRainbow get() = tool == Tool.MAGIC && magic == Magic.RAINBOW

    private val isMirror get() = tool == Tool.MAGIC && magic == Magic.MIRROR

    private fun strokeColor() = when {
        tool == Tool.ERASER -> paper
        isRainbow -> rainbowColor(rainbowHue)
        else -> color
    }

    private fun rainbowColor(hue: Float) = Color.HSVToColor(floatArrayOf(hue, 0.8f, 1f))

    private fun startStroke(s: Stroke) {
        s.drawing = true
        beginAction()
        if (tool == Tool.STAMP) {
            stampAt(s.downX, s.downY)
        } else if (tool == Tool.PATTERN) {
            docCanvas?.let { c ->
                s.pen = PatternPen(pattern, color, patternUnit(), random).also { it.start(c, s.downX, s.downY) }
            }
        } else {
            // A dot where the finger lands, in whatever style the crayon has.
            segment.rewind()
            segment.moveTo(s.downX, s.downY)
            segment.lineTo(s.downX + 0.1f, s.downY)
            rainbowFrom = rainbowHue
            drawSegment(s, s.downX, s.downY, s.downX, s.downY)
        }
        dirty = true
    }

    /** Continues a stroke to (x, y). Returns how far it moved, in document pixels. */
    private fun moveStroke(s: Stroke, x: Float, y: Float): Float {
        if (tool == Tool.STICKER) {
            // The sticker just follows the finger; it goes onto the paper when the finger lifts.
            s.lastX = x
            s.lastY = y
            return 0f
        }
        if (!s.drawing) {
            if (hypot(x - s.downX, y - s.downY) < tapSlop / viewScale) return 0f
            startStroke(s)
        }
        val dist = hypot(x - s.lastX, y - s.lastY)
        if (dist < 1f) return 0f

        if (tool == Tool.STAMP) {
            s.sinceStamp += dist
            if (s.sinceStamp >= stampRadius() * 2.2f) {
                stampAt(x, y)
                s.sinceStamp = 0f
            }
            s.lastX = x
            s.lastY = y
            return dist
        }

        s.pen?.let { pen ->
            docCanvas?.let { pen.lineTo(it, x, y) }
            s.lastX = x
            s.lastY = y
            dirty = true
            return dist
        }

        if (isRainbow) {
            rainbowFrom = rainbowHue
            rainbowHue = (rainbowHue + dist * viewScale / density * 0.5f) % 360f
        }
        // Smooth the line by curving through the midpoints between touch samples.
        val mx = (s.lastX + x) / 2f
        val my = (s.lastY + y) / 2f
        segment.rewind()
        segment.moveTo(s.midX, s.midY)
        segment.quadTo(s.lastX, s.lastY, mx, my)
        drawSegment(s, s.midX, s.midY, mx, my)
        s.midX = mx
        s.midY = my
        s.lastX = x
        s.lastY = y
        return dist
    }

    private fun endStroke(s: Stroke, x: Float, y: Float) {
        if (tool == Tool.STICKER) {
            beginAction()
            placeSticker(x, y)
            dirty = true
            return
        }
        s.pen?.let { pen ->
            moveStroke(s, x, y)
            docCanvas?.let { pen.finish(it) }
            return
        }
        if (s.drawing) {
            if (tool != Tool.STAMP) {
                moveStroke(s, x, y)
                segment.rewind()
                segment.moveTo(s.midX, s.midY)
                segment.lineTo(s.lastX, s.lastY)
                rainbowFrom = rainbowHue
                drawSegment(s, s.midX, s.midY, s.lastX, s.lastY, last = true)
                docCanvas?.let { flushTwinkles(it, s, all = true) }
            }
            return
        }
        // The finger went down and up without moving: a tap on a coloring page.
        beginAction()
        if (tool == Tool.STAMP) {
            stampAt(s.downX, s.downY)
        } else {
            val filled = fill(s.downX, s.downY)
            if (isMirror) fillMirrored(s.downX, s.downY, filled)
        }
        dirty = true
    }

    /**
     * Draws [segment] of stroke [s], which runs from (x0, y0) to (x1, y1), in the current crayon's
     * style. [last] is true for the piece that ends the stroke.
     */
    private fun drawSegment(s: Stroke, x0: Float, y0: Float, x1: Float, y1: Float, last: Boolean = false) {
        val c = docCanvas ?: return
        val w = brushWidth()
        when {
            tool == Tool.MAGIC && magic == Magic.GLOW -> {
                // A soft halo in the crayon color...
                if (glowBlurFor != w) {
                    glowPaint.maskFilter = BlurMaskFilter(w * 0.45f, BlurMaskFilter.Blur.NORMAL)
                    glowBlurFor = w
                }
                glowPaint.color = color
                glowPaint.alpha = 70
                glowPaint.strokeWidth = w * 1.3f
                glowCapPaint.color = glowPaint.color
                glowCapPaint.maskFilter = glowPaint.maskFilter
                val segLength = hypot(x1 - x0, y1 - y0)
                if (segLength > 0.01f) {
                    val ux = (x1 - x0) / segLength
                    val uy = (y1 - y0) / segLength
                    if (!s.glowCapped) {
                        glowCap(c, x0, y0, -ux, -uy, w * 0.65f)
                        s.glowCapped = true
                    }
                    c.drawPath(segment, glowPaint)
                    s.headingX = ux
                    s.headingY = uy
                }
                if (last) {
                    if (s.glowCapped) glowCap(c, x1, y1, s.headingX, s.headingY, w * 0.65f)
                    else c.drawCircle(x1, y1, w * 0.65f, glowCapPaint) // A dot: the finger never moved.
                }
                // ...with a bright core. The new halo spills back over the last bit of core,
                // so redraw the core along the stroke's recent points, not just this segment.
                if (s.coreCount == 0) s.addCorePoint(x0, y0)
                s.addCorePoint(x1, y1)
                corePath.rewind()
                var i = s.coreCount - 1
                corePath.moveTo(s.coreX[i], s.coreY[i])
                var length = 0f
                while (i > 0 && length < w * 2.5f) {
                    length += hypot(s.coreX[i] - s.coreX[i - 1], s.coreY[i] - s.coreY[i - 1])
                    i--
                    corePath.lineTo(s.coreX[i], s.coreY[i])
                }
                if (s.coreCount == 1) corePath.lineTo(x1 + 0.1f, y1)
                strokePaint.color = Palette.mix(color, Color.WHITE, 0.6f)
                strokePaint.strokeWidth = w * 0.4f
                c.drawPath(corePath, strokePaint)
            }
            tool == Tool.MAGIC && magic == Magic.GLITTER -> {
                strokePaint.color = color
                strokePaint.strokeWidth = w * 0.7f
                c.drawPath(segment, strokePaint)
                // Sprinkle twinkles along and around the line, once the line has moved on.
                val length = hypot(x1 - x0, y1 - y0)
                val count = (length / (w * 0.3f)).toInt() + if (random.nextFloat() < 0.5f) 1 else 0
                repeat(count) {
                    val t = random.nextFloat()
                    val ox = (random.nextFloat() - 0.5f) * w * 1.3f
                    val oy = (random.nextFloat() - 0.5f) * w * 1.3f
                    val r = w * (0.12f + random.nextFloat() * 0.2f)
                    // White twinkles only where they sit wholly on the line: half on white paper,
                    // they look like bites taken out of it.
                    val fromLine = if (length > 0.5f) abs(ox * (y1 - y0) - oy * (x1 - x0)) / length else hypot(ox, oy)
                    val white = if (fromLine + r <= w * 0.35f) 1f else 0f
                    s.twinkles += floatArrayOf(x0 + (x1 - x0) * t + ox, y0 + (y1 - y0) * t + oy, r, white)
                }
                flushTwinkles(c, s, all = false, nearX = x1, nearY = y1, clearance = w)
            }
            isMirror -> {
                // The line, and its reflection on the other half of the paper.
                strokePaint.color = color
                strokePaint.strokeWidth = w
                c.drawPath(segment, strokePaint)
                mirrorMatrix.setScale(-1f, 1f)
                mirrorMatrix.postTranslate((doc?.width ?: 0).toFloat(), 0f)
                segment.transform(mirrorMatrix, mirrored)
                c.drawPath(mirrored, strokePaint)
            }
            else -> {
                strokePaint.color = strokeColor()
                strokePaint.strokeWidth = w
                if (isRainbow && rainbowFrom != rainbowHue && hypot(x1 - x0, y1 - y0) > 0.5f) {
                    // Shade along the segment, or each touch sample shows as a stripe of one color.
                    strokePaint.shader = LinearGradient(x0, y0, x1, y1,
                        rainbowColor(rainbowFrom), rainbowColor(rainbowHue), Shader.TileMode.CLAMP)
                }
                c.drawPath(segment, strokePaint)
                strokePaint.shader = null
            }
        }
        dirty = true
    }

    /** Half a disc of glow at a stroke's end, bulging out in direction (ux, uy). */
    private fun glowCap(c: Canvas, x: Float, y: Float, ux: Float, uy: Float, r: Float) {
        val heading = Math.toDegrees(atan2(uy, ux).toDouble()).toFloat()
        capPath.rewind()
        capPath.arcTo(x - r, y - r, x + r, y + r, heading - 90f, 180f, true)
        capPath.close()
        c.drawPath(capPath, glowCapPaint)
    }

    /**
     * Draws the stroke's waiting twinkles: all of them, or only those at least [clearance]
     * away from the pen at (nearX, nearY), which the line won't paint over any more.
     */
    private fun flushTwinkles(c: Canvas, s: Stroke, all: Boolean, nearX: Float = 0f, nearY: Float = 0f, clearance: Float = 0f) {
        val it = s.twinkles.iterator()
        while (it.hasNext()) {
            val t = it.next()
            if (all || hypot(t[0] - nearX, t[1] - nearY) > clearance) {
                twinkle(c, t[0], t[1], t[2], whiteAllowed = t[3] > 0f)
                it.remove()
            }
        }
    }

    /** One glitter twinkle: a little four-pointed star in white, gold or a light tint of the color. */
    private fun twinkle(c: Canvas, x: Float, y: Float, r: Float, whiteAllowed: Boolean = true) {
        fillPaint.color = when (if (whiteAllowed) random.nextInt(3) else 1 + random.nextInt(2)) {
            0 -> Color.WHITE
            1 -> 0xFFFFE082.toInt()
            else -> Palette.mix(color, Color.WHITE, 0.55f)
        }
        val k = r * 0.3f
        stampPath.rewind()
        stampPath.moveTo(x, y - r)
        stampPath.lineTo(x + k, y - k)
        stampPath.lineTo(x + r, y)
        stampPath.lineTo(x + k, y + k)
        stampPath.lineTo(x, y + r)
        stampPath.lineTo(x - k, y + k)
        stampPath.lineTo(x - r, y)
        stampPath.lineTo(x - k, y - k)
        stampPath.close()
        c.drawPath(stampPath, fillPaint)
    }

    private fun stampAt(x: Float, y: Float) {
        val c = docCanvas ?: return
        val r = stampRadius() * (0.85f + random.nextFloat() * 0.3f)
        // Each stamp a little different: its size, its tilt, and which way it faces.
        stampMatrix.setScale(if (random.nextBoolean()) -r else r, r)
        stampMatrix.postRotate(random.nextFloat() * 50f - 25f)
        stampMatrix.postTranslate(x, y)
        Stamps.draw(c, stamp, stampMatrix, color, fillPaint, stampPath)
        sounds?.boing()
    }

    /** Draws the current sticker, [size] across, centered on (x, y). */
    private fun drawSticker(c: Canvas, x: Float, y: Float, size: Float) {
        c.save()
        c.translate(x - size / 2f, y - size / 2f)
        c.scale(size / 100f, size / 100f)
        Stickers.draw(c, sticker, color)
        c.restore()
    }

    private fun placeSticker(x: Float, y: Float) {
        val c = docCanvas ?: return
        drawSticker(c, x, y, stickerSize())
        sounds?.boing()
    }

    // ---- Undo -------------------------------------------------------------------------------

    /** Saves one undo step per touch, however many fingers take part in it. */
    private fun beginAction() {
        if (actionOpen) return
        actionOpen = true
        pushUndo()
    }

    private fun pushUndo() {
        val d = doc ?: return
        try {
            undoStack.addLast(d.copy(Bitmap.Config.ARGB_8888, false))
            while (undoStack.size > maxUndo) undoStack.removeFirst().recycle()
        } catch (e: OutOfMemoryError) {
            clearUndo()
        }
        onHistoryChanged?.invoke()
    }

    private fun clearUndo() {
        undoStack.forEach { it.recycle() }
        undoStack.clear()
        onHistoryChanged?.invoke()
    }

    // ---- Tap to fill ------------------------------------------------------------------------

    /**
     * Fills the shape under (fx, fy). Returns the filled area (nonzero entries, one per document
     * pixel), or null if nothing was filled. An [echo] is the magic mirror's copy of a fill:
     * it stays quiet, so the first fill's sound and spoken name aren't doubled.
     */
    private fun fill(fx: Float, fy: Float, echo: Boolean = false): ByteArray? {
        val bmp = doc ?: return null
        val mask = lineMask ?: return null
        val w = bmp.width
        val h = bmp.height
        val x0 = fx.roundToInt()
        val y0 = fy.roundToInt()
        if (x0 !in 0 until w || y0 !in 0 until h) return null
        val start = nearestOpenPixel(mask, w, h, x0, y0) ?: return null

        val newColor = when {
            tool == Tool.ERASER -> paper
            isRainbow -> strokeColor().also { rainbowHue = (rainbowHue + 45f) % 360f }
            else -> color
        }

        // Scanline flood fill over the outline mask. region: 0 = outside, 1 = filled.
        val region = ByteArray(w * h)
        var minX = w; var maxX = 0; var minY = h; var maxY = 0
        val stack = IntStack()
        stack.push(start)
        while (stack.isNotEmpty()) {
            val seed = stack.pop()
            if (region[seed] != 0.toByte() || mask[seed]) continue
            val y = seed / w
            val row = y * w
            var left = seed - row
            var right = left
            while (left > 0 && isOpen(mask, region, row + left - 1)) left--
            while (right < w - 1 && isOpen(mask, region, row + right + 1)) right++
            for (x in left..right) region[row + x] = 1
            if (left < minX) minX = left
            if (right > maxX) maxX = right
            if (y < minY) minY = y
            if (y > maxY) maxY = y
            if (y > 0) pushRuns(stack, mask, region, row - w, left, right)
            if (y < h - 1) pushRuns(stack, mask, region, row + w, left, right)
        }

        // Grow the fill a few pixels under the outline, so no pale seam shows along its soft edge.
        val grow = max(1, (ColoringPage.lineWidthFor(w, h) / 4f).roundToInt())
        val bx0 = max(0, minX - grow)
        val bx1 = min(w - 1, maxX + grow)
        val by0 = max(0, minY - grow)
        val by1 = min(h - 1, maxY + grow)
        for (pass in 1..grow) {
            val mark = (pass + 1).toByte()
            for (y in by0..by1) {
                val row = y * w
                for (x in bx0..bx1) {
                    val i = row + x
                    if (region[i] != 0.toByte() || !mask[i]) continue
                    if ((x > 0 && grewBefore(region[i - 1], pass)) ||
                        (x < w - 1 && grewBefore(region[i + 1], pass)) ||
                        (y > 0 && grewBefore(region[i - w], pass)) ||
                        (y < h - 1 && grewBefore(region[i + w], pass))
                    ) {
                        region[i] = mark
                    }
                }
            }
        }

        val bw = bx1 - bx0 + 1
        val bh = by1 - by0 + 1
        val px = IntArray(bw * bh)
        bmp.getPixels(px, 0, bw, bx0, by0, bw, bh)
        for (y in 0 until bh) {
            val src = (by0 + y) * w + bx0
            val dst = y * bw
            for (x in 0 until bw) {
                if (region[src + x] != 0.toByte()) px[dst + x] = newColor
            }
        }
        bmp.setPixels(px, 0, bw, bx0, by0, bw, bh)

        if (tool == Tool.MAGIC && magic == Magic.GLITTER) sprinkleRegion(region, w, bx0, by0, bw, bh)
        if (!echo) {
            sounds?.bloop()
            partName(region, w, minX, maxX, minY, maxY)?.let { onPartTapped?.invoke(it) }
        }
        return region
    }

    /**
     * The magic mirror colors the matching shape on the other half of the page too, like a
     * butterfly's other wing. It leaves the empty background around the picture alone.
     */
    private fun fillMirrored(x: Float, y: Float, filled: ByteArray?) {
        val d = doc ?: return
        val inside = insideGrid ?: return
        val mx = d.width - x
        val ix = mx.roundToInt()
        val iy = y.roundToInt()
        if (ix !in 0 until d.width || iy !in 0 until d.height) return
        if (filled != null && filled[iy * d.width + ix] != 0.toByte()) return // The same shape: done already.
        val gx = ix / gridStep
        val gy = iy / gridStep
        if (gx >= gridW || gy >= gridH || !inside[gy * gridW + gx]) return
        fill(mx, y, echo = true)
    }

    /**
     * Which named part of the picture ("Wheel", "Roof") a freshly filled area is, if any:
     * the smallest part holding nearly all of it, so a tap on an eye says "Eye", not "Cat".
     * Checks a sample of the filled pixels, which is plenty and quick.
     */
    private fun partName(region: ByteArray, w: Int, x0: Int, x1: Int, y0: Int, y1: Int): String? {
        if (parts.isEmpty()) return null
        val hits = IntArray(parts.size)
        val bw = x1 - x0 + 1
        val bh = y1 - y0 + 1
        var samples = 0
        var tries = 0
        while (samples < 120 && tries < 3000) {
            tries++
            val x = x0 + random.nextInt(bw)
            val y = y0 + random.nextInt(bh)
            if (region[y * w + x] != 1.toByte()) continue
            samples++
            for (i in parts.indices) if (parts[i].area.contains(x, y)) hits[i]++
        }
        if (samples == 0) return null
        var best: ColoringPage.Part? = null
        for (i in parts.indices) {
            if (hits[i] >= samples * 0.8f && (best == null || parts[i].size < best.size)) best = parts[i]
        }
        return best?.name
    }

    /** Scatters glitter twinkles over a freshly filled shape. */
    private fun sprinkleRegion(region: ByteArray, w: Int, bx: Int, by: Int, bw: Int, bh: Int) {
        val c = docCanvas ?: return
        val spacing = dpToDoc(22f)
        val wanted = ((bw * bh) / (spacing * spacing)).toInt().coerceIn(3, 400)
        var placed = 0
        var tries = 0
        while (placed < wanted && tries < wanted * 6) {
            tries++
            val x = bx + random.nextInt(bw)
            val y = by + random.nextInt(bh)
            if (region[y * w + x] != 1.toByte()) continue
            twinkle(c, x.toFloat(), y.toFloat(), dpToDoc(4f + random.nextFloat() * 6f))
            placed++
        }
    }

    private fun isOpen(mask: BooleanArray, region: ByteArray, i: Int) = !mask[i] && region[i] == 0.toByte()

    /** True if a pixel was filled before grow pass [pass] (marks 1..pass). */
    private fun grewBefore(mark: Byte, pass: Int) = mark.toInt() in 1..pass

    /** Pushes one seed for every run of open pixels in a row between [left] and [right]. */
    private fun pushRuns(stack: IntStack, mask: BooleanArray, region: ByteArray, rowStart: Int, left: Int, right: Int) {
        var inRun = false
        for (x in left..right) {
            val i = rowStart + x
            if (isOpen(mask, region, i)) {
                if (!inRun) {
                    stack.push(i)
                    inRun = true
                }
            } else {
                inRun = false
            }
        }
    }

    /** A tap that lands right on a line still fills the nearest shape. */
    private fun nearestOpenPixel(mask: BooleanArray, w: Int, h: Int, x: Int, y: Int): Int? {
        if (!mask[y * w + x]) return y * w + x
        val maxR = dpToDoc(16f).roundToInt()
        for (r in 1..maxR) {
            for (k in RING_X.indices) {
                val nx = x + RING_X[k] * r
                val ny = y + RING_Y[k] * r
                if (nx in 0 until w && ny in 0 until h && !mask[ny * w + nx]) return ny * w + nx
            }
        }
        return null
    }

    // ---- "All done!" ------------------------------------------------------------------------

    /**
     * A coarse grid over the page, true for spots inside the picture's shapes (not on a line,
     * and not in the empty background around the picture). Used to tell when a page is finished.
     */
    private var insideGrid: BooleanArray? = null
    private var gridStep = 1
    private var gridW = 0
    private var gridH = 0
    private var insideCount = 0
    private var shapeOf: IntArray? = null
    private var shapeSize = IntArray(0)
    private var shapeCounted = BooleanArray(0)
    private var celebrated = false
    private var rowBuffer = IntArray(0)

    private val completionCheck = Runnable {
        if (!celebrated && isPageComplete()) {
            celebrated = true
            onPageComplete?.invoke()
        }
    }

    /** Works out [insideGrid]: open spots that can't be reached from the edge of the page. */
    private fun findInside(mask: BooleanArray, w: Int, h: Int) {
        gridStep = max(2, min(w, h) / 300)
        gridW = w / gridStep
        gridH = h / gridStep
        val open = BooleanArray(gridW * gridH) { g -> !mask[(g / gridW) * gridStep * w + (g % gridW) * gridStep] }
        val outside = BooleanArray(open.size)
        val queue = IntArray(open.size)
        var head = 0
        var tail = 0
        fun visit(g: Int) {
            if (open[g] && !outside[g]) {
                outside[g] = true
                queue[tail++] = g
            }
        }
        for (gx in 0 until gridW) { visit(gx); visit((gridH - 1) * gridW + gx) }
        for (gy in 0 until gridH) { visit(gy * gridW); visit(gy * gridW + gridW - 1) }
        while (head < tail) {
            val g = queue[head++]
            val gx = g % gridW
            if (gx > 0) visit(g - 1)
            if (gx < gridW - 1) visit(g + 1)
            if (g >= gridW) visit(g - gridW)
            if (g < open.size - gridW) visit(g + gridW)
        }
        val inside = BooleanArray(open.size) { open[it] && !outside[it] }
        insideCount = inside.count { it }
        insideGrid = inside
        findShapes(inside)
    }

    /**
     * Numbers the separate shapes inside the picture (the apple, its leaf, its stem...) in
     * [shapeOf], so finishing a page means coloring most of its shapes, not just the biggest one.
     * Specks too small for a toddler's finger don't count.
     */
    private fun findShapes(inside: BooleanArray) {
        val shape = IntArray(inside.size) { -1 }
        val sizes = ArrayList<Int>()
        val queue = IntArray(inside.size)
        var tail = 0
        fun visit(g: Int, id: Int) {
            if (inside[g] && shape[g] == -1) {
                shape[g] = id
                queue[tail++] = g
            }
        }
        for (start in inside.indices) {
            if (!inside[start] || shape[start] != -1) continue
            val id = sizes.size
            var head = 0
            tail = 0
            visit(start, id)
            while (head < tail) {
                val g = queue[head++]
                val gx = g % gridW
                if (gx > 0) visit(g - 1, id)
                if (gx < gridW - 1) visit(g + 1, id)
                if (g >= gridW) visit(g - gridW, id)
                if (g < inside.size - gridW) visit(g + gridW, id)
            }
            sizes += tail
        }
        val minCells = (10f * density / gridStep).let { it * it }
        shapeCounted = BooleanArray(sizes.size) { sizes[it] >= minCells }
        shapeSize = sizes.toIntArray()
        shapeOf = shape
    }

    /**
     * True when the picture is mostly colored in: at least 70% of its inside area,
     * and at least 70% of its shapes each at least half colored.
     */
    private fun isPageComplete(): Boolean {
        val inside = insideGrid ?: return false
        val shapes = shapeOf ?: return false
        val d = doc ?: return false
        if (insideCount < 50) return false
        if (rowBuffer.size != d.width) rowBuffer = IntArray(d.width)
        val coloredIn = IntArray(shapeSize.size)
        var colored = 0
        for (gy in 0 until gridH) {
            val rowStart = gy * gridW
            var any = false
            for (gx in 0 until gridW) if (inside[rowStart + gx]) { any = true; break }
            if (!any) continue
            d.getPixels(rowBuffer, 0, d.width, 0, gy * gridStep, d.width, 1)
            for (gx in 0 until gridW) {
                if (inside[rowStart + gx] && rowBuffer[gx * gridStep] != paper) {
                    colored++
                    coloredIn[shapes[rowStart + gx]]++
                }
            }
        }
        var counted = 0
        var done = 0
        for (i in shapeSize.indices) {
            if (!shapeCounted[i]) continue
            counted++
            if (coloredIn[i] * 2 >= shapeSize[i]) done++
        }
        return colored >= insideCount * 0.7f && done >= counted * 0.7f
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(completionCheck)
        super.onDetachedFromWindow()
    }

    private class IntStack {
        private var data = IntArray(4096)
        private var size = 0
        fun push(v: Int) {
            if (size == data.size) data = data.copyOf(size * 2)
            data[size++] = v
        }
        fun pop() = data[--size]
        fun isNotEmpty() = size > 0
    }

    private companion object {
        const val CORE_POINTS = 64
        val RING_X = intArrayOf(1, -1, 0, 0, 1, 1, -1, -1)
        val RING_Y = intArrayOf(0, 0, 1, -1, 1, -1, 1, -1)

        /** A sticker under a finger is shown this much bigger, as if lifted off the paper. */
        const val LIFTED = 1.12f
    }
}
