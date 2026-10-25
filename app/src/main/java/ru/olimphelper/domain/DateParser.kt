package ru.olimphelper.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * Один день или промежуток из ячейки с датой.
 * [start] = null, если известен только крайний срок («До 14.10.2026»).
 * approx-флаги ставятся, когда известен только месяц («Март 2027»).
 */
data class Span(
    val start: LocalDate?,
    val end: LocalDate,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val approxStart: Boolean = false,
    val approxEnd: Boolean = false,
    /** «с 30.12.2026»: известно только начало. */
    val openEnd: Boolean = false,
    /** «10–11 кл.», если в ячейке даты разбиты по классам. */
    val classes: String? = null,
) {
    val isSingleDay: Boolean get() = start == end && !openEnd && !approxStart && !approxEnd
    val isWholeMonth: Boolean
        get() = approxStart && approxEnd && start != null && YearMonth.from(start) == YearMonth.from(end)
}

data class ParsedDates(
    val spans: List<Span> = emptyList(),
    /** «… или …»: нужно выбрать один из дней. */
    val chooseOne: Boolean = false,
    /** «08/09.02»: источники расходятся, взята более ранняя дата. */
    val conflict: Boolean = false,
)

/**
 * Разбор дат в том виде, в каком их пишут в таблице:
 * «01.10.2026 – 12.01.2027», «23–25.10 и 05–07.12.2026», «14.11.2026, 14:00 или 19.11.2026, 11:00 МСК»,
 * «До 14.10.2026, 15:00 МСК», «Январь – 09.03.2027», «Февраль–март 2027»,
 * «19.12.2026 и 16.01.2027 (6–9); 14 и 28.02.2027 (10–11)», «Не опубликовано».
 * Где не хватает месяца или года, они берутся у соседней даты, а иначе по учебному году.
 */
object DateParser {

    private val CLASS_RE = Regex("""\(\s*(\d{1,2})\s*[–-]\s*(\d{1,2})\s*(?:кл[а-яё.]*)?\s*\)""")
    private const val DASHES = "–—−‒―-"

    fun parse(text: String, seasonStartYear: Int, grade: Int? = null): ParsedDates {
        val segments = normalize(text).split(';').map { seg ->
            val m = CLASS_RE.find(seg)
            if (m == null) seg to null
            else seg.removeRange(m.range) to (m.groupValues[1].toInt()..m.groupValues[2].toInt())
        }
        val kept = segments.filter { (_, cls) -> cls == null || grade == null || grade in cls }
        val spans = ArrayList<Span>()
        var chooseOne = false
        var conflict = false
        for ((seg, cls) in kept) {
            val (pieces, or) = splitPieces(tokenize(seg, keepWords = false))
            fill(pieces, seasonStartYear)
            val label = cls?.let { "${it.first}–${it.last} кл." }
            pieces.mapNotNullTo(spans) { toSpan(it)?.copy(classes = label) }
            chooseOne = chooseOne || or
            conflict = conflict || pieces.any { it.a.altDay != null || it.b?.altDay != null }
        }
        return ParsedDates(spans, chooseOne, conflict)
    }

    /**
     * Первая дата или промежуток в тексте (для примечаний: «Регистрация открыта 06.10–23.11.2026 до 23:59…»).
     * Ищет только числовые даты и возвращает null, если первая дата дальше [maxOffset] символов от начала.
     */
    fun firstSpan(text: String, seasonStartYear: Int, maxOffset: Int = Int.MAX_VALUE): Span? {
        val toks = tokenize(normalize(text), keepWords = true)
        val i0 = toks.indexOfFirst { it.k == K.DATE && !it.monthOnly }
        if (i0 < 0 || toks[i0].pos > maxOffset) return null
        val seq = ArrayList<Tok>()
        toks.getOrNull(i0 - 1)?.takeIf { it.k == K.TO || it.k == K.FROM }?.let { seq += it }
        var dates = 0
        var dash = false
        var i = i0
        loop@ while (i < toks.size) {
            val t = toks[i]
            val next = toks.getOrNull(i + 1)
            when {
                t.k == K.DATE && !t.monthOnly && (dates == 0 || (dash && dates == 1)) -> { seq += t; dates++ }
                t.k == K.TIME -> seq += t
                t.k == K.COMMA && next?.k == K.TIME -> {}
                t.k == K.TO && next?.k == K.TIME -> {}
                t.k == K.DASH && dates == 1 && !dash && next?.k == K.DATE && !next.monthOnly -> { seq += t; dash = true }
                else -> break@loop
            }
            i++
        }
        val p = piece(seq) ?: return null
        fill(listOf(p), seasonStartYear)
        return toSpan(p)
    }

    // --- токены ---

    private enum class K { DATE, TIME, YEAR, TO, FROM, OR, AND, DASH, COMMA, WORD }

    private class Tok(
        val k: K,
        val pos: Int,
        val day: Int? = null,
        val month: Int? = null,
        val year: Int? = null,
        val altDay: Int? = null,
        val monthOnly: Boolean = false,
        val time: LocalTime? = null,
        val word: String = "",
    )

    private fun normalize(s: String) = s.replace(' ', ' ').replace(' ', ' ')

    private fun tokenize(s: String, keepWords: Boolean): List<Tok> {
        val raw = ArrayList<Tok>()
        var i = 0
        var depth = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '(' -> { depth++; i++ }
                c == ')' -> { if (depth > 0) depth--; i++ }
                depth > 0 -> i++
                c.isDigit() -> i = readNumber(s, i, raw)
                c.isLetter() -> {
                    val st = i
                    while (i < s.length && s[i].isLetter()) i++
                    val w = s.substring(st, i).lowercase().replace('ё', 'е')
                    if (w.startsWith("кл")) {
                        // «9–11 классы»: это не даты
                        while (raw.isNotEmpty() && (raw.last().k == K.DASH ||
                                    (raw.last().k == K.DATE && raw.last().month == null))) raw.removeAt(raw.lastIndex)
                    }
                    raw += wordTok(w, st)
                }
                c in DASHES -> { raw += Tok(K.DASH, i); i++ }
                c == ',' -> { raw += Tok(K.COMMA, i); i++ }
                c == ';' -> { raw += Tok(K.AND, i); i++ }
                else -> i++
            }
        }
        // «14 февраля 2027», «март 2027», «с … по …»
        val out = ArrayList<Tok>()
        for ((idx, t) in raw.withIndex()) {
            val prev = out.lastOrNull()
            when {
                t.k == K.DATE && t.monthOnly && prev != null && prev.k == K.DATE && prev.month == null ->
                    out[out.lastIndex] = Tok(K.DATE, prev.pos, prev.day, t.month, null, prev.altDay)
                t.k == K.YEAR && prev != null && prev.k == K.DATE && prev.month != null && prev.year == null ->
                    out[out.lastIndex] = Tok(K.DATE, prev.pos, prev.day, prev.month, t.year, prev.altDay, prev.monthOnly)
                t.k == K.WORD && t.word == "по" && raw.getOrNull(idx + 1)?.k == K.DATE -> out += Tok(K.DASH, t.pos)
                t.k == K.WORD && !keepWords -> {}
                else -> out += t
            }
        }
        return out
    }

    private fun readNumber(s: String, start: Int, out: MutableList<Tok>): Int {
        var i = start
        fun digits(): String {
            val st = i
            while (i < s.length && s[i].isDigit()) i++
            return s.substring(st, i)
        }
        fun at(j: Int, ch: Char) = j < s.length && s[j] == ch
        fun digitAt(j: Int) = j < s.length && s[j].isDigit()

        val n1 = digits()
        if (n1.length == 4) {
            if (at(i, '/') && digitAt(i + 1)) { i++; digits() } // «2026/27»
            out += Tok(K.YEAR, start, year = n1.toInt())
            return i
        }
        if (n1.length > 2) return i
        if (at(i, ':') && digitAt(i + 1)) {
            i++
            val mm = digits()
            val h = n1.toInt()
            val m = mm.toIntOrNull()
            if (h in 0..23 && m != null && m in 0..59 && mm.length == 2) out += Tok(K.TIME, start, time = LocalTime.of(h, m))
            return i
        }
        var alt: Int? = null
        if (at(i, '/') && digitAt(i + 1)) {
            i++
            alt = digits().toIntOrNull()
        }
        var month: Int? = null
        var year: Int? = null
        if (at(i, '.') && digitAt(i + 1)) {
            i++
            month = digits().toIntOrNull()
            if (at(i, '.') && digitAt(i + 1)) {
                val save = i
                i++
                val y = digits()
                if (y.length == 4) year = y.toInt() else i = save
            }
        }
        val day = n1.toInt()
        if (day in 1..31 && (month == null || month in 1..12)) {
            out += Tok(K.DATE, start, day = day, month = month, year = year, altDay = alt?.takeIf { it in 1..31 })
        }
        return i
    }

    private val MONTH_STEMS = listOf(
        "январ" to 1, "феврал" to 2, "март" to 3, "апрел" to 4, "июн" to 6, "июл" to 7,
        "август" to 8, "сентябр" to 9, "октябр" to 10, "ноябр" to 11, "декабр" to 12,
    )

    private fun wordTok(w: String, pos: Int): Tok {
        when (w) {
            "до" -> return Tok(K.TO, pos)
            "с", "со" -> return Tok(K.FROM, pos)
            "или" -> return Tok(K.OR, pos)
            "и" -> return Tok(K.AND, pos)
            "май", "мая", "мае" -> return Tok(K.DATE, pos, month = 5, monthOnly = true)
        }
        MONTH_STEMS.firstOrNull { w.startsWith(it.first) }?.let { return Tok(K.DATE, pos, month = it.second, monthOnly = true) }
        return Tok(K.WORD, pos, word = w)
    }

    // --- куски и заполнение пропусков ---

    private class Part(var day: Int?, var month: Int?, var year: Int?, val monthOnly: Boolean, val altDay: Int?) {
        fun borrow(o: Part?) {
            if (o == null) return
            if (month == null && day != null) month = o.month
            if (year == null && month == o.month) year = o.year
            if (year == null && o.year != null && month != null && o.month != null) year = o.year
        }
    }

    private class Piece(val prefix: K?, val a: Part, val aTime: LocalTime?, val b: Part?, val bTime: LocalTime?)

    private fun Tok.part() = Part(day, month, year, monthOnly, altDay)

    private fun splitPieces(toks: List<Tok>): Pair<List<Piece>, Boolean> {
        val groups = ArrayList<List<Tok>>()
        var cur = ArrayList<Tok>()
        var chooseOne = false
        for ((i, t) in toks.withIndex()) {
            val next = toks.getOrNull(i + 1)
            when {
                t.k == K.OR -> { groups += cur; cur = ArrayList(); chooseOne = true }
                t.k == K.AND -> { groups += cur; cur = ArrayList() }
                t.k == K.COMMA && next?.k == K.DATE -> { groups += cur; cur = ArrayList() }
                t.k == K.DATE && cur.any { it.k == K.DATE } && cur.lastOrNull { it.k != K.COMMA }?.k != K.DASH -> {
                    groups += cur; cur = arrayListOf(t)
                }
                else -> cur += t
            }
        }
        groups += cur
        return groups.mapNotNull(::piece) to chooseOne
    }

    private fun piece(toks: List<Tok>): Piece? {
        var prefix: K? = null
        var a: Part? = null
        var aT: LocalTime? = null
        var b: Part? = null
        var bT: LocalTime? = null
        var dash = false
        for (t in toks) when (t.k) {
            K.TO, K.FROM -> if (a == null) prefix = t.k
            K.DATE -> if (a == null) a = t.part() else if (dash && b == null) b = t.part()
            K.TIME -> if (b != null) bT = t.time else if (a != null && !dash) aT = t.time
            K.DASH -> if (a != null) dash = true
            else -> {}
        }
        return Piece(prefix, a ?: return null, aT, b, bT)
    }

    private fun fill(ps: List<Piece>, season: Int) {
        for (p in ps) p.a.borrow(p.b)
        // «23–25.10 и 05–07.12.2026», «14 и 28.02.2027»: недостающее берём у следующей даты
        for (i in ps.indices.reversed()) {
            val next = ps.getOrNull(i + 1)?.a ?: continue
            ps[i].b?.borrow(next)
            ps[i].a.borrow(ps[i].b ?: next)
            ps[i].a.borrow(next)
        }
        for (i in ps.indices) {
            val prev = ps.getOrNull(i - 1)?.let { it.b ?: it.a } ?: continue
            if (ps[i].a.year == null && ps[i].a.month != null) ps[i].a.year = prev.year
            ps[i].b?.let { if (it.year == null && it.month != null) it.year = prev.year }
        }
        for (p in ps) for (part in listOfNotNull(p.a, p.b)) {
            val m = part.month ?: continue
            if (part.year == null) part.year = if (m >= 8) season else season + 1
        }
    }

    private fun date(p: Part, endOfMonth: Boolean): LocalDate? {
        val m = p.month ?: return null
        val y = p.year ?: return null
        if (p.monthOnly) return if (endOfMonth) YearMonth.of(y, m).atEndOfMonth() else LocalDate.of(y, m, 1)
        val d = minOf(p.day ?: return null, p.altDay ?: Int.MAX_VALUE)
        return runCatching { LocalDate.of(y, m, d) }.getOrNull()
    }

    private fun toSpan(p: Piece): Span? {
        val a = p.a
        val b = p.b
        if (b == null) {
            return when {
                p.prefix == K.TO -> Span(null, date(a, true) ?: return null, endTime = p.aTime, approxEnd = a.monthOnly)
                p.prefix == K.FROM -> date(a, false)?.let {
                    Span(it, it, startTime = p.aTime, approxStart = a.monthOnly, openEnd = true)
                }
                a.monthOnly -> Span(date(a, false) ?: return null, date(a, true) ?: return null, approxStart = true, approxEnd = true)
                else -> date(a, false)?.let { Span(it, it, startTime = p.aTime) }
            }
        }
        var s = date(a, false) ?: return null
        val e = date(b, true) ?: return null
        if (s > e) s = s.minusYears(1)
        return Span(s, e, p.aTime, p.bTime, a.monthOnly, b.monthOnly)
    }
}
