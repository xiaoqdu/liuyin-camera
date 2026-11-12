package com.example.watermark.ui.screens.gallery

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.watermark.PhotoCache
import com.example.watermark.data.PhotoDao
import com.example.watermark.data.WatermarkPhoto
import com.example.watermark.ui.components.GhostButton
import com.example.watermark.ui.components.PrimaryButton
import com.example.watermark.ui.components.WmChip
import com.example.watermark.ui.components.WmIcon
import com.example.watermark.ui.components.WmIconView
import com.example.watermark.ui.theme.WmColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Calendar

private object ThumbCache {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024) {
        // 上限按 KB 计，约 24MB
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun get(key: String): Bitmap? = cache.get(key)
    fun put(key: String, bitmap: Bitmap) { cache.put(key, bitmap) }
}

@Composable
fun PhotoGalleryScreen(
    navController: NavHostController,
    photoDao: PhotoDao
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val photos by photoDao.getAll().collectAsStateWithLifecycle(
        emptyList(),
        lifecycleOwner.lifecycle
    )

    var filter by remember { mutableStateOf(GalleryFilter.ALL) }
    var ascending by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var project by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<WatermarkPhoto?>(null) }
    val titles by photoDao.getTitles().collectAsStateWithLifecycle(emptyList(), lifecycleOwner.lifecycle)
    val scope = rememberCoroutineScope()

    // 首次进入校验一次 URI，失效记录直接清掉
    LaunchedEffect(Unit) {
        runCatching {
            photoDao.getAll().first().forEach { photo ->
                try {
                    context.contentResolver.openInputStream(Uri.parse(photo.uri))?.close()
                } catch (_: Exception) {
                    photoDao.deleteByGalleryId(photo.galleryId)
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val saved = PhotoCache.saveFromUri(it, context)
            saved?.let { u -> navController.navigate("watermark_preview/${Uri.encode(u.toString())}") }
        }
    }

    val visible = remember(photos, filter, ascending, query, project) {
        photos
            .filter { filter.match(it.timestamp) }
            .filter { project.isBlank() || it.title == project }
            .filter {
                query.isBlank() ||
                    it.title.contains(query, true) ||
                    it.location.contains(query, true) ||
                    it.code.contains(query, true)
            }
            .let { if (ascending) it.reversed() else it }
    }

    val navTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WmColor.Surface)
            .padding(top = navTop)
    ) {
        // 页头
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 16.dp, top = 10.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("相册", color = WmColor.TextPrimary, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = "  ${photos.size} 张",
                color = WmColor.TextTertiary,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 6.dp, top = 4.dp)
            )
            Spacer(Modifier.weight(1f))
            CircleIconButton(WmIcon.Shield, "照片验真") { navController.navigate("verify") }
            Spacer(Modifier.width(9.dp))
            CircleIconButton(WmIcon.Plus, "从系统相册导入") { importLauncher.launch("image/*") }
            Spacer(Modifier.width(9.dp))
            CircleIconButton(WmIcon.Gear, "水印设置") { navController.navigate("settings") }
        }

        // 分段筛选
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GalleryFilter.values().forEach { f ->
                WmChip(text = f.label, selected = filter == f, onClick = { filter = f })
            }
        }

        // 搜索 + 项目分类（对齐市面水印相机的查找与分类）
        Row(
            modifier = Modifier
                .padding(start = 18.dp, end = 18.dp, top = 8.dp)
                .fillMaxWidth()
                .height(38.dp)
                .background(Color(0x0FFFFFFF), RoundedCornerShape(10.dp))
                .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WmIconView(WmIcon.Search, tint = WmColor.TextTertiary, size = 14.dp)
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = WmColor.TextPrimary, fontSize = 12.5.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(WmColor.Amber),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text("搜标题、地点或防伪码", color = WmColor.TextTertiary, fontSize = 12.sp)
                    }
                    inner()
                }
            )
            if (query.isNotEmpty()) {
                Text(
                    "清除",
                    color = WmColor.AmberText,
                    fontSize = 11.sp,
                    modifier = Modifier.clickable { query = "" }
                )
            }
        }

        if (titles.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .padding(start = 18.dp, end = 18.dp, top = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WmChip(text = "全部项目", selected = project.isBlank(), onClick = { project = "" })
                titles.take(12).forEach { t ->
                    WmChip(text = t, selected = project == t, onClick = { project = if (project == t) "" else t })
                }
            }
        }

        if (visible.isEmpty()) {
            EmptyGallery(
                onShoot = { navController.navigate("camera") },
                onImport = { importLauncher.launch("image/*") },
                filtered = photos.isNotEmpty()
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 14.dp, end = 14.dp, top = 4.dp, bottom = navBottom + 92.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val groups = visible.groupBy { dayLabel(it.timestamp).first }
                groups.forEach { (_, list) ->
                    items(list) { photo ->
                        val index = visible.indexOf(photo)
                        PhotoTile(
                            photo = photo,
                            onClick = {
                                navController.navigate(
                                    "photo_fullscreen/${Uri.encode(photo.uri)}?index=$index&total=${visible.size}"
                                )
                            },
                            onLongClick = { pendingDelete = photo }
                        )
                    }
                }
            }
        }
    }

    // 长按删除：连系统相册里的成片一起删掉，避免列表里留下空记录
    pendingDelete?.let { photo ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这张照片？", color = WmColor.TextPrimary, fontSize = 15.sp) },
            text = {
                Text(
                    "会同时从系统相册移除，删除后不可恢复。",
                    color = WmColor.TextSecondary,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                Text(
                    "删除",
                    color = WmColor.Danger,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clickable {
                            pendingDelete = null
                            scope.launch {
                                runCatching {
                                    context.contentResolver.delete(Uri.parse(photo.uri), null, null)
                                }
                                photoDao.deleteByGalleryId(photo.galleryId)
                                android.widget.Toast.makeText(context, "已删除", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            },
            dismissButton = {
                Text(
                    "取消",
                    color = WmColor.TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clickable { pendingDelete = null }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            },
            containerColor = WmColor.Card
        )
    }
}

@Composable
private fun CircleIconButton(icon: WmIcon, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(0x12FFFFFF))
            .border(1.dp, WmColor.Hairline, CircleShape)
            .clickable(onClickLabel = label) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        WmIconView(icon, tint = WmColor.TextSecondary, size = 18.dp)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PhotoTile(photo: WatermarkPhoto, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    val context = LocalContext.current
    var bitmap by remember(photo.uri) { mutableStateOf(ThumbCache.get(photo.uri)) }

    LaunchedEffect(photo.uri) {
        if (bitmap == null) {
            bitmap = withContext(Dispatchers.IO) {
                loadThumbnail(context, Uri.parse(photo.uri), 360)
            }?.also { ThumbCache.put(photo.uri, it) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(WmColor.CardHi)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { onLongClick?.invoke() }
            )
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "照片",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        // 右下角小水印标识
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
                .size(16.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0x99090B0E)),
            contentAlignment = Alignment.Center
        ) {
            WmIconView(WmIcon.Camera, tint = Color(0xFFE7EAEF), size = 9.dp, stroke = 1.1.dp)
        }
    }
}

/** 空态：矢量叠放照片 + 两个出口。 */
@Composable
private fun EmptyGallery(onShoot: () -> Unit, onImport: () -> Unit, filtered: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Canvas(modifier = Modifier.size(150.dp, 132.dp)) {
            val s = size.width / 150f
            fun p(v: Float) = v * s

            // 后一张：整体左倾 7°
            withTransform({
                rotate(-7f, pivot = Offset(p(18f), p(26f)))
            }) {
                drawRoundRect(
                    color = Color(0xFF171B21),
                    topLeft = Offset(p(18f), p(26f)),
                    size = Size(p(102f), p(76f)),
                    cornerRadius = CornerRadius(p(12f))
                )
                drawRoundRect(
                    color = Color(0x29FFFFFF),
                    topLeft = Offset(p(18f), p(26f)),
                    size = Size(p(102f), p(76f)),
                    cornerRadius = CornerRadius(p(12f)),
                    style = Stroke(p(1f))
                )
            }
            // 前一张
            drawRoundRect(
                color = Color(0xFF1D222A),
                topLeft = Offset(p(28f), p(40f)),
                size = Size(p(102f), p(76f)),
                cornerRadius = CornerRadius(p(12f))
            )
            drawRoundRect(
                color = Color(0x47FFFFFF),
                topLeft = Offset(p(28f), p(40f)),
                size = Size(p(102f), p(76f)),
                cornerRadius = CornerRadius(p(12f)),
                style = Stroke(p(1f))
            )
            // 山脊线
            val ridge = Path().apply {
                moveTo(p(28f), p(92f))
                lineTo(p(50f), p(73f))
                lineTo(p(66f), p(88f))
                lineTo(p(78f), p(78f))
                lineTo(p(104f), p(100f))
            }
            drawPath(
                ridge,
                Color(0x47FFFFFF),
                style = Stroke(p(1.5f), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            // 琥珀圆的“太阳”
            drawCircle(
                color = Color(0xBFFFB43A),
                radius = p(6.5f),
                center = Offset(p(56f), p(60f)),
                style = Stroke(p(1.5f))
            )
            // 右下角水印标
            drawRoundRect(
                color = Color(0x29FFB43A),
                topLeft = Offset(p(86f), p(94f)),
                size = Size(p(44f), p(20f)),
                cornerRadius = CornerRadius(p(6f))
            )
            drawRoundRect(
                color = Color(0x80FFB43A),
                topLeft = Offset(p(86f), p(94f)),
                size = Size(p(44f), p(20f)),
                cornerRadius = CornerRadius(p(6f)),
                style = Stroke(p(1f))
            )
            drawRoundRect(
                color = Color(0xCCFFD6A0),
                topLeft = Offset(p(92f), p(100f)),
                size = Size(p(32f), p(3f)),
                cornerRadius = CornerRadius(p(1.5f))
            )
            drawRoundRect(
                color = Color(0x73FFD6A0),
                topLeft = Offset(p(92f), p(106f)),
                size = Size(p(20f), p(3f)),
                cornerRadius = CornerRadius(p(1.5f))
            )
        }

        Text(
            text = if (filtered) "这个时间段还没有照片" else "还没有照片",
            color = WmColor.TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 22.dp)
        )
        Text(
            text = if (filtered) "试试切回「全部」，或者再拍一张" else "拍一张带标题、地点和时间的照片，会自动收进这里",
            color = WmColor.TextTertiary,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 22.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PrimaryButton(
                text = "去拍照",
                onClick = onShoot,
                modifier = Modifier.weight(1f),
                height = 46.dp,
                icon = WmIcon.Camera
            )
            GhostButton(
                text = "从相册导入",
                onClick = onImport,
                modifier = Modifier.weight(1f),
                height = 46.dp
            )
        }
    }
}

enum class GalleryFilter(val label: String) {
    ALL("全部"), TODAY("今天"), WEEK("本周");

    fun match(timestamp: Long): Boolean {
        if (this == ALL) return true
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = timestamp }
        return when (this) {
            TODAY -> now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
            WEEK -> {
                val diff = timestamp - System.currentTimeMillis()
                diff > -7L * 24 * 60 * 60 * 1000 && diff <= 0
            }
            ALL -> true
        }
    }
}

/** 日期分组标题：今天 · 11 月 6 日 */
fun dayLabel(timestamp: Long): Pair<String, String> {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = timestamp }
    val dateText = "${target.get(Calendar.MONTH) + 1} 月 ${target.get(Calendar.DAY_OF_MONTH)} 日"
    val sameDay = now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    val yesterday = now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) - target.get(Calendar.DAY_OF_YEAR) == 1
    val prefix = when {
        sameDay -> "今天"
        yesterday -> "昨天"
        else -> "${target.get(Calendar.YEAR)} 年"
    }
    return (if (sameDay || yesterday) "$prefix · $dateText" else "$dateText · $prefix") to dateText
}

/** 缩略图：优先系统缩略图接口，回退到按需采样解码。 */
fun loadThumbnail(context: android.content.Context, uri: Uri, maxDim: Int): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.loadThumbnail(uri, android.util.Size(maxDim, maxDim), null)
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }
    } catch (e: Exception) {
        Log.e("PhotoGallery", "thumbnail failed: ${uri}", e)
        null
    }
}
