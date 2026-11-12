package com.example.watermark.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * 权限查询与跳转。改版后它不再负责弹窗、也不再在拒绝时关掉 App——
 * 是否展示引导卡片、拒绝后给什么退路，全部由 Compose 侧的 PermissionSheet 决定。
 */
class PermissionManager(private val context: Context) {

    companion object {
        val REQUIRED: List<String> = buildList {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        }

        /** 引导卡片上展示的三项（定位合并 fine/coarse）。 */
        val DISPLAY_GROUPS = listOf(
            DisplayPermission("相机", "取景与拍照", listOf(Manifest.permission.CAMERA)),
            DisplayPermission("定位", "把地点写进水印文字", listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)),
            DisplayPermission("照片", "读取素材、保存成片", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) listOf(Manifest.permission.READ_MEDIA_IMAGES) else emptyList())
        )
    }

    data class DisplayPermission(
        val label: String,
        val purpose: String,
        val permissions: List<String>
    )

    fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasCamera(): Boolean = granted(Manifest.permission.CAMERA)

    fun hasLocation(): Boolean =
        granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    fun hasMedia(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) granted(Manifest.permission.READ_MEDIA_IMAGES) else true

    fun allGranted(): Boolean = hasCamera() && hasLocation() && hasMedia()

    /** 组是否已授权。 */
    fun groupGranted(group: DisplayPermission): Boolean =
        group.permissions.isEmpty() || group.permissions.any { granted(it) }

    /**
     * 已拒绝且系统不再愿意弹框 = 永久拒绝。
     * shouldShowRequestPermissionRationale 在“从未申请”时同样返回 false，
     * 所以必须由调用方告知“本轮已经申请过一次”，否则会把首次进入误判成永久拒绝。
     */
    fun permanentDenied(activity: Activity, group: DisplayPermission, askedBefore: Boolean): Boolean {
        if (groupGranted(group)) return false
        if (!askedBefore) return false
        val target = group.permissions.firstOrNull { !granted(it) } ?: return false
        return !ActivityCompat.shouldShowRequestPermissionRationale(activity, target)
    }

    /** 打开应用详情页，让用户可以手动开启。 */
    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}
