package com.example.watermark.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan
import kotlin.math.PI

/**
 * 成片右上角的小地图缩略图。
 *
 * 用 OpenStreetMap 栅格瓦片在本地拼一张 256×256，按经纬度取中心，不需要任何地图 Key。
 * 全部走磁盘缓存：同一个位置只下载一次，断网时直接返回缓存，失败返回 null（水印照常输出）。
 */
object MapThumbnail {

    private const val ZOOM = 15
    private const val TILE = 256
    private const val OUT = 256
    private const val CONNECT_TIMEOUT = 1500
    private const val READ_TIMEOUT = 1500

    /** 同步加载，调用方必须在 IO 线程。 */
    fun load(context: Context, latitude: Double, longitude: Double): Bitmap? {
        // 高德瓦片只覆盖中国大陆；境外坐标直接不画，避免出现一张全白方块
        if (!inChina(latitude, longitude)) return null
        val key = String.format(Locale.US, "%.3f_%.3f", latitude, longitude)
        val cacheFile = File(File(context.cacheDir, "map_thumb"), "$key.png")
        if (cacheFile.exists()) {
            val cached = BitmapFactory.decodeFile(cacheFile.absolutePath)
            if (cached != null && !isBlank(cached)) return cached
            runCatching { cacheFile.delete() }
        }
        val built = try {
            build(context, latitude, longitude)
        } catch (_: Exception) {
            null
        } ?: return null
        runCatching {
            cacheFile.parentFile?.mkdirs()
            FileOutputStream(cacheFile).use { built.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        return built
    }

    private fun inChina(latitude: Double, longitude: Double): Boolean =
        longitude in 73.0..135.5 && latitude in 3.0..54.0

    /** 瓦片没覆盖到的区域会拼出一张接近纯白的图，按采样亮度判掉。 */
    private fun isBlank(bmp: Bitmap): Boolean {
        var bright = 0
        var total = 0
        val stepX = (bmp.width / 12).coerceAtLeast(1)
        val stepY = (bmp.height / 12).coerceAtLeast(1)
        var y = 0
        while (y < bmp.height) {
            var x = 0
            while (x < bmp.width) {
                val c = bmp.getPixel(x, y)
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                if (r > 232 && g > 232 && b > 232) bright++
                total++
                x += stepX
            }
            y += stepY
        }
        return total > 0 && bright.toFloat() / total > 0.94f
    }

    private fun build(context: Context, latitude: Double, longitude: Double): Bitmap? {
        // 高德瓦片是 GCJ-02，先把 GPS 的 WGS-84 转过去，缩略图上的标记点才落在真实道路附近
        val (lat, lng) = wgs84ToGcj02(latitude, longitude)
        val n = 2.0.pow(ZOOM)
        val px = (lng + 180.0) / 360.0 * n * TILE
        val latRad = Math.toRadians(lat)
        val py = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n * TILE

        val originX = px - OUT / 2.0
        val originY = py - OUT / 2.0
        val minTileX = floor(originX / TILE).toInt()
        val maxTileX = floor((originX + OUT - 1) / TILE).toInt()
        val minTileY = floor(originY / TILE).toInt()
        val maxTileY = floor((originY + OUT - 1) / TILE).toInt()

        val out = Bitmap.createBitmap(OUT, OUT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        var drewAny = false
        for (tx in minTileX..maxTileX) {
            for (ty in minTileY..maxTileY) {
                val tile = fetchTile(context, tx, ty) ?: continue
                drewAny = true
                canvas.drawBitmap(
                    tile,
                    (tx * TILE - originX).toFloat(),
                    (ty * TILE - originY).toFloat(),
                    null
                )
            }
        }
        return if (drewAny && !isBlank(out)) out else null
    }

    private fun fetchTile(context: Context, x: Int, y: Int): Bitmap? {
        val dir = File(context.cacheDir, "map_tiles")
        val file = File(dir, "z${ZOOM}_${x}_$y.png")
        if (file.exists()) {
            BitmapFactory.decodeFile(file.absolutePath)?.let { return it }
        }
        val conn = try {
            // 高德栅格瓦片（style=7 标准路网）：国内直连、免 Key；OSM 在国内基本拿不到。
            val url = "https://webrd01.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=7&x=$x&y=$y&z=$ZOOM"
            (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                setRequestProperty("User-Agent", "FriendsWatermarkCamera/1.0 (Android)")
            }
        } catch (_: Exception) {
            return null
        }
        return try {
            if (conn.responseCode != 200) {
                android.util.Log.w("MapThumbnail", "tile $x/$y http ${conn.responseCode}")
                return null
            }
            val bmp = conn.inputStream.use { BitmapFactory.decodeStream(it) } ?: return null
            runCatching {
                dir.mkdirs()
                FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            bmp
        } catch (e: Exception) {
            android.util.Log.w("MapThumbnail", "tile $x/$y failed: ${e.message}")
            null
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    private const val A = 6378245.0
    private const val EE = 0.00669342162296594323

    /** WGS-84（GPS）→ GCJ-02（高德/腾讯瓦片坐标系）。 */
    private fun wgs84ToGcj02(lat: Double, lng: Double): Pair<Double, Double> {
        if (lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271) return lat to lng
        var dLat = transformLat(lng - 105.0, lat - 35.0)
        var dLng = transformLng(lng - 105.0, lat - 35.0)
        val radLat = lat / 180.0 * PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = kotlin.math.sqrt(magic)
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
        dLng = (dLng * 180.0) / (A / sqrtMagic * cos(radLat) * PI)
        return (lat + dLat) to (lng + dLng)
    }

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * kotlin.math.sqrt(kotlin.math.abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * PI) + 320 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLng(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * kotlin.math.sqrt(kotlin.math.abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return ret
    }
}
