package com.example.watermark.domain

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WatermarkEngine {

    /** 大字时间排版所需的几段文字，Canvas 与 Compose 预览共用同一份切分逻辑。 */
    data class StampParts(
        val time: String,
        val date: String,
        val week: String,
        val location: String,
        val coordinate: String?,
        /** 防伪码：由时间 + 经纬度 HMAC 得到，绘制时带「防伪」前缀。 */
        val code: String,
        /** 标题（设置里的「昵称」字段）；为空或「未设置」时不显示。 */
        val title: String? = null,
        /** 附加信息行：天气/海拔/农历/电量/备注等拼成的一行，为空不占位。 */
        val extra: String? = null,
        /** 行业模板明细行（检查项勾选、金额、工时等），为空不占位。 */
        val details: List<IndustryLayout.DetailLine> = emptyList()
    )

    fun buildStampParts(
        settings: com.example.watermark.data.WatermarkSettings,
        currentLocation: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        timestampMs: Long = System.currentTimeMillis(),
        extraText: String? = null
    ): StampParts {
        val date = Date(timestampMs)
        // 锁定时间地点（考勤打卡）：手动位置不采信，定位失败时如实显示「定位中（已锁定）」
        val location = when {
            !currentLocation.isNullOrBlank() && currentLocation != "获取位置失败" -> currentLocation
            settings.lockTimeLocation -> "定位中（已锁定）"
            else -> settings.locationOverride.ifBlank { "未知位置" }
        }
        val coordinate = if (settings.showCoordinates && latitude != null && longitude != null) {
            val lat = String.format(Locale.US, "%.6f", kotlin.math.abs(latitude))
            val lng = String.format(Locale.US, "%.6f", kotlin.math.abs(longitude))
            "经纬度: $lat°${if (latitude >= 0) "N" else "S"},$lng°${if (longitude >= 0) "E" else "W"}"
        } else null
        return StampParts(
            time = SimpleDateFormat("HH:mm", Locale.SIMPLIFIED_CHINESE).format(date),
            date = SimpleDateFormat("yyyy-MM-dd", Locale.SIMPLIFIED_CHINESE).format(date),
            week = SimpleDateFormat("EEEE", Locale.SIMPLIFIED_CHINESE).format(date),
            location = location,
            coordinate = coordinate,
            code = AntiCounterfeit.code(timestampMs, latitude, longitude, location),
            // 「未设置」是默认占位值，等同于没填标题
            title = settings.nickname
                .takeIf { it.isNotBlank() && it != "未设置" }
                ?.take(24),
            extra = extraText?.takeIf { it.isNotBlank() },
            details = IndustryLayout.details(settings)
        )
    }

    /**
     * Format time string based on WatermarkTimeFormat setting.
     */
    private fun formatTime(timestampMs: Long, timeFormat: com.example.watermark.data.WatermarkTimeFormat): String {
        return try {
            if (timeFormat.pattern.isNotEmpty()) {
                SimpleDateFormat(timeFormat.pattern, Locale.SIMPLIFIED_CHINESE).format(Date(timestampMs))
            } else {
                // Fallback for CUSTOM format
                SimpleDateFormat("yyyy年MM月dd日 EEEE HH:mm:ss", Locale.SIMPLIFIED_CHINESE).format(Date(timestampMs))
            }
        } catch (e: Exception) {
            SimpleDateFormat("yyyy年MM月dd日 EEEE HH:mm:ss", Locale.SIMPLIFIED_CHINESE).format(Date(timestampMs))
        }
    }

    /**
     * Build watermark text from template with optional GPS location.
     * @param currentLocation GPS-retrieved location string or null → "获取位置失败"
     * @param timestampMs Time to display (defaults to current time)
     */
    fun buildWatermarkText(
        settings: com.example.watermark.data.WatermarkSettings,
        currentLocation: String? = null,
        timestampMs: Long = System.currentTimeMillis(),
        extraText: String? = null
    ): String {
        val nickname = if (settings.nickname.isBlank()) "未设置" else settings.nickname
        // 考勤打卡等模板锁定时间地点：只认实时定位与系统时间，手动位置一律不采信
        val manual = if (settings.lockTimeLocation) "" else settings.locationOverride
        val location = when {
            currentLocation != null && currentLocation.isNotBlank() && currentLocation != "获取位置失败" -> currentLocation
            manual.isNotBlank() -> manual
            settings.lockTimeLocation -> "定位中（已锁定）"
            else -> "未知位置"
        }
        val time = formatTime(timestampMs, settings.watermarkTimeFormat)
        // 附加信息：模板里有「备注」就填进去；模板没这个占位符时补在末尾一行
        val extra = extraText?.trim().orEmpty()
        val hasNoteSlot = settings.watermarkTemplate.contains("备注")
        val text = settings.watermarkTemplate
            // 「标题」是现在的叫法，「昵称」是旧模板里遗留的占位符，两者都替换掉
            .replace("标题", nickname)
            .replace("昵称", nickname)
            .replace("位置", location)
            .replace("备注", extra)
            .replace("时间", time)
        return if (!hasNoteSlot && extra.isNotBlank()) "$text · $extra" else text
    }

    /**
     * 卡片排版用的正文：在主文本后面接上行业明细行。
     * 卡片按「 · 」拆段排成多行，所以明细行直接以段的形式追加即可。
     */
    fun buildCardText(
        settings: com.example.watermark.data.WatermarkSettings,
        currentLocation: String? = null,
        timestampMs: Long = System.currentTimeMillis(),
        extraText: String? = null
    ): String {
        val base = buildWatermarkText(settings, currentLocation, timestampMs, extraText)
        val details = IndustryLayout.details(settings)
        if (details.isEmpty()) return base
        return buildString {
            append(base)
            details.forEach { append(" · ").append(it.asText()) }
        }
    }

    /**
     * Draw watermark text onto source Bitmap, applying the style from settings.
     */
    fun drawWatermark(
        sourceBitmap: Bitmap,
        settings: com.example.watermark.data.WatermarkSettings,
        currentLocation: String? = null,
        /**
         * 用户在预览页拖动水印产生的位移，单位是“占位图宽/高的比例”。
         * 传 0f 表示沿用位置设置里的锚点。
         */
        extraOffsetX: Float = 0f,
        extraOffsetY: Float = 0f,
        latitude: Double? = null,
        longitude: Double? = null,
        /** 右上角地图缩略图，由调用方在 IO 线程用 MapThumbnail 预取；拿不到就只画水印。 */
        mapThumb: Bitmap? = null,
        /** 防伪码；不传则按当前时间与坐标现算，保证预览与导出同一张图上的码一致。 */
        stampCode: String? = null,
        /** 附加信息行（天气/海拔/农历/电量/备注等），预览与导出共用同一份。 */
        extraText: String? = null
    ): Bitmap {
        val width = sourceBitmap.width
        val height = sourceBitmap.height

        val outputBitmap = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)

        // 行业模板统一切大字排版：明细行在单行卡片里会被截断
        if (IndustryLayout.effectiveLayout(settings) == com.example.watermark.data.WatermarkLayout.STAMP) {
            drawStamp(
                outputBitmap = outputBitmap,
                settings = settings,
                currentLocation = currentLocation,
                latitude = latitude,
                longitude = longitude,
                extraOffsetX = extraOffsetX,
                extraOffsetY = extraOffsetY,
                mapThumb = mapThumb,
                stampCode = stampCode,
                extraText = extraText
            )
            return outputBitmap
        }

        val canvas = Canvas(outputBitmap)

        val text = buildWatermarkText(settings, currentLocation, extraText = extraText)

        if (text.isBlank()) return outputBitmap

        // Paint setup with font family
        // 字号与大字时间同一套量级（按图片宽度等比）：12sp 滑块 ≈ 72px@1080，接近大字时间主行
        val s = width / 1080f
        val maxLineWidth = width - 56f * s - 300f * s
        val paint = Paint().apply {
            textSize = settings.watermarkSize * 6f * s
            color = settings.watermarkColor
            isAntiAlias = true
            setTypeface(Typeface.create(settings.watermarkFontFamily.androidFamily, Typeface.NORMAL))
        }

        // 按「 · 」拆段，逐段排成多行；放不下就整段缩字号，直到塞得进安全宽度
        val segments = text.split(" · ").filter { it.isNotBlank() }
        // 行业明细行单独成行、字号更小：全塞进同一段会被截断
        val details = IndustryLayout.details(settings)
        val detailPaint = Paint(paint).apply { textSize = paint.textSize * 0.62f }
        while (paint.textSize > 20f * s &&
            (segments.any { paint.measureText(it) > maxLineWidth } ||
                details.any { detailPaint.measureText(it.asText()) > maxLineWidth })
        ) {
            paint.textSize *= 0.92f
            // 主字号变化后明细字号跟着走，保证两级字号比例不变
            detailPaint.textSize = paint.textSize * 0.62f
        }
        val fm = paint.fontMetrics
        val lineHeight = (fm.descent - fm.ascent) * 1.12f
        val detailFm = detailPaint.fontMetrics
        val detailLineH = (detailFm.descent - detailFm.ascent) * 1.16f
        val textWidth = maxOf(
            segments.maxOf { paint.measureText(it) },
            if (details.isEmpty()) 0f else details.maxOf { detailPaint.measureText(it.asText()) }
        )
        val textHeight = lineHeight * segments.size +
            if (details.isEmpty()) 0f else detailLineH * details.size + lineHeight * 0.18f

        // Scale padding and border based on bitmap width
        val scale = s
        val padding = (18f * scale).toInt()
        val strokeWidth = (2f * scale).toInt()

        // Calculate watermark box dimensions
        val boxWidth = textWidth.toInt() + padding * 2
        val boxHeight = textHeight.toInt() + padding * 2

        // Background paint setup based on style
        val bgPaint = Paint().apply {
            style = Paint.Style.FILL
        }

        val bgColor = settings.watermarkBgColor

        when (settings.watermarkBgStyle) {
            com.example.watermark.data.WatermarkBgStyle.TRANSPARENT -> {
                // No background
            }
            com.example.watermark.data.WatermarkBgStyle.GRADIENT -> {
                // Vertical gradient: top edge lighter -> middle darker -> bottom edge lighter
                val gradient = android.graphics.LinearGradient(
                    0f, 0f, 0f, boxHeight.toFloat(),
                    intArrayOf(
                        android.graphics.Color.parseColor("#88000000"),
                        android.graphics.Color.parseColor("#CC000000"),
                        android.graphics.Color.parseColor("#88000000")
                    ),
                    null,
                    android.graphics.Shader.TileMode.CLAMP
                )
                bgPaint.shader = gradient
            }
            else -> {
                bgPaint.color = bgColor
            }
        }

        // Calculate target position based on watermarkPosition anchor points
        val pos = settings.watermarkPosition
        var anchorX = (width * pos.offsetX).toInt()
        var anchorY = (height * pos.offsetY).toInt()

        // Adjust anchor to top-left of the watermark box based on position
        var drawX = anchorX
        var drawY = anchorY
        when (pos) {
            com.example.watermark.data.WatermarkPosition.TOP_LEFT -> {
                drawX = anchorX
                drawY = anchorY
            }
            com.example.watermark.data.WatermarkPosition.TOP_RIGHT -> {
                drawX = anchorX - boxWidth
                drawY = anchorY
            }
            com.example.watermark.data.WatermarkPosition.BOTTOM_LEFT -> {
                drawX = anchorX
                drawY = anchorY - boxHeight
            }
            com.example.watermark.data.WatermarkPosition.BOTTOM_RIGHT -> {
                drawX = anchorX - boxWidth
                drawY = anchorY - boxHeight
            }
            com.example.watermark.data.WatermarkPosition.CENTER -> {
                drawX = anchorX - boxWidth / 2
                drawY = anchorY - boxHeight / 2
            }
        }

        // 拖拽位移（归一化比例）叠加到锚点上，再统一做边界收敛
        drawX += (extraOffsetX * width).toInt()
        drawY += (extraOffsetY * height).toInt()

        // 右下角三行角标要占位：卡片贴底且横向逼近右缘时整体上移，避免压字
        val brandReserve = 120f * s + 34f * s
        val brandLeft = width - 260f * s - 28f * s
        if (drawY + boxHeight > height - brandReserve && drawX + boxWidth > brandLeft) {
            drawY = (height - brandReserve - boxHeight).toInt()
        }

        // Clamp to ensure box stays within bitmap
        drawX = drawX.coerceIn(0, width - boxWidth)
        drawY = drawY.coerceIn(0, height - boxHeight)

        // Save canvas state and translate to draw position
        canvas.save()
        canvas.translate(drawX.toFloat(), drawY.toFloat())

        // Draw based on style
        when (settings.watermarkStyle) {
            com.example.watermark.data.WatermarkStyle.SOLID -> {
                // Semi-transparent background + text
                canvas.drawRect(0f, 0f, boxWidth.toFloat(), boxHeight.toFloat(), bgPaint)
            }
            com.example.watermark.data.WatermarkStyle.BORDER -> {
                // 圆角矩形底 + 一圈同色描边（描边画在底框上，而不是把文字中空）
                val radius = 10f * scale
                if (settings.watermarkBgStyle != com.example.watermark.data.WatermarkBgStyle.TRANSPARENT) {
                    canvas.drawRoundRect(0f, 0f, boxWidth.toFloat(), boxHeight.toFloat(), radius, radius, bgPaint)
                }
                // 边框用「背景填充」里选的颜色，不再借用文字颜色
                val borderPaint = Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    setStrokeWidth(strokeWidth.toFloat())
                    color = settings.watermarkBgColor
                }
                canvas.drawRoundRect(
                    strokeWidth / 2f, strokeWidth / 2f,
                    boxWidth.toFloat() - strokeWidth / 2f, boxHeight.toFloat() - strokeWidth / 2f,
                    radius, radius, borderPaint
                )
            }
            com.example.watermark.data.WatermarkStyle.PILL -> {
                // Pill shape background (fully rounded)
                if (settings.watermarkBgStyle != com.example.watermark.data.WatermarkBgStyle.TRANSPARENT) {
                    // 多行时半圆会切到首尾行，圆角上限收到 36*s
                    val radius = minOf(boxHeight / 2f, 36f * scale)
                    canvas.drawRoundRect(0f, 0f, boxWidth.toFloat(), boxHeight.toFloat(), radius, radius, bgPaint)
                }
            }
            com.example.watermark.data.WatermarkStyle.STROKE -> {
                // 文字描边：不铺底板，只给文字加深色外圈（和大字时间同一套观感）
            }
        }

        // 文字单独画在底板之上：多行逐行落基线，边框加大后不会压字
        if (settings.watermarkStyle != com.example.watermark.data.WatermarkStyle.STROKE) {
            paint.style = Paint.Style.FILL
        }
        // 描边样式：先画深色外圈，再把原色字芯压上去，避免出现空心字
        val outlinePaint = if (settings.watermarkStyle == com.example.watermark.data.WatermarkStyle.STROKE) {
            Paint(paint).apply {
                style = Paint.Style.STROKE
                setStrokeWidth(3f * scale)
                this.color = android.graphics.Color.argb(200, 0, 0, 0)
            }
        } else null
        var baseY = padding.toFloat() - fm.ascent
        for (line in segments) {
            outlinePaint?.let { canvas.drawText(line, padding.toFloat(), baseY, it) }
            canvas.drawText(line, padding.toFloat(), baseY, paint)
            baseY += lineHeight
        }
        // 明细行：在主文本下方按小字号逐行排，与大字排版一致地保留每一行
        if (details.isNotEmpty()) {
            var detailY = baseY + lineHeight * 0.18f - detailFm.ascent
            details.forEach { line ->
                val rowText = line.asText()
                if (outlinePaint != null) {
                    outlinePaint.textSize = detailPaint.textSize
                    outlinePaint.strokeWidth = 2f * scale
                    canvas.drawText(rowText, padding.toFloat(), detailY, outlinePaint)
                    outlinePaint.strokeWidth = 3f * scale
                }
                canvas.drawText(rowText, padding.toFloat(), detailY, detailPaint)
                detailY += detailLineH
            }
        }

        canvas.restore()

        // 卡片排版也带右上角地图与右下角角标，和大字时间同一套画法、同一套尺寸
        val badgeMarginX = 28f * s
        val badgeMarginY = 34f * s
        mapThumb?.let { thumb ->
            drawMapBadge(canvas, width.toFloat(), badgeMarginX, badgeMarginY, s, thumb)
        }
        drawBrandBadge(
            canvas = canvas,
            w = width.toFloat(),
            h = height.toFloat(),
            s = s,
            marginX = badgeMarginX,
            marginY = badgeMarginY,
            settings = settings,
            code = stampCode
                ?: buildStampParts(settings, currentLocation, latitude, longitude).code
        )

        return outputBitmap
    }

    /**
     * 大字时间排版（对齐参考样片）：
     *   17:34 │ 2026-11-06
     *         │ 星期五
     *   地点
     *   经纬度: 22.945185°N,113.943431°E
     * 无底板，白色文字 + 投影保证在任何画面上可读。
     */
    private fun drawStamp(
        outputBitmap: Bitmap,
        settings: com.example.watermark.data.WatermarkSettings,
        currentLocation: String?,
        latitude: Double?,
        longitude: Double?,
        extraOffsetX: Float,
        extraOffsetY: Float,
        mapThumb: Bitmap? = null,
        stampCode: String? = null,
        extraText: String? = null
    ) {
        val canvas = Canvas(outputBitmap)
        val parts = buildStampParts(settings, currentLocation, latitude, longitude, extraText = extraText)
        val w = outputBitmap.width.toFloat()
        val h = outputBitmap.height.toFloat()
        val s = w / 1080f

        val stampTypeface = Typeface.create(
            settings.watermarkFontFamily.androidFamily,
            Typeface.BOLD
        )
        val shadowColor = android.graphics.Color.argb(150, 0, 0, 0)

        // 外观样式在这里落地：字号滑块按 12sp 为基准等比缩放，样式决定底板／描边
        val sizeScale = (settings.watermarkSize / 12f).coerceIn(0.65f, 1.8f)
        val style = settings.watermarkStyle
        val outlineColor = android.graphics.Color.argb(190, 0, 0, 0)

        fun makePaint(size: Float, color: Int) = Paint().apply {
            isAntiAlias = true
            setTypeface(stampTypeface)
            textSize = size
            this.color = color
            setShadowLayer(3.2f * s, 0f, 1.6f * s, shadowColor)
        }

        // 描边样式：先画一圈深色外描边再填字，暗背景上比纯投影更硬
        fun drawStyledText(text: String, tx: Float, ty: Float, paint: Paint) {
            if (style == com.example.watermark.data.WatermarkStyle.STROKE) {
                val outline = Paint(paint).apply {
                    this.style = Paint.Style.STROKE
                    strokeWidth = 2.4f * s
                    color = outlineColor
                    clearShadowLayer()
                }
                canvas.drawText(text, tx, ty, outline)
            }
            canvas.drawText(text, tx, ty, paint)
        }

        val timeSize = 88f * s * sizeScale
        val dateSize = 40f * s * sizeScale
        val weekSize = 36f * s * sizeScale
        val locSize = 36f * s * sizeScale
        val coordSize = 31f * s * sizeScale
        // 附加信息行与经纬度同量级，排在经纬度下面
        val extraSize = 28f * s * sizeScale
        // 标题（原「昵称」）：不为空时压在大字时间正上方
        val titleSize = 38f * s * sizeScale
        // 行业明细行：比附加信息再小一号
        val detailSize = 26f * s * sizeScale
        val detailRowH = detailSize * 1.52f
        val barW = 8f * s
        val barH = timeSize * 0.66f
        val gapTimeBar = 15f * s
        val gapBarCol = 17f * s

        val timePaint = makePaint(timeSize, settings.watermarkColor)
        val datePaint = makePaint(dateSize, settings.watermarkColor)
        val weekPaint = makePaint(weekSize, settings.watermarkColor)
        val locPaint = makePaint(locSize, settings.watermarkColor)
        val coordPaint = makePaint(coordSize, settings.watermarkColor)
        val extraPaint = makePaint(extraSize, settings.watermarkColor)
        val titlePaint = makePaint(titleSize, settings.watermarkColor)
        val detailPaint = makePaint(detailSize, settings.watermarkColor)

        val timeW = timePaint.measureText(parts.time)
        val dateW = datePaint.measureText(parts.date)
        val weekW = weekPaint.measureText(parts.week)
        val locW = locPaint.measureText(parts.location)
        val coordW = parts.coordinate?.let { coordPaint.measureText(it) } ?: 0f
        val extraW = parts.extra?.let { extraPaint.measureText(it) } ?: 0f
        val titleW = parts.title?.let { titlePaint.measureText(it) } ?: 0f
        // 明细行：成片与预览统一用「✓ / ✗」字符表达勾选，避免两边画法不一致
        val detailW = parts.details.maxOfOrNull { line -> detailPaint.measureText(line.asText()) } ?: 0f

        val colW = maxOf(dateW, weekW)
        val blockW = timeW + gapTimeBar + barW + gapBarCol + colW
        val timeColH = maxOf(dateSize + weekSize * 1.35f, timeSize * 0.92f)
        val titleH = if (parts.title != null) titleSize * 1.42f else 0f
        val blockH = titleH + timeColH +
            (if (parts.coordinate != null) locSize + coordSize + 26f * s else locSize + 18f * s) +
            (if (parts.extra != null) extraSize + 14f * s else 0f) +
            parts.details.size * detailRowH
        val contentW = maxOf(blockW, locW, coordW, extraW, titleW, detailW)

        val marginX = 28f * s
        val marginY = 34f * s

        val pos = settings.watermarkPosition
        val horizontalStart = when (pos) {
            com.example.watermark.data.WatermarkPosition.TOP_RIGHT,
            com.example.watermark.data.WatermarkPosition.BOTTOM_RIGHT -> w - contentW - marginX
            com.example.watermark.data.WatermarkPosition.CENTER -> (w - contentW) / 2f
            else -> marginX
        }
        val topY = when (pos) {
            com.example.watermark.data.WatermarkPosition.TOP_LEFT,
            com.example.watermark.data.WatermarkPosition.TOP_RIGHT -> marginY
            com.example.watermark.data.WatermarkPosition.CENTER -> (h - blockH) / 2f
            else -> h - blockH - marginY
        }

        val x = (horizontalStart + extraOffsetX * w).coerceIn(0f, maxOf(0f, w - contentW))
        val yStart = (topY + extraOffsetY * h).coerceIn(0f, maxOf(0f, h - blockH))

        // 时间基线与右侧列（标题占位后整体下移）
        val contentTop = yStart + titleH
        val timeBaseline = contentTop + timeSize * 0.82f
        val dateBaseline = contentTop + dateSize * 0.92f
        val weekBaseline = dateBaseline + weekSize * 1.32f

        // 圆角底板 / 胶囊底板：这两种样式才铺底，纯色与描边都不铺（保住参考样片的通透感）
        when (style) {
            com.example.watermark.data.WatermarkStyle.BORDER -> {
                // 整块底色，四周留足内padding，文字不会贴边被圆角切掉
                val padX = 22f * s * sizeScale
                val padY = 18f * s * sizeScale
                canvas.drawRoundRect(
                    RectF(
                        x - padX,
                        yStart - padY,
                        x + contentW + padX,
                        yStart + blockH + padY
                    ),
                    16f * s, 16f * s,
                    Paint().apply {
                        isAntiAlias = true
                        color = settings.watermarkBgColor
                    }
                )
            }
            com.example.watermark.data.WatermarkStyle.PILL -> {
                // 胶囊只包住时间行：整块做胶囊时两端圆弧必然切到下面几行文字
                val padY = 14f * s * sizeScale
                val rectH = timeColH + padY * 2f
                // 两端圆弧要留出半径 0.85 的横向余量，字才落在直边上；贴边时收窄，避免胶囊被画布裁掉
                var padX = rectH / 2f * 0.85f
                padX = minOf(padX, x, maxOf(0f, w - (x + blockW)))
                canvas.drawRoundRect(
                    RectF(
                        x - padX,
                        contentTop - padY,
                        x + blockW + padX,
                        contentTop + timeColH + padY
                    ),
                    rectH / 2f, rectH / 2f,
                    Paint().apply {
                        isAntiAlias = true
                        color = settings.watermarkBgColor
                    }
                )
            }
            else -> Unit
        }

        parts.title?.let {
            drawStyledText(it, x, yStart + titleSize * 1.02f, titlePaint)
        }

        drawStyledText(parts.time, x, timeBaseline, timePaint)

        val barLeft = x + timeW + gapTimeBar
        val barTop = timeBaseline - barH * 0.92f
        val barPaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.parseColor("#FFB43A")
            setShadowLayer(3f * s, 0f, 1.5f * s, shadowColor)
        }
        canvas.drawRect(barLeft, barTop, barLeft + barW, barTop + barH, barPaint)

        val colLeft = barLeft + barW + gapBarCol
        drawStyledText(parts.date, colLeft, dateBaseline, datePaint)
        drawStyledText(parts.week, colLeft, weekBaseline, weekPaint)

        val locBaseline = contentTop + timeColH + locSize + 10f * s
        drawStyledText(parts.location, x, locBaseline, locPaint)

        parts.coordinate?.let {
            drawStyledText(it, x, locBaseline + coordSize + 12f * s, coordPaint)
        }

        parts.extra?.let { line ->
            val extraBaseline = if (parts.coordinate != null) {
                locBaseline + coordSize + 12f * s + extraSize + 10f * s
            } else {
                locBaseline + extraSize + 10f * s
            }
            drawStyledText(line, x, extraBaseline, extraPaint)
        }

        // 行业明细行：勾选项先画方框（勾上用琥珀色对勾），再画文字
        if (parts.details.isNotEmpty()) {
            val detailTop = when {
                parts.extra != null -> {
                    val extraBaseline = if (parts.coordinate != null) {
                        locBaseline + coordSize + 12f * s + extraSize + 10f * s
                    } else {
                        locBaseline + extraSize + 10f * s
                    }
                    extraBaseline + detailSize * 0.6f
                }
                parts.coordinate != null -> locBaseline + coordSize + 12f * s + detailSize * 0.9f
                else -> locBaseline + detailSize * 0.9f
            }
            parts.details.forEachIndexed { index, line ->
                val baseline = detailTop + index * detailRowH
                drawStyledText(line.asText(), x, baseline, detailPaint)
            }
        }

        // 右上角：坐标对应的小地图缩略图（圆角 + 白边 + 中心定位点）
        mapThumb?.let { thumb -> drawMapBadge(canvas, w, marginX, marginY, s, thumb) }

        // 右下角品牌角标：留印相机 / 相机 真实可验 / 防伪 12 位码（无底板，靠投影立住）
        drawBrandBadge(
            canvas = canvas,
            w = w,
            h = h,
            s = s,
            marginX = marginX,
            marginY = marginY,
            settings = settings,
            code = stampCode ?: parts.code
        )
    }

    /** 右上角地图角标：两种排版共用同一套画法。 */
    private fun drawMapBadge(
        canvas: Canvas,
        w: Float,
        marginX: Float,
        marginY: Float,
        s: Float,
        thumb: Bitmap
    ) {
        val mapSize = 200f * s
        val left = w - mapSize - marginX
        val top = marginY
        val radius = 12f * s
        val rect = RectF(left, top, left + mapSize, top + mapSize)
        val clip = Path().apply {
            addRoundRect(rect, radius, radius, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(clip)
        canvas.drawBitmap(
            thumb,
            null,
            rect,
            Paint().apply {
                isAntiAlias = true
                isFilterBitmap = true
            }
        )
        canvas.restore()
        canvas.drawRoundRect(
            rect, radius, radius,
            Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeWidth = 2f * s
                color = android.graphics.Color.argb(214, 255, 255, 255)
                setShadowLayer(3f * s, 0f, 1.5f * s, android.graphics.Color.argb(150, 0, 0, 0))
            }
        )
        val cx = rect.centerX()
        val cy = rect.centerY()
        canvas.drawCircle(
            cx, cy, 5f * s,
            Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.parseColor("#FFB43A")
                setShadowLayer(3f * s, 0f, 1f * s, android.graphics.Color.argb(150, 0, 0, 0))
            }
        )
        canvas.drawCircle(
            cx, cy, 2.4f * s,
            Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.WHITE
            }
        )
    }

    /** 右下角品牌角标：两种排版共用同一套画法。 */
    private fun drawBrandBadge(
        canvas: Canvas,
        w: Float,
        h: Float,
        s: Float,
        marginX: Float,
        marginY: Float,
        settings: com.example.watermark.data.WatermarkSettings,
        code: String
    ) {
        val shadowColor = android.graphics.Color.argb(150, 0, 0, 0)
        val brand1Text = "留印相机"
        val brand2Text = "相机 真实可验"
        val codeText = "防伪 $code"
        val brand1Size = 30f * s
        val brand2Size = 21f * s
        val brand3Size = 19f * s
        val brand1Paint = Paint().apply {
            isAntiAlias = true
            setTypeface(Typeface.create(settings.watermarkFontFamily.androidFamily, Typeface.BOLD))
            textSize = brand1Size
            color = android.graphics.Color.argb(235, 255, 255, 255)
            setShadowLayer(3.2f * s, 0f, 1.6f * s, shadowColor)
        }
        val brand2Paint = Paint().apply {
            isAntiAlias = true
            setTypeface(Typeface.create(settings.watermarkFontFamily.androidFamily, Typeface.NORMAL))
            textSize = brand2Size
            color = android.graphics.Color.argb(184, 255, 255, 255)
            setShadowLayer(3.2f * s, 0f, 1.6f * s, shadowColor)
        }
        val brand3Paint = Paint().apply {
            isAntiAlias = true
            setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL))
            textSize = brand3Size
            color = android.graphics.Color.argb(168, 255, 255, 255)
            setShadowLayer(3.2f * s, 0f, 1.6f * s, shadowColor)
        }
        val brandRight = w - marginX
        val brandBaseline3 = h - marginY
        val brandBaseline2 = brandBaseline3 - brand3Size * 1.5f
        val brandBaseline1 = brandBaseline2 - brand2Size * 1.5f
        canvas.drawText(brand1Text, brandRight - brand1Paint.measureText(brand1Text), brandBaseline1, brand1Paint)
        canvas.drawText(brand2Text, brandRight - brand2Paint.measureText(brand2Text), brandBaseline2, brand2Paint)
        canvas.drawText(codeText, brandRight - brand3Paint.measureText(codeText), brandBaseline3, brand3Paint)
    }
}
