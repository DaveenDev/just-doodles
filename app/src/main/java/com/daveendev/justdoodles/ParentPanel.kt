package com.daveendev.justdoodles

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random
import android.speech.tts.Voice as PhoneVoice

/**
 * Settings for grown-ups. Opened by holding the gear for two seconds, too long for a toddler's tap.
 * The donate page sits one step further in, behind a sum to answer, since it leads out of the app.
 */
@SuppressLint("ViewConstructor", "UseSwitchCompatOrMaterialCode")
class ParentPanel(
    context: Context,
    private val host: Host,
    private val onClose: () -> Unit,
    /** Open straight on the way to the PLUS page (the sum first), as the locked card's grown-up button does. */
    startOnPlus: Boolean = false,
) : FrameLayout(context) {

    /** What the settings read and change. */
    interface Host {
        var soundsOn: Boolean
        var voiceOn: Boolean

        /** This phone's voices that work without the internet, best first (see [Voice.choices]). */
        fun voices(): List<PhoneVoice>
        /** The name of the voice picked here, or null when the app picks. */
        var voiceName: String?
        /** Says a few words in the voice now in use, even with the names turned off. */
        fun sayVoiceSample()

        var babyOn: Boolean

        /** The bedtime timer's length in minutes, or 0 when it's off. */
        val bedtimeChoice: Int

        /** Whole minutes left until bedtime, or 0 when the timer is off. */
        val bedtimeMinutesLeft: Int
        fun setBedtime(minutes: Int)

        val pinned: Boolean
        fun setPinned(pinned: Boolean)

        /** Pages with something drawn on them, newest first. */
        fun drawingIds(): List<String>
        fun drawingName(id: String): String
        fun drawingThumb(id: String, width: Int, height: Int): Bitmap
        fun saveDrawing(id: String)
        fun shareDrawing(id: String)
        fun deleteDrawing(id: String)

        /** Whether this build sells PLUS (the Play build does; the sideloaded one has it all already). */
        val plusAvailable: Boolean
        val plusState: PlusState
        /** PLUS's price as Google Play shows it ("₱79.00"), once Play has said. */
        val plusPrice: String?
        /** What the last buy or restore came to, or null before either. */
        val plusResult: PlusResult?
        fun buyPlus()
        /** Asks Google Play again for a PLUS bought earlier; [onDone] says whether it found one. */
        fun restorePlus(onDone: (Boolean) -> Unit)
    }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = (v * density).roundToInt()

    private val scroll = ScrollView(context).apply { isVerticalScrollBarEnabled = false }
    private val cardWidth = min(dp(560f), resources.displayMetrics.widthPixels - dp(48f))
    private val settingsCard: View
    /** The settings' PLUS button, whose words change once PLUS is on. Null in the sideloaded build. */
    private var plusPill: TextView? = null
    /** True while the PLUS page is showing, so news from Google Play can redraw it. */
    private var onPlusPage = false

    init {
        setBackgroundColor(0xCC263238.toInt())
        isClickable = true
        setOnClickListener { onClose() } // A tap outside the card closes it.

        settingsCard = card().apply {
            // Done sits next to the title, so it's on screen even when a landscape phone is too
            // short for the whole card and the rest has to scroll.
            addView(header(context.getString(R.string.parent_title), R.string.parent_done) { onClose() })
            addView(label(R.string.parent_about, 14f, 0xFF607D8B.toInt()), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                topMargin = dp(4f)
                bottomMargin = dp(8f)
            })
            addView(row(R.string.parent_sounds, R.string.parent_sounds_detail, host.soundsOn) { host.soundsOn = it })
            addView(row(R.string.parent_voice, R.string.parent_voice_detail, host.voiceOn) { host.voiceOn = it })
            addView(voiceRow())
            addView(row(R.string.parent_baby, R.string.parent_baby_detail, host.babyOn) { host.babyOn = it })
            addView(bedtimeRow())
            addView(pinRow())
            addView(pill(context.getString(R.string.parent_drawings), 0xFFE3F2FD.toInt(), 0xFF1565C0.toInt()) { showDrawings() },
                stacked(12f))
            // The Play build sells PLUS; the sideloaded one has everything already.
            if (host.plusAvailable) {
                val p = pill(plusPillText(), 0xFFFFF8E1.toInt(), 0xFFE65100.toInt()) { openPlus() }
                plusPill = p
                addView(p, stacked(10f))
            }
            // The Play Store build hides this: Google Play doesn't allow links to outside payment pages.
            if (context.resources.getBoolean(R.bool.show_donate)) {
                addView(pill(context.getString(R.string.parent_support), 0xFFFCE4EC.toInt(), 0xFFC2185B.toInt()) { showCheck() },
                    stacked(10f))
            }
        }

        addView(scroll, LayoutParams(cardWidth, WRAP_CONTENT, Gravity.CENTER).apply {
            topMargin = dp(16f)
            bottomMargin = dp(16f)
        })
        show(settingsCard)
        if (startOnPlus && host.plusAvailable) openPlus()
    }

    private fun show(card: View) {
        onPlusPage = false
        scroll.removeAllViews()
        scroll.addView(card)
        // After the new card is laid out: a page taller than the screen (PLUS) would otherwise open part-way down.
        scroll.post { scroll.scrollTo(0, 0) }
    }

    // ---- Voice ------------------------------------------------------------------------------

    /**
     * Which of the phone's voices says the names. Automatic is the app's pick ([Voice.choices]'
     * first); the arrows step through the rest. Every tap says a few words in the voice it lands on.
     */
    private fun voiceRow(): View {
        val status = label("", 13f, 0xFF2E7D32.toInt(), bold = true)
        val grey = 0xFFECEFF1.toInt()
        lateinit var auto: TextView
        lateinit var pickers: List<TextView>
        fun refresh() {
            val voices = host.voices()
            // -1 while the app picks, or when the picked voice has since been removed from the phone.
            val i = voices.indexOfFirst { it.name == host.voiceName }
            status.text = when {
                voices.isEmpty() -> context.getString(R.string.parent_voice_none)
                i < 0 -> context.getString(R.string.parent_voice_auto_now, voices.size, voices[0].locale.displayName)
                else -> context.getString(R.string.parent_voice_now, i + 1, voices.size, voices[i].locale.displayName)
            }
            pickers.forEach { it.visibility = if (voices.isEmpty()) GONE else VISIBLE }
            highlight(auto, i < 0)
        }
        fun step(by: Int) {
            val voices = host.voices()
            if (voices.isNotEmpty()) {
                val i = voices.indexOfFirst { it.name == host.voiceName }.coerceAtLeast(0)
                host.voiceName = voices[(i + by).mod(voices.size)].name
                host.sayVoiceSample()
            }
            refresh()
        }
        auto = pill(context.getString(R.string.parent_voice_auto), 0, 0) {
            host.voiceName = null
            host.sayVoiceSample()
            refresh()
        }
        pickers = listOf(pill("◀", grey, Palette.INK) { step(-1) }, pill("▶", grey, Palette.INK) { step(1) }, auto)
        val more = pill(context.getString(R.string.parent_voice_more), grey, Palette.INK) { openSpeechSettings() }
        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            for ((button, weight) in pickers.zip(listOf(1f, 1f, 2f)) + (more to 2f)) {
                button.setPadding(0, dp(8f), 0, dp(8f))
                addView(button, LinearLayout.LayoutParams(0, WRAP_CONTENT, weight).apply {
                    marginStart = dp(3f)
                    marginEnd = dp(3f)
                })
            }
        }
        refresh()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8f), 0, dp(8f))
            addView(label(R.string.parent_voice_pick, 17f, Palette.INK, bold = true))
            addView(label(R.string.parent_voice_pick_detail, 13f, 0xFF78909C.toInt()))
            addView(status)
            addView(buttons, stacked(8f))
        }
    }

    /** Android's text-to-speech settings, where grown-ups can download more voices for the phone. */
    private fun openSpeechSettings() {
        for (action in listOf("com.android.settings.TTS_SETTINGS", TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) {
            try {
                context.startActivity(Intent(action))
                return
            } catch (e: ActivityNotFoundException) {
                // Not on this phone: try the next way in.
            }
        }
    }

    // ---- Bedtime timer and pinning ----------------------------------------------------------

    /** Off, 10, 20 or 30 minutes. When time is up the app goes to sleep (see [SleepView]). */
    private fun bedtimeRow(): View {
        val status = label("", 13f, 0xFF2E7D32.toInt(), bold = true)
        val choices = intArrayOf(0, 10, 20, 30)
        val pills = ArrayList<TextView>()
        fun refresh() {
            val left = host.bedtimeMinutesLeft
            status.text = if (left > 0) context.getString(R.string.parent_bedtime_left, left) else ""
            status.visibility = if (left > 0) VISIBLE else GONE
            pills.forEachIndexed { i, pill -> highlight(pill, choices[i] == host.bedtimeChoice) }
        }
        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            for (minutes in choices) {
                val text = if (minutes == 0) context.getString(R.string.parent_bedtime_off)
                else context.getString(R.string.parent_bedtime_minutes, minutes)
                val pill = pill(text, 0, 0) {
                    host.setBedtime(minutes)
                    refresh()
                }.apply { setPadding(0, dp(8f), 0, dp(8f)) }
                pills += pill
                addView(pill, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                    marginStart = dp(3f)
                    marginEnd = dp(3f)
                })
            }
        }
        refresh()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8f), 0, dp(8f))
            addView(label(R.string.parent_bedtime, 17f, Palette.INK, bold = true))
            addView(label(R.string.parent_bedtime_detail, 13f, 0xFF78909C.toInt()))
            addView(status)
            addView(buttons, stacked(8f))
        }
    }

    /** The chosen option is orange; the others are gray. */
    private fun highlight(pill: TextView, chosen: Boolean) {
        (pill.background as GradientDrawable).setColor(if (chosen) 0xFFFF8F00.toInt() else 0xFFECEFF1.toInt())
        pill.setTextColor(if (chosen) Color.WHITE else Palette.INK)
    }

    /** Locks the phone to this app with Android's own app pinning. */
    private fun pinRow(): View {
        val pinned = host.pinned
        val button = pill(context.getString(if (pinned) R.string.parent_unpin_button else R.string.parent_pin_button),
            0xFFECEFF1.toInt(), Palette.INK) {
            // Close first, so Android's "Pin this app?" question isn't hidden behind the settings.
            onClose()
            host.setPinned(!pinned)
        }.apply { setPadding(dp(24f), dp(8f), dp(24f), dp(8f)) }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8f), 0, dp(8f))
            val texts = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(R.string.parent_pin, 17f, Palette.INK, bold = true))
                addView(label(R.string.parent_pin_detail, 13f, 0xFF78909C.toInt()))
            }
            addView(texts, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(12f) })
            addView(button)
        }
    }

    // ---- Drawings ---------------------------------------------------------------------------

    /** Every drawing as a small picture. Tap one to save, share or delete it. */
    private fun showDrawings() {
        val ids = host.drawingIds()
        val columns = 3
        val gap = dp(5f)
        val inner = cardWidth - dp(48f)
        val thumbW = inner / columns - 2 * gap
        val thumbH = thumbW * 9 / 16
        show(card().apply {
            addView(header(context.getString(R.string.drawings_title), R.string.check_back) { show(settingsCard) })
            val about = if (ids.isEmpty()) context.getString(R.string.drawings_none)
            else context.getString(R.string.drawings_about, Paper.MAX_DRAWN)
            addView(label(about, 14f, 0xFF607D8B.toInt()), stacked(4f))
            for (rowStart in ids.indices step columns) {
                val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                for (id in ids.subList(rowStart, min(rowStart + columns, ids.size))) {
                    val thumb = ThumbView(context, host.drawingThumb(id, thumbW, thumbH), current = false).apply {
                        contentDescription = host.drawingName(id)
                        setOnClickListener { showDrawing(id) }
                    }
                    row.addView(thumb, LinearLayout.LayoutParams(thumbW, thumbH).apply { setMargins(gap, gap, gap, gap) })
                }
                addView(row, stacked(if (rowStart == 0) 8f else 0f))
            }
        })
    }

    private fun showDrawing(id: String) {
        val previewW = (cardWidth - dp(48f)) * 11 / 20
        val previewH = previewW * 9 / 16
        // Deleting takes a second tap, in case the first one was a slip.
        lateinit var deletePill: TextView
        var armed = false
        deletePill = pill(context.getString(R.string.drawings_delete), 0xFFFFEBEE.toInt(), 0xFFC62828.toInt()) {
            if (armed) {
                host.deleteDrawing(id)
                showDrawings()
            } else {
                armed = true
                deletePill.text = context.getString(R.string.drawings_delete_confirm)
            }
        }
        // Saving and sharing come with PLUS. Without it they say so, and the drawing stays safe in the app.
        val plusNote = label(R.string.drawings_plus_note, 13f, 0xFF78909C.toInt()).apply { visibility = GONE }
        fun export(text: Int, fill: Int, action: () -> Unit) =
            if (Plus.canExport()) pill(context.getString(text), fill, Color.WHITE, action)
            else pill(context.getString(R.string.drawings_plus, context.getString(text)), 0xFFECEFF1.toInt(), Palette.INK) {
                if (host.plusAvailable) openPlus() else plusNote.visibility = VISIBLE
            }
        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(export(R.string.drawings_save, 0xFF43A047.toInt()) { host.saveDrawing(id) }, stacked(0f))
            addView(export(R.string.drawings_share, 0xFF1E88E5.toInt()) { host.shareDrawing(id) }, stacked(10f))
            addView(deletePill, stacked(10f))
            for (i in 0 until childCount) getChildAt(i).setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
            addView(plusNote, stacked(8f))
        }
        show(card().apply {
            addView(header(host.drawingName(id), R.string.check_back) { showDrawings() })
            // The picture on the left and what to do with it on the right, so it all fits on a landscape phone.
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(ThumbView(context, host.drawingThumb(id, previewW, previewH), current = false),
                    LinearLayout.LayoutParams(previewW, previewH))
                addView(buttons, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(16f) })
            }, stacked(12f))
        })
    }

    // ---- PLUS -------------------------------------------------------------------------------

    private fun plusPillText() = context.getString(
        if (host.plusState == PlusState.OWNED) R.string.plus_pill_on else R.string.plus_pill,
    )

    /**
     * The way to the PLUS page: the harder sum first, since that page can spend money. Once PLUS
     * is on there's nothing left to buy, only Restore, so it opens straight away.
     */
    private fun openPlus() {
        if (host.plusState == PlusState.OWNED) showPlus() else showCheck(hard = true) { showPlus() }
    }

    /** What PLUS adds, its price from Google Play, and the buttons to buy or restore it. */
    private fun showPlus(news: PlusResult? = null) {
        val owned = host.plusState == PlusState.OWNED
        show(card().apply {
            addView(header(context.getString(R.string.plus_title), R.string.check_back) { show(settingsCard) })
            addView(label(R.string.plus_intro, 15f, 0xFF455A64.toInt(), bold = true), stacked(8f))
            for (line in resources.getStringArray(R.array.plus_features)) {
                addView(label("•  $line", 15f, Palette.INK), stacked(4f))
            }
            if (!owned) {
                val price = host.plusPrice
                val buy = if (price != null) {
                    pill(context.getString(R.string.plus_buy, price), 0xFFFF8F00.toInt(), Color.WHITE) { host.buyPlus() }
                } else {
                    // No price yet means Play hasn't answered: nothing to buy until it does.
                    pill(context.getString(R.string.plus_loading), 0xFFECEFF1.toInt(), 0xFF90A4AE.toInt()) { }
                }
                addView(buy, stacked(16f))
            }
            addView(pill(context.getString(R.string.plus_restore), 0, 0xFF1565C0.toInt()) { host.restorePlus { } }
                .apply { textSize = 15f }, stacked(8f))
            plusStatus(owned, news)?.let { status ->
                addView(label(status, 14f, 0xFF2E7D32.toInt(), bold = true).apply { gravity = Gravity.CENTER }, stacked(10f))
            }
            addView(label(R.string.plus_note, 13f, 0xFF78909C.toInt()).apply { gravity = Gravity.CENTER }, stacked(12f))
        })
        onPlusPage = true
    }

    /** One line on where PLUS stands: what Play just said ([news]) if anything, else the state it's in. */
    private fun plusStatus(owned: Boolean, news: PlusResult?): String? {
        val text = when (news) {
            PlusResult.BOUGHT -> R.string.plus_on
            PlusResult.RESTORED -> R.string.plus_restored
            PlusResult.NOT_FOUND -> R.string.plus_not_found
            PlusResult.NO_CONNECTION -> R.string.plus_no_connection
            PlusResult.NOT_AVAILABLE -> R.string.plus_not_available
            PlusResult.FAILED -> R.string.plus_failed
            PlusResult.PENDING, PlusResult.CANCELED, null -> when {
                owned -> R.string.plus_on
                host.plusState == PlusState.PENDING -> R.string.plus_pending
                host.plusState == PlusState.UNAVAILABLE -> R.string.plus_not_available
                else -> return null
            }
        }
        return context.getString(text)
    }

    /** Google Play said something new (bought, restored, refunded, a price): bring what's showing up to date. */
    fun plusChanged() {
        plusPill?.text = plusPillText()
        if (onPlusPage) showPlus(news = host.plusResult)
    }

    // ---- Donate -----------------------------------------------------------------------------

    /**
     * A sum to answer by picking from a few numbers. A wrong pick goes back to the settings, so
     * tapping at random doesn't get through. The [hard] one, before anything that can spend money,
     * has a two-digit number and six answers.
     */
    private fun showCheck(hard: Boolean = false, next: () -> Unit = { showDonate() }) {
        val a = if (hard) Random.nextInt(11, 20) else Random.nextInt(3, 10)
        val b = Random.nextInt(3, 10)
        val answer = a + b
        val choices = mutableSetOf(answer)
        while (choices.size < if (hard) 6 else 4) choices.add(answer + Random.nextInt(-5, 6))
        show(card().apply {
            addView(header(context.getString(R.string.check_title), R.string.check_back) { show(settingsCard) })
            addView(label(context.getString(R.string.check_question, a, b), 28f, Palette.INK, bold = true).apply {
                gravity = Gravity.CENTER
            }, stacked(16f))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                for (choice in choices.shuffled()) {
                    addView(pill(choice.toString(), 0xFFECEFF1.toInt(), Palette.INK) {
                        if (choice == answer) next() else show(settingsCard)
                    }.apply { setPadding(0, dp(12f), 0, dp(12f)) }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                        marginStart = dp(4f)
                        marginEnd = dp(4f)
                    })
                }
            }, stacked(16f))
        })
    }

    private fun showDonate() {
        val paypal = context.getString(R.string.donate_paypal_url)
        val bmc = context.getString(R.string.donate_bmc_url)
        val wallet = context.getString(R.string.donate_wallet_number)
        show(card().apply {
            addView(header(context.getString(R.string.donate_title), R.string.check_back) { show(settingsCard) })
            addView(label(R.string.donate_message, 15f, 0xFF455A64.toInt()), stacked(8f))
            if (paypal.isNotBlank()) {
                addView(pill(context.getString(R.string.donate_paypal), 0xFF0070BA.toInt(), Color.WHITE) { openLink(paypal) }, stacked(16f))
            }
            if (bmc.isNotBlank()) {
                addView(pill(context.getString(R.string.donate_bmc), 0xFFFFDD00.toInt(), 0xFF212121.toInt()) { openLink(bmc) }, stacked(10f))
            }
            if (wallet.isNotBlank()) {
                lateinit var walletPill: TextView
                walletPill = pill(context.getString(R.string.donate_wallet, wallet), 0xFF007DFE.toInt(), Color.WHITE) {
                    context.getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText("GCash / Maya", wallet))
                    walletPill.text = context.getString(R.string.donate_wallet_copied, wallet)
                }
                addView(walletPill, stacked(10f))
            }
            addView(label(R.string.donate_note, 13f, 0xFF78909C.toInt()).apply { gravity = Gravity.CENTER }, stacked(12f))
        })
    }

    /** Hands the link to the phone's browser. The app itself still never touches the internet. */
    private fun openLink(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            // No browser on the device: nothing to do.
        }
    }

    // ---- Building blocks --------------------------------------------------------------------

    private fun card() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = 24f * density
        }
        setPadding(dp(24f), dp(20f), dp(24f), dp(20f))
        isClickable = true // Taps on the card itself don't close it.
    }

    /** Title on the left, an orange button on the right. */
    private fun header(title: String, button: Int, onButton: () -> Unit) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(label(title, 22f, Palette.INK, bold = true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        addView(pill(context.getString(button), 0xFFFF8F00.toInt(), Color.WHITE, onButton))
    }

    private fun stacked(top: Float) = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(top) }

    private fun pill(text: String, fill: Int, textColor: Int, onTap: () -> Unit) =
        label(text, 18f, textColor, bold = true).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(fill)
                cornerRadius = 100f * density
            }
            setPadding(dp(32f), dp(10f), dp(32f), dp(10f))
            setOnClickListener { onTap() }
        }

    private fun label(text: Int, size: Float, color: Int, bold: Boolean = false) =
        label(context.getString(text), size, color, bold)

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false) = TextView(context).apply {
        setText(text)
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    /** A setting: name and explanation on the left, a switch on the right. Tapping anywhere on it flips it. */
    private fun row(title: Int, detail: Int, on: Boolean, onChange: (Boolean) -> Unit): View {
        val switch = Switch(context).apply {
            isChecked = on
            setOnCheckedChangeListener { _, checked -> onChange(checked) }
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8f), 0, dp(8f))
            val texts = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(title, 17f, Palette.INK, bold = true))
                addView(label(detail, 13f, 0xFF78909C.toInt()))
            }
            addView(texts, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(12f) })
            addView(switch)
            setOnClickListener { switch.toggle() }
        }
    }
}
