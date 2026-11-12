package com.example.watermark.ui.screens.gallery

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.watermark.ui.components.GhostButton
import com.example.watermark.ui.components.PrimaryButton
import com.example.watermark.ui.components.WmIcon
import com.example.watermark.ui.components.WmIconView
import com.example.watermark.ui.theme.WmColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PhotoFullScreen(
    navController: NavHostController,
    uri: String,
    index: Int = 0,
    total: Int = 0
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val parsed = remember(uri) { Uri.parse(uri) }

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var meta by remember { mutableStateOf(PhotoMeta()) }
    var uiVisible by remember { mutableStateOf(true) }
    var scale by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var loading by remember { mutableStateOf(true) }

    val screenWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { configuration.screenWidthDp.dp.toPx() }

    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            bitmap = decodeForScreen(context, parsed, screenWidthPx.toInt().coerceAtLeast(720))
            meta = readMeta(context, parsed)
        }
        loading = false
    }

    Box(modifier = Modifier.fillMaxSize().background(WmColor.Ink)) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "照片",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = pan.x
                        translationY = pan.y
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                scale = if (scale > 1f) {
                                    pan = Offset.Zero
                                    1f
                                } else 2.2f
                            },
                            onTap = { uiVisible = !uiVisible }
                        )
                    }
                    .pointerInput(scale) {
                        detectDragGestures { change, delta ->
                            if (scale > 1f) {
                                change.consume()
                                pan = Offset(pan.x + delta.x, pan.y + delta.y)
                            }
                        }
                    }
            )
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = WmColor.Amber, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
            }
        }

        AnimatedVisibility(
            visible = uiVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Color(0xE605070A), Color(0x0005070A)))
                    )
                    .padding(
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
                        bottom = 26.dp,
                        start = 16.dp,
                        end = 16.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleGlass(WmIcon.Back, "返回") { navController.popBackStack() }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (total > 0) "${index + 1} / $total" else "",
                        color = WmColor.TextSecondary,
                        fontSize = 12.sp,
                        letterSpacing = 0.6.sp
                    )
                }
                CircleGlass(WmIcon.More, "隐藏界面") { uiVisible = false }
            }
        }

        AnimatedVisibility(
            visible = uiVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Color(0x00111419), Color(0xF2111419), Color(0xFF111419)))
                    )
                    .padding(horizontal = 16.dp)
                    .padding(top = 30.dp, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x14FFFFFF))
                        .border(1.dp, WmColor.HairlineSoft, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetaColumn("拍摄时间", meta.time)
                    MetaColumn("尺寸", meta.dimension)
                    MetaColumn("大小", meta.size, Alignment.End)
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PrimaryButton(
                        text = "分享这张照片",
                        onClick = { sharePhoto(context, parsed) },
                        modifier = Modifier.weight(1f),
                        icon = WmIcon.Share
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color(0x1AFFFFFF), RoundedCornerShape(14.dp))
                            .border(1.dp, WmColor.Hairline, RoundedCornerShape(14.dp))
                            .clickable { uiVisible = false },
                        contentAlignment = Alignment.Center
                    ) {
                        WmIconView(WmIcon.Sort, tint = WmColor.TextSecondary, size = 20.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CircleGlass(icon: WmIcon, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(WmColor.Glass)
            .border(1.dp, WmColor.Hairline, androidx.compose.foundation.shape.CircleShape)
            .clickable(onClickLabel = label) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        WmIconView(icon, tint = WmColor.TextPrimary, size = 19.dp)
    }
}

@Composable
private fun MetaColumn(label: String, value: String, align: Alignment.Horizontal = Alignment.Start) {
    Column(horizontalAlignment = align) {
        Text(label, color = WmColor.TextTertiary, fontSize = 10.sp)
        Text(
            value,
            color = WmColor.TextPrimary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

private data class PhotoMeta(
    val time: String = "—",
    val dimension: String = "—",
    val size: String = "—"
)

/** 按屏宽降采样，避免全尺寸解码 OOM。 */
private fun decodeForScreen(context: android.content.Context, uri: Uri, targetWidth: Int): Bitmap? {
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > targetWidth * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }.onFailure { Log.e("PhotoFullScreen", "decode failed", it) }.getOrNull()
}

private fun readMeta(context: android.content.Context, uri: Uri): PhotoMeta {
    var time = "—"
    var dimension = "—"
    var size = "—"
    runCatching {
        val cols = mutableListOf(
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) cols.add(MediaStore.Images.Media.DATE_TAKEN)
        context.contentResolver.query(uri, cols.toTypedArray(), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val bytes = runCatching { c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)) }.getOrDefault(0L)
                val w = runCatching { c.getInt(c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)) }.getOrDefault(0)
                val h = runCatching { c.getInt(c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)) }.getOrDefault(0)
                if (bytes > 0) size = if (bytes > 1024 * 1024) String.format(Locale.US, "%.1f MB", bytes / 1048576f) else "${bytes / 1024} KB"
                if (w > 0 && h > 0) dimension = "$w × $h"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val taken = runCatching { c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)) }.getOrDefault(0L)
                    if (taken > 0) {
                        time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(taken))
                    }
                }
            }
        }
    }
    if (time == "—") {
        time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
    }
    return PhotoMeta(time = time, dimension = dimension, size = size)
}

fun sharePhoto(context: android.content.Context, uri: Uri) {
    runCatching {
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "分享照片"))
    }.onFailure {
        Log.w("PhotoFullScreen", "share failed", it)
    }
}
