package com.example.watermark

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Manages a single temp photo stored in app cache directory.
 * Avoids static Bitmap holding to prevent memory leaks.
 */
object PhotoCache {
    private var cachedFile: File? = null
    private var cachedUri: Uri? = null
    /** Exif file from original capture; survives Bitmap compression loss. */
    private var cachedExifFile: File? = null
    /** 连拍批次：每张单独一个文件，统一在 recycle() 时清理。 */
    private val batchFiles = mutableListOf<File>()

    fun save(bitmap: Bitmap, context: Context): Uri? {
        recycle()
        return writeTemp(bitmap, context, "temp_")
    }

    /** 连拍用：不清理前面的文件，整批交给 recycle() 统一删除。 */
    fun saveForBatch(bitmap: Bitmap, context: Context): Uri? {
        val uri = writeTemp(bitmap, context, "temp_${batchFiles.size}_")
        uri?.let { u -> batchFiles.add(File(u.path!!)) }
        return uri
    }

    private fun writeTemp(bitmap: Bitmap, context: Context, prefix: String): Uri? {
        val file = File(context.cacheDir, "${prefix}${System.currentTimeMillis()}.jpg")
        return try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            file.takeIf { it.exists() }?.let { f ->
                cachedFile = f
                cachedUri = Uri.fromFile(f)
                cachedUri!!
            }
        } catch (e: Exception) {
            file.delete()
            null
        }
    }

    /**
     * Save bitmap AND an Exif file (preserves Exif that Bitmap.compress loses).
     */
    fun saveWithExif(bitmap: Bitmap, context: Context, exifFile: File): Uri? {
        // Copy Exif to our cache directory first
        val destExif = File(context.cacheDir, "exif_${System.currentTimeMillis()}.exif")
        try {
            exifFile.inputStream().use { input ->
                destExif.outputStream().use { input.copyTo(it) }
            }
        } catch (_: Exception) {
            // Exif copy failure is non-fatal; proceed without it
        }
        val uri = save(bitmap, context)
        if (uri != null) {
            cachedExifFile = destExif
        }
        return uri
    }

    /**
     * Copy content from a Uri to cache file.
     */
    fun saveFromUri(uri: Uri, context: Context): Uri? {
        recycle()
        val file = File(context.cacheDir, "temp_${System.currentTimeMillis()}.jpg")
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { out ->
                    input.copyTo(out)
                }
            }
            file.takeIf { it.exists() }?.let { f ->
                cachedFile = f
                cachedUri = Uri.fromFile(f)
                cachedUri!!
            }
        } catch (e: Exception) {
            file.delete()
            null
        }
    }

    /**
     * Load bitmap from a file URI with inSampleSize to prevent OOM.
     * maxDim 默认 1200 仅用于屏幕预览；导出时必须传更大的值，否则成片会被降采样。
     */
    fun getPhotoFromUri(context: Context, uri: Uri, maxDim: Int = 1200): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options().apply {
                inSampleSize = computeSampleSize(uri, context, maxDim)
            }
            val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    /** 导出专用：按原始分辨率（上限 4096）解码，避免成片被压小。 */
    fun getPhotoForExport(context: Context, uri: Uri): Bitmap? = getPhotoFromUri(context, uri, 4096)

    /**
     * Delete cached file and recycle held bitmap reference.
     */
    fun recycle() {
        cachedFile?.delete()
        cachedFile = null
        cachedUri = null
        batchFiles.forEach { runCatching { it.delete() } }
        batchFiles.clear()
        cachedExifFile?.delete()
        cachedExifFile = null
    }

    val uri: Uri? get() = cachedUri

    /** 最近一次拍摄的原始底片（未加水印），供设置页实时预览做背景，避免叠出两层水印。 */
    fun latestOriginalUri(context: Context): Uri? {
        val newest = context.cacheDir
            ?.listFiles { f -> f.isFile && f.name.startsWith("temp_") && f.name.endsWith(".jpg") }
            ?.maxByOrNull { it.lastModified() }
        return newest?.let { Uri.fromFile(it) }
    }

    /** Returns the cached Exif source file (camera capture only). */
    fun getCachedExifFile(): String? = cachedExifFile?.takeIf { it.exists() }?.absolutePath

    /** Restore Exif tags from our serialized cache to the target file. */
    fun restoreExifTo(context: android.content.Context, targetFile: File): Boolean {
        val src = cachedExifFile ?: return false
        if (!src.exists()) return false
        try {
            val orig = ExifInterface(targetFile)
            src.readLines(Charsets.UTF_8).forEach { line ->
                val eqIdx = line.indexOf('=')
                if (eqIdx > 0) {
                    val tag = line.substring(0, eqIdx)
                    val value = line.substring(eqIdx + 1)
                    orig.setAttribute(tag, value)
                }
            }
            orig.saveAttributes()
            return true
        } catch (_: Exception) {
            return false
        }
    }

    private fun computeSampleSize(uri: Uri, context: Context, maxDim: Int): Int {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()
            val rawWidth = options.outWidth
            val rawHeight = options.outHeight
            val raw = if (rawWidth > rawHeight) rawWidth else rawHeight
            if (raw <= 0) 1
            else if (raw <= maxDim) 1
            else (1 until 64).firstOrNull { (1 shl it) * maxDim >= raw } ?: 1
        } catch (_: Exception) {
            1
        }
    }
}
