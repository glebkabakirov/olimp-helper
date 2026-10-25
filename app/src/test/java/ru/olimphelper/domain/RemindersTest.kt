package ru.olimphelper.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.olimphelper.data.SheetSource
import ru.olimphelper.data.Settings
import ru.olimphelper.data.UpdatesFile
import java.time.LocalDate

class RemindersTest {

    private val table = SheetSource.parse(javaClass.classLoader!!.getResourceAsStream("table.xlsx")!!.readBytes())

    @Test fun remindsThreeDaysBeforeAndNotTwice() {
        // Всесибирская: конец регистрации 06.11.2026 → за 3 дня это 03.11
        val today = LocalDate.parse("2026-11-03")
        val model = EventBuilder.build(table, UpdatesFile(), 11, emptySet(), today)
        val due = Reminders.due(model, emptySet(), Settings(), today, emptyMap())
        val vs = due.filter { it.event.olympiad == "Всесибирская" && it.event.type == EventType.REG_CLOSE }
        assertEquals(1, vs.size)
        assertEquals(3L, vs[0].daysBefore)
        assertTrue(Reminders.title(vs[0]).startsWith("Через 3 дня"))

        val again = Reminders.due(model, emptySet(), Settings(), today, due.associate { it.id to 0L })
        assertTrue(again.isEmpty())
    }

    @Test fun longStageEndRemindedThreeDaysAhead() {
        // онлайн-отбор Бельчонка до 12.01.2027
        val today = LocalDate.parse("2027-01-09")
        val model = EventBuilder.build(table, UpdatesFile(), 11, emptySet(), today)
        assertTrue(Reminders.due(model, emptySet(), Settings(), today, emptyMap())
            .any { it.event.olympiad == "Бельчонок" && it.event.type == EventType.STAGE_END && it.daysBefore == 3L })
    }

    @Test fun digestCoversWhatDatesMiss() {
        val today = LocalDate.parse("2026-10-25")
        val model = EventBuilder.build(table, UpdatesFile(), 11, emptySet(), today)
        val d = Reminders.digest(model, emptySet(), emptySet(), today)
        assertTrue(d.any { "Бельчонок" in it && "срок не указан" in it })
        // основной тур олимпиады по ИИ прошёл 18.10, финал 16.11, отметки нет
        assertTrue(d.any { "искусственному" in it && "Прошёл дальше" in it })

        val belKey = model.stages.first { it.row.olympiad == "Бельчонок" && it.row.stage == "Отборочный" }.key
        val aiKey = model.stages.first { it.row.olympiad.startsWith("Всероссийская олимпиада по иск") && it.row.stage == "Основной" }.key
        val after = Reminders.digest(model, setOf(belKey), setOf(aiKey), today)
        assertTrue(after.none { "Бельчонок" in it || "искусственному" in it })
    }

    @Test fun registeredAndConditionalAreSilent() {
        val today = LocalDate.parse("2026-11-03")
        val model = EventBuilder.build(table, UpdatesFile(), 11, emptySet(), today)
        val key = model.stages.first { it.row.olympiad == "Всесибирская" && it.row.stage == "Отборочный" }.key
        val due = Reminders.due(model, setOf(key), Settings(), today, emptyMap())
        assertTrue(due.none { it.event.olympiad == "Всесибирская" && it.event.type.isReg })

        // финал олимпиады по ИИ 16.11, за 3 дня — 13.11; без «Прошёл дальше» молчим
        val d2 = LocalDate.parse("2026-11-13")
        val m2 = EventBuilder.build(table, UpdatesFile(), 11, emptySet(), d2)
        assertTrue(Reminders.due(m2, emptySet(), Settings(), d2, emptyMap()).none { it.event.stageName == "Заключительный" && it.event.olympiad.startsWith("Всероссийская олимпиада по искусственному") })
    }
}
