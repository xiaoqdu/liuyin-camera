package com.example.watermark.domain

import com.example.watermark.data.WatermarkSettings
import com.example.watermark.data.WatermarkTemplatePreset
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * 行业模板的专属版式：每个行业模板配一组自己的字段，把该行业真正需要写在水印上的
 * 业务信息（检查项勾选、物资金额、验收结论、工时结算等）结构化落到成片上。
 *
 * 字段值统一存在 settings.industryData 里，编码为「key=value」按行拼接，
 * value 走 URL 编码，避免出现换行/等号把格式撑破。
 */
object IndustryLayout {

    enum class FieldType { TEXT, CHOICE, TOGGLE }

    /** 一个可编辑字段：TEXT 走输入框，CHOICE 走单选 chip，TOGGLE 是「正常/异常」勾选。 */
    data class Field(
        val key: String,
        val label: String,
        val type: FieldType = FieldType.TEXT,
        val placeholder: String = "",
        /** CHOICE 的候选值；第一个为默认 */
        val choices: List<String> = emptyList(),
        /** TOGGLE 的默认是否为「已勾选」 */
        val defaultChecked: Boolean = true
    )

    /** 渲染用的一行明细：label 为空表示没有前缀；check 非空表示这一行带勾选方框。 */
    data class DetailLine(
        val label: String?,
        val value: String,
        val check: Boolean? = null
    ) {
        /** 卡片排版/纯文本场景下的一行写法（勾选态用 ✓ / ✗ 表示）。 */
        fun asText(): String {
            val mark = when (check) {
                true -> " ✓"
                false -> " ✗"
                null -> ""
            }
            return when {
                label.isNullOrBlank() -> "$value$mark"
                else -> "$label：$value$mark"
            }
        }
    }

    // ===================== 各行业字段定义 =====================

    fun fieldsOf(preset: WatermarkTemplatePreset): List<Field> = when (preset) {
        WatermarkTemplatePreset.ATTENDANCE -> listOf(
            Field("name", "姓名", placeholder = "如：张三"),
            Field("no", "工号", placeholder = "如：A0231"),
            Field(
                "shift", "打卡类型", FieldType.CHOICE,
                choices = listOf("上班打卡", "下班打卡", "外勤打卡")
            )
        )

        WatermarkTemplatePreset.INSPECTION -> listOf(
            Field("point", "巡检点位", placeholder = "如：3 号配电房"),
            Field("item1", "设备运行", FieldType.TOGGLE),
            Field("item2", "安全防护", FieldType.TOGGLE),
            Field("item3", "环境卫生", FieldType.TOGGLE),
            Field("extra", "隐患说明", placeholder = "如：2 号柜门未锁")
        )

        WatermarkTemplatePreset.PURCHASE -> listOf(
            Field("item", "物资名称", placeholder = "如：镀锌管"),
            Field("spec", "规格型号", placeholder = "如：DN50"),
            Field("qty", "数量", placeholder = "如：120"),
            Field("price", "单价（元）", placeholder = "如：38.5")
        )

        WatermarkTemplatePreset.MATERIAL -> listOf(
            Field("item", "材料名称", placeholder = "如：C30 混凝土"),
            Field("spec", "规格/批号", placeholder = "如：P.O42.5"),
            Field("qty", "到场数量", placeholder = "如：36 m³"),
            Field(
                "result", "验收结论", FieldType.CHOICE,
                choices = listOf("合格", "不合格", "待复检")
            )
        )

        WatermarkTemplatePreset.LABOR -> listOf(
            Field("job", "工种", placeholder = "如：钢筋工"),
            Field("people", "出勤人数", placeholder = "如：8"),
            Field("hours", "人均工时", placeholder = "如：9"),
            Field("rate", "工时单价（元）", placeholder = "如：32")
        )

        WatermarkTemplatePreset.COMMERCE -> listOf(
            Field("item", "商品名称", placeholder = "如：纯棉四件套"),
            Field("price", "价格", placeholder = "如：199"),
            Field("promo", "卖点/优惠", placeholder = "如：第二件半价")
        )

        WatermarkTemplatePreset.TRAVEL -> listOf(
            Field("spot", "景点/地点", placeholder = "如：深圳湾公园"),
            Field("weatherNote", "天气备注", placeholder = "如：晴，微风"),
            Field("companion", "同行", placeholder = "如：一家三口")
        )

        else -> emptyList()
    }

    fun hasLayout(preset: WatermarkTemplatePreset): Boolean = fieldsOf(preset).isNotEmpty()

    /**
     * 这套设置实际该用哪种排版：行业模板有 3～5 行结构化明细，单行卡片塞不下（会被截断），
     * 所以只要当前是行业模板就统一走大字排版，保证预览和成片都完整。
     */
    fun effectiveLayout(settings: WatermarkSettings): com.example.watermark.data.WatermarkLayout =
        if (hasLayout(settings.watermarkTemplatePreset)) com.example.watermark.data.WatermarkLayout.STAMP
        else settings.watermarkLayout

    // ===================== 值存取 =====================

    fun decode(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return raw.split('\n').mapNotNull { line ->
            val idx = line.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            val key = line.substring(0, idx)
            val value = try {
                URLDecoder.decode(line.substring(idx + 1), "UTF-8")
            } catch (e: Exception) {
                line.substring(idx + 1)
            }
            key to value
        }.toMap()
    }

    fun encode(values: Map<String, String>): String = values.entries
        .filter { it.value.isNotBlank() }
        .joinToString("\n") { (k, v) ->
            k + "=" + try {
                URLEncoder.encode(v, "UTF-8")
            } catch (e: Exception) {
                v
            }
        }

    /** 读取某行业字段的当前值；TOGGLE 未设置时取默认勾选，CHOICE 未设置时取第一项。 */
    fun valuesOf(settings: WatermarkSettings, preset: WatermarkTemplatePreset): Map<String, String> {
        val stored = decode(settings.industryData)
        return fieldsOf(preset).associate { field ->
            val raw = stored[field.key]
            val fallback = when (field.type) {
                FieldType.TOGGLE -> if (field.defaultChecked) "1" else "0"
                FieldType.CHOICE -> field.choices.firstOrNull().orEmpty()
                FieldType.TEXT -> ""
            }
            field.key to (raw ?: fallback)
        }
    }

    // ===================== 生成明细行 =====================

    /**
     * 按当前行业模板生成水印明细行。空值字段不占行，避免成片出现「工号：」这种半截信息。
     * 数量 × 单价、人数 × 工时 这类会顺手算好合计，省得人肉对照。
     */
    fun details(settings: WatermarkSettings): List<DetailLine> {
        val preset = settings.watermarkTemplatePreset
        if (!hasLayout(preset)) return emptyList()
        val v = valuesOf(settings, preset)
        fun text(key: String): String = v[key].orEmpty().trim()

        return when (preset) {
            WatermarkTemplatePreset.ATTENDANCE -> buildList {
                if (text("name").isNotBlank()) add(DetailLine("姓名", text("name")))
                if (text("no").isNotBlank()) add(DetailLine("工号", text("no")))
                if (text("shift").isNotBlank()) add(DetailLine("类型", text("shift")))
            }

            WatermarkTemplatePreset.INSPECTION -> buildList {
                if (text("point").isNotBlank()) add(DetailLine("点位", text("point")))
                val items = listOf("item1" to "设备运行", "item2" to "安全防护", "item3" to "环境卫生")
                items.forEach { (key, label) ->
                    val ok = v[key] == "1"
                    add(DetailLine(label, if (ok) "正常" else "异常", check = ok))
                }
                val failCount = items.count { v[it.first] != "1" }
                add(
                    DetailLine(
                        "检查结论",
                        if (failCount == 0) "全部正常" else "异常 $failCount 项",
                        check = failCount == 0
                    )
                )
                if (text("extra").isNotBlank()) add(DetailLine("隐患说明", text("extra")))
            }

            WatermarkTemplatePreset.PURCHASE -> buildList {
                if (text("item").isNotBlank()) add(DetailLine("物资", text("item")))
                if (text("spec").isNotBlank()) add(DetailLine("规格", text("spec")))
                val qty = text("qty").toDoubleOrNull()
                val price = text("price").toDoubleOrNull()
                if (qty != null && price != null) {
                    add(DetailLine("数量", "${trimNum(qty)} × ${trimNum(price)} 元"))
                    add(DetailLine("合计", "${trimNum(qty * price)} 元"))
                } else {
                    if (text("qty").isNotBlank()) add(DetailLine("数量", text("qty")))
                    if (text("price").isNotBlank()) add(DetailLine("单价", text("price") + " 元"))
                }
            }

            WatermarkTemplatePreset.MATERIAL -> buildList {
                if (text("item").isNotBlank()) add(DetailLine("材料", text("item")))
                if (text("spec").isNotBlank()) add(DetailLine("规格", text("spec")))
                if (text("qty").isNotBlank()) add(DetailLine("数量", text("qty")))
                if (text("result").isNotBlank()) {
                    add(DetailLine("验收", text("result"), check = text("result") == "合格"))
                }
            }

            WatermarkTemplatePreset.LABOR -> buildList {
                if (text("job").isNotBlank()) add(DetailLine("工种", text("job")))
                val people = text("people").toDoubleOrNull()
                val hours = text("hours").toDoubleOrNull()
                val rate = text("rate").toDoubleOrNull()
                if (people != null && hours != null) {
                    add(DetailLine("出勤", "${trimNum(people)} 人 × ${trimNum(hours)} h"))
                    add(DetailLine("合计工时", "${trimNum(people * hours)} 人时"))
                } else {
                    if (text("people").isNotBlank()) add(DetailLine("出勤", text("people") + " 人"))
                    if (text("hours").isNotBlank()) add(DetailLine("人均工时", text("hours") + " h"))
                }
                if (people != null && hours != null && rate != null) {
                    add(DetailLine("人工费", "${trimNum(people * hours * rate)} 元"))
                } else if (text("rate").isNotBlank()) {
                    add(DetailLine("工时单价", text("rate") + " 元"))
                }
            }

            WatermarkTemplatePreset.COMMERCE -> buildList {
                if (text("item").isNotBlank()) add(DetailLine("商品", text("item")))
                if (text("price").isNotBlank()) add(DetailLine("价格", text("price")))
                if (text("promo").isNotBlank()) add(DetailLine(null, text("promo")))
            }

            WatermarkTemplatePreset.TRAVEL -> buildList {
                if (text("spot").isNotBlank()) add(DetailLine("地点", text("spot")))
                if (text("weatherNote").isNotBlank()) add(DetailLine("天气", text("weatherNote")))
                if (text("companion").isNotBlank()) add(DetailLine("同行", text("companion")))
            }

            else -> emptyList()
        }
    }

    private fun trimNum(d: Double): String {
        return if (d == d.toLong().toDouble()) d.toLong().toString()
        else String.format(java.util.Locale.US, "%.2f", d).trimEnd('0').trimEnd('.')
    }
}
