package com.shashank.applocker.data.intruder

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.shashank.applocker.domain.intruder.IntruderPhotoName
import com.shashank.applocker.service.IntruderPhotoSink
import com.shashank.applocker.service.PhotoTarget
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saves intruder photos to the shared gallery as `Pictures/Intruder/Intruder-yyyyMMdd-HHmmss.jpg`.
 *
 * - Android 10+: a MediaStore insert with RELATIVE_PATH and IS_PENDING while writing. No storage
 *   permission is needed.
 * - Android 8-9: a MediaStore insert pointing at the public Pictures/Intruder folder. This needs
 *   WRITE_EXTERNAL_STORAGE (declared with maxSdkVersion 28 and requested at runtime).
 *
 * Photos are never kept in private storage, and file names or URIs are never logged.
 */
@Singleton
class MediaStoreIntruderPhotoSink @Inject constructor(
    @ApplicationContext private val context: Context,
) : IntruderPhotoSink {
    private val resolver: ContentResolver get() = context.contentResolver

    override fun open(): PhotoTarget? = try {
        val name = IntruderPhotoName.unique(IntruderPhotoName.format(System.currentTimeMillis()), ::nameExists)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$FOLDER")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), FOLDER)
                check(dir.isDirectory || dir.mkdirs())
                @Suppress("DEPRECATION")
                put(MediaStore.Images.Media.DATA, File(dir, name).absolutePath)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        val stream = uri?.let { resolver.openOutputStream(it) }
        if (uri == null || stream == null) {
            uri?.let { runCatching { resolver.delete(it, null, null) } }
            null
        } else {
            Target(uri, stream)
        }
    } catch (_: Exception) {
        null
    }

    private fun nameExists(name: String): Boolean =
        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.DISPLAY_NAME} = ?",
            arrayOf(name),
            null,
        )?.use { it.moveToFirst() } ?: false

    private inner class Target(private val uri: Uri, override val outputStream: OutputStream) : PhotoTarget {
        override fun commit() {
            runCatching { outputStream.flush() }
            if (Build.VERSION.SDK_INT >= 29) {
                val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
            }
        }

        override fun abort() {
            runCatching { outputStream.close() }
            runCatching { resolver.delete(uri, null, null) }
        }
    }

    private companion object {
        const val FOLDER = "Intruder"
    }
}
