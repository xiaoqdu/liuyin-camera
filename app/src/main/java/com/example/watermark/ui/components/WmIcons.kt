package com.example.watermark.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 统一的图标语言：24 网格、细描边、无填充，避免混用系统图标导致风格割裂。 */
enum class WmIcon {
    Camera, Gallery, Gear, Grid, Pin, Back, Flip, Reset, Check, Plus,
    ChevronRight, ChevronDown, More, Share, Lock, Sort, Close, Ratio, Trash, Download,
    Search, Shield
}

@Composable
fun WmIconView(
    type: WmIcon,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    stroke: Dp = 1.6.dp
) {
    Canvas(modifier = modifier.then(Modifier.size(size))) {
        val u = this.size.minDimension / 24f
        val w = stroke.toPx()
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(tint, p(x1, y1), p(x2, y2), strokeWidth = w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        fun poly(vararg pts: Float) {
            val path = Path()
            path.moveTo(pts[0] * u, pts[1] * u)
            var i = 2
            while (i + 1 < pts.size) { path.lineTo(pts[i] * u, pts[i + 1] * u); i += 2 }
            drawPath(path, tint, style = Stroke(width = w, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
        fun round(x: Float, y: Float, wx: Float, wy: Float, r: Float) =
            drawRoundRect(
                color = tint,
                topLeft = p(x, y),
                size = Size(wx * u, wy * u),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r * u, r * u),
                style = Stroke(width = w)
            )
        fun circle(cx: Float, cy: Float, r: Float, fill: Boolean = false) =
            drawCircle(tint, r * u, p(cx, cy), style = if (fill) androidx.compose.ui.graphics.drawscope.Fill else Stroke(width = w))

        when (type) {
            WmIcon.Back -> poly(15f, 5f, 8f, 12f, 15f, 19f)
            WmIcon.ChevronRight -> poly(9f, 6f, 15f, 12f, 9f, 18f)
            WmIcon.ChevronDown -> poly(6f, 9f, 12f, 15f, 18f, 9f)
            WmIcon.Close -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
            WmIcon.Check -> poly(5f, 12.5f, 9.6f, 17f, 19f, 7f)
            WmIcon.Download -> {
                line(12f, 4f, 12f, 14.6f)
                poly(8.4f, 10.8f, 12f, 14.6f, 15.6f, 10.8f)
                line(4.6f, 17.6f, 4.6f, 19.4f)
                line(4.6f, 19.4f, 19.4f, 19.4f)
                line(19.4f, 19.4f, 19.4f, 17.6f)
            }
            WmIcon.Plus -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
            WmIcon.Search -> {
                circle(10.6f, 10.6f, 5.6f)
                line(14.8f, 14.8f, 20f, 20f)
            }
            WmIcon.Shield -> {
                poly(12f, 3.6f, 19.2f, 6.6f, 19.2f, 12.4f)
                poly(19.2f, 12.4f, 12f, 20.4f)
                poly(12f, 20.4f, 4.8f, 12.4f)
                poly(4.8f, 12.4f, 4.8f, 6.6f)
                poly(4.8f, 6.6f, 12f, 3.6f)
                poly(8.9f, 12f, 11.2f, 14.3f, 15.4f, 9.6f)
            }
            WmIcon.Grid -> {
                line(3f, 8.6f, 21f, 8.6f); line(3f, 15.4f, 21f, 15.4f)
                line(8.6f, 3f, 8.6f, 21f); line(15.4f, 3f, 15.4f, 21f)
            }
            WmIcon.Gear -> {
                circle(12f, 12f, 5.2f)
                circle(12f, 12f, 1.4f, fill = true)
                for (i in 0 until 8) {
                    val a = (Math.PI / 4) * i
                    val cx = 12f + Math.cos(a).toFloat() * 8.4f
                    val cy = 12f + Math.sin(a).toFloat() * 8.4f
                    line(cx * 0.86f + 12f * 0.14f, cy * 0.86f + 12f * 0.14f, cx, cy)
                }
            }
            WmIcon.Camera -> {
                round(3f, 6.6f, 18f, 13.2f, 3f)
                circle(12f, 13.2f, 3.4f)
                poly(8.6f, 6.6f, 9.9f, 4.4f, 14.1f, 4.4f, 15.4f, 6.6f)
            }
            WmIcon.Gallery -> {
                round(3.4f, 4.6f, 17.2f, 14.8f, 3f)
                poly(3.4f, 15.6f, 8.2f, 11.2f, 11.9f, 14.7f, 14.4f, 12.4f, 20.6f, 17.4f)
                circle(15.6f, 9.2f, 1.5f)
            }
            WmIcon.Pin -> {
                circle(12f, 10f, 3.9f)
                poly(8.4f, 12.9f, 12f, 21f, 15.6f, 12.9f)
            }
            WmIcon.Flip -> {
                drawArc(
                    color = tint, startAngle = 200f, sweepAngle = 140f, useCenter = false,
                    topLeft = p(3.4f, 3.4f), size = Size(17.2f * u, 17.2f * u),
                    style = Stroke(width = w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
                drawArc(
                    color = tint, startAngle = 20f, sweepAngle = 140f, useCenter = false,
                    topLeft = p(3.4f, 3.4f), size = Size(17.2f * u, 17.2f * u),
                    style = Stroke(width = w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
                line(15.8f, 3.2f, 17.4f, 5.4f); line(15.8f, 3.2f, 13.4f, 4.2f)
                line(8.2f, 20.8f, 6.6f, 18.6f); line(8.2f, 20.8f, 10.6f, 19.8f)
            }
            WmIcon.Reset -> {
                drawArc(
                    color = tint, startAngle = 150f, sweepAngle = 260f, useCenter = false,
                    topLeft = p(4f, 4f), size = Size(16f * u, 16f * u),
                    style = Stroke(width = w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
                poly(4f, 3.6f, 3.4f, 9.2f, 9f, 8.4f)
            }
            WmIcon.More -> {
                circle(5.2f, 12f, 1.5f, fill = true)
                circle(12f, 12f, 1.5f, fill = true)
                circle(18.8f, 12f, 1.5f, fill = true)
            }
            WmIcon.Share -> {
                circle(7f, 12.4f, 2.4f); circle(17f, 6.2f, 2.4f); circle(17f, 18.6f, 2.4f)
                line(9.2f, 11.3f, 14.9f, 7.3f); line(9.2f, 13.5f, 14.9f, 17.5f)
            }
            WmIcon.Lock -> {
                round(4.6f, 10.4f, 14.8f, 10.4f, 2.4f)
                drawArc(
                    color = tint, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                    topLeft = p(7.6f, 3.4f), size = Size(8.8f * u, 8.8f * u),
                    style = Stroke(width = w)
                )
            }
            WmIcon.Sort -> { line(4f, 7f, 20f, 7f); line(4f, 12f, 14.5f, 12f); line(4f, 17f, 9.5f, 17f) }
            WmIcon.Ratio -> { round(4f, 6f, 16f, 12f, 1.6f); line(9.2f, 6f, 9.2f, 18f); line(15f, 6f, 15f, 18f) }
            WmIcon.Trash -> {
                round(5f, 7.4f, 14f, 12.6f, 2f)
                line(3.4f, 7.4f, 20.6f, 7.4f)
                poly(9.4f, 7.4f, 9.4f, 4.4f, 14.6f, 4.4f, 14.6f, 7.4f)
            }
        }
    }
}
