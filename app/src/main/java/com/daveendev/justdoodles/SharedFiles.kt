package com.daveendev.justdoodles

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream

/**
 * Hands a drawing to another app through the share sheet, without any storage permission.
 *
 * The picture is written to a private cache folder, and the app it's shared with gets a
 * one-off right to read that one file. The provider isn't exported, so no other app can ask
 * it for anything, and it only ever serves files from that folder.
 */
class SharedFiles : ContentProvider() {

    override fun onCreate() = true

    override fun getType(uri: Uri) = "image/png"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("Shared drawings are read-only")
        val file = fileFor(requireNotNull(context), uri) ?: throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    /** The file's name and size, which the receiving app may ask for. */
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val file = fileFor(requireNotNull(context), uri) ?: return null
        val columns = (projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE))
            .filter { it == OpenableColumns.DISPLAY_NAME || it == OpenableColumns.SIZE }
        return MatrixCursor(columns.toTypedArray(), 1).apply {
            addRow(columns.map { if (it == OpenableColumns.DISPLAY_NAME) file.name else file.length() })
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        private const val FOLDER = "shared"

        /**
         * Saves [picture] as a PNG called [name], ready to share, and returns its address.
         * Only the newest shared picture is kept. Slow: call it off the main thread.
         */
        fun write(context: Context, name: String, picture: Bitmap): Uri {
            val dir = File(context.cacheDir, FOLDER)
            dir.deleteRecursively()
            dir.mkdirs()
            FileOutputStream(File(dir, name)).use { picture.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return Uri.Builder()
                .scheme("content")
                .authority("${context.packageName}.files")
                .appendPath(name)
                .build()
        }

        private fun fileFor(context: Context, uri: Uri): File? {
            val dir = File(context.cacheDir, FOLDER).canonicalFile
            val file = File(dir, uri.lastPathSegment ?: return null).canonicalFile
            // Never anything outside the shared folder, whatever the address says.
            return file.takeIf { it.parentFile == dir && it.isFile }
        }
    }
}
