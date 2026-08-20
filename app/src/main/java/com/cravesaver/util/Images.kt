package com.cravesaver.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File

/** 读取 content URI 图片并压缩成 JPEG 字节：最长边 ≤ maxEdge，quality 80 */
fun compressToJpeg(
    context: Context,
    uri: Uri,
    maxEdge: Int = 1280,
    quality: Int = 80
): ByteArray {
    val source = context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input)
    } ?: throw IllegalArgumentException("无法读取图片")
    return toJpegBytes(source, maxEdge, quality)
}

/** 读取本地文件图片并压缩成 JPEG 字节（后台 Worker 用） */
fun compressToJpeg(
    file: File,
    maxEdge: Int = 1280,
    quality: Int = 80
): ByteArray {
    val source = BitmapFactory.decodeFile(file.absolutePath)
        ?: throw IllegalArgumentException("无法读取图片")
    return toJpegBytes(source, maxEdge, quality)
}

/**
 * 把 content URI 的图片复制到 cacheDir 临时文件。
 * Photo Picker 的 URI 在进程重启后可能失效，入队 WorkManager 前先落地一份。
 */
fun copyImageToCache(context: Context, uri: Uri): File {
    val file = File.createTempFile("recognize_", ".jpg", context.cacheDir)
    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalArgumentException("无法读取图片")
        return file
    } catch (e: Exception) {
        file.delete()
        throw e
    }
}

/** 缩放（最长边 ≤ maxEdge）并按 JPEG quality 压缩 */
private fun toJpegBytes(source: Bitmap, maxEdge: Int, quality: Int): ByteArray {
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
