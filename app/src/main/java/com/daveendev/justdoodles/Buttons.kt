package com.daveendev.justdoodles

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import kotlin.math.min

/** A big square button showing one of the [Icons]. */
open class ToolButton(context: Context, val icon: Icon) : View(context) {

    /** Shows the white "this one is in use" tile behind the icon. */
    var chosen = false
        set(value) { field = value; invalidate() }

    /** Current crayon color, for icons that show it. */
    var tint = Palette.colors[0]
        set(value) { field = value; invalidate() }

    var sizeIndex = 1
        set(value) { field = value; invalidate() }

    /** Which magic the magic-crayon button shows. */
    var magic = Magic.RAINBOW
        set(value) { field = value; invalidate() }

    /** Which stamp the stamp button shows. */
    var stamp = StampKind.STAR
        set(value) { field = value; invalidate() }

    /** Which pattern the pattern-crayon button shows. */
    var pattern = Pattern.HEARTS
        set(value) { field = value; invalidate() }

    /** Which sticker the sticker button shows. */
    var sticker = Sticker.SUN
        set(value) { field = value; invalidate() }

    /** Needs PLUS: a small padlock sits in the corner. */
    var locked = false
        set(value) { field = value; invalidate() }

    /** 0..1 while a hold-to-confirm button is being held down. */
    protected var progress = 0f

    protected val density = resources.displayMetrics.density
    private val tile = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val tileEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x22263238
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = 0xFF43A047.toInt()
    }

    init {
        isClickable = true
        isSoundEffectsEnabled = false
        contentDescription = icon.label
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val box = min(width, height) * 0.88f
        val cx = width / 2f
        val cy = height / 2f
        val left = cx - box / 2f
        val top = cy - box / 2f

        canvas.save()
        if (isPressed) canvas.scale(0.88f, 0.88f, cx, cy)
        if (chosen) {
            val r = box * 0.26f
            tileEdge.strokeWidth = 1.5f * density
            canvas.drawRoundRect(left, top, left + box, top + box, r, r, tile)
            canvas.drawRoundRect(left, top, left + box, top + box, r, r, tileEdge)
        }
        val inset = box * 0.12f
        canvas.translate(left + inset, top + inset)
        canvas.scale((box - 2 * inset) / 100f, (box - 2 * inset) / 100f)
        Icons.draw(canvas, icon, tint, sizeIndex, magic, stamp, pattern, sticker)
        canvas.restore()
        if (locked) Icons.lockBadge(canvas, left + box * 0.84f, top + box * 0.84f, box * 0.42f)

        if (progress > 0f) {
            ring.strokeWidth = 5f * density
            val r = box / 2f - ring.strokeWidth / 2f
            canvas.drawArc(cx - r, cy - r, cx + r, cy + r, -90f, 360f * progress, false, ring)
        }
    }
}

/**
 * A button that only fires after being held for [holdMs], with a ring that fills up meanwhile.
 * Used for "clean page", so a stray toddler tap can't wipe a picture, and as the gate to
 * the grown-up settings.
 *
 * Timed with the clock rather than an animator, so it still works when the phone has
 * animations switched off.
 */
class HoldButton(
    context: Context,
    icon: Icon,
    private val holdMs: Long = 1200L,
    /** Called when a touch ends before the hold is done, for buttons where a tap means something else. */
    private val onShortTap: (() -> Unit)? = null,
    private val onHold: () -> Unit,
) : ToolButton(context, icon) {

    private var downAt = 0L

    private val tick = object : Runnable {
        override fun run() {
            progress = ((SystemClock.uptimeMillis() - downAt).toFloat() / holdMs).coerceAtMost(1f)
            invalidate()
            if (progress >= 1f) {
                stop()
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onHold()
            } else {
                postOnAnimation(this)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isPressed = true
                downAt = SystemClock.uptimeMillis()
                removeCallbacks(tick)
                postOnAnimation(tick)
            }
            MotionEvent.ACTION_UP -> {
                // A hold that finished has already stopped and reset the press, so this is only a short tap.
                val short = isPressed
                stop()
                if (short) onShortTap?.invoke()
            }
            MotionEvent.ACTION_CANCEL -> stop()
        }
        return true
    }

    private fun stop() {
        removeCallbacks(tick)
        isPressed = false
        progress = 0f
        invalidate()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }
}

/**
 * One of the big cards of the start menu, Play and Learn: a picture ([icon]) on a colored card, with [label]
 * underneath for the grown-ups. Little ones go by the picture.
 */
@SuppressLint("ViewConstructor")
class MenuCard(context: Context, private val icon: Icon, private val label: String, private val cardColor: Int) : View(context) {

    private val density = resources.displayMetrics.density
    private val card = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
    }
    private val textEdge = Paint(text).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        color = Palette.INK
    }

    /** Needs PLUS: the picture fades a little and a padlock sits in the corner. */
    var locked = false
        set(value) { field = value; invalidate() }

    init {
        isClickable = true
        isSoundEffectsEnabled = false
        contentDescription = label
        elevation = 6f * density
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 28f * density)
            }
        }
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        // Sink a little under the finger, like a real button.
        scaleX = if (isPressed) 0.95f else 1f
        scaleY = scaleX
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = 28f * density
        card.color = cardColor
        canvas.drawRoundRect(0f, 0f, w, h, r, r, card)
        card.color = 0x33FFFFFF
        canvas.drawRoundRect(0f, 0f, w, h * 0.5f, r, r, card)

        val labelSize = min(w * 0.14f, h * 0.12f)
        val picture = min(w * 0.82f, h - labelSize * 2.6f)
        // In the middle of the space above the label, so a tall card isn't empty at the bottom.
        val above = h - labelSize * 2f
        val layer = if (locked) canvas.saveLayerAlpha(0f, 0f, w, h, 150) else canvas.save()
        canvas.translate((w - picture) / 2f, ((above - picture) / 2f).coerceAtLeast(h * 0.05f))
        canvas.scale(picture / 100f, picture / 100f)
        Icons.draw(canvas, icon, Palette.colors[0], 1)
        canvas.restoreToCount(layer)
        if (locked) {
            val s = min(w, h) * 0.26f
            Icons.lockBadge(canvas, w - s * 0.75f, s * 0.75f, s)
        }

        text.textSize = labelSize
        textEdge.textSize = labelSize
        textEdge.strokeWidth = labelSize * 0.16f
        val baseline = h - labelSize * 0.75f
        canvas.drawText(label, w / 2f, baseline, textEdge)
        canvas.drawText(label, w / 2f, baseline, text)
    }
}

/** A round blob of paint to pick a color. */
class ColorSwatch(context: Context, val color: Int) : View(context) {

    var chosen = false
        set(value) { field = value; invalidate() }

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        isClickable = true
        isSoundEffectsEnabled = false
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        var r = min(width, height) / 2f * if (chosen) 0.96f else 0.78f
        if (isPressed) r *= 0.88f
        if (chosen) {
            paint.color = Palette.INK
            canvas.drawCircle(cx, cy, r, paint)
            r -= 3f * density
            paint.color = Color.WHITE
            canvas.drawCircle(cx, cy, r, paint)
            r -= 3f * density
        } else {
            paint.color = 0x1F000000
            canvas.drawCircle(cx, cy + 1.5f * density, r, paint)
            paint.color = Color.WHITE
            canvas.drawCircle(cx, cy, r, paint)
            r -= 2.5f * density
        }
        paint.color = color
        canvas.drawCircle(cx, cy, r, paint)
    }
}

/** A rounded preview card in the picture picker. [image] must already be the view's size. */
class ThumbView(context: Context, image: Bitmap, private val current: Boolean) : View(context) {

    private val density = resources.displayMetrics.density
    private val picture = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = BitmapShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val veil = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFFFFF }

    private val radius = 14f * density

    /** Needs PLUS: the picture fades a little and a padlock sits in the corner. */
    var locked = false
        set(value) { field = value; invalidate() }

    init {
        isClickable = true
        isSoundEffectsEnabled = false
        elevation = 3f * density
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = radius
        canvas.save()
        if (isPressed) canvas.scale(0.94f, 0.94f, w / 2f, h / 2f)
        canvas.drawRoundRect(0f, 0f, w, h, r, r, picture)
        if (locked) canvas.drawRoundRect(0f, 0f, w, h, r, r, veil)
        if (current) {
            edge.color = 0xFFFF8F00.toInt()
            edge.strokeWidth = 5f * density
        } else {
            edge.color = 0x33263238
            edge.strokeWidth = 1.5f * density
        }
        val inset = edge.strokeWidth / 2f
        canvas.drawRoundRect(inset, inset, w - inset, h - inset, r - inset, r - inset, edge)
        if (locked) {
            val s = min(w, h) * 0.36f
            Icons.lockBadge(canvas, w - s * 0.62f, s * 0.62f, s)
        }
        canvas.restore()
    }
}
