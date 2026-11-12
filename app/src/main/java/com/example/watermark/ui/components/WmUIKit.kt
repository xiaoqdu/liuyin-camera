package com.example.watermark.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.watermark.data.WatermarkBgStyle
import com.example.watermark.data.WatermarkFontFamily
import androidx.compose.foundation.Image
import androidx.compose.runtime.produceState
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.example.watermark.domain.MapThumbnail
import com.example.watermark.data.WatermarkSettings
import com.example.watermark.data.WatermarkStyle
import com.example.watermark.ui.theme.WmColor

/** 虚线描边：预览页水印选中框用。 */
fun Modifier.dashedBorder(
    color: Color,
    width: Dp = 1.2.dp,
    dash: Dp = 6.dp,
    gap: Dp = 5.dp,
    radius: Dp = 10.dp
): Modifier = this.drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(radius.toPx(), radius.toPx()),
        style = Stroke(
            width = width.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), gap.toPx()), 0f)
        )
    )
}

/** 浮在照片上的玻璃圆钮，替代原来 44dp 的纯黑实心圆。 */
@Composable
fun GlassIconButton(
    icon: WmIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = WmColor.TextPrimary,
    strong: Boolean = false
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (strong) WmColor.GlassStrong else WmColor.Glass)
            .border(1.dp, WmColor.Hairline, CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        WmIconView(type = icon, tint = tint, size = size * 0.5f)
    }
}

/** 分段 chip：设置页里所有下拉的统一替代品。 */
@Composable
fun WmChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    enabled: Boolean = true
) {
    val bg by animateColorAsState(
        if (selected) Color(0x29FFB43A) else Color(0x0FFFFFFF),
        animationSpec = tween(160), label = "chipBg"
    )
    val ring by animateColorAsState(
        if (selected) Color(0x66FFB43A) else Color(0x1AFFFFFF),
        animationSpec = tween(160), label = "chipRing"
    )
    val fg by animateColorAsState(
        if (selected) Color(0xFFFFCE86) else if (enabled) WmColor.TextSecondary else WmColor.TextTertiary,
        animationSpec = tween(160), label = "chipFg"
    )
    Column(
        modifier = modifier
            .height(29.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(bg)
            .border(1.dp, ring, RoundedCornerShape(15.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (trailing != null) {
            Text(
                text = trailing,
                color = if (selected) Color(0xB3FFCE86) else WmColor.TextTertiary,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}

/** 分组标题：12sp、灰、右侧可跟一句说明。 */
@Composable
fun SectionHeader(title: String, trailing: String? = null, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 18.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = WmColor.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.4.sp
        )
        if (trailing != null) {
            Text(
                text = trailing,
                color = WmColor.TextTertiary,
                fontSize = 10.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/** 设置页/相册页的实心深灰卡，全页不再出现白卡。 */
@Composable
fun WmCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WmColor.Card)
            .padding(horizontal = 14.dp, vertical = 4.dp)
    ) { content() }
}

/** 卡内一行：左标签、右值、箭头。 */
@Composable
fun SettingRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = onClick != null,
    content: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = WmColor.TextSecondary, fontSize = 13.sp)
        Box(Modifier.weight(1f))
        if (content != null) content()
        else Text(
            text = value,
            color = WmColor.TextPrimary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (showChevron) {
            WmIconView(
                type = WmIcon.ChevronRight,
                tint = WmColor.TextTertiary,
                size = 15.dp,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

/** 主按钮：琥珀横向渐变 + 按压缩放。 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: WmIcon? = WmIcon.Check,
    height: Dp = 52.dp
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(90), label = "btnScale")
    Box(
        modifier = modifier
            .height(height)
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (enabled && !loading) Brush.verticalGradient(listOf(Color(0xFFFFC365), Color(0xFFFFA63C)))
                else Brush.verticalGradient(listOf(Color(0xFF2A2F37), Color(0xFF2A2F37)))
            )
            .clickable(interactionSource = interaction, indication = null, enabled = enabled && !loading) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = WmColor.OnAmber,
                    strokeWidth = 2.dp
                )
            } else if (icon != null) {
                WmIconView(type = icon, tint = if (enabled) WmColor.OnAmber else WmColor.TextTertiary, size = 18.dp)
            }
            Text(
                text = text,
                color = if (enabled) WmColor.OnAmber else WmColor.TextTertiary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = if (loading || icon != null) 8.dp else 0.dp)
            )
        }
    }
}

/** 次按钮：玻璃描边。 */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 46.dp
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(Color(0x14FFFFFF))
            .border(1.dp, WmColor.Hairline, RoundedCornerShape(height / 2))
            .clickable {
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = WmColor.TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
    }
}

/** 浮岛导航：琥珀渐变选中药丸 + 常显文字，取代原来 56dp 通栏图标条。 */
@Composable
fun BottomDock(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val labels = remember { listOf("相机", "相册") }
    val icons = remember { listOf(WmIcon.Camera, WmIcon.Gallery) }
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xB80E1115))
            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(22.dp))
            .padding(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        icons.forEachIndexed { index, icon ->
            val active = selected == index
            Row(
                modifier = Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .then(
                        if (active) Modifier
                            .background(
                                Brush.verticalGradient(
                                    listOf(WmColor.Amber.copy(alpha = 0.22f), WmColor.AmberDeep.copy(alpha = 0.16f))
                                )
                            )
                            .border(1.dp, WmColor.Amber.copy(alpha = 0.45f), RoundedCornerShape(17.dp))
                        else Modifier
                    )
                    .clickable { onSelect(index) }
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                WmIconView(
                    type = icon,
                    tint = if (active) WmColor.AmberText else WmColor.TextSecondary,
                    size = 17.dp
                )
                Text(
                    text = labels[index],
                    color = if (active) WmColor.AmberText else WmColor.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal
                )
            }
        }
    }
}

/** 水印的唯一渲染实现：相机页、确认页、设置预览、样式缩略图都走这里。 */
@Composable
fun WatermarkView(
    settings: WatermarkSettings,
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = settings.watermarkSize.sp,
    maxLines: Int = 1,
    shapeOverride: WatermarkStyle? = null,
    /** 行业明细行：单独排在主文本下面、字号更小，避免塞进同一段被截断。 */
    details: List<String> = emptyList()
) {
    val style = shapeOverride ?: settings.watermarkStyle
    val fg = Color(settings.watermarkColor)
    val bg = Color(settings.watermarkBgColor)
    val fontFamily = when (settings.watermarkFontFamily) {
        WatermarkFontFamily.SERIF -> FontFamily.Serif
        WatermarkFontFamily.MONOSPACE -> FontFamily.Monospace
        WatermarkFontFamily.CONDENSED -> FontFamily.SansSerif
        else -> FontFamily.Default
    }
    val radius = when (style) {
        WatermarkStyle.SOLID -> 12.dp
        WatermarkStyle.BORDER -> 14.dp
        WatermarkStyle.PILL -> 26.dp
        WatermarkStyle.STROKE -> 12.dp
    }
    // 胶囊两端是半圆，横向留白要够，否则多行文字会被圆弧切掉
    val hPad = if (style == WatermarkStyle.PILL) 22.dp else 16.dp
    val vPad = if (style == WatermarkStyle.PILL) 9.dp else 10.dp

    val container = Modifier
        .then(
            when {
                style == WatermarkStyle.BORDER ->
                    Modifier.background(if (settings.watermarkBgStyle != WatermarkBgStyle.TRANSPARENT) bg else Color.Transparent)
                        .border(1.dp, bg, RoundedCornerShape(radius))
                settings.watermarkBgStyle == WatermarkBgStyle.TRANSPARENT || style == WatermarkStyle.STROKE ->
                    Modifier
                settings.watermarkBgStyle == WatermarkBgStyle.GRADIENT ->
                    Modifier.background(
                        Brush.horizontalGradient(
                            listOf(bg.copy(alpha = 0.55f), bg, bg.copy(alpha = 0.55f))
                        ),
                        RoundedCornerShape(radius)
                    )
                else -> Modifier.background(bg, RoundedCornerShape(radius))
            }
        )
        .padding(horizontal = hPad, vertical = vPad)

    // 结构化文案：昵称加粗、时间等宽、分隔点降透明度，前面一道琥珀竖条
    val parts = text.split("·").map { it.trim() }.filter { it.isNotEmpty() }
    val annotated = buildAnnotatedString {
        parts.forEachIndexed { index, part ->
            if (index > 0) {
                withStyle(SpanStyle(color = fg.copy(alpha = 0.34f))) { append(" · ") }
            }
            val isTime = part.any { it.isDigit() } && (part.contains(":") || part.contains("年") || part.contains("-"))
            val span = SpanStyle(
                fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal,
                fontFamily = if (isTime) FontFamily.Monospace else fontFamily
            )
            withStyle(span) { append(part) }
        }
    }

    Row(
        modifier = modifier.then(container).height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(Brush.verticalGradient(listOf(WmColor.Amber, WmColor.AmberDeep)))
        )
        Spacer(Modifier.width(9.dp))
        Column {
            WmStrokeText(
                text = annotated,
                style = TextStyle(
                    color = fg,
                    fontSize = fontSize,
                    fontWeight = if (settings.watermarkFontFamily == WatermarkFontFamily.CONDENSED) FontWeight.SemiBold else FontWeight.Normal,
                    letterSpacing = if (settings.watermarkFontFamily == WatermarkFontFamily.CONDENSED) (-0.3).sp else 0.1.sp,
                    lineHeight = fontSize * 1.5f
                ),
                stroke = style == WatermarkStyle.STROKE,
                maxLines = maxLines,
                softWrap = maxLines > 1
            )
            // 行业明细行：比主文本小一号，逐行往下排，和成片里的画法一致
            details.forEach { line ->
                WmStrokeText(
                    text = androidx.compose.ui.text.AnnotatedString(line),
                    style = TextStyle(
                        color = fg,
                        fontSize = (fontSize.value * 0.62f).sp,
                        lineHeight = (fontSize.value * 0.95f).sp
                    ),
                    stroke = style == WatermarkStyle.STROKE,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 1.5.dp)
                )
            }
        }
    }
}

/** 大字时间版式的 Compose 预览：与 WatermarkEngine.drawStamp 同比例同排版。 */
@Composable
fun StampWatermarkView(
    time: String,
    date: String,
    week: String,
    location: String,
    coordinate: String?,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    color: Color = Color(0xFFFFFFFF),
    /** 底板颜色，跟成片一样取「背景填充」里选的颜色。 */
    bgColor: Color = Color(0x99000000),
    title: String? = null,
    /** 附加信息行（天气/海拔/农历/电量/备注等），与成片同一份文本。 */
    extra: String? = null,
    /** 行业模板明细行（勾选项等），与成片同一份文本。 */
    details: List<String> = emptyList(),
    /** 样式为圆角底板／胶囊底板时，预览也铺一层半透明底。 */
    plate: Boolean = false,
    /** 底板是否做成胶囊（PILL）。 */
    pill: Boolean = false,
    /** 文字描边样式：深色外圈 + 原色字芯。 */
    stroke: Boolean = false
) {
    val shadow = androidx.compose.ui.graphics.Shadow(
        color = Color(0x99000000),
        offset = Offset(0f, 1.5f),
        blurRadius = 3f
    )
    Column(
        modifier = if (plate && !pill) {
            // 不裁剪内容（clip 会把大字号的两端切掉），只画背景
            modifier
                .background(bgColor, RoundedCornerShape((14 * scale).dp))
                .padding(horizontal = (16 * scale).dp, vertical = (10 * scale).dp)
        } else {
            modifier
        }
    ) {
        title?.let {
            WmStrokeText(
                text = androidx.compose.ui.text.AnnotatedString(it),
                style = TextStyle(
                    color = color,
                    fontSize = (13 * scale).sp,
                    fontWeight = FontWeight.SemiBold,
                    shadow = shadow
                ),
                stroke = stroke,
                maxLines = 1,
                modifier = Modifier.padding(bottom = (2 * scale).dp)
            )
        }
        // 胶囊只包住时间行，和成片里的画法保持一致：多行整块做胶囊必然切掉下面的字
        Box(
            modifier = if (pill) {
                Modifier
                    .background(bgColor, RoundedCornerShape(999.dp))
                    .padding(horizontal = (24 * scale).dp, vertical = (9 * scale).dp)
            } else {
                Modifier
            }
        ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WmStrokeText(
                text = androidx.compose.ui.text.AnnotatedString(time),
                style = TextStyle(
                    color = color,
                    fontSize = (26 * scale).sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = (30 * scale).sp,
                    shadow = shadow
                ),
                stroke = stroke,
                maxLines = 1
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = (6 * scale).dp)
                    .width((2.5 * scale).dp)
                    .height((19 * scale).dp)
                    .background(Color(0xFFFFB43A))
            )
            Column {
                WmStrokeText(
                    text = androidx.compose.ui.text.AnnotatedString(date),
                    style = TextStyle(
                        color = color,
                        fontSize = (12.5 * scale).sp,
                        fontWeight = FontWeight.Bold,
                        shadow = shadow
                    ),
                    stroke = stroke,
                    maxLines = 1
                )
                WmStrokeText(
                    text = androidx.compose.ui.text.AnnotatedString(week),
                    style = TextStyle(
                        color = color,
                        fontSize = (11.5 * scale).sp,
                        shadow = shadow
                    ),
                    stroke = stroke,
                    maxLines = 1,
                    modifier = Modifier.padding(top = (2 * scale).dp)
                )
            }
        }
        }
        WmStrokeText(
            text = androidx.compose.ui.text.AnnotatedString(location),
            style = TextStyle(
                color = color,
                fontSize = (11.5 * scale).sp,
                shadow = shadow
            ),
            stroke = stroke,
            maxLines = 1,
            modifier = Modifier.padding(top = (5 * scale).dp)
        )
        coordinate?.let {
            WmStrokeText(
                text = androidx.compose.ui.text.AnnotatedString(it),
                style = TextStyle(
                    color = color,
                    fontSize = (10 * scale).sp,
                    shadow = shadow
                ),
                stroke = stroke,
                maxLines = 1,
                modifier = Modifier.padding(top = (2 * scale).dp)
            )
        }
        extra?.let {
            WmStrokeText(
                text = androidx.compose.ui.text.AnnotatedString(it),
                style = TextStyle(
                    color = color,
                    fontSize = (9.5 * scale).sp,
                    shadow = shadow
                ),
                stroke = stroke,
                maxLines = 2,
                modifier = Modifier.padding(top = (3 * scale).dp)
            )
        }
        // 行业明细行：逐行往下排，勾选项用 ✓ / ✗ 表示（成片里是方框+对勾）
        details.forEach { line ->
            WmStrokeText(
                text = androidx.compose.ui.text.AnnotatedString(line),
                style = TextStyle(
                    color = color,
                    fontSize = (9 * scale).sp,
                    shadow = shadow
                ),
                stroke = stroke,
                maxLines = 1,
                modifier = Modifier.padding(top = (1.5 * scale).dp)
            )
        }
    }
}

/** 右下角品牌角标：与 drawStamp 的角标同文案同层级，仅做屏幕预览用。 */@Composable
fun StampBrandMark(
    code: String,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    color: Color = Color(0xFFFFFFFF)
) {
    val shadow = androidx.compose.ui.graphics.Shadow(
        color = Color(0x99000000),
        offset = Offset(0f, 1.5f),
        blurRadius = 3f
    )
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        Text(
            "留印相机",
            color = color.copy(alpha = 0.92f),
            fontSize = (11 * scale).sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(shadow = shadow)
        )
        Text(
            "相机 真实可验",
            color = color.copy(alpha = 0.72f),
            fontSize = (8.5 * scale).sp,
            modifier = Modifier.padding(top = (2 * scale).dp),
            style = TextStyle(shadow = shadow)
        )
        Text(
            "防伪 $code",
            color = color.copy(alpha = 0.66f),
            fontSize = (8 * scale).sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = (0.4 * scale).sp,
            modifier = Modifier.padding(top = (1.5 * scale).dp),
            style = TextStyle(shadow = shadow)
        )
    }
}

/** 右上角坐标地图：与 drawStamp 的缩略图同位同尺寸，瓦片走 MapThumbnail 缓存。 */
@Composable
fun StampMapThumb(
    latitude: Double?,
    longitude: Double?,
    modifier: Modifier = Modifier,
    scale: Float = 1f
) {
    if (latitude == null || longitude == null) return
    val context = LocalContext.current
    val thumb by produceState<android.graphics.Bitmap?>(initialValue = null, latitude, longitude) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { MapThumbnail.load(context, latitude, longitude) }.getOrNull()
        }
    }
    // 取不到瓦片（境外坐标、断网）就不画，留一个空框比白方块体面
    val bitmap = thumb ?: return
    Box(
        modifier = modifier
            .size((76 * scale).dp)
            .clip(RoundedCornerShape((4.6 * scale).dp))
            .background(Color(0x33FFFFFF))
            .border((0.6 * scale).dp, Color(0xD6FFFFFF), RoundedCornerShape((4.6 * scale).dp)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "拍摄位置地图",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .size((3.4 * scale).dp)
                .clip(CircleShape)
                .background(WmColor.Amber)
        )
    }
}

/** 呼吸点：实时预览、定位状态共用。 */
@Composable
fun BreathingDot(color: Color = WmColor.Amber, size: Dp = 6.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun HairlineDivider(modifier: Modifier = Modifier, color: Color = WmColor.HairlineSoft) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color)
    )
}

/** 小标签：状态、序号等。 */
@Composable
fun MiniTag(text: String, color: Color = WmColor.TextSecondary, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = color,
        fontSize = 10.sp,
        letterSpacing = 0.3.sp,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0x1FFFFFFF))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/** 字号倍率：成片 drawStamp 与 Compose 预览共用同一套换算式（12 为常规）。 */
fun stampSizeScale(watermarkSize: Float): Float =
    (watermarkSize / 12f).coerceIn(0.65f, 1.8f)

/** 样式是否需要在文字后铺底板（BORDER / PILL）。 */
fun stampHasPlate(style: com.example.watermark.data.WatermarkStyle): Boolean =
    style == com.example.watermark.data.WatermarkStyle.BORDER ||
        style == com.example.watermark.data.WatermarkStyle.PILL

/** 样式是否为胶囊底板。 */
fun stampIsPill(style: com.example.watermark.data.WatermarkStyle): Boolean =
    style == com.example.watermark.data.WatermarkStyle.PILL

/**
 * 描边文字：成片里「文字描边」是深色外圈 + 原色字芯，预览用两层文字叠出来，
 * 只有一层 ChStroke 会渲染成空心字，和导出对不上。
 */
@Composable
fun WmStrokeText(
    text: androidx.compose.ui.text.AnnotatedString,
    style: TextStyle,
    stroke: Boolean,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    softWrap: Boolean = false,
    overflow: TextOverflow = TextOverflow.Ellipsis
) {
    if (!stroke) {
        Text(
            text = text,
            style = style,
            modifier = modifier,
            maxLines = maxLines,
            softWrap = softWrap,
            overflow = overflow
        )
    } else {
        val widthPx = with(androidx.compose.ui.platform.LocalDensity.current) { 1.8.dp.toPx() }
        Box(modifier = modifier) {
            Text(
                text = text,
                style = style.copy(
                    color = Color(0xE6000000),
                    drawStyle = Stroke(width = widthPx)
                ),
                maxLines = maxLines,
                softWrap = softWrap,
                overflow = overflow
            )
            Text(
                text = text,
                style = style,
                maxLines = maxLines,
                softWrap = softWrap,
                overflow = overflow
            )
        }
    }
}
