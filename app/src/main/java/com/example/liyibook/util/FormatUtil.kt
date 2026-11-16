package com.example.liyibook.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 对应小程序 excelSync.js / bookshelf.js 中的格式化与文件名逻辑。 */
object FormatUtil {
    private val stampSuffix = Regex("-\\d{8}([_ -]\\d{6})?$")

    /** 去掉扩展名与末尾时间戳，得到同一份 Excel 的识别主键。 */
    fun baseNameOf(name: String): String {
        var s = name.replace(Regex("\\.[^/.]+$"), "").trim()
        s = s.replace(stampSuffix, "").trim()
        return s
    }

    fun normalizeName(name: String) = baseNameOf(name).lowercase(Locale.ROOT)

    /** 书名去重比较；无 sourceName 的旧账本用 title 兜底。 */
    fun findSameBookIndex(
        books: List<com.example.liyibook.model.Book>,
        sourceName: String
    ): Int {
        val key = normalizeName(sourceName)
        if (key.isEmpty()) return -1
        var idx = books.indexOfFirst { normalizeName(it.sourceName) == key }
        if (idx >= 0) return idx
        return books.indexOfFirst { it.sourceName.isBlank() && normalizeName(it.title) == key }
    }

    fun initialFileName(sourceName: String) = "${baseNameOf(sourceName)}.xlsx"

    fun changedFileName(sourceName: String): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "${baseNameOf(sourceName)}-$stamp.xlsx"
    }

    fun nowStamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

    /** 日期统一中文显示。 */
    fun formatDateCN(value: String): String {
        if (value.isBlank()) return ""
        if (Regex("年\\d{1,2}月\\d{1,2}日").containsMatchIn(value)) return value
        val patterns = listOf(
            Regex("(\\d{4})[/-.](\\d{1,2})[/-.](\\d{1,2})"),
            Regex("(\\d{1,2})[/-.](\\d{1,2})[/-.](\\d{4})")
        )
        val hit = patterns.firstNotNullOfOrNull { it.find(value) }
        if (hit != null) {
            val g = hit.groupValues
            return if (hit.groups[1]?.value?.length == 4)
                "${g[1]}年${g[2].toInt()}月${g[3].toInt()}日"
            else
                "${g[3]}年${g[1].toInt()}月${g[2].toInt()}日"
        }
        return value
    }

    fun todayCN(): String =
        SimpleDateFormat("yyyy/M/d", Locale.getDefault()).format(Date())

    fun money(value: Double): String = String.format(Locale.getDefault(), "%.2f", value)

    /** 千分位整数（我的页累计礼金）。 */
    fun moneyThousands(value: Double): String =
        String.format(Locale.getDefault(), "%,d", Math.round(value))
}
