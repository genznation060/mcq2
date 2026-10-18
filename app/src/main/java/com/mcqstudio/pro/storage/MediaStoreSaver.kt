package com.mcqstudio.pro.storage

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object MediaStoreSaver {

    const val RELATIVE_SUBDIRECTORY = "Movies/MCQ Studio"

    /**
     * Saves an MP4 file into public MediaStore storage under "Movies/MCQ Studio"
     * so it immediately and permanently appears in Gallery and Google Photos.
     */
    suspend fun saveVideoToGallery(
        context: Context,
        sourceFile: File,
        baseName: String = "MCQ_Studio_Video"
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = "${baseName}_${System.currentTimeMillis()}.mp4"
            val resolver = context.contentResolver

            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.TITLE, fileName.removeSuffix(".mp4"))
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                put(MediaStore.Video.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, RELATIVE_SUBDIRECTORY)
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val uri = resolver.insert(collection, values)
                ?: throw IllegalStateException("Failed to insert video record into MediaStore.")

            resolver.openOutputStream(uri)?.use { outputStream ->
                FileInputStream(sourceFile).use { inputStream ->
                    inputStream.copyTo(outputStream, bufferSize = 64 * 1024)
                }
                outputStream.flush()
            } ?: throw IllegalStateException("Failed to open output stream for MediaStore Uri.")

            // On Android 10+, release the IS_PENDING lock so gallery scanners pick it up instantly
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }

            uri
        }
    }

    /**
     * Opens the saved video in any installed video player / gallery app.
     */
    fun openVideoInPlayer(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(Intent.createChooser(intent, "Open Video").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {
            // Player not found
        }
    }

    /**
     * Launches standard Android system share sheet for the saved video.
     */
    fun shareVideo(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(Intent.createChooser(intent, "Share MCQ Video").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {
            // Share handler not found
        }
    }
}
