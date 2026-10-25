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
                if (n !in days) return@mapNotNull null
                val id = "${e.id}@$n"
                if (id in notified) null else Due(id, e, n)
            }
    }

    /**
     * Еженедельная сводка о том, что напоминаниями по датам не ловится: открытые регистрации без отметки
     * (особенно без срока) и финалы, где предыдущий этап уже прошёл, а «Прошёл дальше» не отмечено.
     */
    fun digest(model: Model, registered: Set<String>, passed: Set<String>, today: LocalDate): List<String> {
        val regs = Tabs.openRegistrations(model, registered, passed, today).map { r ->
            val name = r.stages[0].row.olympiad + " · " + r.stages.joinToString(", ") { it.row.stage }
            "Регистрация открыта: $name" + (r.deadline?.let { ", до ${it.dayOfMonth}.%02d".format(it.monthValue) } ?: ", срок не указан")
        }
        val byKey = model.stages.associateBy { it.key }
        val finals = model.stages.filter { st ->
            val prev = st.prevKey?.let(byKey::get) ?: return@filter false
            !st.isRegRow && st.isFinal && st.prevKey !in passed &&
                prev.dates.spans.isNotEmpty() && prev.dates.spans.all { it.end < today } &&
                st.dates.spans.let { sp -> sp.isEmpty() || sp.any { it.end >= today } }
        }.map { "Прошёл в финал «${it.row.olympiad}»? Отметь «Прошёл дальше», иначе о финале не напомню" }
        return regs + finals
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
