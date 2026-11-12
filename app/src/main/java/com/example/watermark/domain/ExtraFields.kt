package com.example.watermark.domain

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.telephony.TelephonyManager
import com.example.watermark.data.WatermarkSettings
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 附加信息字段（对齐市面水印相机）：
 * 天气／温度／湿度／风速／海拔／方位角／农历／节日／星座／倒计时／电量／网络／自定义备注。
 *
 * 所有字段先在这里拼成一行「·」分隔的附加文本，再交给 WatermarkEngine 绘制；
 * Compose 预览与 Canvas 导出拿到的是同一个字符串，避免两侧不一致。
 */
object ExtraFields {

    data class Weather(
        val text: String? = null,
        val temperature: Int? = null,
        val humidity: Int? = null,
        val wind: String? = null
    )

    // ---------------- 农历 / 节日 / 星座 / 倒计时 ----------------

    private const val LUNAR_MONTHS = "正,二,三,四,五,六,七,八,九,十,冬,腊"
    private const val LUNAR_DAYS = "初一,初二,初三,初四,初五,初六,初七,初八,初九,初十,十一,十二,十三,十四,十五,十六,十七,十八,十九,二十,廿一,廿二,廿三,廿四,廿五,廿六,廿七,廿八,廿九,三十"

    private fun lunar(ts: Long): Triple<Int, Int, Boolean>? = try {
        val cc = android.icu.util.ChineseCalendar()
        cc.timeInMillis = ts
        val month = cc.get(android.icu.util.ChineseCalendar.MONTH) + 1
        val day = cc.get(android.icu.util.ChineseCalendar.DAY_OF_MONTH)
        val leap = cc.get(android.icu.util.ChineseCalendar.IS_LEAP_MONTH) == 1
        Triple(month, day, leap)
    } catch (e: Throwable) {
        null
    }

    /** 农历十月初三 ／ 农历闰六月初一 */
    fun lunarText(ts: Long): String? {
        val (m, d, leap) = lunar(ts) ?: return null
        val monthName = LUNAR_MONTHS.split(",").getOrNull(m - 1) ?: return null
        val dayName = LUNAR_DAYS.split(",").getOrNull(d - 1) ?: return null
        return "农历${if (leap) "闰" else ""}${monthName}月$dayName"
    }

    private val SOLAR_FESTIVALS = mapOf(
        "01-01" to "元旦",
        "02-14" to "情人节",
        "03-08" to "妇女节",
        "05-01" to "劳动节",
        "05-04" to "青年节",
        "06-01" to "儿童节",
        "07-01" to "建党节",
        "08-01" to "建军节",
        "09-10" to "教师节",
        "10-01" to "国庆节",
        "12-24" to "平安夜",
        "12-25" to "圣诞节"
    )

    private val LUNAR_FESTIVALS = mapOf(
        "1-1" to "春节",
        "1-15" to "元宵节",
        "2-2" to "龙抬头",
        "5-5" to "端午节",
        "7-7" to "七夕",
        "7-15" to "中元节",
        "8-15" to "中秋节",
        "9-9" to "重阳节",
        "12-8" to "腊八节"
    )

    fun festivalText(ts: Long): String? {
        val cal = Calendar.getInstance().apply { timeInMillis = ts }
        val solarKey = String.format(
            Locale.US, "%02d-%02d",
            cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
        )
        SOLAR_FESTIVALS[solarKey]?.let { return it }
        val (m, d, leap) = lunar(ts) ?: return null
        if (leap) return null
        // 除夕：腊月最后一天（次日为正月）
        val next = Calendar.getInstance().apply { timeInMillis = ts + 86_400_000L }
        val nextLunar = lunar(next.timeInMillis)
        if (m == 12 && nextLunar != null && nextLunar.first == 1) return "除夕"
        LUNAR_FESTIVALS["$m-$d"]?.let { return it }
        return null
    }

    fun constellationText(ts: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = ts }
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        val idx = when {
            (m == 1 && d >= 20) || (m == 2 && d <= 18) -> 0   // 水瓶
            (m == 2 && d >= 19) || (m == 3 && d <= 20) -> 1   // 双鱼
            (m == 3 && d >= 21) || (m == 4 && d <= 19) -> 2   // 白羊
            (m == 4 && d >= 20) || (m == 5 && d <= 20) -> 3   // 金牛
            (m == 5 && d >= 21) || (m == 6 && d <= 21) -> 4   // 双子
            (m == 6 && d >= 22) || (m == 7 && d <= 22) -> 5   // 巨蟹
            (m == 7 && d >= 23) || (m == 8 && d <= 22) -> 6   // 狮子
            (m == 8 && d >= 23) || (m == 9 && d <= 22) -> 7   // 处女
            (m == 9 && d >= 23) || (m == 10 && d <= 23) -> 8  // 天秤
            (m == 10 && d >= 24) || (m == 11 && d <= 22) -> 9 // 天蝎
            (m == 11 && d >= 23) || (m == 12 && d <= 21) -> 10 // 射手
            else -> 11                                        // 摩羯
        }
        return listOf("水瓶座", "双鱼座", "白羊座", "金牛座", "双子座", "巨蟹座",
            "狮子座", "处女座", "天秤座", "天蝎座", "射手座", "摩羯座")[idx]
    }

    /** 距离目标日的倒计时：距元旦 12 天 ／ 已过元旦 3 天 */
    fun countdownText(ts: Long, target: String, label: String): String? {
        val pattern = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val targetMs = try {
            pattern.parse(target)?.time ?: return null
        } catch (e: Exception) {
            return null
        }
        val startOfToday = Calendar.getInstance().apply {
            timeInMillis = ts
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val days = ((targetMs - startOfToday) / 86_400_000L).toInt()
        val name = label.ifBlank { "目标日" }
        return when {
            days > 0 -> "距$name $days 天"
            days == 0 -> "今天就是$name"
            else -> "$name 已过 ${-days} 天"
        }
    }

    // ---------------- 设备信息 ----------------

    fun batteryText(context: Context): String? {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val level = try {
            bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        } catch (e: Exception) {
            -1
        }
        return if (level in 1..100) "电量 $level%" else null
    }

    fun networkText(context: Context): String? {
        // 权限缺失或厂商 ROM 限制时返回 null，绝不能把异常抛到 Compose 组合里
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return null
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "离线"
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "Wi-Fi"
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "有线网络"
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val type = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) tm?.dataNetworkType ?: 0 else 0
            } catch (e: Exception) {
                0
            }
            return when (type) {
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                TelephonyManager.NETWORK_TYPE_LTE -> "4G"
                TelephonyManager.NETWORK_TYPE_HSPAP,
                TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_HSUPA,
                TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                TelephonyManager.NETWORK_TYPE_GPRS,
                TelephonyManager.NETWORK_TYPE_EDGE,
                TelephonyManager.NETWORK_TYPE_CDMA -> "2G"
                else -> "移动网络"
            }
        }
        return "离线"
        } catch (e: Exception) {
            null
        }
    }

    fun altitudeText(location: Location?): String? {
        val a = Barometer.heightMeters?.toDouble()
        val fromLoc = location?.takeIf { it.hasAltitude() }?.altitude
        val value = a ?: fromLoc ?: return null
        return "海拔 ${value.toInt()} 米"
    }

    fun bearingText(): String? {
        val azimuth = Bearing.azimuth ?: return null
        val dirs = arrayOf("北", "东北", "东", "东南", "南", "西南", "西", "西北")
        val idx = (((azimuth / 45f) + 0.5f).toInt() + 8) % 8
        return "方位角 ${azimuth.toInt()}° ${dirs[idx]}"
    }

    // ---------------- 汇总 ----------------

    fun compose(
        context: Context?,
        settings: WatermarkSettings,
        location: Location? = null,
        weather: Weather? = null,
        timestampMs: Long = System.currentTimeMillis()
    ): String? {
        val parts = mutableListOf<String>()
        if (settings.showWeather) weather?.text?.let { parts += it }
        if (settings.showTemperature) weather?.temperature?.let { parts += "${it}℃" }
        if (settings.showHumidity) weather?.humidity?.let { parts += "湿度 $it%" }
        if (settings.showWind) weather?.wind?.let { parts += "风速 $it" }
        if (settings.showAltitude) altitudeText(location)?.let { parts += it }
        if (settings.showBearing) bearingText()?.let { parts += it }
        if (settings.showLunar) lunarText(timestampMs)?.let { parts += it }
        if (settings.showFestival) festivalText(timestampMs)?.let { parts += it }
        if (settings.showConstellation) parts += constellationText(timestampMs)
        if (settings.showCountdown) {
            countdownText(timestampMs, settings.countdownTarget, settings.countdownLabel)?.let { parts += it }
        }
        if (settings.showBattery) context?.let { batteryText(it)?.let { t -> parts += t } }
        if (settings.showNetwork) context?.let { networkText(it)?.let { t -> parts += t } }
        if (settings.extraNote.isNotBlank()) parts += settings.extraNote.trim()
        return if (parts.isEmpty()) null else parts.joinToString(" · ")
    }
}

/** 方位角（罗盘）：注册一次，持续取最新读数。 */
object Bearing : SensorEventListener {
    @Volatile
    var azimuth: Float? = null
        private set
    private var registered = false

    fun register(context: Context) {
        if (registered) return
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: return
        sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        registered = true
    }

    override fun onSensorChanged(event: SensorEvent) {
        val r = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(r, event.values)
        val o = FloatArray(3)
        SensorManager.getOrientation(r, o)
        var deg = Math.toDegrees(o[0].toDouble()).toFloat()
        if (deg < 0f) deg += 360f
        azimuth = deg
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

/** 海拔：气压计换算（无气压计返回 null）。 */
object Barometer : SensorEventListener {
    @Volatile
    var heightMeters: Float? = null
        private set
    private var registered = false

    fun register(context: Context) {
        if (registered) return
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_PRESSURE) ?: return
        sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        registered = true
    }

    override fun onSensorChanged(event: SensorEvent) {
        heightMeters = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, event.values[0])
    }

    override fun onAccuracyChanged(sensor: Sensor? , accuracy: Int) = Unit
}

/** 天气：open-meteo 免费接口，10 分钟缓存；失败静默降级。 */
object WeatherProvider {
    private var cachedAt = 0L
    private var cached: ExtraFields.Weather? = null
    private var cacheKey = ""

    private val CODES = mapOf(
        0 to "晴", 1 to "多云", 2 to "多云", 3 to "阴",
        45 to "雾", 48 to "雾凇",
        51 to "小雨", 53 to "小雨", 55 to "中雨",
        61 to "小雨", 63 to "中雨", 65 to "大雨",
        71 to "小雪", 73 to "中雪", 75 to "大雪",
        80 to "阵雨", 81 to "阵雨", 82 to "强阵雨",
        95 to "雷阵雨", 96 to "雷阵雨", 99 to "雷阵雨"
    )

    fun fetch(latitude: Double?, longitude: Double?): ExtraFields.Weather? {
        if (latitude == null || longitude == null) return null
        val key = String.format(Locale.US, "%.2f,%.2f", latitude, longitude)
        val now = System.currentTimeMillis()
        cached?.let { if (key == cacheKey && now - cachedAt < 600_000L) return it }
        return try {
            val url = URL(
                "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude" +
                    "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m" +
                    "&windspeed_unit=ms&timezone=auto"
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 2500
                readTimeout = 2500
                requestMethod = "GET"
            }
            val body = conn.inputStream.bufferedReader().use(BufferedReader::readText)
            conn.disconnect()
            val temp = Regex("\"temperature_2m\":(-?[0-9.]+)").find(body)?.groupValues?.get(1)?.toDoubleOrNull()
            val hum = Regex("\"relative_humidity_2m\":([0-9.]+)").find(body)?.groupValues?.get(1)?.toDoubleOrNull()
            val code = Regex("\"weather_code\":([0-9]+)").find(body)?.groupValues?.get(1)?.toIntOrNull()
            val wind = Regex("\"wind_speed_10m\":([0-9.]+)").find(body)?.groupValues?.get(1)?.toDoubleOrNull()
            val result = ExtraFields.Weather(
                text = code?.let { CODES[it] ?: "多云" },
                temperature = temp?.toInt(),
                humidity = hum?.toInt(),
                wind = wind?.let { String.format(Locale.US, "%.1f m/s", it) }
            )
            cached = result
            cachedAt = now
            cacheKey = key
            result
        } catch (e: Throwable) {
            null
        }
    }
}
