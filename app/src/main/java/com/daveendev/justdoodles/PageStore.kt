package com.daveendev.justdoodles

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Keeps one saved picture per page in the app's private storage, so switching pages
 * (or leaving the app) never loses a drawing. Nothing ever leaves the device.
 */
class PageStore(context: Context) {

    private val dir = File(context.filesDir, "drawings").apply { mkdirs() }
    private val io = Executors.newSingleThreadExecutor()

    /** Pictures handed to [save] that are not on disk yet. */
    private val pending = ConcurrentHashMap<String, Bitmap>()

    private fun file(id: String) = File(dir, "$id.png")

    /** Writes [bitmap] in the background. The store owns [bitmap] from now on. */
    fun save(id: String, bitmap: Bitmap) {
        pending[id] = bitmap
        io.execute {
            try {
                val tmp = File(dir, "$id.tmp")
                FileOutputStream(tmp).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                if (!tmp.renameTo(file(id))) Log.w(TAG, "Could not save page $id")
            } catch (e: IOException) {
                Log.w(TAG, "Could not save page $id", e)
            }
            pending.remove(id, bitmap)
        }
    }

    /** True if page [id] has a saved picture. */
    fun has(id: String) = pending.containsKey(id) || file(id).exists()

    /** When page [id] was last saved, in milliseconds since 1970. */
    fun lastModified(id: String) = if (pending.containsKey(id)) System.currentTimeMillis() else file(id).lastModified()

    /** Throws away page [id]'s picture, including one still waiting to be written. */
    fun delete(id: String) {
        pending.remove(id)
        file(id).delete()
        // A save queued earlier may still write the file; this runs after it and removes it again.
        io.execute { file(id).delete() }
    }

    /** The saved picture for [id] as a new mutable bitmap, or null if it was never drawn on. */
    fun load(id: String): Bitmap? {
        pending[id]?.let { return it.copy(Bitmap.Config.ARGB_8888, true) }
        val f = file(id)
        if (!f.exists()) return null
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inMutable = true })
    }

    /** A small version of the saved picture for [id], at least [width] pixels wide if possible. */
    fun loadThumb(id: String, width: Int): Bitmap? {
        pending[id]?.let {
            return Bitmap.createScaledBitmap(it, width, maxOf(1, it.height * width / it.width), true)
        }
        val f = file(id)
        if (!f.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width) sample *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private companion object {
        const val TAG = "JustDoodles"
    }
}
