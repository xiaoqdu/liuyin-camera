package com.example.watermark.ui.screens.watermark

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.ExifInterface
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.watermark.PhotoCache
import com.example.watermark.data.PhotoDao
import com.example.watermark.data.SettingsRepository
import com.example.watermark.data.WatermarkPhoto
import com.example.watermark.data.WatermarkPosition
import com.example.watermark.domain.LocationProvider
import com.example.watermark.domain.MapThumbnail
import com.example.watermark.domain.WatermarkEngine
import com.example.watermark.ui.components.GlassIconButton
import com.example.watermark.ui.components.PrimaryButton
import com.example.watermark.ui.components.WatermarkView
import com.example.watermark.ui.components.WmIcon
import com.example.watermark.ui.components.WmIconView
import com.example.watermark.ui.components.StampWatermarkView
import com.example.watermark.ui.components.StampBrandMark
import com.example.watermark.ui.components.StampMapThumb
import com.example.watermark.ui.components.dashedBorder
import com.example.watermark.ui.components.stampHasPlate
import com.example.watermark.ui.components.stampIsPill
import com.example.watermark.ui.components.stampSizeScale
import com.example.watermark.ui.theme.WmColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun WatermarkPreviewScreen(
    navController: NavHostController,
    uri: String,
    photoDao: PhotoDao,
    settingsRepository: SettingsRepository
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings = settingsRepository.settingsFlow.collectAsStateWithLifecycle(
        SettingsRepository.defaultSettings,
        lifecycleOwner.lifecycle
    ).value
    val parsedUri = remember(uri) { Uri.parse(uri) }
    val engine = remember { WatermarkEngine() }

    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var currentLocation by remember { mutableStateOf<String?>(null) }
    var currentFix by remember { mutableStateOf<LocationProvider.Fix?>(null) }
    var drag by remember { mutableStateOf(Offset.Zero) }      // px
    var exporting by remember { mutableStateOf(false) }
    var savedDone by remember { mutableStateOf(false) }
    var containerSize by remember { mutableStateOf(Size.Zero) }
    var wmSize by remember { mutableStateOf(Size.Zero) }
    val busy = remember { AtomicBoolean(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        currentFix = runCatching { LocationProvider(context).getBestFix() }.getOrNull()
        currentLocation = currentFix?.address
    }

    // 附加字段：方位角/气压计常驻订阅，天气按坐标拉取（失败静默降级）
    var weather by remember { mutableStateOf<com.example.watermark.domain.ExtraFields.Weather?>(null) }
    LaunchedEffect(currentFix) {
        com.example.watermark.domain.Bearing.register(context)
        com.example.watermark.domain.Barometer.register(context)
        val fix = currentFix ?: return@LaunchedEffect
        weather = withContext(Dispatchers.IO) {
            com.example.watermark.domain.WeatherProvider.fetch(fix.latitude, fix.longitude)
        }
    }
    val extraText = com.example.watermark.domain.ExtraFields.compose(
        context = context,
        settings = settings,
        location = currentFix?.let { f ->
            android.location.Location("fix").apply {
                latitude = f.latitude
                longitude = f.longitude
                f.altitude?.let { setAltitude(it) }
            }
        },
        weather = weather
    )

    LaunchedEffect(uri) {
        photo = withContext(Dispatchers.IO) { PhotoCache.getPhotoFromUri(context, parsedUri) }
        android.util.Log.d("WatermarkPreview", "load uri=$uri parsed=$parsedUri ok=${photo != null}")
        if (photo == null && !busy.get()) navController.popBackStack()
    }

    DisposableEffect(Unit) {
        onDispose { PhotoCache.recycle() }
    }

    LaunchedEffect(savedDone) {
        if (savedDone) {
            delay(800)
            navController.navigate("gallery") {
                popUpTo(0)
            }
        }
    }

    val text = engine.buildWatermarkText(settings, currentLocation, extraText = extraText)
    val stampParts = remember(settings, currentLocation, currentFix, extraText) {
        engine.buildStampParts(
            settings = settings,
            currentLocation = currentLocation,
            latitude = currentFix?.latitude,
            longitude = currentFix?.longitude,
            extraText = extraText
        )
    }
    val navTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(modifier = Modifier.fillMaxSize().background(WmColor.Ink)) {
        photo?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "待加水印照片",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 右上角坐标地图（两种排版都带）
        StampMapThumb(
            latitude = currentFix?.latitude,
            longitude = currentFix?.longitude,
            scale = 1f,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 20.dp, top = navTop + 64.dp)
        )

        // 右下角品牌角标（两种排版都带）
        StampBrandMark(
            code = stampParts.code,
            scale = 1f,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 22.dp, bottom = 348.dp)
        )

        // 安全区细框
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .padding(top = navTop, bottom = 120.dp)
                .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(10.dp))
        )

        // 水印：真实可拖，位移最终写入导出
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = navTop + 66.dp, bottom = 330.dp)
                .onGloballyPositioned {
                    containerSize = Size(it.size.width.toFloat(), it.size.height.toFloat())
                },
            contentAlignment = settings.watermarkPosition.toAlignment()
        ) {
            val baseX = when (settings.watermarkPosition) {
                WatermarkPosition.TOP_RIGHT, WatermarkPosition.BOTTOM_RIGHT -> containerSize.width - wmSize.width
                WatermarkPosition.CENTER -> (containerSize.width - wmSize.width) / 2f
                else -> 0f
            }
            val baseY = when (settings.watermarkPosition) {
                WatermarkPosition.BOTTOM_LEFT, WatermarkPosition.BOTTOM_RIGHT -> containerSize.height - wmSize.height
                WatermarkPosition.CENTER -> (containerSize.height - wmSize.height) / 2f
                else -> 0f
            }

            Box(
                modifier = Modifier
                    .onGloballyPositioned { wmSize = Size(it.size.width.toFloat(), it.size.height.toFloat()) }
                    .pointerInput(containerSize, wmSize, settings.watermarkPosition) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            if (containerSize.width <= 0f || wmSize.width <= 0f) return@detectDragGestures
                            val minX = minOf(0f, -baseX)
                            val maxX = maxOf(0f, containerSize.width - wmSize.width - baseX)
                            val minY = minOf(0f, -baseY)
                            val maxY = maxOf(0f, containerSize.height - wmSize.height - baseY)
                            drag = Offset(
                                x = (drag.x + dragAmount.x).coerceIn(minX, maxX),
                                y = (drag.y + dragAmount.y).coerceIn(minY, maxY)
                            )
                        }
                    }
                    .graphicsOffset(drag)
            ) {
                Box(
                    modifier = Modifier
                        .dashedBorder(color = WmColor.Amber.copy(alpha = 0.85f), width = 1.5.dp, dash = 6.dp, gap = 5.dp, radius = 10.dp)
                        .padding(6.dp)
                        .drawBehind {
                            val s = 8.dp.toPx()
                            val c = WmColor.Amber
                            val off = 4.dp.toPx()
                            drawRect(c, Offset(-off, -off), Size(s, s))
                            drawRect(c, Offset(size.width + off - s, -off), Size(s, s))
                            drawRect(c, Offset(-off, size.height + off - s), Size(s, s))
                            drawRect(c, Offset(size.width + off - s, size.height + off - s), Size(s, s))
                        }
                ) {
                    if (com.example.watermark.domain.IndustryLayout.effectiveLayout(settings) == com.example.watermark.data.WatermarkLayout.STAMP) {
                        StampWatermarkView(
                            time = stampParts.time,
                            date = stampParts.date,
                            week = stampParts.week,
                            location = stampParts.location,
                            coordinate = stampParts.coordinate,
                            title = stampParts.title,
                            extra = stampParts.extra,
                            details = stampParts.details.map { it.asText() },
                            color = androidx.compose.ui.graphics.Color(settings.watermarkColor),
                            bgColor = androidx.compose.ui.graphics.Color(settings.watermarkBgColor),
                            scale = 1f * stampSizeScale(settings.watermarkSize),
                            plate = stampHasPlate(settings.watermarkStyle),
                            pill = stampIsPill(settings.watermarkStyle),
                            stroke = settings.watermarkStyle == com.example.watermark.data.WatermarkStyle.STROKE
                        )
                    } else {
                        WatermarkView(
                            settings = settings,
                            text = text,
                            details = stampParts.details.map { it.asText() },
                            fontSize = (21f * stampSizeScale(settings.watermarkSize)).sp,
                            maxLines = 4
                        )
                    }
                }
            }
        }

        // 顶部：返回 / 确认水印 / 重置
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = navTop + 12.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassIconButton(icon = WmIcon.Back, onClick = { navController.popBackStack() }, size = 40.dp)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .background(Color(0x990C0F13), RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "确认水印",
                        color = Color(0xFFE9EDF2),
                        fontSize = 12.5.sp
                    )
                }
            }
            GlassIconButton(
                icon = WmIcon.Reset,
                onClick = { drag = Offset.Zero },
                tint = if (drag != Offset.Zero) WmColor.AmberText else WmColor.TextSecondary,
                size = 40.dp
            )
        }

        // 底部面板
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(Color(0xDB111419))
                .padding(horizontal = 18.dp)
                .padding(top = 10.dp, bottom = 22.dp)
        ) {
            Box(
                modifier = Modifier
                    .padding(bottom = 14.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .background(Color(0x38FFFFFF), RoundedCornerShape(4.dp))
                    .align(Alignment.CenterHorizontally)
            )
            Text(
                text = if (drag == Offset.Zero) "按住水印拖动微调，导出后位置与预览一致"
                else "已微调位置 · 导出时同步生效",
                color = Color(0x8FF2F4F7),
                fontSize = 11.5.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickChip("模板", settings.watermarkTemplatePreset.label, Modifier.weight(1f))
                QuickChip("时间格式", settings.watermarkTimeFormat.shortLabel, Modifier.weight(1f))
                QuickChip("字号", "${settings.watermarkSize.toInt()} sp", Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton(
                    text = when {
                        savedDone -> "已保存到相册"
                        exporting -> "导出中…"
                        else -> "导出到相册"
                    },
                    onClick = {
                        if (busy.get()) return@PrimaryButton
                        busy.set(true)
                        exporting = true
                        val bmp = photo
                        val dx = if (containerSize.width > 0) drag.x / containerSize.width else 0f
                        val dy = if (containerSize.height > 0) drag.y / containerSize.height else 0f
                        scope.launch {
                            try {
                                if (bmp != null) {
                                    withContext(Dispatchers.IO) {
                                        // 导出走原图分辨率，预览用的 1200px 采样图只用来显示
                                        val fullBmp = PhotoCache.getPhotoForExport(context, parsedUri) ?: bmp
                                        val mapThumb = currentFix?.let {
                                            MapThumbnail.load(context, it.latitude, it.longitude)
                                        }
                                        // 连拍：整批按同一水印内容逐张导出
                                        val batchUris = com.example.watermark.ui.screens.camera.BurstBatch.pending
                                            .ifEmpty { listOf(parsedUri) }
                                        batchUris.forEach { one ->
                                            val oneFull = if (one == parsedUri) fullBmp
                                            else PhotoCache.getPhotoForExport(context, one) ?: return@forEach
                                            val finalBitmap = engine.drawWatermark(
                                                sourceBitmap = oneFull,
                                                settings = settings,
                                                currentLocation = currentLocation,
                                                extraOffsetX = dx,
                                                extraOffsetY = dy,
                                                latitude = currentFix?.latitude,
                                                longitude = currentFix?.longitude,
                                                mapThumb = mapThumb,
                                                stampCode = stampParts.code,
                                                extraText = extraText
                                            )
                                            saveToGallery(context, finalBitmap, settings, photoDao, one, stampParts.location, stampParts.code)
                                            if (oneFull !== fullBmp) oneFull.recycle()
                                        }
                                        com.example.watermark.ui.screens.camera.BurstBatch.pending = emptyList()
                                    }
                                }
                                exporting = false
                                savedDone = true
                                busy.set(false)
                            } catch (e: Exception) {
                                exporting = false
                                busy.set(false)
                                Log.e("WatermarkPreview", "export failed", e)
                                android.widget.Toast.makeText(context, "保存失败：${e.message}", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    loading = exporting,
                    icon = if (savedDone) WmIcon.Check else WmIcon.Download
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .background(Color(0x12FFFFFF), RoundedCornerShape(14.dp))
                        .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(14.dp))
                        .clickable {
                            if (!busy.get()) {
                                busy.set(true)
                                val bmp = photo
                                val dx = if (containerSize.width > 0) drag.x / containerSize.width else 0f
                                val dy = if (containerSize.height > 0) drag.y / containerSize.height else 0f
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            if (bmp != null) {
                                                val fullBmp = PhotoCache.getPhotoForExport(context, parsedUri) ?: bmp
                                                val mapThumb = currentFix?.let {
                                                    MapThumbnail.load(context, it.latitude, it.longitude)
                                                }
                                                val out = engine.drawWatermark(
                                                    sourceBitmap = fullBmp,
                                                    settings = settings,
                                                    currentLocation = currentLocation,
                                                    extraOffsetX = dx,
                                                    extraOffsetY = dy,
                                                    latitude = currentFix?.latitude,
                                                    longitude = currentFix?.longitude,
                                                    mapThumb = mapThumb,
                                                    stampCode = stampParts.code,
                                                    extraText = extraText
                                                )
                                                shareTo(context, out)
                                            }
                                        }
                                    } finally {
                                        busy.set(false)
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    WmIconView(WmIcon.Share, tint = WmColor.TextSecondary, size = 20.dp)
                }
            }
        }
    }
}

@Composable
private fun QuickChip(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(Color(0x0FFFFFFF))
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(11.dp))
            .padding(horizontal = 11.dp, vertical = 10.dp)
    ) {
        Text(label, color = WmColor.TextTertiary, fontSize = 10.sp, letterSpacing = 0.4.sp)
        Text(
            value,
            color = Color(0xFFEDF0F4),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

private fun WatermarkPosition.toAlignment(): Alignment = when (this) {
    WatermarkPosition.TOP_LEFT -> Alignment.TopStart
    WatermarkPosition.TOP_RIGHT -> Alignment.TopEnd
    WatermarkPosition.BOTTOM_LEFT -> Alignment.BottomStart
    WatermarkPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
    WatermarkPosition.CENTER -> Alignment.Center
}

private fun Modifier.graphicsOffset(offset: Offset): Modifier = this.graphicsLayer {
    translationX = offset.x
    translationY = offset.y
}

private val com.example.watermark.data.WatermarkTimeFormat.shortLabel: String
    get() = when (this) {
        com.example.watermark.data.WatermarkTimeFormat.YYYY_MM_DD_WEEKTIME -> "年月日 星期 时:分"
        com.example.watermark.data.WatermarkTimeFormat.YYYY_MM_DD_HHMM -> "年月日 时:分:秒"
        com.example.watermark.data.WatermarkTimeFormat.YYYY_MM_DD -> "年月日"
        com.example.watermark.data.WatermarkTimeFormat.MM_DD_YYYY -> "月日 年"
        com.example.watermark.data.WatermarkTimeFormat.HH_MM_SS -> "时:分:秒"
        com.example.watermark.data.WatermarkTimeFormat.YYYY_MM_DD_SLASH -> "年/月/日 时:分"
        com.example.watermark.data.WatermarkTimeFormat.CUSTOM -> "自定义"
    }

/** 导出到系统相册（保留 Exif），并写入 Room 索引。 */
suspend fun saveToGallery(
    context: Context,
    bitmap: Bitmap,
    settings: com.example.watermark.data.WatermarkSettings,
    photoDao: PhotoDao,
    originalUri: Uri,
    locationText: String? = null,
    codeText: String? = null
): Uri? {
    val fileName = "watermark_${System.currentTimeMillis()}.jpg"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: throw RuntimeException("无法创建文件")

    val tempFile = File(context.cacheDir, "export_${System.currentTimeMillis()}.jpg")
    FileOutputStream(tempFile).use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
    }

    try {
        var restored = PhotoCache.restoreExifTo(context, tempFile)
        if (!restored) {
            getRealPathFromUri(context, originalUri)?.let { path ->
                runCatching {
                    val src = ExifInterface(path)
                    val dst = ExifInterface(tempFile)
                    listOf(
                        ExifInterface.TAG_DATETIME,
                        ExifInterface.TAG_GPS_LATITUDE,
                        ExifInterface.TAG_GPS_LONGITUDE,
                        ExifInterface.TAG_GPS_LATITUDE_REF,
                        ExifInterface.TAG_GPS_LONGITUDE_REF,
                        ExifInterface.TAG_MAKE,
                        ExifInterface.TAG_MODEL,
                        ExifInterface.TAG_FOCAL_LENGTH,
                        ExifInterface.TAG_EXPOSURE_TIME,
                        ExifInterface.TAG_ISO_SPEED_RATINGS,
                        ExifInterface.TAG_ORIENTATION
                    ).forEach { tag ->
                        src.getAttribute(tag)?.let { dst.setAttribute(tag, it) }
                    }
                    dst.saveAttributes()
                    restored = true
                }
            }
        }
    } catch (e: Exception) {
        Log.w("WatermarkPreview", "exif copy skipped", e)
    }

    context.contentResolver.openOutputStream(uri)?.use { output ->
        FileInputStream(tempFile).use { input -> input.copyTo(output) }
    }
    tempFile.delete()

    values.clear()
    values.put(MediaStore.Images.Media.IS_PENDING, 0)
    context.contentResolver.update(uri, values, null, null)

    photoDao.insert(
        WatermarkPhoto(
            uri = uri.toString(),
            timestamp = System.currentTimeMillis(),
            galleryId = uri.lastPathSegment?.toLongOrNull() ?: System.currentTimeMillis(),
            title = settings.nickname.takeIf { it.isNotBlank() && it != "未设置" }.orEmpty(),
            location = locationText.orEmpty(),
            code = codeText.orEmpty()
        )
    )
    return uri
}

/** 分享成片：写临时文件并通过 FileProvider 授权。 */
fun shareTo(context: Context, bitmap: Bitmap) {
    runCatching {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "share_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out) }
        val authority = "${context.packageName}.fileprovider"
        val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "分享照片"))
    }.onFailure {
        Log.w("WatermarkPreview", "share failed", it)
        android.widget.Toast.makeText(context, "分享失败：${it.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

private fun getRealPathFromUri(context: Context, uri: Uri): String? {
    if (uri.scheme == "file") return uri.path
    return try {
        val projection = arrayOf(MediaStore.MediaColumns.DATA)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            if (cursor.moveToFirst()) cursor.getString(index) else null
        }
    } catch (_: Exception) {
        null
    }
}
