package ru.olimphelper.data

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

/**
 * Минимальное чтение .xlsx: текст ячеек и гиперссылки. Google отдаёт таблицу в xlsx вместе со ссылками,
 * которые в CSV теряются. SAX работает и на Android, и в JVM-тестах.
 */
object XlsxReader {

    class Sheet(val name: String, val cells: Map<Pair<Int, Int>, String>, val links: Map<Pair<Int, Int>, String>) {
        val rowCount: Int get() = (cells.keys.maxOfOrNull { it.first } ?: -1) + 1
        fun text(row: Int, col: Int): String = cells[row to col].orEmpty()
    }

    private const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    fun read(bytes: ByteArray): List<Sheet> {
        val files = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (!e.isDirectory) files[e.name.trimStart('/')] = zip.readBytes()
            }
        }
        require("xl/workbook.xml" in files) { "Это не xlsx-файл" }
        val shared = files["xl/sharedStrings.xml"]?.let(::sharedStrings).orEmpty()
        val wbRels = files["xl/_rels/workbook.xml.rels"]?.let(::rels).orEmpty()
        return workbookSheets(files.getValue("xl/workbook.xml")).mapNotNull { (name, rid) ->
            val target = wbRels[rid] ?: return@mapNotNull null
            val path = if (target.startsWith("/")) target.trimStart('/') else "xl/$target"
            val data = files[path] ?: return@mapNotNull null
            val relsPath = path.substringBeforeLast('/') + "/_rels/" + path.substringAfterLast('/') + ".rels"
            val sheetRels = files[relsPath]?.let(::rels).orEmpty()
            sheet(name, data, shared, sheetRels)
        }
    }

    private fun parse(bytes: ByteArray, handler: DefaultHandler) {
        val f = SAXParserFactory.newInstance()
        f.isNamespaceAware = true
        f.newSAXParser().parse(ByteArrayInputStream(bytes), handler)
    }

    private fun Attributes.byLocal(name: String): String? {
        for (i in 0 until length) if (getLocalName(i) == name || getQName(i) == name) return getValue(i)
        return null
    }

    private fun relId(a: Attributes): String? =
        a.getValue(REL_NS, "id") ?: (0 until a.length).firstOrNull { a.getQName(it).endsWith(":id") }?.let { a.getValue(it) }

    private fun workbookSheets(bytes: ByteArray): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        parse(bytes, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String, qName: String?, a: Attributes) {
                if (localName == "sheet") out += (a.byLocal("name").orEmpty()) to (relId(a).orEmpty())
            }
        })
        return out
    }

    private fun rels(bytes: ByteArray): Map<String, String> {
        val out = HashMap<String, String>()
        parse(bytes, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String, qName: String?, a: Attributes) {
                if (localName == "Relationship") {
                    val id = a.byLocal("Id")
                    val t = a.byLocal("Target")
                    if (id != null && t != null) out[id] = t
                }
            }
        })
        return out
    }

    private fun sharedStrings(bytes: ByteArray): List<String> {
        val out = ArrayList<String>()
        parse(bytes, object : DefaultHandler() {
            val sb = StringBuilder()
            var inT = false
            var inPhonetic = false
            override fun startElement(uri: String?, localName: String, qName: String?, a: Attributes) {
                when (localName) {
                    "si" -> sb.setLength(0)
                    "t" -> inT = true
                    "rPh" -> inPhonetic = true
                }
            }
            override fun endElement(uri: String?, localName: String, qName: String?) {
                when (localName) {
                    "si" -> out += sb.toString()
                    "t" -> inT = false
                    "rPh" -> inPhonetic = false
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inT && !inPhonetic) sb.append(ch, start, length)
            }
        })
        return out
    }

    private fun sheet(name: String, bytes: ByteArray, shared: List<String>, rels: Map<String, String>): Sheet {
        val cells = HashMap<Pair<Int, Int>, String>()
        val links = HashMap<Pair<Int, Int>, String>()
        parse(bytes, object : DefaultHandler() {
            var ref: Pair<Int, Int>? = null
            var type: String? = null
            val sb = StringBuilder()
            var collect = false
            override fun startElement(uri: String?, localName: String, qName: String?, a: Attributes) {
                when (localName) {
                    "c" -> { ref = a.byLocal("r")?.let(::cellRef); type = a.byLocal("t"); sb.setLength(0) }
                    "v", "t" -> if (ref != null) collect = true
                    "hyperlink" -> {
                        val at = a.byLocal("ref")?.substringBefore(':')?.let(::cellRef)
                        val target = relId(a)?.let { rels[it] }
                        if (at != null && target != null) links[at] = target
                    }
                }
            }
            override fun endElement(uri: String?, localName: String, qName: String?) {
                when (localName) {
                    "v", "t" -> collect = false
                    "c" -> {
                        val r = ref
                        if (r != null) {
                            val raw = sb.toString()
                            val v = if (type == "s") raw.trim().toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty() else raw
                            if (v.isNotEmpty()) cells[r] = v
                        }
                        ref = null
                    }
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (collect) sb.append(ch, start, length)
            }
        })
        return Sheet(name, cells, links)
    }

    /** «G12» → (11, 6), нумерация с нуля. */
    fun cellRef(s: String): Pair<Int, Int>? {
        var col = 0
        var i = 0
        while (i < s.length && s[i].isLetter()) {
            col = col * 26 + (s[i].uppercaseChar() - 'A' + 1)
            i++
        }
        val row = s.substring(i).toIntOrNull() ?: return null
        if (i == 0) return null
        return (row - 1) to (col - 1)
    }
}
