package ru.olimphelper.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/** Регистрация открыта сейчас, а срок дальше горизонта «Скоро» или не указан. */
data class OpenReg(
    val stages: List<Stage>,
    val deadline: LocalDate?,
    val deadlineTime: LocalTime?,
    /** Когда заканчивается сам этап: подсказка, если срок регистрации не указан. */
    val stageEnds: LocalDate?,
    val url: String?,
)

data class Ongoing(val stage: Stage, val until: LocalDate, val untilTime: LocalTime?)

data class DayGroup(val date: LocalDate, val events: List<OEvent>)

data class MonthGroup(val month: YearMonth, val events: List<OEvent>)

data class TabsData(
    val openRegs: List<OpenReg>,
    val ongoing: List<Ongoing>,
    val soon: List<DayGroup>,
    val later: List<MonthGroup>,
    val undated: List<Stage>,
)

object Tabs {

    fun hidden(e: OEvent, registered: Set<String>): Boolean = e.type.isReg && e.stageKeys.all { it in registered }

    fun inSoon(e: OEvent, today: LocalDate, horizonDays: Int): Boolean =
        !e.conditional && !e.approx && e.date >= today && e.date <= today.plusDays(horizonDays.toLong())

    fun build(model: Model, registered: Set<String>, passed: Set<String>, today: LocalDate, horizonDays: Int): TabsData {
        val horizon = today.plusDays(horizonDays.toLong())
        val visible = model.events.filter { !hidden(it, registered) && it.date >= today }
        val (soonEv, laterEv) = visible.partition { inSoon(it, today, horizonDays) }

        val realStages = model.stages.filter { !it.isRegRow }
        return TabsData(
            // те, у кого срок в пределах горизонта, и так видны в ленте событий
            openRegs = openRegistrations(model, registered, passed, today).filter { it.deadline == null || it.deadline > horizon },
            ongoing = ongoing(model, passed, today),
            soon = soonEv.groupBy { it.date }.toSortedMap().map { (d, ev) -> DayGroup(d, ev) },
            later = laterEv.groupBy { YearMonth.from(it.date) }.toSortedMap().map { (m, ev) -> MonthGroup(m, ev) },
            undated = realStages.filter { it.dates.spans.isEmpty() },
        )
    }

    private fun waitsForPass(st: Stage, passed: Set<String>) = st.isFinal && st.prevKey != null && st.prevKey !in passed

    /** Все регистрации, открытые сегодня, где ещё нет отметки «Зарегистрировался», по сроку. */
    fun openRegistrations(model: Model, registered: Set<String>, passed: Set<String>, today: LocalDate): List<OpenReg> =
        model.stages
            .filter { st ->
                val reg = st.reg ?: return@filter false
                !st.isRegRow && st.key !in registered && !waitsForPass(st, passed) &&
                    Registration.isOpen(reg.status, reg.window, today) &&
                    st.dates.spans.let { sp -> sp.isEmpty() || sp.any { it.end >= today } }
            }
            .map { st ->
                val w = st.reg!!.window?.takeIf { !it.openEnd }
                OpenReg(listOf(st), w?.end, w?.endTime, st.dates.spans.maxOfOrNull { it.end }, st.reg.url ?: st.row.sourceUrl)
            }
            .groupBy { listOf(norm(it.stages[0].row.olympiad), it.deadline) }
            .values.map { g -> g[0].copy(stages = g.flatMap { it.stages }) }
            .sortedWith(compareBy(nullsLast()) { it.deadline })

    /** Этапы, которые уже начались и ещё не закончились. */
    fun ongoing(model: Model, passed: Set<String>, today: LocalDate): List<Ongoing> =
        model.stages
            .filter { !it.isRegRow && !waitsForPass(it, passed) }
            .mapNotNull { st ->
                st.dates.spans.firstOrNull { s ->
                    val start = s.start
                    start != null && !s.approxStart && !s.openEnd && start <= today && s.end > today
                }?.let { Ongoing(st, it.end, it.endTime) }
            }
            .sortedBy { it.until }
}
