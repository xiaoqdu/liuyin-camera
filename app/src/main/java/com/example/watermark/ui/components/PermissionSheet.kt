package com.example.watermark.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.watermark.ui.theme.WmColor
import com.example.watermark.util.PermissionManager

/**
 * 首启权限引导：一屏讲完三项权限的用途与状态。
 * 永久拒绝时把主按钮换成「去系统设置」，并保留「先浏览相册」这条退路。
 */
@Composable
fun PermissionSheet(
    groups: List<PermissionManager.DisplayPermission>,
    granted: (PermissionManager.DisplayPermission) -> Boolean,
    permanent: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onLater: () -> Unit
) {
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xCC05070A), Color(0xF205070A))))
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 0.dp)
                .padding(bottom = 0.dp)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Color(0xF013161B))
                .padding(start = 20.dp, end = 20.dp, top = 20.dp)
                .padding(bottom = navBottom + 24.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFFFFC365), Color(0xFFFFA63C)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                WmIconView(WmIcon.Lock, tint = Color(0xFF231607), size = 24.dp)
            }

            Text(
                text = if (permanent) "还需要你手动打开权限" else "开启三项权限，水印才能自动生成",
                color = WmColor.TextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 26.sp,
                modifier = Modifier.padding(top = 14.dp)
            )
            Text(
                text = if (permanent)
                    "刚才的权限请求被系统记住了「不再询问」，需要到设置里手动打开。你也可以先只浏览相册。"
                else
                    "只用于下面写明的用途，不会读取你的其他信息。",
                color = WmColor.TextSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(Modifier.height(14.dp))

            groups.forEach { group ->
                val ok = granted(group)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x14FFFFFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        WmIconView(
                            type = when (group.label) {
                                "相机" -> WmIcon.Camera
                                "定位" -> WmIcon.Pin
                                else -> WmIcon.Gallery
                            },
                            tint = if (ok) WmColor.Good else WmColor.TextSecondary,
                            size = 18.dp
                        )
                    }
                    Column(modifier = Modifier.padding(start = 11.dp)) {
                        Text(group.label, color = WmColor.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(group.purpose, color = WmColor.TextTertiary, fontSize = 11.sp)
                    }
                    Box(Modifier.weight(1f))
                    Text(
                        text = if (ok) "已授权" else "待授权",
                        color = if (ok) WmColor.Good else WmColor.AmberText,
                        fontSize = 10.5.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (ok) Color(0x1A7EE0B8) else Color(0x1FFFB43A))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            PrimaryButton(
                text = if (permanent) "去系统设置开启" else "允许全部",
                onClick = if (permanent) onOpenSettings else onAllow,
                icon = if (permanent) WmIcon.Gear else WmIcon.Check,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(9.dp))
            GhostButton(
                text = "暂不，先浏览相册",
                onClick = onLater,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    "拒绝后可在系统设置中随时修改",
                    color = WmColor.TextTertiary,
                    fontSize = 10.5.sp
                )
            }
        }
    }
}
