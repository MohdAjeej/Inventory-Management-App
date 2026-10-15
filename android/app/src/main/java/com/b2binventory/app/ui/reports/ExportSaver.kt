package com.b2binventory.app.ui.reports

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import okhttp3.ResponseBody
import java.io.File

/** Saves a downloaded report to Downloads (Android 10+) or app storage, returning a shareable Uri. */
object ExportSaver {

    fun save(context: Context, body: ResponseBody, fileName: String, mimeType: String): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Could not create the file in Downloads")
            resolver.openOutputStream(uri)?.use { out -> body.byteStream().use { it.copyTo(out) } }
            uri
        } else {
            val dir = File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }
            val file = File(dir, fileName)
            file.outputStream().use { out -> body.byteStream().use { it.copyTo(out) } }
            FileProvider.getUriForFile(context, context.packageName + ".files", file)
        }
    }

    fun open(context: Context, uri: Uri, mimeType: String) {
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val share = Intent(Intent.ACTION_SEND).setType(mimeType).putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(share, "Share report").putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(view))
        context.startActivity(chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
