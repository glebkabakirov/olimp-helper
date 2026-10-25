package ru.olimphelper.ui

import ru.olimphelper.domain.EventType
import ru.olimphelper.domain.OEvent
import ru.olimphelper.domain.plural
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val RU: Locale = Locale.forLanguageTag("ru")
private val DAY = DateTimeFormatter.ofPattern("EEE, d MMMM", RU)
private val SHORT = DateTimeFormatter.ofPattern("d MMM", RU)
private val MONTH = DateTimeFormatter.ofPattern("LLLL yyyy", RU)
private val STAMP = DateTimeFormatter.ofPattern("d MMM, HH:mm", RU)

fun String.cap(): String = replaceFirstChar { it.uppercase() }

fun dayTitle(d: LocalDate, today: LocalDate): String {
    val n = ChronoUnit.DAYS.between(today, d)
    val rel = when (n) {
        0L -> "Сегодня"
        1L -> "Завтра"
        2L -> "Послезавтра"
        else -> "Через $n ${plural(n, "день", "дня", "дней")}"
    }
    return "$rel · ${d.format(DAY)}"
}

fun relative(d: LocalDate, today: LocalDate): String {
    val n = ChronoUnit.DAYS.between(today, d)
    return when {
        n == 0L -> "сегодня"
        n == 1L -> "завтра"
        n > 1 -> "через $n ${plural(n, "день", "дня", "дней")}"
        n == -1L -> "вчера"
        else -> "${-n} ${plural(-n, "день", "дня", "дней")} назад"
    }
}

fun monthTitle(m: YearMonth): String = m.atDay(1).format(MONTH).cap()

fun shortDate(d: LocalDate): String = d.format(SHORT)

fun dayDate(d: LocalDate): String = d.format(DAY)

fun withTime(d: LocalDate, t: LocalTime?): String = dayDate(d) + (t?.let { ", $it" }.orEmpty())

fun eventDate(e: OEvent): String =
    if (e.approx) "≈ " + e.date.format(MONTH) else withTime(e.date, e.time)

fun typeLabel(t: EventType): String = when (t) {
    EventType.REG_OPEN -> "Открытие регистрации"
    EventType.REG_CLOSE -> "Конец регистрации"
    EventType.STAGE_START -> "Начало этапа"
    EventType.STAGE_END -> "Конец этапа"
    EventType.STAGE_DAY -> "День этапа"
}

fun stamp(ms: Long): String =
    if (ms <= 0) "никогда" else Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(STAMP)

fun levelLabel(level: String): String = when (level.trim()) {
    "I", "II", "III" -> "${level.trim()} уровень"
    "—", "-", "" -> "вне перечня РСОШ"
    else -> level
}
