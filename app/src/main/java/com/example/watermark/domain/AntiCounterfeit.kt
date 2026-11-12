package com.example.watermark.domain

import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 成片防伪码。
 *
 * 原理（也是今日水印相机类「防伪 XXXX」的做法）：
 * 拍摄时间、经纬度、应用标识一起做 HMAC-SHA256，把摘要按自定义字符表压成 12 位短码。
 * 同样的输入永远得到同样的码，改动任何一个字段（时间、坐标）码就完全不同；
 * 想反向伪造一条「码正确」的记录，没有密钥算不出来。
 *
 * 说明：这里是本地可校验码，适合「照片与拍摄信息绑定、可被客服/后台复算」的场景。
 * 若要对外承诺「任何人可在线查真伪」，需要服务端签发并维护一张码表。
 */
object AntiCounterfeit {

    /** 去掉 0/O/1/I 等易混字符，便于人工抄写与电话核对。 */
    private const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

    /** 应用级密钥：换 App 或换密钥，旧码即失效。 */
    private const val SECRET = "friends-watermark-camera-2026-v1"

    /** 12 位防伪码，形如 H1XYCUTP29W3B（这里不带前缀 H，绘制时由 UI 加「防伪」二字）。 */
    fun code(
        timestampMs: Long,
        latitude: Double?,
        longitude: Double?,
        nickname: String = ""
    ): String {
        val payload = buildString {
            append(timestampMs / 1000L)
            append('|')
            append(latitude?.let { String.format(Locale.US, "%.6f", it) } ?: "-")
            append('|')
            append(longitude?.let { String.format(Locale.US, "%.6f", it) } ?: "-")
            append('|')
            append(nickname)
        }
        val digest = hmac(payload)
        val sb = StringBuilder(12)
        for (i in 0 until 12) {
            sb.append(ALPHABET[(digest[i].toInt() and 0xFF) % ALPHABET.length])
        }
        return sb.toString()
    }

    /** 校验：给一段拍摄信息，重新算码并比对。 */
    fun verify(
        code: String,
        timestampMs: Long,
        latitude: Double?,
        longitude: Double?,
        nickname: String = ""
    ): Boolean = code.uppercase(Locale.US) == code(timestampMs, latitude, longitude, nickname)

    /** 码格式自检：12 位、且只含字符表里的字符（验真页输入第一道关）。 */
    fun isWellFormed(code: String): Boolean {
        val normalized = code.trim().uppercase(Locale.US)
        if (normalized.length != 12) return false
        return normalized.all { ALPHABET.indexOf(it) >= 0 }
    }

    private fun hmac(payload: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(SECRET.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(payload.toByteArray(Charsets.UTF_8))
    }
}
