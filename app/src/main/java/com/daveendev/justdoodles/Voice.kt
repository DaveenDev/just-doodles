package com.daveendev.justdoodles

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
import android.speech.tts.Voice as PhoneVoice

/**
 * Says color and tool names out loud ("Red!", "Glitter!") with the phone's own
 * text-to-speech voice, so little ones hear the word as they pick. No permission is needed;
 * the text only goes to the phone's speech engine.
 *
 * The voice speaks the language of the app's words (see `voice_locale` in strings.xml), so a
 * translated set of color names gets a matching voice. If the phone has no voice for it,
 * it falls back to English.
 *
 * Only voices that work without the internet are used (see [choices]). Grown-ups can pick one
 * in the settings; otherwise the app picks the best one itself.
 */
class Voice(context: Context) : TextToSpeech.OnInitListener {

    var enabled = true

    /**
     * The name of the voice a grown-up picked, or null to let the app pick ([choices]' first).
     * A picked voice that has since been removed from the phone counts as null.
     */
    var chosen: String? = null
        set(value) {
            field = value
            if (ready) useVoice()
        }

    private val locale = Locale.forLanguageTag(context.getString(R.string.voice_locale))
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        if (tts.setLanguage(locale) < TextToSpeech.LANG_AVAILABLE) tts.setLanguage(Locale.US)
        // A little slower and higher than everyday speech, the way grown-ups talk to toddlers.
        // Much higher than this and most voices start to sound like a chipmunk.
        tts.setSpeechRate(0.85f)
        tts.setPitch(1.2f)
        ready = true
        useVoice()
    }

    /**
     * The phone's voices for the app's language (English if it has none) that are installed and
     * work without the internet, best first: the clearest, then the quickest to start talking,
     * then the one picked in Android's own settings, then the phone's accent. Voices that need
     * the internet are left out on purpose: they send the words to the speech company's servers,
     * and nothing in this app leaves the phone.
     *
     * Asks the speech engine each time, so voices installed while the app is open show up.
     */
    fun choices(): List<PhoneVoice> {
        if (!ready) return emptyList()
        val offline = tts.voices.orEmpty().filter {
            !it.isNetworkConnectionRequired && it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true
        }
        val ours = offline.filter { sameLanguage(it.locale, locale) }
            .ifEmpty { offline.filter { sameLanguage(it.locale, Locale.ENGLISH) } }
        val default = tts.defaultVoice
        val country = (default?.locale ?: Locale.getDefault()).country
        return ours.sortedWith(
            compareByDescending<PhoneVoice> { it.quality }
                .thenBy { it.latency }
                .thenByDescending { it.name == default?.name }
                .thenByDescending { it.locale.country == country }
                .thenBy { it.name },
        )
    }

    /** Switches to the [chosen] voice, or to the best one when none is chosen or it's gone. */
    private fun useVoice() {
        val voices = choices()
        val voice = voices.firstOrNull { it.name == chosen } ?: voices.firstOrNull() ?: return
        tts.setVoice(voice)
    }

    /** Engines name languages two ways ("en" or "eng"), so this compares both. */
    private fun sameLanguage(a: Locale, b: Locale) =
        a.language == b.language || runCatching { a.isO3Language == b.isO3Language }.getOrDefault(false)

    /** Says [text], cutting off anything still being said. */
    fun say(text: String) {
        if (enabled) preview(text)
    }

    /** Says [text] even with the names turned off, so grown-ups can hear a voice before picking it. */
    fun preview(text: String) {
        if (ready) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "say")
    }

    fun stop() {
        if (ready) tts.stop()
    }

    fun shutdown() = tts.shutdown()
}
