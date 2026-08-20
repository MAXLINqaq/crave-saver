package com.cravesaver.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream

/** 读取图片并压缩成 JPEG 字节：最长边 ≤ maxEdge，quality 80，控制 base64 后的请求体积 */
fun compressToJpeg(
    context: Context,
    uri: Uri,
    maxEdge: Int = 1280,
    quality: Int = 80
): ByteArray {
    val source = context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input)
    } ?: throw IllegalArgumentException("无法读取图片")

    val longest = maxOf(source.width, source.height)
    val bitmap = if (longest > maxEdge) {
        val scale = maxEdge.toFloat() / longest
        Bitmap.createScaledBitmap(
            source,
            (source.width * scale).toInt().coerceAtLeast(1),
            (source.height * scale).toInt().coerceAtLeast(1),
            true
        )
    } else {
        source
    }

    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
    return out.toByteArray()
}
