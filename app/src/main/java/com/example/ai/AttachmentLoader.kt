package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AttachmentLoader {

    suspend fun loadImageAttachment(
        context: Context,
        uri: Uri
    ): Result<AiAttachment.ImageAttachment> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = resolveDisplayName(context, uri) ?: "image.jpg"
            val rawBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = false
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                } ?: error("Unable to open image stream.")
            }
            val scaled = scaleDownBitmap(rawBitmap, maxDimension = 1280)
            if (rawBitmap !== scaled && !rawBitmap.isRecycled) {
                rawBitmap.recycle()
            }
            AiAttachment.ImageAttachment(name = fileName, bitmap = scaled)
        }
    }

    suspend fun loadDataAttachment(
        context: Context,
        uri: Uri
    ): Result<AiAttachment.DataAttachment> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = resolveDisplayName(context, uri) ?: "data.txt"
            val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().use { reader ->
                    val buffer = CharArray(32_000)
                    val read = reader.read(buffer)
                    if (read > 0) String(buffer, 0, read) else ""
                }
            } ?: error("Unable to read selected file.")
            if (content.isBlank()) {
                error("The selected file is empty.")
            }
            if (content.contains('\u0000')) {
                error("Unsupported binary file. Please choose a text, CSV, or JSON file.")
            }
            AiAttachment.DataAttachment(name = fileName, textContent = content)
        }
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        return cursor.getString(idx)
                    }
                }
            }
        return uri.lastPathSegment
    }

    private fun scaleDownBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }
        val ratio = width.toFloat() / height.toFloat()
        val targetWidth: Int
        val targetHeight: Int
        if (width >= height) {
            targetWidth = maxDimension
            targetHeight = (maxDimension / ratio).toInt().coerceAtLeast(1)
        } else {
            targetHeight = maxDimension
            targetWidth = (maxDimension * ratio).toInt().coerceAtLeast(1)
        }
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }
}
