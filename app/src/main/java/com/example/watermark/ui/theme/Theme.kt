package com.example.watermark.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * 固定深色主题。
 * 刻意去掉 dynamicColor：壁纸取色会让“三个页面像三个 App”，改版后整套 UI 只跟随 WmColor。
 */
private val WmDarkColorScheme = darkColorScheme(
    primary = WmColor.Amber,
    onPrimary = WmColor.OnAmber,
    primaryContainer = WmColor.AmberDeep,
    onPrimaryContainer = WmColor.OnAmber,
    secondary = WmColor.AmberDeep,
    onSecondary = WmColor.OnAmber,
    tertiary = WmColor.AmberText,
    onTertiary = WmColor.OnAmber,
    background = WmColor.Surface,
    onBackground = WmColor.TextPrimary,
    surface = WmColor.Card,
    onSurface = WmColor.TextPrimary,
    surfaceVariant = WmColor.CardHi,
    onSurfaceVariant = WmColor.TextSecondary,
    surfaceContainer = WmColor.Card,
    surfaceContainerHigh = WmColor.CardHi,
    outline = WmColor.Hairline,
    outlineVariant = WmColor.HairlineSoft,
    error = WmColor.Danger,
    onError = WmColor.TextPrimary
)

@Composable
fun WatermarkTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = WmDarkColorScheme,
        typography = Typography,
        content = content
    )
}
