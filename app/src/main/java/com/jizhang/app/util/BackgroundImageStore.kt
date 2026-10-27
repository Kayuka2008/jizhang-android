package com.jizhang.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.annotation.WorkerThread
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/** 背景仅保存在应用私有目录；解码、压缩与删除由调用方放在 IO 线程。 */
object BackgroundImageStore {
    private const val MAX_EDGE = 2048
    private const val DIRECTORY = "appearance"
    private val fileNamePattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]*\\.jpg")

    /** 不接受路径或目录名，避免持久化内容被当作任意文件路径。 */
    fun resolve(context: Context, name: String?): File? {
        if (name == null || !fileNamePattern.matches(name)) return null
        return File(File(context.filesDir, DIRECTORY), name).takeIf { it.isFile }
    }

    /** 返回新文件名；调用成功前不会修改现有背景。 */
    @WorkerThread
    fun importImage(context: Context, uri: Uri): String {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val input = resolver.openInputStream(uri) ?: throw IOException("无法读取图片")
        input.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("图片格式无效")

        var sample = 1
        val longest = max(bounds.outWidth, bounds.outHeight)
        while (longest / (sample * 2L) >= MAX_EDGE) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        var bitmap: Bitmap? = null
        var temporary: File? = null
        var destination: File? = null
        var complete = false
        try {
            bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: throw IOException("图片解码失败")
            val decoded = checkNotNull(bitmap)
            val edge = max(decoded.width, decoded.height)
            if (edge > MAX_EDGE) {
                val scale = MAX_EDGE.toFloat() / edge
                val resized = Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * scale).roundToInt().coerceAtLeast(1),
                    (decoded.height * scale).roundToInt().coerceAtLeast(1),
                    true,
                )
                if (resized !== decoded) decoded.recycle()
                bitmap = resized
            }
            val current = checkNotNull(bitmap)
            val matrix = orientationMatrix(orientation)
            if (!matrix.isIdentity) {
                val rotated = Bitmap.createBitmap(current, 0, 0, current.width, current.height, matrix, true)
                if (rotated !== current) current.recycle()
                bitmap = rotated
            }
            val oriented = checkNotNull(bitmap)
            if (oriented.hasAlpha()) {
                val flattened = Bitmap.createBitmap(oriented.width, oriented.height, Bitmap.Config.ARGB_8888)
                Canvas(flattened).apply {
                    drawColor(Color.WHITE)
                    drawBitmap(oriented, 0f, 0f, null)
                }
                oriented.recycle()
                bitmap = flattened
            }
            val directory = File(context.filesDir, DIRECTORY)
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("无法保存背景")
            val name = "background-${UUID.randomUUID()}.jpg"
            destination = File(directory, name)
            temporary = File(directory, "$name.tmp")
            val finalBitmap = checkNotNull(bitmap)
            FileOutputStream(temporary).use { output ->
                if (!finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)) throw IOException("图片保存失败")
                output.fd.sync()
            }
            if (!temporary.renameTo(destination)) throw IOException("背景文件保存失败")
            complete = true
            return name
        } finally {
            bitmap?.recycle()
            temporary?.delete()
            if (!complete) destination?.delete()
        }
    }

    @WorkerThread
    fun delete(context: Context, name: String?) {
        resolve(context, name)?.delete()
    }

    /** 恢复默认和恢复出厂时一并移除旧图及未完成的临时文件。 */
    @WorkerThread
    fun clear(context: Context) {
        val directory = File(context.filesDir, DIRECTORY)
        directory.listFiles()?.forEach { if (it.isFile) it.delete() }
        directory.delete()
    }

    private fun orientationMatrix(orientation: Int) = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                postRotate(90f)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                postRotate(-90f)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
        }
    }
}
