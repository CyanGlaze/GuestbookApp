package com.example.liyibook.util

import android.icu.text.Transliterator
import java.text.Normalizer

/**
 * 拼音匹配：移植原小程序 pinyin-match.js 的能力。
 * 全拼/简拼借助 Android 内置 ICU Transliterator（Han-Latin）生成，
 * 无需打包 28KB 的拼音字典；多音字姓氏沿用原项目的 SURNAME_MAP 兜底。
 */
object PinyinUtil {

    data class Segment(val text: String, val hit: Boolean)

    // 姓氏首字母映射（多音字按姓氏读音优先，常用姓氏全覆盖）—— 与原项目 detail.js 一致
    private val SURNAME_MAP = mapOf(
        "单" to "S", "仇" to "Q", "解" to "X", "查" to "Z", "乐" to "Y",
        "区" to "O", "朴" to "P", "尉" to "Y", "盖" to "G", "翟" to "Z", "牟" to "M", "员" to "Y",
        "覃" to "Q", "秘" to "B", "华" to "H", "燕" to "Y", "应" to "Y", "纪" to "J", "那" to "N",
        "折" to "S", "繁" to "P", "句" to "G", "相" to "X", "宁" to "N", "冼" to "X",
        "谌" to "S", "种" to "C", "藏" to "Z", "阚" to "K", "都" to "D", "合" to "H", "红" to "H",
        "安" to "A", "白" to "B", "包" to "B", "鲍" to "B", "毕" to "B", "边" to "B", "卞" to "B", "伯" to "B",
        "蔡" to "C", "曹" to "C", "岑" to "C", "柴" to "C", "常" to "C", "晁" to "C", "陈" to "C", "成" to "C",
        "程" to "C", "池" to "C", "褚" to "C", "崔" to "C", "戴" to "D", "邓" to "D", "丁" to "D", "董" to "D",
        "杜" to "D", "段" to "D", "敦" to "D", "樊" to "F", "范" to "F", "方" to "F", "房" to "F", "费" to "F",
        "冯" to "F", "符" to "F", "傅" to "F", "甘" to "G", "高" to "G", "葛" to "G", "龚" to "G", "巩" to "G",
        "古" to "G", "顾" to "G", "关" to "G", "管" to "G", "桂" to "G", "郭" to "G", "韩" to "H", "杭" to "H",
        "郝" to "H", "何" to "H", "贺" to "H", "洪" to "H", "侯" to "H", "胡" to "H", "花" to "H", "黄" to "H",
        "霍" to "H", "姬" to "J", "吉" to "J", "季" to "J", "贾" to "J", "简" to "J", "江" to "J",
        "姜" to "J", "蒋" to "J", "金" to "J", "荆" to "J", "景" to "J", "康" to "C", "柯" to "K", "孔" to "K",
        "寇" to "K", "匡" to "K", "赖" to "L", "蓝" to "L", "郎" to "L", "雷" to "L", "黎" to "L", "李" to "L",
        "连" to "L", "廉" to "L", "梁" to "L", "廖" to "L", "林" to "L", "凌" to "L", "刘" to "L", "柳" to "L",
        "龙" to "L", "楼" to "L", "卢" to "L", "鲁" to "L", "陆" to "L", "路" to "L", "吕" to "L", "罗" to "R",
        "骆" to "L", "马" to "M", "麦" to "M", "梅" to "M", "孟" to "M", "闵" to "M", "莫" to "M",
        "穆" to "M", "倪" to "N", "聂" to "N", "欧" to "O", "欧阳" to "O", "潘" to "P", "庞" to "P", "彭" to "P",
        "皮" to "P", "蒲" to "P", "浦" to "P", "戚" to "Q", "祁" to "Q", "齐" to "Q", "钱" to "Q", "秦" to "Q",
        "邱" to "Q", "裘" to "Q", "屈" to "Q", "全" to "Q", "任" to "R", "阮" to "R", "芮" to "R", "商" to "S",
        "尚" to "S", "邵" to "S", "佘" to "S", "沈" to "S", "施" to "S", "石" to "S", "时" to "S", "史" to "S",
        "司徒" to "S", "苏" to "S", "孙" to "S", "隋" to "S", "汤" to "T", "唐" to "T", "陶" to "T", "田" to "T",
        "童" to "T", "涂" to "T", "万" to "W", "汪" to "W", "王" to "W", "韦" to "W", "卫" to "W", "魏" to "W",
        "温" to "W", "文" to "W", "翁" to "W", "吴" to "W", "伍" to "W", "武" to "W", "奚" to "X", "夏" to "X",
        "项" to "X", "萧" to "X", "肖" to "X", "谢" to "X", "邢" to "X", "徐" to "X", "许" to "X", "薛" to "X",
        "严" to "Y", "颜" to "Y", "杨" to "Y", "姚" to "Y", "叶" to "Y", "易" to "Y", "殷" to "Y", "尹" to "Y",
        "雍" to "Y", "尤" to "Y", "于" to "Y", "余" to "Y", "俞" to "Y", "虞" to "Y", "元" to "Y", "袁" to "Y",
        "岳" to "Y", "云" to "Y", "曾" to "Z", "詹" to "Z", "张" to "Z", "赵" to "Z", "郑" to "Z", "钟" to "Z",
        "仲" to "Z", "周" to "Z", "朱" to "Z", "祝" to "Z", "庄" to "Z", "卓" to "Z", "邹" to "Z", "祖" to "Z"
    )

    private val transliterator: Transliterator? by lazy {
        runCatching { Transliterator.getInstance("Han-Latin") }.getOrNull()
    }

    private val letterCache = mutableMapOf<Char, String>()

    /** 单字全拼（无声调、仅 a-z）。 */
    private fun pinyinOfChar(c: Char): String {
        letterCache[c]?.let { return it }
        val raw = transliterator?.transliterate(c.toString()) ?: ""
        val normalized = Normalizer.normalize(raw, Normalizer.Form.NFD)
        val py = normalized.replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("[^a-z]"), "")
        letterCache[c] = py
        return py
    }

    /** 姓名首字母分组。姓氏多音字优先，其余按拼音首字母。 */
    fun getFirstLetter(name: String?): String {
        if (name.isNullOrBlank()) return "#"
        val first = name.trim()[0]
        if (first in 'a'..'z' || first in 'A'..'Z') return first.uppercaseChar().toString()
        val sur = SURNAME_MAP[first.toString()]
        if (sur != null) return sur
        val py = pinyinOfChar(first)
        return if (py.isNotEmpty()) py[0].uppercaseChar().toString() else "#"
    }

    private data class Mapped(val full: String, val initials: String,
                              val fullRange: IntArray, val initRange: IntArray)

    /** 构造 原文→拼音/简拼 的位置映射，用于把命中区间映射回字符下标。 */
    private fun mapOf(text: String): Mapped {
        val full = StringBuilder()
        val initials = StringBuilder()
        val fullRange = IntArray(text.length + 1)
        val initRange = IntArray(text.length + 1)
        for (i in text.indices) {
            val c = text[i]
            val py = when {
                c in 'a'..'z' || c in 'A'..'Z' -> c.lowercase().toString()
                c.isDigit() -> c.toString()
                else -> pinyinOfChar(c)
            }
            fullRange[i] = full.length
            initRange[i] = initials.length
            full.append(py)
            if (py.isNotEmpty()) initials.append(py[0])
        }
        fullRange[text.length] = full.length
        initRange[text.length] = initials.length
        return Mapped(full.toString(), initials.toString(), fullRange, initRange)
    }

    /** 返回命中区间 [start, end)（字符下标），未命中返回 null。 */
    fun matchRange(text: String, keyRaw: String): IntRange? {
        if (text.isEmpty()) return null
        val key = keyRaw.lowercase().trim()
        if (key.isEmpty()) return null

        // 1) 直接子串
        val direct = text.lowercase().indexOf(key)
        if (direct >= 0) return direct until (direct + key.length)

        val m = mapOf(text)
        // 2) 全拼
        val f = m.full.indexOf(key)
        if (f >= 0) return rangeFromOffsets(m.fullRange, f, f + key.length, text.length)
        // 3) 简拼（首字母）
        val s = m.initials.indexOf(key)
        if (s >= 0) return rangeFromOffsets(m.initRange, s, s + key.length, text.length)

        // 4) 分段匹配：中文段直接定位，拉丁段用拼音定位，顺序推进
        val parts = key.split(Regex("[^a-z0-9\\u4e00-\\u9fa5]+")).filter { it.isNotEmpty() }
        if (parts.size > 1) {
            var cursor = 0
            var start = -1
            var end = -1
            var ok = true
            for (part in parts) {
                val hit = matchRange(text.substring(cursor), part) ?: run { ok = false; break }
                val absStart = cursor + hit.first
                val absEnd = cursor + hit.last + 1
                if (start < 0) start = absStart
                end = absEnd
                cursor = absEnd
            }
            if (ok && start >= 0) return start until end
        }
        return null
    }

    private fun rangeFromOffsets(offsets: IntArray, from: Int, to: Int, textLen: Int): IntRange {
        var start = 0
        while (start < textLen && offsets[start + 1] <= from) start++
        var end = start
        while (end < textLen && offsets[end + 1] < to) end++
        return start until (end + 1).coerceAtMost(textLen)
    }

    fun smartMatch(target: String, key: String): Boolean =
        key.isBlank() || matchRange(target, key) != null

    /** 高亮切片，供 Compose AnnotatedString 使用。 */
    fun highlight(text: String, key: String): List<Segment> {
        val range = matchRange(text, key)
        if (range == null || range.first >= text.length) return listOf(Segment(text, false))
        val out = mutableListOf<Segment>()
        if (range.first > 0) out.add(Segment(text.substring(0, range.first), false))
        out.add(Segment(text.substring(range.first, range.last + 1), true))
        if (range.last + 1 < text.length) out.add(Segment(text.substring(range.last + 1), false))
        return out
    }
}
