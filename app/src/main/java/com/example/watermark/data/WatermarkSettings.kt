package com.example.watermark.data

import androidx.compose.ui.graphics.Color

data class WatermarkSettings(
    val nickname: String = "未设置",
    val watermarkTemplate: String = "标题 · 位置 · 时间",
    val watermarkTemplatePreset: WatermarkTemplatePreset = WatermarkTemplatePreset.BASIC,
    val locationOverride: String = "",
    val watermarkTimeFormat: WatermarkTimeFormat = WatermarkTimeFormat.YYYY_MM_DD_HHMM,
    // 改版默认：白字 + 半透明黑底 + 左下角。原来的“黑字压黑底”在任何深色照片上都看不见。
    val watermarkColor: Int = 0xFFFFFFFF.toInt(), // white
    val watermarkBgStyle: WatermarkBgStyle = WatermarkBgStyle.SOLID,
    val watermarkBgColor: Int = 0x99000000.toInt(), // semi-transparent black
    val watermarkSize: Float = 12f,
    val watermarkFontFamily: WatermarkFontFamily = WatermarkFontFamily.DEFAULT,
    val watermarkPosition: WatermarkPosition = WatermarkPosition.BOTTOM_LEFT,
    val watermarkStyle: WatermarkStyle = WatermarkStyle.SOLID,
    val watermarkLayout: WatermarkLayout = WatermarkLayout.STAMP,
    val showCoordinates: Boolean = true,
    // ===== 附加信息字段（对齐市面水印相机）=====
    val showWeather: Boolean = false,
    val showTemperature: Boolean = false,
    val showHumidity: Boolean = false,
    val showWind: Boolean = false,
    val showAltitude: Boolean = false,
    val showBearing: Boolean = false,
    val showLunar: Boolean = false,
    val showFestival: Boolean = false,
    val showConstellation: Boolean = false,
    val showCountdown: Boolean = false,
    val showBattery: Boolean = false,
    val showNetwork: Boolean = false,
    /** 自定义备注行：整行原样显示，不受其他开关影响。 */
    val extraNote: String = "",
    /** 倒计时目标日（yyyy-MM-dd），空则不显示。 */
    val countdownTarget: String = "",
    val countdownLabel: String = "",
    /** 考勤打卡类模板：时间与地点锁定为拍摄瞬间，不允许手动改动。 */
    val lockTimeLocation: Boolean = false,
    /** 行业模板专属字段的值（key=value 按行存放，见 domain/IndustryLayout）。 */
    val industryData: String = "",
    val favoriteTemplates: String = "",
    // ===== 拍摄能力（对齐市面水印相机）=====
    /** 出片比例：RATIO_4_3 / RATIO_16_9 / RATIO_1_1 / FULL（全屏即不裁）。 */
    val cameraRatio: String = "4:3",
    /** 定时拍照秒数，0 = 关闭。 */
    val cameraTimerSec: Int = 0,
    /** 连拍张数，1 = 单张。 */
    val cameraBurst: Int = 1,
    /** 闪光灯：0 关闭 / 1 常亮 / 2 自动。 */
    val cameraFlash: Int = 0,
    /** 音量键当快门。 */
    val volumeShutter: Boolean = true,
    /** 水平仪参考线。 */
    val showLevel: Boolean = true,
    /** 网格线。 */
    val showGrid: Boolean = true
)

/** 水印排版：卡片（旧版单行卡片）／大字时间（今日水印式时间块）。 */
enum class WatermarkLayout(
    val label: String
) {
    CARD("卡片水印"),
    STAMP("大字时间")
}

enum class WatermarkPosition(
    val label: String,
    val offsetX: Float,
    val offsetY: Float
) {
    TOP_LEFT("左上角", 0.05f, 0.05f),
    TOP_RIGHT("右上角", 0.95f, 0.05f),
    BOTTOM_LEFT("左下角", 0.05f, 0.95f),
    BOTTOM_RIGHT("右下角", 0.95f, 0.95f),
    CENTER("居中", 0.5f, 0.5f)
}

enum class WatermarkStyle(
    val label: String
) {
    SOLID("纯色背景"),
    BORDER("圆角矩形"),
    PILL("胶囊圆角"),
    STROKE("文字描边")
}

enum class WatermarkFontFamily(
    val label: String,
    val androidFamily: String
) {
    DEFAULT("默认", "sans-serif"),
    SERIF("衬线体", "serif"),
    MONOSPACE("等宽体", "monospace"),
    SANS("无衬线", "sans-serif"),
    CONDENSED("紧凑", "sans-serif-condensed")
}

enum class WatermarkTimeFormat(
    val label: String,
    val pattern: String
) {
    YYYY_MM_DD_WEEKTIME("XXXX年XX月XX日 星期X 24小时制", "yyyy年MM月dd日 EEEE HH:mm:ss"),
    YYYY_MM_DD_HHMM("XXXX-XX-XX XX:XX:XX", "yyyy-MM-dd HH:mm:ss"),
    YYYY_MM_DD("XXXX年XX月XX日", "yyyy年MM月dd日"),
    MM_DD_YYYY("XX月XX日 XXXX", "MM月dd日 yyyy"),
    HH_MM_SS("XX:XX:XX", "HH:mm:ss"),
    YYYY_MM_DD_SLASH("XXXX/XX/XX XX:XX:XX", "yyyy/MM/dd HH:mm:ss"),
    CUSTOM("自定义", "")
}

enum class WatermarkTemplatePreset(
    val label: String,
    val template: String
) {
    BASIC("基础模板", "标题 · 位置 · 时间"),
    TIME_FIRST("时间优先", "时间 · 位置 · 标题"),
    SIMPLE("简洁版", "位置 · 时间"),
    CUSTOM("自定义", ""),
    // ===== 行业模板（对齐市面水印相机的行业模板库）=====
    ATTENDANCE("考勤打卡", "考勤打卡 · 位置 · 时间"),
    INSPECTION("工程巡检", "巡检 · 标题 · 位置 · 时间"),
    PURCHASE("物资采购", "物资采购 · 标题 · 位置 · 时间"),
    MATERIAL("材料验收", "材料验收 · 标题 · 位置 · 时间"),
    LABOR("劳务用工", "劳务 · 标题 · 位置 · 时间"),
    COMMERCE("微商水印", "标题 · 位置 · 时间 · 备注"),
    TRAVEL("旅行记录", "标题 · 位置 · 时间")
}

// 水印背景填充样式
enum class WatermarkBgStyle(
    val label: String
) {
    SOLID("纯色背景"),
    TRANSPARENT("无背景"),
    BORDER("圆角边框"),
    GRADIENT("渐变背景")
}

// 水印背景颜色预设
enum class WatermarkBgColor(
    val label: String,
    val color: Int
) {
    BLACK("半透明黑", 0x80000000.toInt()),
    WHITE("半透明白", 0x80FFFFFF.toInt()),
    BLUE("半透明蓝", 0x800000FF.toInt()),
    RED("半透明红", 0x80FF0000.toInt()),
    GREEN("半透明绿", 0x8000FF00.toInt()),
    YELLOW("半透明黄", 0x80FFFF00.toInt()),
    CUSTOM("自定义", 0x80000000.toInt())
}
