package ru.olimphelper.data

/** Превращает xlsx из Google-таблицы в [SheetTable], ориентируясь на названия колонок, а не на их порядок. */
object SheetSource {

    private val ID_RE = Regex("""/spreadsheets/d/([A-Za-z0-9_-]+)""")
    private val SEASON_RE = Regex("""(20\d{2})\s*/\s*\d{2}""")

    fun exportUrl(sheetUrl: String): String {
        val id = ID_RE.find(sheetUrl)?.groupValues?.get(1) ?: sheetUrl.trim()
        return "https://docs.google.com/spreadsheets/d/$id/export?format=xlsx"
    }

    fun parse(bytes: ByteArray): SheetTable {
        val sheets = XlsxReader.read(bytes)
        for (sheet in sheets) {
            val headerRow = (0 until minOf(sheet.rowCount, 10)).firstOrNull { r ->
                val texts = (0 until 30).map { sheet.text(r, it).lowercase() }
                texts.any { it.startsWith("олимпиада") } && texts.any { it.startsWith("этап") }
            } ?: continue
            return parseSheet(sheet, headerRow)
        }
        error("В таблице не нашлось листа с колонками «Олимпиада» и «Этап»")
    }

    private fun parseSheet(sheet: XlsxReader.Sheet, header: Int): SheetTable {
        val headers = (0 until 40).associateWith { sheet.text(header, it).trim() }.filterValues { it.isNotEmpty() }
        fun col(pred: (String) -> Boolean): Int? = headers.entries.firstOrNull { pred(it.value.lowercase()) }?.key

        val cOlymp = col { it.startsWith("олимпиада") } ?: error("Нет колонки «Олимпиада»")
        val cLevel = col { it.startsWith("уровень") }
        val cStage = col { it == "этап" || it.startsWith("этап ") || it.startsWith("этап") }
        val cDate = col { it.startsWith("дата") }
        val cDateStatus = col { it.startsWith("статус") }
        val cNote = col { it.startsWith("примечание") }
        val cSource = col { "ссылка" in it && "источник" in it } ?: col { "источник" in it && !it.startsWith("примечание") }
        val cReg = col { it.startsWith("регистрация") }
        val cRegUrl = col { "ссылка" in it && "регистрац" in it }
        val cChecked = col { it.startsWith("проверено") }

        val season = headers.values.firstNotNullOfOrNull { SEASON_RE.find(it)?.groupValues?.get(1)?.toInt() }

        fun text(r: Int, c: Int?) = c?.let { sheet.text(r, it).trim() }.orEmpty()
        fun link(r: Int, c: Int?): String? {
            c ?: return null
            sheet.links[r to c]?.let { return it }
            return sheet.text(r, c).trim().takeIf { it.startsWith("http") }
        }

        val rows = ArrayList<SheetRow>()
        val foot = ArrayList<String>()
        for (r in header + 1 until sheet.rowCount) {
            val name = text(r, cOlymp)
            if (name.isEmpty()) {
                (0 until 40).map { sheet.text(r, it).trim() }.filter { it.isNotEmpty() }.forEach { foot += it }
                continue
            }
            rows += SheetRow(
                index = r,
                olympiad = name,
                level = text(r, cLevel),
                stage = text(r, cStage),
                dateText = text(r, cDate),
                dateStatus = text(r, cDateStatus),
                note = text(r, cNote),
                sourceUrl = link(r, cSource),
                regStatus = text(r, cReg),
                regUrl = link(r, cRegUrl),
                checked = text(r, cChecked),
            )
        }
        val footnote = foot.filter { !it.equals("Перечень РСОШ", ignoreCase = true) }.joinToString("\n").ifEmpty { null }
        return SheetTable(rows, season, footnote)
    }
}
