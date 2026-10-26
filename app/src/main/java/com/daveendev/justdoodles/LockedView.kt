package com.daveendev.justdoodles

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import kotlin.math.roundToInt

/**
 * What a child sees on tapping something that needs PLUS: [subject] (the picture or button
 * they tapped) on a white card, next to a smiling padlock that wiggles. The chime and "Ask a
 * grown-up" come from whoever shows it. There's no price and no way to buy here: any tap closes
 * it, and it closes itself after a few seconds. The small gray gear in the corner is for
 * grown-ups, and only does something when held for [holdMs].
 */
@SuppressLint("ViewConstructor")
class LockedView(
    context: Context,
    subject: Bitmap,
    holdMs: Long,
    grownUpLabel: String,
    onGrownUp: () -> Unit,
    private val onClose: () -> Unit,
) : FrameLayout(context) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = (v * density).roundToInt()

    private val holder = FrameLayout(context)
    /** The big padlock: a tap closes the card like any tap, and a grown-up's 2-second hold opens the way to PLUS. */
    private val padlock = HoldButton(context, Icon.LOCK, holdMs, onShortTap = { onClose() }, onHold = onGrownUp)
    private val gear = HoldButton(context, Icon.GEAR, holdMs, onHold = onGrownUp)

    private val closeLater = object : Runnable {
        override fun run() {
            // A grown-up is holding the padlock or the gear: let them finish.
            if (gear.isPressed || padlock.isPressed) postDelayed(this, 500L) else onClose()
        }
    }

    init {
        setBackgroundColor(0x33000000)
        setOnClickListener { onClose() }

        val pad = dp(18f)
        val gearSize = dp(44f)
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 28f * density
            }
            elevation = 8f * density
            setOnClickListener { onClose() }
        }
        card.addView(ThumbView(context, subject, current = false).apply { setOnClickListener { onClose() } },
            LinearLayout.LayoutParams(subject.width, subject.height))
        val lockSize = (subject.height * 0.8f).roundToInt()
        padlock.contentDescription = context.getString(R.string.locked_hold_lock)
        // Swing from the top of the shackle, like a padlock hanging on a hook.
        padlock.pivotX = lockSize / 2f
        padlock.pivotY = lockSize * 0.15f
        card.addView(padlock, LinearLayout.LayoutParams(lockSize, lockSize).apply {
            marginStart = pad
            marginEnd = gearSize / 2
        })

        gear.alpha = 0.6f
        gear.contentDescription = grownUpLabel
        gear.elevation = 10f * density // Above the card, which has a shadow of its own.
        holder.addView(card)
        holder.addView(gear, LayoutParams(gearSize, gearSize, Gravity.TOP or Gravity.END))
        addView(holder, LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        holder.scaleX = 0.85f
        holder.scaleY = 0.85f
        holder.animate().scaleX(1f).scaleY(1f).setInterpolator(OvershootInterpolator(2f)).setDuration(260).start()
        // It wiggles once, as if it's trying to open.
        ObjectAnimator.ofFloat(padlock, View.ROTATION, 0f, -14f, 12f, -9f, 6f, -3f, 0f).apply {
            duration = 800
            startDelay = 150
        }.start()
        postDelayed(closeLater, SHOW_MS)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(closeLater)
        super.onDetachedFromWindow()
    }

    private companion object {
        const val SHOW_MS = 3000L
    }
}
