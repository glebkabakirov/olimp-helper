package ru.olimphelper.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.olimphelper.data.SheetSource
import ru.olimphelper.data.SheetTable
import ru.olimphelper.data.UpdateItem
import ru.olimphelper.data.UpdatesFile
import java.io.File
import java.time.LocalDate

/** Проверки на снимке настоящей таблицы (src/test/resources/table.xlsx, 25.10.2026). */
class RealTableTest {

    private val today = LocalDate.parse("2026-10-25")
    private val table: SheetTable by lazy {
        SheetSource.parse(javaClass.classLoader!!.getResourceAsStream("table.xlsx")!!.readBytes())
    }

    private fun model(passed: Set<String> = emptySet(), updates: UpdatesFile = UpdatesFile(), grade: Int? = 11) =
        EventBuilder.build(table, updates, grade, passed, today)

    private fun Model.stage(olymp: String, stage: String) =
        stages.first { norm(it.row.olympiad).startsWith(norm(olymp)) && norm(it.row.stage) == norm(stage) }

    private fun Model.events(olymp: String) = events.filter { norm(it.olympiad).startsWith(norm(olymp)) }

    @Test fun readsRowsAndLinks() {
        assertEquals(2026, table.seasonStartYear)
        assertTrue(table.rows.size > 50)
        val b = table.rows.first { it.olympiad == "Бельчонок" && it.stage == "Отборочный" }
        assertEquals("https://dovuz.sfu.ru/abiturientu-sfu/olimpiady/belchonok/2026-2027-uchebnyy-god/", b.sourceUrl)
        assertEquals("https://dovuz.sfu.ru/reg/", b.regUrl)
        assertNotNull(table.footnote)
    }

    @Test fun lomonosovRegistrationRowLinksToStage() {
        val m = model()
        val reg = m.stage("Ломоносов", "Регистрация")
        assertTrue(reg.isRegRow)
        assertEquals(m.stage("Ломоносов", "Отборочный").key, reg.targetKey)
        val close = m.events("Ломоносов").single { it.type == EventType.REG_CLOSE }
        assertEquals(LocalDate.parse("2026-11-20"), close.date)
        assertEquals("12:00", close.time.toString())
    }

    @Test fun innopolisSecondTour() {
        val ev = model().events("Innopolis").filter { it.stageName == "II отборочный" }
        assertEquals(LocalDate.parse("2026-11-18"), ev.single { it.type == EventType.REG_CLOSE }.date)
        assertEquals(LocalDate.parse("2026-11-22"), ev.single { it.type == EventType.STAGE_DAY }.date)
    }

    @Test fun moshForEleventhGrade() {
        val days = model().events("МОШ").filter { it.stageName == "Отборочный" }.map { it.date }
        assertEquals(listOf(LocalDate.parse("2027-02-14"), LocalDate.parse("2027-02-28")), days)
    }

    @Test fun wavesAndVariants() {
        val m = model()
        val waves = m.events("Шаг в будущее").filter { it.type == EventType.STAGE_START }
        assertEquals(listOf("2026-10-23", "2026-12-05"), waves.map { it.date.toString() })
        assertEquals("1-я волна", waves[0].label)
        val hse = m.events("Высшая проба").filter { it.type == EventType.STAGE_DAY }
        assertEquals(2, hse.size)
        assertTrue(hse.all { it.label!!.startsWith("вариант") })
    }

    @Test fun finalIsConditionalUntilPassed() {
        val m = model()
        val final = m.events("Всероссийская олимпиада по искусственному").single { it.stageName == "Заключительный" }
        assertTrue(final.conditional)
        val prev = m.stage("Всероссийская олимпиада по искусственному", "Основной").key
        assertFalse(model(passed = setOf(prev)).events("Всероссийская олимпиада по искусственному").single { it.stageName == "Заключительный" }.conditional)
    }

    @Test fun gazpromGeneralRegistrationMerged() {
        val close = model().events("Газпром").single { it.type == EventType.REG_CLOSE }
        assertEquals(LocalDate.parse("2027-01-15"), close.date)
        assertEquals(2, close.stageKeys.size)
    }

    @Test fun registeredHidesRegistrationButNotStage() {
        val m = model()
        val key = m.stage("Ломоносов", "Отборочный").key
        val tabs = Tabs.build(m, setOf(key), emptySet(), today, 14)
        val lomo = (tabs.soon.flatMap { it.events } + tabs.later.flatMap { it.events }).filter { it.olympiad == "Ломоносов" }
        assertTrue(lomo.none { it.type.isReg })
        assertTrue(lomo.any { it.type == EventType.STAGE_START && it.date == LocalDate.parse("2026-11-13") })
        val before = Tabs.build(m, emptySet(), emptySet(), today, 14)
        assertTrue((before.soon.flatMap { it.events } + before.later.flatMap { it.events }).any { it.olympiad == "Ломоносов" && it.type == EventType.REG_CLOSE })
    }

    @Test fun openRegistrationsWithoutDeadline() {
        val tabs = Tabs.build(model(), emptySet(), emptySet(), today, 14)
        val names = tabs.openRegs.map { it.stages[0].row.olympiad }
        assertTrue("Бельчонок" in names)
        assertTrue(tabs.openRegs.first { it.stages[0].row.olympiad == "Бельчонок" }.deadline == null)
    }

    @Test fun claudeUpdateOverridesDate() {
        val u = UpdatesFile(items = listOf(
            UpdateItem(id = "x", olympiad = "Ломоносов", stage = "Заключительный", date = "15.03.2027", summary = "Появилась дата финала"),
            UpdateItem(id = "y", olympiad = "Открытая олимпиада школьников (ИТМО)", stage = "Отборочный", registration = "ОТКРЫТА",
                registrationDates = "01.11.2026 – 30.11.2026", summary = "Открыта регистрация"),
            UpdateItem(id = "z", kind = "new_stage", olympiad = "Ломоносов", stage = "Дополнительный тур", date = "01.12.2026"),
        ))
        val m = model(updates = u)
        val st = m.stage("Ломоносов", "Заключительный")
        assertTrue(st.dateFromClaude)
        assertEquals("Не опубликовано", st.original!!.dateText)
        val e = m.events("Ломоносов").single { it.stageName == "Заключительный" }
        assertEquals(LocalDate.parse("2027-03-15"), e.date)
        assertTrue(e.fromClaude)
        val itmo = m.events("Открытая олимпиада школьников (ИТМО)").single { it.type == EventType.REG_CLOSE }
        assertEquals(LocalDate.parse("2026-11-30"), itmo.date)
        assertTrue(m.events("Ломоносов").any { it.stageName == "Дополнительный тур" })
    }

    @Test fun diffFindsChangedCell() {
        val changed = table.rows.map { if (it.olympiad == "Ломоносов" && it.stage == "Заключительный") it.copy(dateText = "20.03.2027") else it }
        val d = TableDiff.diff(table.rows, changed, 0)
        assertEquals(1, d.size)
        assertEquals("Дата", d[0].fields.single().field)
        assertEquals("20.03.2027", d[0].fields.single().new)
    }

    /** Полная распечатка разбора, чтобы глазами сверить каждую строку: build/parse-dump.txt. */
    @Test fun dump() {
        val m = model()
        val sb = StringBuilder()
        for (o in m.olympiads) {
            sb.append("\n=== ${o.name} [${o.level}]\n")
            for (st in o.stages) {
                sb.append("  -- ${st.row.stage} | «${st.row.dateText}» | рег: «${st.row.regStatus}»")
                if (st.isRegRow) sb.append(" → для ${st.targetKey}")
                if (st.isFinal) sb.append(" | финал после ${st.prevKey}")
                sb.append('\n')
                st.reg?.let { sb.append("     reg: ${it.status.state} opens=${it.status.opensOn} window=${it.window} src=«${it.windowSource}»\n") }
            }
            for (e in m.events.filter { it.olympiad == o.name }) {
                sb.append("     ${e.date} ${e.time ?: ""} ${e.type}${if (e.approx) " ≈" else ""}${if (e.conditional) " [если пройдёшь]" else ""} ${e.stageName} ${e.label ?: ""}\n")
            }
        }
        val tabs = Tabs.build(m, emptySet(), emptySet(), today, 14)
        sb.append("\n##### СКОРО\n")
        tabs.openRegs.forEach { sb.append("  open: ${it.stages.joinToString { s -> s.row.olympiad + " / " + s.row.stage }} до ${it.deadline}\n") }
        tabs.ongoing.forEach { sb.append("  идёт: ${it.stage.row.olympiad} / ${it.stage.row.stage} до ${it.until}\n") }
        tabs.soon.forEach { g -> g.events.forEach { sb.append("  ${g.date} ${it.type} ${it.olympiad} / ${it.stageName}\n") } }
        sb.append("##### НЕ ОБЪЯВЛЕНО\n")
        tabs.undated.forEach { sb.append("  ${it.row.olympiad} / ${it.row.stage}\n") }
        File("build").mkdirs()
        File("build/parse-dump.txt").writeText(sb.toString())
    }
}
