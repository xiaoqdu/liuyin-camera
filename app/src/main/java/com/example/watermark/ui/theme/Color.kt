package com.example.watermark.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 改版后的唯一色板。
 * 深色为底，让取景内容成为画面里唯一的亮部；琥珀是唯一强调色。
 */
object WmColor {
    // 底
    val Ink = Color(0xFF0B0D10)          // 取景页 / App 根底
    val Surface = Color(0xFF15181D)      // 设置页、相册页底
    val Card = Color(0xFF1C2027)         // 卡片
    val CardHi = Color(0xFF252A32)       // 卡片内浮层 / 样式缩略图底

    // 强调
    val Amber = Color(0xFFFFB43A)
    val AmberDeep = Color(0xFFFF8A3D)
    val AmberSoft = Color(0xFFFFC365)
    val AmberText = Color(0xFFFFCE86)
    val OnAmber = Color(0xFF231607)

    // 文本
    val TextPrimary = Color(0xFFF2F4F7)
    val TextSecondary = Color(0x8FF2F4F7)   // 56%
    val TextTertiary = Color(0x57F2F4F7)    // 34%

    // 线与玻璃
    val Hairline = Color(0x1AFFFFFF)        // 10%
    val HairlineSoft = Color(0x0FFFFFFF)    // 6%
    val Glass = Color(0x9E14181D)           // 取景层上的玻璃
    val GlassStrong = Color(0xD9111419)     // 底部面板
    val Scrim = Color(0xB3000000)
    val Danger = Color(0xFFFF6B5E)
    val Good = Color(0xFF7EE0B8)
}
