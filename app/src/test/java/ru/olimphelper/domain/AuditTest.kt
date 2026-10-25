package ru.olimphelper.domain

import org.junit.Test
import ru.olimphelper.data.SheetSource
import ru.olimphelper.data.Settings
import ru.olimphelper.data.UpdatesFile
import java.io.File
import java.time.LocalDate

/** Распечатка: какие напоминания придут в ближайшие 60 дней и что останется без напоминаний. build/audit.txt */
class AuditTest {

    @Test fun audit() {
        val table = SheetSource.parse(javaClass.classLoader!!.getResourceAsStream("table.xlsx")!!.readBytes())
        val start = LocalDate.parse("2026-10-25")
        val sb = StringBuilder()
        val reminded = HashSet<String>()
        for (d in 0L..60L) {
            val today = start.plusDays(d)
            val model = EventBuilder.build(table, UpdatesFile(), 11, emptySet(), today)
            for (due in Reminders.due(model, emptySet(), Settings(), today, emptyMap())) {
                sb.append("$today  ${Reminders.title(due)} — ${Reminders.text(due)}\n")
                reminded += due.event.id
            }
        }
        val model = EventBuilder.build(table, UpdatesFile(), 11, emptySet(), start)
        sb.append("\n### Будущие события БЕЗ напоминаний\n")
        model.events.filter { it.date >= start && it.id !in reminded && it.date <= start.plusDays(57) }
            .forEach { sb.append("${it.date} ${it.type} ${it.olympiad} / ${it.stageName} cond=${it.conditional} approx=${it.approx}\n") }
        sb.append("\n### Открытые регистрации без срока\n")
        Tabs.openRegistrations(model, emptySet(), emptySet(), start).filter { it.deadline == null }
            .forEach { sb.append("${it.stages[0].row.olympiad} / ${it.stages[0].row.stage}\n") }
        File("build").mkdirs()
        File("build/audit.txt").writeText(sb.toString())
    }
}
