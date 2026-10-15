package com.b2binventory.app.ui.common

import android.content.Context
import android.net.Uri
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

object ImageParts {
    private const val MAX_BYTES = 5 * 1024 * 1024

    /** Reads a picked image into a multipart part named "file"; null if unreadable or too large. */
    fun fromUri(context: Context, uri: Uri): MultipartBody.Part? {
        val resolver = context.contentResolver
        val type = resolver.getType(uri) ?: "image/jpeg"
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        if (bytes.size > MAX_BYTES) return null
        val extension = when (type) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        return MultipartBody.Part.createFormData("file", "image.$extension", bytes.toRequestBody(type.toMediaTypeOrNull()))
    }
}
