package ru.olimphelper.domain

import ru.olimphelper.data.SheetRow
import ru.olimphelper.data.SheetTable
import ru.olimphelper.data.UpdateItem
import ru.olimphelper.data.UpdatesFile
import java.time.LocalDate
import java.time.LocalTime

enum class EventType(val isReg: Boolean) {
    REG_OPEN(true), REG_CLOSE(true), STAGE_START(false), STAGE_END(false), STAGE_DAY(false)
}

data class StageReg(
    val status: RegStatus,
    val window: Span?,
    /** Откуда взято окно: фраза из примечания или строка «Регистрация…». */
    val windowSource: String?,
    val url: String?,
    val fromClaude: Boolean,
)

data class Stage(
    val key: String,
    /** Строка с наложенными находками Claude. */
    val row: SheetRow,
    /** Исходная строка таблицы, если Claude что-то в ней поправил; null для этапа, которого нет в таблице. */
    val original: SheetRow?,
    val updates: List<UpdateItem>,
    /** Строка вида «Регистрация на I отборочный»: сама по себе не этап, а окно регистрации для [targetKey]. */
    val isRegRow: Boolean,
    val targetKey: String?,
    val isFinal: Boolean,
    /** Этап, после которого нужна отметка «Прошёл дальше», чтобы финал стал «настоящим». */
    val prevKey: String?,
    val dates: ParsedDates,
    val reg: StageReg?,
    val dateFromClaude: Boolean,
)

data class OEvent(
    val id: String,
    val stageKeys: List<String>,
    val olympiad: String,
    val stageName: String,
    val level: String,
    val type: EventType,
    val date: LocalDate,
    val time: LocalTime?,
    val approx: Boolean,
    /** Финал или регистрация на него, а «Прошёл дальше» ещё не отмечено. */
    val conditional: Boolean,
    val prevKey: String?,
    val fromClaude: Boolean,
    val label: String?,
    val raw: String,
    val url: String?,
)

data class Olympiad(val name: String, val level: String, val stages: List<Stage>, val sourceUrl: String?)

data class Model(
    val season: Int,
    val olympiads: List<Olympiad>,
    val stages: List<Stage>,
    val events: List<OEvent>,
)

object EventBuilder {

    fun seasonFor(table: SheetTable?, today: LocalDate): Int =
        table?.seasonStartYear ?: if (today.monthValue >= 8) today.year else today.year - 1

    fun build(
        table: SheetTable,
        updates: UpdatesFile,
        grade: Int?,
        passed: Set<String>,
        today: LocalDate,
    ): Model {
        val season = seasonFor(table, today)
        val (rows, applied) = applyUpdates(table.rows, updates.items)

        // Ключи этапов; повторяющиеся получают суффикс, чтобы галочки не путались.
        val seen = HashMap<String, Int>()
        val keys = rows.map { r ->
            val k = stageKey(r.olympiad, r.stage)
            val n = seen.merge(k, 1, Int::plus)!!
            if (n == 1) k else "$k#$n"
        }

        val groups = LinkedHashMap<String, MutableList<Int>>()
        rows.forEachIndexed { i, r -> groups.getOrPut(norm(r.olympiad)) { ArrayList() } += i }

        val stages = arrayOfNulls<Stage>(rows.size)
        val olympiads = ArrayList<Olympiad>()
        for ((_, idx) in groups) {
            val isReg = idx.associateWith { isRegRow(rows[it].stage) }
            val target = HashMap<Int, Int>() // строка регистрации → этап
            for (i in idx) if (isReg[i] == true) findTarget(rows, idx, i, isReg)?.let { target[i] = it }
            val linked = target.entries.groupBy({ it.value }, { it.key })

            for ((pos, i) in idx.withIndex()) {
                val r = rows[i]
                val final = isFinal(r.stage)
                val prev = if (final) {
                    idx.subList(0, pos).lastOrNull { isReg[it] != true && !isFinal(rows[it].stage) }?.let { keys[it] }
                } else null
                val ups = applied[i].orEmpty()
                stages[i] = Stage(
                    key = keys[i],
                    row = r,
                    original = table.rows.firstOrNull { it.index == r.index && ups.isNotEmpty() && ups.none { u -> u.kind == "new_stage" } },
                    updates = ups,
                    isRegRow = isReg[i] == true,
                    targetKey = target[i]?.let { keys[it] },
                    isFinal = final,
                    prevKey = prev,
                    dates = if (isReg[i] == true) ParsedDates() else DateParser.parse(r.dateText, season, grade),
                    reg = if (isReg[i] == true) null else regFor(r, linked[i].orEmpty().map { rows[it] }, ups, season),
                    dateFromClaude = ups.any { it.date != null },
                )
            }
            val first = rows[idx.first()]
            olympiads += Olympiad(
                name = first.olympiad,
                level = first.level,
                stages = idx.map { stages[it]!! },
                sourceUrl = idx.firstNotNullOfOrNull { rows[it].sourceUrl },
            )
        }

        val all = stages.filterNotNull()
        val events = mergeRegEvents(all.flatMap { eventsFor(it, passed) })
            .sortedWith(compareBy({ it.date }, { it.time ?: LocalTime.MIN }))
        // id нужны уникальные: по ним работают списки и учёт отправленных напоминаний
        val ids = HashMap<String, Int>()
        val unique = events.map { e ->
            val n = ids.merge(e.id, 1, Int::plus)!!
            if (n == 1) e else e.copy(id = "${e.id}~$n")
        }
        return Model(season, olympiads, all, unique)
    }

    fun isRegRow(stage: String): Boolean = norm(stage).startsWith("регистрац") && '/' !in stage

    fun isFinal(stage: String): Boolean = norm(stage).let { "заключит" in it || "финал" in it }

    private fun findTarget(rows: List<SheetRow>, idx: List<Int>, regIdx: Int, isReg: Map<Int, Boolean>): Int? {
        val name = norm(rows[regIdx].stage)
        val target = name.substringAfter("регистрация на ", "").trim()
        val candidates = idx.filter { isReg[it] != true }
        if (target.isNotEmpty()) {
            candidates.firstOrNull { norm(rows[it].stage) == target }?.let { return it }
            candidates.firstOrNull { norm(rows[it].stage).startsWith(target) }?.let { return it }
        }
        return candidates.firstOrNull { it > regIdx } ?: candidates.lastOrNull()
    }

    private fun regFor(row: SheetRow, regRows: List<SheetRow>, ups: List<UpdateItem>, season: Int): StageReg {
        val claudeDates = ups.lastOrNull { it.registrationDates != null }
        val regRow = regRows.firstOrNull()
        val statusText = when {
            ups.any { it.registration != null } -> ups.last { it.registration != null }.registration!!
            regRow != null && regRow.regStatus.isNotBlank() -> regRow.regStatus
            else -> row.regStatus
        }
        val status = Registration.status(statusText, season)
        val url = row.regUrl ?: regRow?.regUrl
        if (claudeDates != null) {
            val span = DateParser.parse(claudeDates.registrationDates!!, season).spans.firstOrNull()
            return StageReg(status, span, "с сайта: ${claudeDates.registrationDates}", url, fromClaude = true)
        }
        if (regRow != null) {
            val span = DateParser.parse(regRow.dateText, season).spans.firstOrNull()
            return StageReg(status, span, "«${regRow.stage}»: ${regRow.dateText}", url, fromClaude = ups.any { it.registration != null })
        }
        val fromNote = Registration.fromNote(row.note, season)
        return StageReg(status, fromNote?.first, fromNote?.second, url, fromClaude = ups.any { it.registration != null })
    }

    private fun eventsFor(st: Stage, passed: Set<String>): List<OEvent> {
        if (st.isRegRow) return emptyList()
        val r = st.row
        val conditional = st.isFinal && st.prevKey != null && st.prevKey !in passed
        val out = ArrayList<OEvent>()
        fun ev(type: EventType, date: LocalDate, time: LocalTime?, approx: Boolean, label: String?, raw: String, url: String?, claude: Boolean) {
            out += OEvent(
                id = "${st.key}#$type#$date", stageKeys = listOf(st.key), olympiad = r.olympiad, stageName = r.stage,
                level = r.level, type = type, date = date, time = time, approx = approx, conditional = conditional,
                prevKey = st.prevKey, fromClaude = claude, label = label, raw = raw, url = url,
            )
        }

        val spans = st.dates.spans
        spans.forEachIndexed { i, s ->
            val label = listOfNotNull(
                when {
                    spans.size <= 1 -> null
                    st.dates.chooseOne -> "вариант ${i + 1} из ${spans.size}, нужен один"
                    "волн" in r.stage.lowercase() -> "${i + 1}-я волна"
                    else -> "${i + 1}-я дата из ${spans.size}"
                },
                s.classes,
                if (st.dates.conflict) "источники расходятся" else null,
            ).joinToString(" · ").ifEmpty { null }
            val raw = r.dateText
            val url = r.sourceUrl
            val start = s.start
            when {
                start == null -> ev(EventType.STAGE_END, s.end, s.endTime, s.approxEnd, label, raw, url, st.dateFromClaude)
                s.isWholeMonth -> ev(EventType.STAGE_DAY, start, null, true, label, raw, url, st.dateFromClaude)
                s.isSingleDay -> ev(EventType.STAGE_DAY, start, s.startTime, false, label, raw, url, st.dateFromClaude)
                s.openEnd -> ev(EventType.STAGE_START, start, s.startTime, s.approxStart, label, raw, url, st.dateFromClaude)
                else -> {
                    ev(EventType.STAGE_START, start, s.startTime, s.approxStart, label, raw, url, st.dateFromClaude)
                    ev(EventType.STAGE_END, s.end, s.endTime, s.approxEnd, label, raw, url, st.dateFromClaude)
                }
            }
        }

        val reg = st.reg ?: return out
        if (reg.status.state == RegState.CLOSED) return out
        val w = reg.window
        val url = reg.url ?: r.sourceUrl
        val raw = reg.windowSource ?: reg.status.text
        val opens = w?.start ?: reg.status.opensOn
        if (opens != null) ev(EventType.REG_OPEN, opens, w?.startTime, w?.approxStart == true, null, raw, url, reg.fromClaude)
        if (w != null && !w.openEnd) ev(EventType.REG_CLOSE, w.end, w.endTime, w.approxEnd, null, raw, url, reg.fromClaude)
        return out
    }

    /** Одна общая регистрация на несколько этапов (как у «Газпрома») показывается одним событием. */
    private fun mergeRegEvents(events: List<OEvent>): List<OEvent> {
        val (reg, other) = events.partition { it.type.isReg }
        val merged = reg.groupBy { listOf(norm(it.olympiad), it.type, it.date, it.time) }.values.map { g ->
            if (g.size == 1) g[0]
            else g[0].copy(
                id = g.joinToString("+") { it.id },
                stageKeys = g.flatMap { it.stageKeys },
                stageName = g.joinToString(", ") { it.stageName },
                conditional = g.all { it.conditional },
            )
        }
        return other + merged
    }

    /** Накладывает находки Claude на строки. Возвращает строки и находки по индексу строки. */
    private fun applyUpdates(rows: List<SheetRow>, items: List<UpdateItem>): Pair<List<SheetRow>, Map<Int, List<UpdateItem>>> {
        val out = rows.toMutableList()
        val applied = HashMap<Int, MutableList<UpdateItem>>()
        for (u in items) {
            val i = out.indexOfFirst { norm(it.olympiad) == norm(u.olympiad) && norm(it.stage) == norm(u.stage) }
                .takeIf { it >= 0 }
                ?: out.indexOfFirst { sameOlympiad(it.olympiad, u.olympiad) && norm(it.stage) == norm(u.stage) }
            if (i >= 0) {
                val r = out[i]
                out[i] = r.copy(
                    dateText = u.date ?: r.dateText,
                    regStatus = u.registration ?: r.regStatus,
                    level = u.level ?: r.level,
                )
                applied.getOrPut(i) { ArrayList() } += u
            } else if (u.kind == "new_stage") {
                val last = out.indexOfLast { sameOlympiad(it.olympiad, u.olympiad) }
                val base = out.getOrNull(last)
                val row = SheetRow(
                    index = -1 - applied.size,
                    olympiad = base?.olympiad ?: u.olympiad,
                    level = u.level ?: base?.level.orEmpty(),
                    stage = u.stage,
                    dateText = u.date.orEmpty(),
                    note = u.summary,
                    sourceUrl = u.sourceUrl ?: base?.sourceUrl,
                    regStatus = u.registration.orEmpty(),
                    regUrl = base?.regUrl,
                )
                val at = if (last >= 0) last + 1 else out.size
                out.add(at, row)
                // сдвигаем уже применённые индексы после вставки
                val shifted = applied.entries.associate { (k, v) -> (if (k >= at) k + 1 else k) to v }
                applied.clear()
                shifted.forEach { (k, v) -> applied[k] = v }
                applied.getOrPut(at) { ArrayList() } += u
            }
        }
        return out to applied
    }

    private fun sameOlympiad(a: String, b: String): Boolean {
        val x = norm(a)
        val y = norm(b)
        return x == y || (x.length >= 4 && y.length >= 4 && (x.startsWith(y) || y.startsWith(x)))
    }
}
