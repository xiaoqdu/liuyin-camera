package com.example.watermark.ui.screens.settings

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.watermark.data.PhotoDao
import com.example.watermark.data.SettingsRepository
import com.example.watermark.data.WatermarkFontFamily
import com.example.watermark.data.WatermarkLayout
import com.example.watermark.data.WatermarkPosition
import com.example.watermark.data.WatermarkSettings
import com.example.watermark.data.WatermarkStyle
import com.example.watermark.data.WatermarkTemplatePreset
import com.example.watermark.data.WatermarkTimeFormat
import com.example.watermark.data.WatermarkBgStyle
import com.example.watermark.domain.LocationProvider
import com.example.watermark.domain.WatermarkEngine
import com.example.watermark.ui.components.BreathingDot
import com.example.watermark.ui.components.ColorSwatchRow
import com.example.watermark.ui.components.CustomColorDialog
import com.example.watermark.ui.components.GlassIconButton
import com.example.watermark.ui.components.HairlineDivider
import com.example.watermark.ui.components.MiniTag
import com.example.watermark.ui.components.PrimaryButton
import com.example.watermark.ui.components.SectionHeader
import com.example.watermark.ui.components.StampWatermarkView
import com.example.watermark.ui.components.StampBrandMark
import com.example.watermark.ui.components.StampMapThumb
import com.example.watermark.ui.components.WatermarkBgSwatches
import com.example.watermark.ui.components.WatermarkTextSwatches
import com.example.watermark.ui.components.WatermarkView
import com.example.watermark.ui.components.WmChip
import com.example.watermark.ui.components.WmIcon
import com.example.watermark.ui.components.WmIconView
import com.example.watermark.ui.components.contrastRatio
import com.example.watermark.ui.components.stampHasPlate
import com.example.watermark.ui.components.stampIsPill
import com.example.watermark.ui.components.stampSizeScale
import com.example.watermark.ui.theme.WmColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    navController: NavHostController,
    settingsRepository: SettingsRepository,
    photoDao: PhotoDao
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val saved = settingsRepository.settingsFlow.collectAsStateWithLifecycle(
        SettingsRepository.defaultSettings,
        lifecycleOwner.lifecycle
    ).value

    var draft by remember { mutableStateOf(SettingsRepository.defaultSettings) }

    var location by remember { mutableStateOf<String?>(null) }
    var locationFix by remember { mutableStateOf<LocationProvider.Fix?>(null) }
    var tick by remember { mutableStateOf(0) }
    var customTextColor by remember { mutableStateOf(false) }
    var customBgColor by remember { mutableStateOf(false) }
    var latestPhoto by remember { mutableStateOf<Bitmap?>(null) }

    val engine = remember { WatermarkEngine() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        locationFix = runCatching { LocationProvider(context).getBestFix() }.getOrNull()
        location = locationFix?.address
    }
    LaunchedEffect(Unit) {
        while (true) {
            tick++
            delay(1000)
        }
    }
    LaunchedEffect(Unit) {
        val stored = runCatching { settingsRepository.settingsFlow.first() }.getOrNull()
        if (stored != null) draft = stored
    }

    LaunchedEffect(Unit) {
        // 预览底图必须用未加水印的原片：相册里存的是已成片，直接当底图会叠出两层水印
        val firstUri = withContext(Dispatchers.IO) {
            runCatching { com.example.watermark.PhotoCache.latestOriginalUri(context) }.getOrNull()
        }
        if (firstUri != null) {
            latestPhoto = withContext(Dispatchers.IO) {
                runCatching {
                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(firstUri)?.use {
                        android.graphics.BitmapFactory.decodeStream(it, null, bounds)
                    }
                    var sample = 1
                    while (bounds.outWidth / sample > 720) sample *= 2
                    context.contentResolver.openInputStream(firstUri)?.use {
                        android.graphics.BitmapFactory.decodeStream(
                            it,
                            null,
                            android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                        )
                    }
                }.getOrNull()
            }
        }
    }

    // 锁定模式下预览同样不采信手动位置：有实时定位用它，否则走锁定占位文案
    val previewLocation = if (draft.lockTimeLocation) {
        location ?: "定位中（已锁定）"
    } else {
        draft.locationOverride.ifBlank { location }
    }
    // 设置页预览用示例天气，避免设置项打开后仍看不到效果（只影响预览，不带入成片）
    val sampleWeather = com.example.watermark.domain.ExtraFields.Weather(
        text = "晴", temperature = 26, humidity = 68, wind = "2.4 m/s"
    )
    val previewExtra = com.example.watermark.domain.ExtraFields.compose(
        context = context,
        settings = draft,
        location = null,
        weather = sampleWeather
    )
    val previewText = remember(draft, previewLocation, tick, previewExtra) {
        engine.buildWatermarkText(draft, previewLocation, extraText = previewExtra)
    }
    val previewStamp = remember(draft, previewLocation, locationFix, tick, previewExtra) {
        engine.buildStampParts(
            settings = draft,
            currentLocation = previewLocation,
            latitude = locationFix?.latitude,
            longitude = locationFix?.longitude,
            extraText = previewExtra
        )
    }

    val dirty = draft != saved
    val navTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WmColor.Surface)
            .padding(top = navTop)
    ) {
        // 页头
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "水印设置",
                color = WmColor.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.1.sp
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(34.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .clickable { navController.popBackStack() },
                contentAlignment = Alignment.Center
            ) {
                WmIconView(WmIcon.Back, tint = WmColor.TextPrimary, size = 20.dp)
            }
            Text(
                text = "保存",
                color = if (dirty) WmColor.AmberText else WmColor.TextTertiary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (dirty) Color(0x1FFFB43A) else Color.Transparent)
                    .clickable(enabled = dirty) {
                        scope.launch {
                            settingsRepository.saveSettings(draft)
                            navController.popBackStack()
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }

        // 可滚动主体
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
        // 实时预览
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .height(186.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF1B2027))
        ) {
            latestPhoto?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } ?: Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF2A3038), Color(0xFF1D242B), Color(0xFF23282F))
                        )
                    )
            )

            // 预览水印：位置、样式、字号都按当前草稿渲染
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(listOf(Color(0x1A000000), Color(0x8C000000)))
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                contentAlignment = when (draft.watermarkPosition) {
                    WatermarkPosition.TOP_LEFT -> Alignment.TopStart
                    WatermarkPosition.TOP_RIGHT -> Alignment.TopEnd
                    WatermarkPosition.BOTTOM_LEFT -> Alignment.BottomStart
                    WatermarkPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
                    WatermarkPosition.CENTER -> Alignment.Center
                }
            ) {
                if (com.example.watermark.domain.IndustryLayout.effectiveLayout(draft) == WatermarkLayout.STAMP) {
                    StampWatermarkView(
                        time = previewStamp.time,
                        date = previewStamp.date,
                        week = previewStamp.week,
                        location = previewStamp.location,
                        coordinate = previewStamp.coordinate,
                        title = previewStamp.title,
                        extra = previewStamp.extra,
                        details = previewStamp.details.map { it.asText() },
                        color = androidx.compose.ui.graphics.Color(draft.watermarkColor),
                        bgColor = androidx.compose.ui.graphics.Color(draft.watermarkBgColor),
                        scale = (if (previewStamp.details.isNotEmpty()) 0.56f else 0.72f) * stampSizeScale(draft.watermarkSize),
                        plate = stampHasPlate(draft.watermarkStyle),
                        pill = stampIsPill(draft.watermarkStyle),
                        stroke = draft.watermarkStyle == com.example.watermark.data.WatermarkStyle.STROKE
                    )
                } else {
                    WatermarkView(
                        settings = draft,
                        text = previewText,
                        details = previewStamp.details.map { it.asText() },
                        // 行业明细行会多占三到五行，预览字号相应收一点，避免在预览框里被裁掉
                        fontSize = ((if (previewStamp.details.isNotEmpty()) 11f else 15f) *
                            stampSizeScale(draft.watermarkSize)).sp,
                        maxLines = 3
                    )
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x2EFFB43A))
                    .border(1.dp, Color(0x5CFFB43A), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BreathingDot(size = 5.dp)
                Text(
                    "实时预览",
                    color = Color(0xFFFFD79A),
                    fontSize = 9.5.sp,
                    letterSpacing = 0.4.sp,
                    modifier = Modifier.padding(start = 5.dp)
                )
            }
            if (latestPhoto == null) {
                Box(Modifier.align(Alignment.BottomEnd).padding(10.dp)) {
                    MiniTag("示例背景", WmColor.TextTertiary)
                }
            }
            if (com.example.watermark.domain.IndustryLayout.effectiveLayout(draft) == WatermarkLayout.STAMP) {
                StampMapThumb(
                    latitude = locationFix?.latitude,
                    longitude = locationFix?.longitude,
                    scale = 0.5f,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 10.dp, top = 44.dp)
                )
                StampBrandMark(
                    code = previewStamp.code,
                    scale = 0.62f,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = 14.dp,
                            bottom = if (latestPhoto == null) 38.dp else 12.dp
                        )
                )
            }
        }

            SectionHeader("水印内容")
            WmCardBlock {
                InlineField(
                    label = "标题",
                    value = draft.nickname.takeIf { it != "未设置" }.orEmpty(),
                    placeholder = "留空则不显示，会压在大字时间上方",
                    onChange = { draft = draft.copy(nickname = it) }
                )
                HairlineDivider()
                Column(Modifier.padding(vertical = 12.dp)) {
                    Text("模板", color = WmColor.TextSecondary, fontSize = 13.sp)
                    TokensRow(draft.watermarkTemplate) { token ->
                        draft = draft.copy(
                            watermarkTemplate = draft.watermarkTemplate + token,
                            watermarkTemplatePreset = WatermarkTemplatePreset.CUSTOM
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WatermarkTemplatePreset.values().forEach { preset ->
                            WmChip(
                                text = presetShortLabel(preset),
                                selected = draft.watermarkTemplatePreset == preset,
                                onClick = {
                                    val industry = com.example.watermark.domain.IndustryLayout.hasLayout(preset)
                                    draft = draft.copy(
                                        watermarkTemplatePreset = preset,
                                        watermarkTemplate = if (preset == WatermarkTemplatePreset.CUSTOM) draft.watermarkTemplate else preset.template,
                                        // 考勤打卡：时间地点锁定，上来就把手动位置清掉，只认实时定位
                                        lockTimeLocation = preset == WatermarkTemplatePreset.ATTENDANCE,
                                        locationOverride = if (preset == WatermarkTemplatePreset.ATTENDANCE) "" else draft.locationOverride,
                                        // 行业模板字段多，单行卡片塞不下，默认切成大字排版
                                        watermarkLayout = if (industry) WatermarkLayout.STAMP else draft.watermarkLayout
                                    )
                                }
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF21262D))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        BasicTextField(
                            value = draft.watermarkTemplate.replace("昵称", "标题"),
                            onValueChange = {
                                draft = draft.copy(
                                    watermarkTemplate = it,
                                    watermarkTemplatePreset = WatermarkTemplatePreset.CUSTOM
                                )
                            },
                            textStyle = TextStyle(color = WmColor.TextPrimary, fontSize = 12.5.sp),
                            cursorBrush = SolidColor(WmColor.Amber),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (draft.watermarkTemplate.isEmpty()) {
                                    Text("标题 · 位置 · 时间", color = WmColor.TextTertiary, fontSize = 12.5.sp)
                                }
                                inner()
                            }
                        )
                    }
                }
            }

            if (com.example.watermark.domain.IndustryLayout.hasLayout(draft.watermarkTemplatePreset)) {
                SectionHeader("行业字段", trailing = "会写进水印，空着不占位")
                WmCardBlock {
                    IndustryFieldEditor(
                        preset = draft.watermarkTemplatePreset,
                        data = draft.industryData,
                        onChange = { draft = draft.copy(industryData = it) }
                    )
                }
            }

            SectionHeader("时间与定位")
            WmCardBlock {
                Column(Modifier.padding(vertical = 12.dp)) {
                    Text("时间格式", color = WmColor.TextSecondary, fontSize = 13.sp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WatermarkTimeFormat.values().forEach { format ->
                            WmChip(
                                text = timeFormatShort(format),
                                selected = draft.watermarkTimeFormat == format,
                                onClick = { draft = draft.copy(watermarkTimeFormat = format) }
                            )
                        }
                    }
                }
                HairlineDivider()
                if (draft.lockTimeLocation) {
                    // 考勤打卡模板：时间地点锁定，不给手动位置入口，只显示锁定状态
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("时间与地点已锁定", color = WmColor.TextPrimary, fontSize = 13.sp)
                            Text(
                                text = "考勤打卡模板只认实时定位与系统时间 · ${location ?: "正在定位…"}",
                                color = WmColor.TextTertiary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        WmChip(text = "已锁定", selected = true, onClick = {})
                    }
                } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("自动读取位置", color = WmColor.TextPrimary, fontSize = 13.sp)
                        Text(
                            text = if (draft.locationOverride.isBlank()) (location ?: "正在定位…") else "已使用手动位置",
                            color = WmColor.TextTertiary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Switch(
                        checked = draft.locationOverride.isBlank(),
                        onCheckedChange = { auto ->
                            draft = draft.copy(locationOverride = if (auto) "" else (location ?: ""))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = WmColor.OnAmber,
                            checkedTrackColor = WmColor.Amber,
                            uncheckedThumbColor = Color(0xFF6B7480),
                            uncheckedTrackColor = Color(0xFF2A2F37)
                        )
                    )
                }
                if (draft.locationOverride.isNotBlank()) {
                    HairlineDivider()
                    InlineField(
                        label = "手动位置",
                        value = draft.locationOverride,
                        placeholder = "例如：深圳湾公园",
                        onChange = { draft = draft.copy(locationOverride = it) }
                    )
                }
                }
            }

            SectionHeader("外观样式", trailing = "滑到底可以直接看到成片效果")
            WmCardBlock {
                // 排版：卡片水印 / 大字时间（今日水印式）
                Column(Modifier.padding(vertical = 13.dp)) {
                    Text("排版", color = WmColor.TextSecondary, fontSize = 13.sp)
                    Row(
                        modifier = Modifier.padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WatermarkLayout.values().forEach { layout ->
                            val on = draft.watermarkLayout == layout
                            Box(
                                modifier = Modifier
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (on) Color(0x29FFB43A) else Color(0x0FFFFFFF))
                                    .border(
                                        1.dp,
                                        if (on) Color(0x6BFFB43A) else Color(0x1FFFFFFF),
                                        RoundedCornerShape(16.dp)
                                    )
                                    .clickable { draft = draft.copy(watermarkLayout = layout) }
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    layout.label,
                                    color = if (on) Color(0xFFFFCE86) else WmColor.TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (on) FontWeight.Medium else FontWeight.Normal
                                )
                            }
                        }
                    }
                    if (com.example.watermark.domain.IndustryLayout.effectiveLayout(draft) == WatermarkLayout.STAMP) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("显示经纬度", color = WmColor.TextSecondary, fontSize = 13.sp)
                                Text(
                                    "在时间块下方补一行 纬度/经度",
                                    color = WmColor.TextTertiary,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            androidx.compose.material3.Switch(
                                checked = draft.showCoordinates,
                                onCheckedChange = { draft = draft.copy(showCoordinates = it) },
                                colors = androidx.compose.material3.SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF231607),
                                    checkedTrackColor = WmColor.Amber,
                                    uncheckedThumbColor = Color(0xFF8A919B),
                                    uncheckedTrackColor = Color(0x1FFFFFFF)
                                )
                            )
                        }
                    }
                }
                HairlineDivider()
                // 附加信息字段（对齐市面水印相机）
                Column(Modifier.padding(vertical = 14.dp)) {
                    Text("附加信息", color = WmColor.TextSecondary, fontSize = 13.sp)
                    Text(
                        "选中的字段会拼成一行，追加在地点／经纬度下面",
                        color = WmColor.TextTertiary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    val extraChips = listOf(
                        Triple("天气", draft.showWeather) { v: Boolean -> draft = draft.copy(showWeather = v) },
                        Triple("温度", draft.showTemperature) { v: Boolean -> draft = draft.copy(showTemperature = v) },
                        Triple("湿度", draft.showHumidity) { v: Boolean -> draft = draft.copy(showHumidity = v) },
                        Triple("风速", draft.showWind) { v: Boolean -> draft = draft.copy(showWind = v) },
                        Triple("海拔", draft.showAltitude) { v: Boolean -> draft = draft.copy(showAltitude = v) },
                        Triple("方位角", draft.showBearing) { v: Boolean -> draft = draft.copy(showBearing = v) },
                        Triple("农历", draft.showLunar) { v: Boolean -> draft = draft.copy(showLunar = v) },
                        Triple("节日", draft.showFestival) { v: Boolean -> draft = draft.copy(showFestival = v) },
                        Triple("星座", draft.showConstellation) { v: Boolean -> draft = draft.copy(showConstellation = v) },
                        Triple("倒计时", draft.showCountdown) { v: Boolean -> draft = draft.copy(showCountdown = v) },
                        Triple("电量", draft.showBattery) { v: Boolean -> draft = draft.copy(showBattery = v) },
                        Triple("网络", draft.showNetwork) { v: Boolean -> draft = draft.copy(showNetwork = v) }
                    )
                    extraChips.chunked(4).forEach { rowItems ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { (label, selected, setter) ->
                                ExtraChip(
                                    label = label,
                                    selected = selected,
                                    onClick = { setter(!selected) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    if (draft.showCountdown) {
                        InlineField(
                            label = "倒计时名称",
                            value = draft.countdownLabel,
                            placeholder = "如：春节",
                            onChange = { draft = draft.copy(countdownLabel = it) }
                        )
                        InlineField(
                            label = "目标日期",
                            value = draft.countdownTarget,
                            placeholder = "yyyy-MM-dd",
                            onChange = { draft = draft.copy(countdownTarget = it) }
                        )
                    }
                    InlineField(
                        label = "自定义备注",
                        value = draft.extraNote,
                        placeholder = "如：3 号楼东侧",
                        onChange = { draft = draft.copy(extraNote = it) }
                    )
                }
                HairlineDivider()
                // 字号
                Column(Modifier.padding(vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("字号", color = WmColor.TextSecondary, fontSize = 13.sp)
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${draft.watermarkSize.toInt()}",
                            color = WmColor.AmberText,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(" sp", color = WmColor.TextTertiary, fontSize = 10.sp)
                    }
                    SizeSlider(
                        value = draft.watermarkSize,
                        onChange = { draft = draft.copy(watermarkSize = it) },
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
                HairlineDivider()
                // 字体
                Column(Modifier.padding(vertical = 12.dp)) {
                    Text("字体", color = WmColor.TextSecondary, fontSize = 13.sp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WatermarkFontFamily.values().forEach { family ->
                            FontChip(
                                family = family,
                                selected = draft.watermarkFontFamily == family,
                                onClick = { draft = draft.copy(watermarkFontFamily = family) }
                            )
                        }
                    }
                }
                HairlineDivider()
                // 文字颜色
                Column(Modifier.padding(vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("文字颜色", color = WmColor.TextSecondary, fontSize = 13.sp)
                        Spacer(Modifier.weight(1f))
                        val ratio = contrastRatio(draft.watermarkColor, draft.watermarkBgColor)
                        if (ratio < 3f) {
                            Text(
                                "对比度偏低",
                                color = WmColor.AmberText,
                                fontSize = 10.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(7.dp))
                                    .clickable {
                                        draft = draft.copy(watermarkColor = 0xFFFFFFFF.toInt(), watermarkBgColor = 0x99000000.toInt())
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    ColorSwatchRow(
                        currentInt = draft.watermarkColor,
                        swatches = WatermarkTextSwatches,
                        onPick = { draft = draft.copy(watermarkColor = it) },
                        onCustom = { customTextColor = true },
                        swatchSize = 32.dp
                    )
                }
                HairlineDivider()
                // 背景 + 样式 + 位置
                Column(Modifier.padding(vertical = 12.dp)) {
                    Text("水印样式", color = WmColor.TextSecondary, fontSize = 13.sp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StyleTile(
                            modifier = Modifier.weight(1f),
                            draft = draft,
                            style = WatermarkStyle.SOLID,
                            selected = draft.watermarkStyle == WatermarkStyle.SOLID,
                            onClick = { draft = draft.copy(watermarkStyle = WatermarkStyle.SOLID) }
                        )
                        StyleTile(
                            modifier = Modifier.weight(1f),
                            draft = draft,
                            style = WatermarkStyle.BORDER,
                            selected = draft.watermarkStyle == WatermarkStyle.BORDER,
                            onClick = { draft = draft.copy(watermarkStyle = WatermarkStyle.BORDER) }
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StyleTile(
                            modifier = Modifier.weight(1f),
                            draft = draft,
                            style = WatermarkStyle.PILL,
                            selected = draft.watermarkStyle == WatermarkStyle.PILL,
                            onClick = { draft = draft.copy(watermarkStyle = WatermarkStyle.PILL) }
                        )
                        StyleTile(
                            modifier = Modifier.weight(1f),
                            draft = draft,
                            style = WatermarkStyle.STROKE,
                            selected = draft.watermarkStyle == WatermarkStyle.STROKE,
                            onClick = { draft = draft.copy(watermarkStyle = WatermarkStyle.STROKE) }
                        )
                    }
                }
                HairlineDivider()
                Column(Modifier.padding(vertical = 12.dp)) {
                    Text("背景填充", color = WmColor.TextSecondary, fontSize = 13.sp)
                    Row(
                        modifier = Modifier.padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WatermarkBgStyle.values().forEach { bgStyle ->
                            WmChip(
                                text = bgStyle.label,
                                selected = draft.watermarkBgStyle == bgStyle,
                                onClick = { draft = draft.copy(watermarkBgStyle = bgStyle) }
                            )
                        }
                    }
                    if (draft.watermarkBgStyle != WatermarkBgStyle.TRANSPARENT) {
                        ColorSwatchRow(
                            currentInt = draft.watermarkBgColor,
                            swatches = WatermarkBgSwatches,
                            onPick = { draft = draft.copy(watermarkBgColor = it) },
                            onCustom = { customBgColor = true },
                            swatchSize = 26.dp
                        )
                    }
                }
                HairlineDivider()
                Column(Modifier.padding(vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("水印位置", color = WmColor.TextSecondary, fontSize = 13.sp)
                        Spacer(Modifier.weight(1f))
                        Text(
                            draft.watermarkPosition.label,
                            color = WmColor.AmberText,
                            fontSize = 11.sp
                        )
                    }
                    PositionGrid(
                        value = draft.watermarkPosition,
                        onChange = { draft = draft.copy(watermarkPosition = it) },
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        // 吸底保存
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x000B0D10), Color(0xF00B0D10))
                    )
                )
                .padding(horizontal = 14.dp)
                .padding(top = 14.dp, bottom = 14.dp + navBottom)
        ) {
            PrimaryButton(
                text = "保存并返回",
                onClick = {
                    scope.launch {
                        settingsRepository.saveSettings(draft)
                        navController.popBackStack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = true,
                height = 52.dp,
                icon = WmIcon.Check
            )
        }
    }

    if (customTextColor) {
        CustomColorDialog(
            initialInt = draft.watermarkColor,
            onDismiss = { customTextColor = false },
            onConfirm = { draft = draft.copy(watermarkColor = it) }
        )
    }
    if (customBgColor) {
        CustomColorDialog(
            initialInt = draft.watermarkBgColor,
            onDismiss = { customBgColor = false },
            onConfirm = { draft = draft.copy(watermarkBgColor = it) }
        )
    }
}

@Composable
private fun WmCardBlock(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WmColor.Card)
            .padding(horizontal = 14.dp)
    ) { content() }
}

/** 卡内可直接编辑的一行：左标签、右输入。 */
@Composable
private fun InlineField(
    label: String,
    value: String,
    placeholder: String,
    onChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = WmColor.TextSecondary, fontSize = 13.sp)
        Box(Modifier.weight(1f))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = TextStyle(color = WmColor.TextPrimary, fontSize = 13.sp, textAlign = TextAlign.End),
            cursorBrush = SolidColor(WmColor.Amber),
            singleLine = true,
            modifier = Modifier.width(200.dp),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterEnd, modifier = Modifier.fillMaxWidth()) {
                    if (value.isEmpty()) {
                        Text(placeholder, color = WmColor.TextTertiary, fontSize = 13.sp)
                    }
                    inner()
                }
            }
        )
    }
}

/** 模板占位符：点一下就插进模板末尾。 */
@Composable
private fun TokensRow(template: String, onInsert: (String) -> Unit) {
    Row(
        modifier = Modifier.padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val normalized = template.replace("昵称", "标题")
        listOf("标题", "位置", "时间").forEach { token ->
            val used = normalized.contains(token)
            Text(
                text = token,
                color = if (used) WmColor.TextTertiary else WmColor.AmberText,
                fontSize = 10.5.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (used) Color(0x0FFFFFFF) else Color(0x1AFFB43A))
                    .clickable { if (!used) onInsert(if (template.isBlank()) token else " · $token") }
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            )
        }
        Spacer(Modifier.weight(1f))
        Text("点按插入", color = WmColor.TextTertiary, fontSize = 9.5.sp)
    }
}

@Composable
private fun FontChip(family: WatermarkFontFamily, selected: Boolean, onClick: () -> Unit) {
    val font = when (family) {
        WatermarkFontFamily.SERIF -> FontFamily.Serif
        WatermarkFontFamily.MONOSPACE -> FontFamily.Monospace
        WatermarkFontFamily.CONDENSED -> FontFamily.SansSerif
        else -> FontFamily.Default
    }
    Box(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (selected) Color(0x29FFB43A) else Color(0x0FFFFFFF))
            .border(
                1.dp,
                if (selected) Color(0x6BFFB43A) else Color(0x1FFFFFFF),
                RoundedCornerShape(15.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = family.label,
            color = if (selected) Color(0xFFFFCE86) else WmColor.TextSecondary,
            fontSize = 11.5.sp,
            fontFamily = font,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            letterSpacing = if (family == WatermarkFontFamily.CONDENSED) (-0.4).sp else 0.sp
        )
    }
}

/** 字号滑杆：8–32，带四档刻度。 */
@Composable
private fun SizeSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Text("8", color = WmColor.TextTertiary, fontSize = 9.sp)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(30.dp)
                .padding(horizontal = 8.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        onChange((8f + fraction * 24f).coerceIn(8f, 32f))
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        onChange((8f + fraction * 24f).coerceIn(8f, 32f))
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val cy = size.height / 2f
                drawLine(Color(0xFF2A2F37), Offset(0f, cy), Offset(size.width, cy), strokeWidth = 3.dp.toPx())
                val fraction = ((value - 8f) / 24f).coerceIn(0f, 1f)
                val x = fraction * size.width
                drawLine(WmColor.Amber, Offset(0f, cy), Offset(x, cy), strokeWidth = 3.dp.toPx())
                // 四档刻度
                listOf(8, 16, 24, 32).forEach { tick ->
                    val tx = ((tick - 8) / 24f) * size.width
                    drawLine(
                        color = if (value >= tick) WmColor.Amber.copy(alpha = 0.7f) else Color(0xFF3A4048),
                        start = Offset(tx, cy + 8.dp.toPx()),
                        end = Offset(tx, cy + 13.dp.toPx()),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                drawCircle(WmColor.AmberSoft, 5.dp.toPx(), Offset(x, cy))
                drawCircle(Color(0xFF14171C), 3.dp.toPx(), Offset(x, cy))
            }
        }
        Text("32", color = WmColor.TextTertiary, fontSize = 9.sp)
    }
}

@Composable
private fun StyleTile(
    modifier: Modifier,
    draft: WatermarkSettings,
    style: WatermarkStyle,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF252A32), Color(0xFF171B21))))
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) Color(0x99FFB43A) else Color(0x14FFFFFF),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        WatermarkView(
            settings = draft,
            text = "11 月 6 日 · 深圳湾",
            fontSize = 10.sp,
            shapeOverride = style
        )
        Text(
            text = style.label,
            color = if (selected) WmColor.AmberText else Color(0x6BFFFFFF),
            fontSize = 8.5.sp,
            letterSpacing = 0.4.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 7.dp, bottom = 5.dp)
        )
    }
}

/** 3×3 九宫格：四个角 + 中心可点，其余位置留空不做假按钮。 */
@Composable
private fun PositionGrid(value: WatermarkPosition, onChange: (WatermarkPosition) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        val rows = listOf(
            listOf(WatermarkPosition.TOP_LEFT, null, WatermarkPosition.TOP_RIGHT),
            listOf(null, WatermarkPosition.CENTER, null),
            listOf(WatermarkPosition.BOTTOM_LEFT, null, WatermarkPosition.BOTTOM_RIGHT)
        )
        rows.forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (rowIndex == 0) 0.dp else 5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                row.forEach { position ->
                    if (position == null) {
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                        )
                    } else {
                        val active = value == position
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) Color(0x24FFB43A) else Color(0x0AFFFFFF))
                                .border(
                                    width = if (active) 1.2.dp else 1.dp,
                                    color = if (active) Color(0x80FFB43A) else Color(0x0FFFFFFF),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onChange(position) },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier
                                    .size(width = 16.dp, height = 11.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (active) WmColor.Amber else Color(0x2EFFFFFF))
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 附加信息开关：小方块 chip，选中即琥珀描边。 */
@Composable
private fun ExtraChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(34.dp)
            .background(
                if (selected) Color(0x1FFFB43A) else Color(0x0FFFFFFF),
                RoundedCornerShape(9.dp)
            )
            .border(
                1.dp,
                if (selected) WmColor.Amber else Color(0x1FFFFFFF),
                RoundedCornerShape(9.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) WmColor.AmberText else WmColor.TextSecondary,
            fontSize = 12.sp
        )
    }
}

/** 行业模板字段编辑器：TEXT 走输入框、CHOICE 走单选 chip、TOGGLE 走「正常/异常」勾选。 */
@Composable
private fun IndustryFieldEditor(
    preset: WatermarkTemplatePreset,
    data: String,
    onChange: (String) -> Unit
) {
    val fields = com.example.watermark.domain.IndustryLayout.fieldsOf(preset)
    val values = com.example.watermark.domain.IndustryLayout.decode(data).toMutableMap()

    fun put(key: String, value: String) {
        values[key] = value
        onChange(com.example.watermark.domain.IndustryLayout.encode(values))
    }

    Column(Modifier.padding(vertical = 4.dp)) {
        fields.forEach { field ->
            when (field.type) {
                com.example.watermark.domain.IndustryLayout.FieldType.TEXT -> {
                    InlineField(
                        label = field.label,
                        value = values[field.key].orEmpty(),
                        placeholder = field.placeholder,
                        onChange = { put(field.key, it) }
                    )
                    HairlineDivider()
                }

                com.example.watermark.domain.IndustryLayout.FieldType.CHOICE -> {
                    Column(Modifier.padding(vertical = 9.dp)) {
                        Text(field.label, color = WmColor.TextSecondary, fontSize = 12.5.sp)
                        Row(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val current = values[field.key]?.takeIf { it.isNotBlank() }
                                ?: field.choices.firstOrNull().orEmpty()
                            field.choices.forEach { choice ->
                                WmChip(
                                    text = choice,
                                    selected = current == choice,
                                    onClick = { put(field.key, choice) }
                                )
                            }
                        }
                    }
                    HairlineDivider()
                }

                com.example.watermark.domain.IndustryLayout.FieldType.TOGGLE -> {
                    val checked = (values[field.key] ?: if (field.defaultChecked) "1" else "0") == "1"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            field.label,
                            color = WmColor.TextPrimary,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            ExtraChip(label = "正常", selected = checked, onClick = { put(field.key, "1") })
                            ExtraChip(label = "异常", selected = !checked, onClick = { put(field.key, "0") })
                        }
                    }
                    HairlineDivider()
                }
            }
        }
        // 实时结算提示：行业里最容易被手算错的就是金额和工时
        val lines = com.example.watermark.domain.IndustryLayout.details(
            SettingsRepository.defaultSettings.copy(
                watermarkTemplatePreset = preset,
                industryData = com.example.watermark.domain.IndustryLayout.encode(values)
            )
        )
        if (lines.isNotEmpty()) {
            Text(
                text = "预览：" + lines.joinToString(" · ") { it.asText() },
                color = WmColor.TextTertiary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
            )
        }
    }
}

private fun presetShortLabel(preset: WatermarkTemplatePreset): String = when (preset) {
    WatermarkTemplatePreset.BASIC -> "基础"
    WatermarkTemplatePreset.TIME_FIRST -> "时间优先"
    WatermarkTemplatePreset.SIMPLE -> "简洁"
    WatermarkTemplatePreset.CUSTOM -> "自定义"
    WatermarkTemplatePreset.ATTENDANCE -> "考勤"
    WatermarkTemplatePreset.INSPECTION -> "巡检"
    WatermarkTemplatePreset.PURCHASE -> "采购"
    WatermarkTemplatePreset.MATERIAL -> "材料"
    WatermarkTemplatePreset.LABOR -> "劳务"
    WatermarkTemplatePreset.COMMERCE -> "微商"
    WatermarkTemplatePreset.TRAVEL -> "旅行"
}

private fun timeFormatShort(format: WatermarkTimeFormat): String = when (format) {
    WatermarkTimeFormat.YYYY_MM_DD_WEEKTIME -> "年月日 星期 时间"
    WatermarkTimeFormat.YYYY_MM_DD_HHMM -> "年-月-日 时:分:秒"
    WatermarkTimeFormat.YYYY_MM_DD -> "年月日"
    WatermarkTimeFormat.MM_DD_YYYY -> "月日 年"
    WatermarkTimeFormat.HH_MM_SS -> "时:分:秒"
    WatermarkTimeFormat.YYYY_MM_DD_SLASH -> "年/月/日 时间"
    WatermarkTimeFormat.CUSTOM -> "自定义"
}