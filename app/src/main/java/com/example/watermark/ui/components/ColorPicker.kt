package com.example.watermark.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.watermark.ui.theme.WmColor

/** 水印文字色：低饱和、压在照片上也稳，选中态用“内点 + 外环”。 */
val WatermarkTextSwatches: List<Int> = listOf(
    0xFFFFFFFF.toInt(), // 白
    0xFF14171C.toInt(), // 墨
    0xFFFFD08A.toInt(), // 暖黄
    0xFF8FC3FF.toInt(), // 冷蓝
    0xFF9CE0B0.toInt(), // 豆绿
    0xFFFF9E8A.toInt()  // 珊瑚
)

/** 背景色（带透明度）：压在照片上的底色。 */
val WatermarkBgSwatches: List<Int> = listOf(
    0x99000000.toInt(),
    0x99FFFFFF.toInt(),
    0x990000FF.toInt(),
    0x99FF0000.toInt(),
    0x9900AA00.toInt(),
    0x99FFCC00.toInt()
)

@Composable
fun ColorSwatchRow(
    currentInt: Int,
    swatches: List<Int>,
    onPick: (Int) -> Unit,
    onCustom: (() -> Unit)? = null,
    swatchSize: Dp = 34.dp
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        swatches.forEach { value ->
            ColorSwatch(
                colorInt = value,
                selected = currentInt == value,
                size = swatchSize,
                onClick = { onPick(value) }
            )
        }
        if (onCustom != null) {
            Box(
                modifier = Modifier
                    .size(swatchSize)
                    .clip(CircleShape)
                    .border(1.dp, WmColor.Hairline, CircleShape)
                    .clickable { onCustom() },
                contentAlignment = Alignment.Center
            ) {
                WmIconView(WmIcon.Plus, tint = WmColor.TextSecondary, size = 14.dp)
            }
        }
    }
}

@Composable
private fun ColorSwatch(colorInt: Int, selected: Boolean, size: Dp, onClick: () -> Unit) {
    val color = Color(colorInt)
    Box(
        modifier = Modifier
            .size(size)
            .then(
                if (selected) Modifier
                    .border(1.5.dp, WmColor.Amber, CircleShape)
                    .padding(3.dp) else Modifier
            )
            .clip(CircleShape)
            .background(color)
            .border(1.dp, Color(0x26FFFFFF), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            val luminance = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (luminance > 0.55f) Color(0xFF14171C) else Color.White)
            )
        }
    }
}

/** 自定义取色：HSV 三条滑杆，够用且不引入第三方。 */
@Composable
fun CustomColorDialog(
    initialInt: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val hsv = remember(initialInt) {
        val arr = FloatArray(3)
        android.graphics.Color.colorToHSV(initialInt, arr)
        arr
    }
    var h by remember { mutableStateOf(hsv[0]) }
    var s by remember { mutableStateOf(if (hsv[1] <= 0f) 0.2f else hsv[1]) }
    var v by remember { mutableStateOf(if (hsv[2] <= 0f) 0.9f else hsv[2]) }
    var alpha by remember { mutableStateOf(android.graphics.Color.alpha(initialInt) / 255f) }
    val result = remember(h, s, v, alpha) {
        val c = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
        android.graphics.Color.argb((alpha * 255).toInt(), android.graphics.Color.red(c), android.graphics.Color.green(c), android.graphics.Color.blue(c))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WmColor.Card,
        title = { Text("自定义颜色", color = WmColor.TextPrimary, fontSize = 16.sp) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF14171C)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(result))
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = String.format("#%08X", result),
                        color = WmColor.TextSecondary,
                        fontSize = 12.sp
                    )
                }
                SlideRow("色相", h, 0f, 360f) { h = it }
                SlideRow("饱和", s, 0f, 1f) { s = it }
                SlideRow("明度", v, 0f, 1f) { v = it }
                SlideRow("透明", alpha, 0f, 1f) { alpha = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(result); onDismiss() }) {
                Text("用这个", color = WmColor.AmberText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = WmColor.TextSecondary) }
        }
    )
}

@Composable
private fun SlideRow(label: String, value: Float, from: Float, to: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = WmColor.TextSecondary, fontSize = 11.sp, modifier = Modifier.width(34.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = from..to,
            colors = SliderDefaults.colors(
                thumbColor = WmColor.Amber,
                activeTrackColor = WmColor.Amber.copy(alpha = 0.8f),
                inactiveTrackColor = Color(0xFF2A2F37)
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

/** 对比度判断：文字色与背景色太接近时提醒一句。 */
fun contrastRatio(fg: Int, bg: Int): Float {
    fun channel(c: Int, shift: Int) = ((c shr shift) and 0xFF) / 255f
    fun lum(c: Int): Float {
        fun lin(v: Float) = if (v <= 0.03928f) v / 12.92f else Math.pow(((v + 0.055) / 1.055).toDouble(), 2.4).toFloat()
        return 0.2126f * lin(channel(c, 16)) + 0.7152f * lin(channel(c, 8)) + 0.0722f * lin(channel(c, 0))
    }
    val l1 = lum(fg)
    val l2 = lum(bg)
    val lighter = maxOf(l1, l2)
    val darker = minOf(l1, l2)
    return (lighter + 0.05f) / (darker + 0.05f)
}
