package com.daveendev.justdoodles

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Every sound in the app, made from scratch in code: no sound files ship with the app and
 * nothing is downloaded. Soft marimba notes (one per crayon color, on a five-note scale so any
 * order sounds pleasant), pops, a water-drop "bloop", a boing, whooshes, a sparkle, a happy
 * jingle, a bedtime lullaby, a soft doorbell chime for something locked, and a quiet
 * crayon-on-paper scratch that plays while a finger draws.
 *
 * The sounds are rendered once per launch to small WAV files in the cache folder (a few
 * milliseconds of work) and played through [SoundPool], which mixes several at once quickly.
 * Everything is kept gentle: no loud or sudden noises.
 */
class Sounds(context: Context) {

    var enabled = true
        set(value) {
            field = value
            if (!value) stopScribble()
        }

    private val pool = SoundPool.Builder()
        .setMaxStreams(10)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    /** Sound name -> SoundPool id. Filled in from a background thread as sounds are made. */
    private val ids = ConcurrentHashMap<String, Int>()

    private var scribbleStream = 0
    private var scribbleVolume = 0f

    init {
        val dir = File(context.cacheDir, "sounds").apply { mkdirs() }
        Thread({
            try {
                for ((name, samples) in renderAll()) {
                    val file = File(dir, "$name.wav")
                    writeWav(file, samples)
                    ids[name] = pool.load(file.path, 1)
                }
            } catch (e: Exception) {
                Log.w("JustDoodles", "Could not prepare sounds", e)
            }
        }, "sounds").start()
    }

    /** One of the 12 notes, low to high. Each crayon color has its own. */
    fun note(index: Int, volume: Float = 0.7f) = play("note${index.coerceIn(0, NOTES.size - 1)}", volume)

    fun pop(pitch: Float = 1f) = play("pop", 0.6f, pitch)
    fun bloop() = play("bloop", 0.7f, 0.9f + Random.nextFloat() * 0.25f)
    fun boing() = play("boing", 0.55f, 0.9f + Random.nextFloat() * 0.4f)
    fun whoosh() = play("whoosh", 0.5f)
    fun sparkle() = play("sparkle", 0.55f)
    fun jingle() = play("jingle", 0.75f)
    fun lullaby() = play("lullaby", 0.7f)

    /** "Ding-dong": something needs PLUS. Friendly, never a buzzer. */
    fun chime() = play("chime", 0.6f)

    /**
     * Keeps the crayon scratch going while a finger moves. [level] is 0 (still) to 1 (fast).
     * Call [stopScribble] when every finger has lifted.
     */
    fun scribble(level: Float) {
        if (!enabled) return
        val id = ids["scribble"] ?: return
        scribbleVolume += (level.coerceIn(0f, 1f) * 0.45f - scribbleVolume) * 0.35f
        if (scribbleStream == 0) scribbleStream = pool.play(id, 0f, 0f, 0, -1, 1f)
        pool.setVolume(scribbleStream, scribbleVolume, scribbleVolume)
    }

    fun stopScribble() {
        if (scribbleStream != 0) pool.stop(scribbleStream)
        scribbleStream = 0
        scribbleVolume = 0f
    }

    fun release() {
        stopScribble()
        pool.release()
    }

    private fun play(name: String, volume: Float, rate: Float = 1f) {
        if (!enabled) return
        val id = ids[name] ?: return
        pool.play(id, volume, volume, 1, 0, rate.coerceIn(0.5f, 2f))
    }

    // ---- Making the sounds ------------------------------------------------------------------

    private fun renderAll(): List<Pair<String, FloatArray>> {
        val sounds = ArrayList<Pair<String, FloatArray>>()
        NOTES.forEachIndexed { i, f -> sounds += "note$i" to marimba(f, 0.7) }
        sounds += "pop" to sweep(900.0, 320.0, 0.09, fade = 1.5)
        sounds += "bloop" to sweep(260.0, 880.0, 0.2, fade = 1.2)
        sounds += "boing" to sweep(280.0, 620.0, 0.32, fade = 1.0, wobble = 0.08)
        sounds += "whoosh" to sweep(720.0, 220.0, 0.24, fade = 1.0)
        sounds += "sparkle" to mix(
            listOf(0.0, 0.05, 0.10, 0.15).zip(listOf(1318.5, 1568.0, 2093.0, 2637.0)).map { (at, f) -> at to marimba(f, 0.35, 0.5) },
        )
        sounds += "jingle" to mix(
            listOf(
                0.00 to marimba(523.25, 0.5), 0.12 to marimba(659.25, 0.5),
                0.24 to marimba(783.99, 0.5), 0.36 to marimba(1046.5, 0.9),
                0.36 to marimba(2093.0, 0.6, 0.25), 0.44 to marimba(2637.0, 0.5, 0.2),
            ),
        )
        sounds += "scribble" to crayonLoop()
        sounds += "lullaby" to lullabyTune()
        // E then C, like a doorbell.
        sounds += "chime" to mix(listOf(0.0 to bell(659.25, 0.9), 0.3 to bell(523.25, 1.3)))
        return sounds
    }

    /** A slow, soft tune for bedtime, on the same five-note scale as everything else. */
    private fun lullabyTune(): FloatArray {
        // (beat, Hz): E G E D C, D E C, A G C.
        val tune = listOf(
            0.0 to 659.25, 1.0 to 783.99, 2.0 to 659.25, 3.0 to 587.33, 4.0 to 523.25,
            5.5 to 587.33, 6.5 to 659.25, 7.5 to 523.25,
            9.0 to 440.00, 10.0 to 392.00, 11.0 to 523.25,
        )
        val beat = 0.55
        return mix(tune.map { (at, f) -> at * beat to bell(f, if (at == 11.0) 2.6 else 1.6) })
    }

    /** A music-box note: like the marimba, but softer and ringing on for longer. */
    private fun bell(freq: Double, seconds: Double, volume: Double = 0.4): FloatArray {
        val n = (seconds * RATE).toInt()
        return FloatArray(n) { i ->
            val t = i / RATE.toDouble()
            val body = sin(2 * PI * freq * t) * exp(-t * 2.2) + 0.2 * sin(2 * PI * freq * 2 * t) * exp(-t * 4.0)
            val end = min(1.0, (seconds - t) / 0.05)
            (body * attack(t) * end * volume).toFloat()
        }
    }

    /** A soft mallet note: a pure tone with a quick bright "tock" at the start, fading away. */
    private fun marimba(freq: Double, seconds: Double, volume: Double = 0.55): FloatArray {
        val n = (seconds * RATE).toInt()
        return FloatArray(n) { i ->
            val t = i / RATE.toDouble()
            val body = sin(2 * PI * freq * t) * exp(-t * 6.0)
            val tock = 0.3 * sin(2 * PI * freq * 4 * t) * exp(-t * 40.0)
            val end = min(1.0, (seconds - t) / 0.03) // fade out the last 30 ms, so there's no click
            ((body + tock) * attack(t) * end * volume).toFloat()
        }
    }

    /** A tone that glides from [from] to [to] Hz, optionally with a springy [wobble]. */
    private fun sweep(from: Double, to: Double, seconds: Double, fade: Double, wobble: Double = 0.0, volume: Double = 0.5): FloatArray {
        val n = (seconds * RATE).toInt()
        val out = FloatArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i / RATE.toDouble()
            val p = t / seconds
            val f = from * (to / from).pow(p) * (1 + wobble * sin(2 * PI * 18 * t))
            phase += 2 * PI * f / RATE
            out[i] = (sin(phase) * attack(t) * (1 - p).pow(fade) * volume).toFloat()
        }
        return out
    }

    /**
     * A soft papery scratch that loops without a seam: filtered noise with little random
     * swells, like a crayon's grain catching on paper.
     */
    private fun crayonLoop(): FloatArray {
        val length = (0.8 * RATE).toInt()
        val blend = (0.06 * RATE).toInt()
        val raw = FloatArray(length + blend)
        val random = Random(3)
        var low = 0.0
        var highIn = 0.0
        var high = 0.0
        var grain = 0.6
        var grainTarget = 0.6
        for (i in raw.indices) {
            val white = random.nextDouble() * 2 - 1
            low += 0.35 * (white - low)          // soften the hiss
            high = 0.97 * (high + low - highIn)  // and take out the rumble
            highIn = low
            if (i % 550 == 0) grainTarget = 0.35 + random.nextDouble() * 0.65
            grain += 0.003 * (grainTarget - grain)
            raw[i] = (high * grain * 0.9).toFloat()
        }
        // Cross-fade the tail into the start, so the end runs straight on into the beginning.
        return FloatArray(length) { i ->
            if (i < blend) {
                val a = i / blend.toFloat()
                raw[i] * a + raw[length + i] * (1 - a)
            } else {
                raw[i]
            }
        }
    }

    /** Layers sounds that start at different times (in seconds), keeping peaks soft. */
    private fun mix(parts: List<Pair<Double, FloatArray>>): FloatArray {
        val n = parts.maxOf { (at, s) -> (at * RATE).toInt() + s.size }
        val out = FloatArray(n)
        for ((at, s) in parts) {
            val start = (at * RATE).toInt()
            for (i in s.indices) out[start + i] += s[i]
        }
        for (i in out.indices) out[i] = tanh(out[i].toDouble()).toFloat()
        return out
    }

    /** A 5 ms fade-in, so nothing starts with a click. */
    private fun attack(t: Double) = min(1.0, t / 0.005)

    private fun writeWav(file: File, samples: FloatArray) {
        val bytes = ByteBuffer.allocate(44 + samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(36 + samples.size * 2).put("WAVE".toByteArray())
        bytes.put("fmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(1)          // PCM, mono
            .putInt(RATE).putInt(RATE * 2)    // sample rate, bytes per second
            .putShort(2).putShort(16)         // bytes per sample, bits per sample
        bytes.put("data".toByteArray()).putInt(samples.size * 2)
        for (s in samples) bytes.putShort((s.coerceIn(-1f, 1f) * 32767).toInt().toShort())
        file.writeBytes(bytes.array())
    }

    private companion object {
        const val RATE = 22050

        /** A major pentatonic scale from G4 up to A6. */
        val NOTES = doubleArrayOf(
            392.00, 440.00, 523.25, 587.33, 659.25, 783.99,
            880.00, 1046.50, 1174.66, 1318.51, 1567.98, 1760.00,
        )
    }
}
