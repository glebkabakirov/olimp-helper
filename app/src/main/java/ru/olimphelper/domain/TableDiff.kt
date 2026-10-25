package ru.olimphelper.domain

import ru.olimphelper.data.ChangeEntry
import ru.olimphelper.data.FieldChange
import ru.olimphelper.data.SheetRow

/** Что поменялось в таблице между двумя синхронизациями. */
object TableDiff {

    private val FIELDS: List<Pair<String, (SheetRow) -> String>> = listOf(
        "Дата" to { r -> r.dateText },
        "Регистрация" to { r -> r.regStatus },
        "Статус даты" to { r -> r.dateStatus },
        "Уровень" to { r -> r.level },
        "Примечание" to { r -> r.note },
        "Ссылка на источник" to { r -> r.sourceUrl.orEmpty() },
        "Ссылка на регистрацию" to { r -> r.regUrl.orEmpty() },
    )

    fun diff(old: List<SheetRow>, new: List<SheetRow>, at: Long): List<ChangeEntry> {
        val before = keyed(old)
        val after = keyed(new)
        val out = ArrayList<ChangeEntry>()
        for ((k, r) in after) {
            val o = before[k]
            if (o == null) {
                out += ChangeEntry(at, "added", r.olympiad, r.stage, summary = "Новый этап: ${r.dateText}", url = r.sourceUrl)
                continue
            }
            val fields = FIELDS.mapNotNull { (name, get) ->
                val a = get(o).trim()
                val b = get(r).trim()
                if (a != b) FieldChange(name, a, b) else null
            }
            if (fields.isNotEmpty()) out += ChangeEntry(at, "changed", r.olympiad, r.stage, fields, url = r.sourceUrl)
        }
        for ((k, o) in before) if (k !in after) {
            out += ChangeEntry(at, "removed", o.olympiad, o.stage, summary = "Строка удалена из таблицы")
        }
        return out
    }

    private fun keyed(rows: List<SheetRow>): Map<String, SheetRow> {
        val seen = HashMap<String, Int>()
        return rows.associateBy { r ->
            val k = stageKey(r.olympiad, r.stage)
            val n = seen.merge(k, 1, Int::plus)!!
            if (n == 1) k else "$k#$n"
        }
    }
}
