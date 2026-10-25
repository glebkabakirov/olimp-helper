package ru.olimphelper.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.olimphelper.data.SheetSource
import ru.olimphelper.data.UpdatesFile
import ru.olimphelper.data.WidgetConfig
import ru.olimphelper.data.WidgetContent
import java.time.LocalDate

class WidgetDataTest {

    private val today = LocalDate.parse("2026-10-25")
    private val table = SheetSource.parse(javaClass.classLoader!!.getResourceAsStream("table.xlsx")!!.readBytes())
    private val model = EventBuilder.build(table, UpdatesFile(), 11, emptySet(), today)

    private fun items(cfg: WidgetConfig, registered: Set<String> = emptySet()) =
        WidgetData.items(model, registered, emptySet(), cfg, today)

    @Test fun allShowsTimelineWithinDays() {
        val week = items(WidgetConfig(WidgetContent.ALL, days = 7))
        assertTrue(week.isNotEmpty())
        assertTrue(week.all { it.date!! <= today.plusDays(7) })
        assertTrue(items(WidgetConfig(WidgetContent.ALL, days = 30)).size > week.size)
    }

    @Test fun registrationsOnly() {
        val regs = items(WidgetConfig(WidgetContent.REGISTRATIONS, days = 14))
        assertTrue(regs.all { it.badge.contains("регистрац", ignoreCase = true) })
        assertTrue(regs.any { it.olympiad == "Всесибирская" && it.badge == "Конец регистрации" })
        assertTrue(regs.any { it.olympiad == "Бельчонок" && it.date == null })
    }

    @Test fun stagesIncludeOngoing() {
        val st = items(WidgetConfig(WidgetContent.STAGES, days = 14))
        assertTrue(st.none { it.badge.contains("регистрац", ignoreCase = true) })
        assertTrue(st.any { it.badge == "Идёт" && it.olympiad == "Гранит науки" })
    }

    @Test fun openRegsRespectRegisteredAndFilter() {
        val all = items(WidgetConfig(WidgetContent.OPEN_REGS))
        val key = model.stages.first { it.row.olympiad == "Бельчонок" && it.row.stage == "Отборочный" }.key
        assertTrue(items(WidgetConfig(WidgetContent.OPEN_REGS), setOf(key)).size == all.size - 1)
        val only = items(WidgetConfig(WidgetContent.OPEN_REGS, olympiads = setOf(norm("Ломоносов"))))
        assertEquals(listOf("Ломоносов"), only.map { it.olympiad })
    }
}
