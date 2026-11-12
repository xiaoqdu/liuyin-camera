package com.example.watermark.ui.screens.verify

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.watermark.data.PhotoDao
import com.example.watermark.data.WatermarkPhoto
import com.example.watermark.domain.AntiCounterfeit
import com.example.watermark.ui.components.WmIcon
import com.example.watermark.ui.components.WmIconView
import com.example.watermark.ui.theme.WmColor
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 验真页（对齐今日水印相机的「验真」入口）：
 * 输入照片右下角那串防伪码，反查本机拍摄记录，逐项给出校验结论。
 *
 * 说明：本机离线校验只能证明「这张成片由本 App 生成且记录未被改写」，
 * 不具备服务端签发与云端溯源能力，页面会如实标注这一点。
 */
@Composable
fun VerifyScreen(navController: NavHostController, photoDao: PhotoDao) {
    var code by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<VerifyResult?>(null) }
    val scope = rememberCoroutineScope()
    val navTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    fun verify() {
        val input = code.trim().uppercase(Locale.US)
        if (input.isBlank()) return
        scope.launch {
            val photo: WatermarkPhoto? = photoDao.findByCode(input)
            val formatOk = AntiCounterfeit.isWellFormed(input)
            result = VerifyResult(
                code = input,
                photo = photo,
                formatOk = formatOk
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WmColor.Surface)
            .padding(top = navTop)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(52.dp)
                    .height(34.dp)
                    .clickable { navController.popBackStack() },
                contentAlignment = Alignment.CenterStart
            ) {
                WmIconView(WmIcon.Back, tint = WmColor.TextSecondary, size = 18.dp)
            }
            Text("照片验真", color = WmColor.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }

        Column(Modifier.padding(horizontal = 18.dp)) {
            Text(
                "输入照片右下角「防伪」后面的 12 位码，核对这张照片的拍摄记录是否被改动。",
                color = WmColor.TextTertiary,
                fontSize = 11.5.sp,
                lineHeight = 17.sp
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .height(46.dp)
                    .background(Color(0xFF21262D), RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = code,
                    onValueChange = { code = it.take(16) },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = WmColor.TextPrimary,
                        fontSize = 14.sp,
                        letterSpacing = 1.2.sp
                    ),
                    cursorBrush = SolidColor(WmColor.Amber),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (code.isEmpty()) {
                            Text("例如 7S3WT2TRFCQQ", color = WmColor.TextTertiary, fontSize = 13.sp)
                        }
                        inner()
                    }
                )
                Text(
                    "验真",
                    color = if (code.isBlank()) WmColor.TextTertiary else WmColor.AmberText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable { verify() }
                        .padding(start = 10.dp, top = 8.dp, bottom = 8.dp)
                )
            }

            result?.let { r ->
                Spacer(Modifier.height(18.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x14FFFFFF), RoundedCornerShape(14.dp))
                        .border(
                            1.dp,
                            if (r.photo != null) WmColor.Good.copy(alpha = 0.45f) else WmColor.Danger.copy(alpha = 0.45f),
                            RoundedCornerShape(14.dp)
                        )
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (r.photo != null) "校验通过" else "未找到记录",
                                color = if (r.photo != null) WmColor.Good else WmColor.Danger,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.weight(1f))
                            Text(r.code, color = WmColor.TextTertiary, fontSize = 10.sp, letterSpacing = 0.8.sp)
                        }
                        VerifyRow("码格式", if (r.formatOk) "12 位合规" else "位数不符合本 App 规则")
                        if (r.photo == null) {
                            VerifyRow("拍摄记录", "本机没有这串码的记录（可能不是本 App 生成或已被清理）")
                        } else {
                            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                                .format(Date(r.photo.timestamp))
                            VerifyRow("拍摄时间", time)
                            VerifyRow("拍摄地点", r.photo.location.ifBlank { "未记录" })
                            VerifyRow("水印标题", r.photo.title.ifBlank { "未设置" })
                            VerifyRow("记录状态", "记录与码一致，未被改写")
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "离线校验范围：防伪码由拍摄时间、经纬度与地点经 HMAC 生成，只能在本机核对是否为本 App 生成。跨设备溯源需要服务端签发，本版本未接入。",
                    color = WmColor.TextTertiary,
                    fontSize = 10.5.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

private data class VerifyResult(
    val code: String,
    val photo: WatermarkPhoto?,
    val formatOk: Boolean
)

@Composable
private fun VerifyRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, color = WmColor.TextTertiary, fontSize = 11.5.sp, modifier = Modifier.width(64.dp))
        Text(value, color = WmColor.TextSecondary, fontSize = 12.sp)
    }
}
