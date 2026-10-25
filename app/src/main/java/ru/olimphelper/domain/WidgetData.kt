package ru.olimphelper.domain

import ru.olimphelper.data.WidgetConfig
import ru.olimphelper.data.WidgetContent
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Строка виджета. [date] = null — срок не указан. */
data class WidgetItem(
    val key: String,
    val badge: String,
    val olympiad: String,
    val stage: String,
    val date: LocalDate?,
    val time: LocalTime?,
    /** До конца регистрации не больше недели или до события не больше 3 дней. */
    val urgent: Boolean,
    /** «до 12 янв.» вместо даты события: для идущих этапов и открытых регистраций. */
    val until: Boolean = false,
)

/** Что показать в виджете при заданной настройке. */
object WidgetData {

    fun items(
        model: Model,
        registered: Set<String>,
        passed: Set<String>,
        cfg: WidgetConfig,
        today: LocalDate,
    ): List<WidgetItem> {
        val wanted = { olympiad: String -> cfg.olympiads.isEmpty() || norm(olympiad) in cfg.olympiads }
        val horizon = today.plusDays(cfg.days.toLong())
        val events = model.events.filter {
            wanted(it.olympiad) && !Tabs.hidden(it, registered) && !it.conditional && !it.approx &&
                it.date >= today && it.date <= horizon
        }
        val openRegs = Tabs.openRegistrations(model, registered, passed, today).filter { wanted(it.stages[0].row.olympiad) }

        return when (cfg.content) {
            WidgetContent.ALL -> events.map { it.toItem(today) }
            WidgetContent.REGISTRATIONS ->
                events.filter { it.type.isReg }.map { it.toItem(today) } +
                    openRegs.filter { it.deadline == null || it.deadline > horizon }.map { it.toItem(today) }
            WidgetContent.STAGES ->
                Tabs.ongoing(model, passed, today).filter { wanted(it.stage.row.olympiad) }.map {
                    WidgetItem("on" + it.stage.key, "Идёт", it.stage.row.olympiad, it.stage.row.stage, it.until, it.untilTime,
                        urgent = ChronoUnit.DAYS.between(today, it.until) <= 3, until = true)
                } + events.filter { !it.type.isReg }.map { it.toItem(today) }
            WidgetContent.OPEN_REGS -> openRegs.map { it.toItem(today) }
        }
    }

    private fun OEvent.toItem(today: LocalDate) = WidgetItem(
        key = id,
        badge = when (type) {
            EventType.REG_OPEN -> "Открытие регистрации"
            EventType.REG_CLOSE -> "Конец регистрации"
            EventType.STAGE_START -> "Начало этапа"
            EventType.STAGE_END -> "Конец этапа"
            EventType.STAGE_DAY -> "Этап"
        },
        olympiad = olympiad,
        stage = listOfNotNull(stageName, label).joinToString(" · "),
        date = date,
        time = time,
        urgent = ChronoUnit.DAYS.between(today, date) <= if (type == EventType.REG_CLOSE) 7 else 3,
    )

    private fun OpenReg.toItem(today: LocalDate) = WidgetItem(
        key = "reg" + stages.joinToString { it.key },
        badge = "Регистрация открыта",
        olympiad = stages[0].row.olympiad,
        stage = stages.joinToString(", ") { it.row.stage },
        date = deadline,
        time = deadlineTime,
        urgent = deadline != null && ChronoUnit.DAYS.between(today, deadline) <= 7,
        until = true,
    )
}
