package com.example.liyibook.util

import com.example.liyibook.model.GiftRecord
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 轻量 XLSX 读写器（纯标准库，无 Apache POI）。
 * - 读：解析 sharedStrings.xml + xl/worksheets/sheet*.xml，按表头名定位列
 *   （兼容 姓名/名字/来宾、礼金/金额/份子钱、地址/单位/住址、备注/说明）。
 * - 写：生成最小合法 xlsx（inlineStr 单元格），可被 Excel/WPS/小程序 SheetJS 读取。
 */
object XlsxUtil {

    data class Parsed(val records: List<GiftRecord>, val headers: List<String>)

    fun read(input: InputStream): Parsed {
        val shared = mutableListOf<String>()
        val sheetXml = StringBuilder()
        ZipInputStream(input).use { zis ->
            var entry: ZipEntry?
            while (zis.nextEntry.also { entry = it } != null) {
                val name = entry!!.name
                when {
                    name == "xl/sharedStrings.xml" ->
                        shared.addAll(parseSharedStrings(zis.readBytes()))
                    name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml") ->
                        sheetXml.append(String(zis.readBytes(), Charsets.UTF_8))
                }
                zis.closeEntry()
            }
        }
        if (sheetXml.isEmpty()) return Parsed(emptyList(), emptyList())
        return parseSheet(sheetXml.toString(), shared)
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        return try {
            val doc = safeDoc(bytes)
            val out = mutableListOf<String>()
            val siList = doc.getElementsByTagName("si")
            for (i in 0 until siList.length) {
                val sb = StringBuilder()
                collectText(siList.item(i), sb)
                out.add(sb.toString())
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    private val nsRegex = Regex("^<(\\w+:)?")
    private fun stripNs(tag: String) = tag.substringAfter(':')

    private fun parseSheet(xml: String, shared: List<String>): Parsed {
        val rows = mutableListOf<MutableMap<Int, String>>()
        // 用 regex 逐行解析，避免 XML 命名空间差异带来的兼容问题
        val rowRegex = Regex("<(?:\\w+:)?row[^>]*>(.*?)</(?:\\w+:)?row>", RegexOption.DOT_MATCHES_ALL)
        val cellRegex = Regex("<(?:\\w+:)?c\\b([^>]*)>(.*?)</(?:\\w+:)?c>|<(?:\\w+:)?c\\b([^>]*)/>", RegexOption.DOT_MATCHES_ALL)
        val refRegex = Regex("""r="([A-Z]+)(\d+)"""")
        val tRegex = Regex("""t="([^"]+)"""")
        val vRegex = Regex("<(?:\\w+:)?v[^>]*>(.*?)</(?:\\w+:)?v>", RegexOption.DOT_MATCHES_ALL)
        val tTextRegex = Regex("<(?:\\w+:)?t[^>]*>(.*?)</(?:\\w+:)?t>", RegexOption.DOT_MATCHES_ALL)

        for (rowMatch in rowRegex.findAll(xml)) {
            val body = rowMatch.groupValues[1]
            val cells = mutableMapOf<Int, String>()
            for (c in cellRegex.findAll(body)) {
                val attrs = if (c.groupValues[1].isNotEmpty()) c.groupValues[1] else c.groupValues[3]
                val inner = c.groupValues[2]
                val ref = refRegex.find(attrs) ?: continue
                val col = colToIndex(ref.groupValues[1])
                val type = tRegex.find(attrs)?.groupValues?.get(1) ?: "n"
                val value = when (type) {
                    "s" -> {
                        val idx = vRegex.find(inner)?.groupValues?.get(1)?.trim()?.toIntOrNull() ?: -1
                        shared.getOrNull(idx) ?: ""
                    }
                    "inlineStr" -> tTextRegex.findAll(inner).joinToString("") {
                        decodeXml(it.groupValues[1])
                    }
                    else -> vRegex.find(inner)?.groupValues?.get(1)?.let { decodeXml(it) } ?: ""
                }
                cells[col] = value
            }
            rows.add(cells)
        }
        if (rows.isEmpty()) return Parsed(emptyList(), emptyList())

        val headerRow = rows[0]
        val maxCol = (listOf(headerRow) + rows).maxOf { m -> m.keys.maxOrNull() ?: -1 }
        val headers = (0..maxCol).map { headerRow[it]?.trim() ?: "" }
        val idxOf: (List<String>) -> Int = { names -> headers.indexOfFirst { it in names } }
        val iName = idxOf(listOf("姓名", "名字", "来宾", "name", "Name"))
        val iAmount = idxOf(listOf("礼金", "金额", "份子钱", "amount", "礼金(元)"))
        val iAddr = idxOf(listOf("地址", "单位", "住址", "address"))
        val iRemark = idxOf(listOf("备注", "说明", "remark"))
        if (iName < 0) return Parsed(emptyList(), headers)

        val records = mutableListOf<GiftRecord>()
        for (r in rows.drop(1)) {
            val name = (r[iName] ?: "").trim()
            if (name.isBlank()) continue
            val amount = if (iAmount >= 0) parseAmount(r[iAmount] ?: "") else 0.0
            records.add(
                GiftRecord(
                    name = name,
                    amount = amount,
                    address = if (iAddr >= 0) (r[iAddr] ?: "").trim() else "",
                    remark = if (iRemark >= 0) (r[iRemark] ?: "").trim() else ""
                )
            )
        }
        return Parsed(records, headers)
    }

    private fun parseAmount(raw: String): Double =
        raw.replace(Regex("[,，¥￥\\s元]"), "").toDoubleOrNull() ?: 0.0

    private fun colToIndex(letters: String): Int {
        var n = 0
        for (ch in letters) n = n * 26 + (ch - 'A' + 1)
        return n - 1
    }

    private fun indexToCol(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.insert(0, ('A'.code + rem).toChar())
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    /** 导出：把记录写成标准 xlsx 字节。 */
    fun write(records: List<GiftRecord>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            put(zos, "[Content_Types].xml", CONTENT_TYPES)
            put(zos, "_rels/.rels", ROOT_RELS)
            put(zos, "xl/workbook.xml", WORKBOOK)
            put(zos, "xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            put(zos, "xl/styles.xml", STYLES)
            put(zos, "xl/worksheets/sheet1.xml", buildSheetXml(records))
        }
        return baos.toByteArray()
    }

    private fun put(zos: ZipOutputStream, path: String, content: String) {
        zos.putNextEntry(ZipEntry(path))
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun buildSheetXml(records: List<GiftRecord>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("""<cols><col min="1" max="1" width="14" customWidth="1"/>""")
        sb.append("""<col min="2" max="2" width="12" customWidth="1"/>""")
        sb.append("""<col min="3" max="3" width="28" customWidth="1"/>""")
        sb.append("""<col min="4" max="4" width="32" customWidth="1"/></cols>""")
        sb.append("<sheetData>")
        sb.append(rowXml(1, listOf("姓名", "礼金", "地址", "备注")))
        records.forEachIndexed { i, r ->
            sb.append(
                rowXml(
                    i + 2,
                    listOf(r.name, r.amount.toString(), r.address, r.remark),
                    numericCol = 1
                )
            )
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun rowXml(row: Int, values: List<String>, numericCol: Int = -1): String {
        val sb = StringBuilder("<row r=\"$row\">")
        values.forEachIndexed { col, v ->
            val ref = "${indexToCol(col)}$row"
            if (col == numericCol) {
                val num = v.toDoubleOrNull() ?: 0.0
                sb.append("""<c r="$ref"><v>$num</v></c>""")
            } else {
                sb.append("""<c r="$ref" t="inlineStr"><is><t xml:space="preserve">${encodeXml(v)}</t></is></c>""")
            }
        }
        sb.append("</row>")
        return sb.toString()
    }

    private fun safeDoc(bytes: ByteArray) =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        }.newDocumentBuilder().parse(java.io.ByteArrayInputStream(bytes))

    private fun collectText(node: org.w3c.dom.Node, sb: StringBuilder) {
        if (node.nodeType == org.w3c.dom.Node.TEXT_NODE || node.nodeType == org.w3c.dom.Node.CDATA_SECTION_NODE) {
            sb.append(node.nodeValue ?: "")
            return
        }
        val children = node.childNodes ?: return
        for (i in 0 until children.length) collectText(children.item(i), sb)
    }

    private fun decodeXml(s: String) = s
        .replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'")
        .replace("&amp;", "&")

    private fun encodeXml(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private const val WORKBOOK = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="礼金记录" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""

    private const val WORKBOOK_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>
<fills count="1"><fill><patternFill patternType="none"/></fill></fills>
<borders count="1"><border/></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/></cellXfs>
</styleSheet>"""
}
