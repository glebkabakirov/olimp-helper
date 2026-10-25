package ru.olimphelper.domain

import ru.olimphelper.data.Settings
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Какие напоминания пора отправить сегодня. */
object Reminders {

    data class Due(val id: String, val event: OEvent, val daysBefore: Long)

    fun due(model: Model, registered: Set<String>, settings: Settings, today: LocalDate, notified: Map<String, Long>): List<Due> {
        val days = buildSet {
            if (settings.remind3) add(3L)
            if (settings.remind1) add(1L)
            if (settings.remind0) add(0L)
        }
        return model.events
            .filter { !it.conditional && !it.approx && !Tabs.hidden(it, registered) }
            .mapNotNull { e ->
                val n = ChronoUnit.DAYS.between(today, e.date)
                // о конце длинного этапа достаточно сказать накануне
                val wanted = if (e.type == EventType.STAGE_END) n == 1L && settings.remind1 else n in days
                if (!wanted) return@mapNotNull null
                val id = "${e.id}@$n"
                if (id in notified) null else Due(id, e, n)
            }
    }

    fun title(d: Due): String {
        val whenText = when (d.daysBefore) {
            0L -> "Сегодня"
            1L -> "Завтра"
            else -> "Через ${d.daysBefore} ${plural(d.daysBefore, "день", "дня", "дней")}"
        }
        val what = when (d.event.type) {
            EventType.REG_OPEN -> "открывается регистрация"
            EventType.REG_CLOSE -> "закрывается регистрация"
            EventType.STAGE_START -> "начинается этап"
            EventType.STAGE_END -> "заканчивается этап"
            EventType.STAGE_DAY -> "этап олимпиады"
        }
        return "$whenText $what"
    }

    fun text(d: Due): String {
        val e = d.event
        val time = e.time?.let { ", $it" }.orEmpty()
        return "${e.olympiad} · ${e.stageName}$time" + (e.label?.let { " ($it)" }.orEmpty())
    }
}
