package ru.olimphelper.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class DateParserTest {

    private fun d(s: String) = LocalDate.parse(s)
    private fun t(s: String) = LocalTime.parse(s)
    private fun p(text: String, grade: Int? = 11) = DateParser.parse(text, 2026, grade)
    private fun one(text: String) = p(text).spans.single()

    @Test fun range() {
        val s = one("01.10.2026 – 12.01.2027")
        assertEquals(d("2026-10-01"), s.start)
        assertEquals(d("2027-01-12"), s.end)
    }

    @Test fun singleDay() {
        val s = one("14.02.2027")
        assertTrue(s.isSingleDay)
        assertEquals(d("2027-02-14"), s.start)
    }

    @Test fun rangeWithEndTime() {
        val s = one("05.10.2026 – 13.01.2027, 23:59 МСК")
        assertEquals(d("2027-01-13"), s.end)
        assertEquals(t("23:59"), s.endTime)
    }

    @Test fun twoWaves() {
        val r = p("23–25.10 и 05–07.12.2026")
        assertEquals(2, r.spans.size)
        assertEquals(d("2026-10-23"), r.spans[0].start)
        assertEquals(d("2026-10-25"), r.spans[0].end)
        assertEquals(d("2026-12-05"), r.spans[1].start)
        assertEquals(d("2026-12-07"), r.spans[1].end)
    }

    @Test fun startWithoutYear() {
        val s = one("20.10–01.12.2026")
        assertEquals(d("2026-10-20"), s.start)
        assertEquals(d("2026-12-01"), s.end)
    }

    @Test fun monthToDate() {
        val s = one("Январь – 09.03.2027")
        assertEquals(d("2027-01-01"), s.start)
        assertTrue(s.approxStart)
        assertEquals(d("2027-03-09"), s.end)
    }

    @Test fun wholeMonth() {
        val s = one("Март 2027")
        assertTrue(s.isWholeMonth)
        assertEquals(d("2027-03-01"), s.start)
        assertEquals(d("2027-03-31"), s.end)
    }

    @Test fun monthRange() {
        val s = one("Февраль–март 2027")
        assertEquals(d("2027-02-01"), s.start)
        assertEquals(d("2027-03-31"), s.end)
        assertTrue(s.approxStart && s.approxEnd)
    }

    @Test fun deadlineOnly() {
        val s = one("До 14.10.2026, 15:00 МСК")
        assertNull(s.start)
        assertEquals(d("2026-10-14"), s.end)
        assertEquals(t("15:00"), s.endTime)
    }

    @Test fun chooseOne() {
        val r = p("14.11.2026, 14:00 или 19.11.2026, 11:00 МСК")
        assertTrue(r.chooseOne)
        assertEquals(listOf(d("2026-11-14"), d("2026-11-19")), r.spans.map { it.start })
        assertEquals(listOf(t("14:00"), t("11:00")), r.spans.map { it.startTime })
    }

    @Test fun rangeWithTimeAtEnd() {
        val s = one("24.10–20.11.2026, 12:00 МСК")
        assertEquals(d("2026-10-24"), s.start)
        assertEquals(d("2026-11-20"), s.end)
        assertEquals(t("12:00"), s.endTime)
        val s2 = one("13–20.11.2026, 12:00 МСК")
        assertEquals(d("2026-11-13"), s2.start)
    }

    @Test fun classSegments() {
        val text = "19.12.2026 и 16.01.2027 (6–9); 14 и 28.02.2027 (10–11)"
        assertEquals(listOf(d("2027-02-14"), d("2027-02-28")), p(text, 11).spans.map { it.start })
        assertEquals(listOf(d("2026-12-19"), d("2027-01-16")), p(text, 8).spans.map { it.start })
        assertEquals(4, p(text, null).spans.size)
        assertEquals("10–11 кл.", p(text, 11).spans[0].classes)
        assertEquals(listOf(d("2027-03-28")), p("14.02.2027 (6–9); 28.03.2027 (10–11)", 11).spans.map { it.start })
    }

    @Test fun conflictingDay() {
        val r = p("09.11.2026 – 08/09.02.2027")
        assertTrue(r.conflict)
        assertEquals(d("2027-02-08"), r.spans.single().end)
    }

    @Test fun notPublished() {
        assertTrue(p("Не опубликовано").spans.isEmpty())
    }

    @Test fun yearCrossing() {
        val s = one("28.12–15.01.2027")
        assertEquals(d("2026-12-28"), s.start)
        assertEquals(d("2027-01-15"), s.end)
        assertEquals(d("2027-02-26"), one("26–28.02.2027").start)
    }

    // --- окна регистрации из примечаний ---

    private fun note(text: String) = Registration.fromNote(text, 2026)?.first

    @Test fun noteRegistrationAndStage() {
        val s = note("Регистрация и отбор идут 05.10.2026–13.01.2027; завершение 13.01.2027 в 23:59 МСК.")!!
        assertEquals(d("2026-10-05"), s.start)
        assertEquals(d("2027-01-13"), s.end)
    }

    @Test fun noteTimesBothEnds() {
        val s = note("Запись на II тур: 19.10.2026, 10:00 — 18.11.2026, 12:00 МСК. Пробный тур 21.11, основной 22.11.")!!
        assertEquals(d("2026-10-19"), s.start)
        assertEquals(t("10:00"), s.startTime)
        assertEquals(d("2026-11-18"), s.end)
        assertEquals(t("12:00"), s.endTime)
    }

    @Test fun noteUntilTime() {
        val s = note("Информатики нет в утверждённом перечне РСОШ 2026/27. Регистрация открыта 06.10–23.11.2026 до 23:59 (часовой пояс на странице не указан). Отбор с 26.10.")!!
        assertEquals(d("2026-10-06"), s.start)
        assertEquals(d("2026-11-23"), s.end)
        assertEquals(t("23:59"), s.endTime)
    }

    @Test fun noteDeadline() {
        val s = note("Общая регистрация открыта до 15.01.2027 по объявлению 21.09.2026; час не указан.")!!
        assertNull(s.start)
        assertEquals(d("2027-01-15"), s.end)
        val s2 = note("Общий приём заявок идёт до 15.01.2027 (Олимпиада.ру, 21.09). Час не указан.")!!
        assertEquals(d("2027-01-15"), s2.end)
    }

    @Test fun noteOpensFrom() {
        val s = note("Это общий период финалов. Запись на финал с 30.12.2026. Дипломантам прошлого года нужна общая регистрация до 01.12.")!!
        assertEquals(d("2026-12-30"), s.start)
        assertTrue(s.openEnd)
    }

    @Test fun noteVenueChoice() {
        val s = note("Общий период финала 30.01–02.02.2027. День информатики в открытой части не указан. Выбор города и документов 20–24.01.2027.")!!
        assertEquals(d("2027-01-20"), s.start)
        assertEquals(d("2027-01-24"), s.end)
        val s2 = note("Не в перечне РСОШ. Финал 31.01.2027; выбор площадки и документы 17–30.12.2026.")!!
        assertEquals(d("2026-12-17"), s2.start)
    }

    @Test fun noteWithoutDates() {
        assertNull(note("Объявление 02.10 сообщает о продолжающейся записи. На странице организатора срок регистрации указан как «август–сентябрь»; форма недоступна проверке (403)."))
        assertNull(note("Отбор до 12.01.2027, 17:00 Красноярск (13:00 МСК). Отдельный час закрытия регистрации не указан."))
        assertNull(note("Даты указаны в шапке официального сайта. Сроки и ссылка регистрации 2026/27 не объявлены."))
    }
}
