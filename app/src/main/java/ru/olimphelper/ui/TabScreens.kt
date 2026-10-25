@file:OptIn(ExperimentalLayoutApi::class)

package ru.olimphelper.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.olimphelper.domain.Ongoing
import ru.olimphelper.domain.OpenReg
import ru.olimphelper.domain.Registration
import ru.olimphelper.domain.Tabs

private val LIST_PADDING = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)

@Composable
fun SoonScreen(u: UiState, actions: Actions) {
    val tabs = u.tabs ?: return
    val days = u.state.settings.horizonDays
    LazyColumn(Modifier.fillMaxSize(), contentPadding = LIST_PADDING, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionHeader("Ближайшие $days дней", "регистрации и этапы, которые начнутся или закончатся") }
        if (tabs.soon.isEmpty()) item { EmptyHint("В ближайшие $days дней ничего не горит") }
        for (g in tabs.soon) {
            item(key = "d" + g.date) { DayHeader(dayTitle(g.date, u.today)) }
            items(g.events, key = { it.id }) { EventCard(it, u.today, actions) }
        }
        if (tabs.openRegs.isNotEmpty()) {
            item { SectionHeader("Регистрация открыта", "срок дальше $days дней или не указан, а ты ещё не отметился") }
            items(tabs.openRegs, key = { "o" + it.stages.joinToString { s -> s.key } }) { OpenRegCard(it, u, actions) }
        }
        if (tabs.ongoing.isNotEmpty()) {
            item { SectionHeader("Идёт сейчас", "этапы, которые уже начались") }
            item { OngoingCard(tabs.ongoing, actions) }
        }
    }
}

@Composable
private fun OpenRegCard(o: OpenReg, u: UiState, actions: Actions) {
    val st = o.stages.first()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    OutlinedCard(onClick = { actions.openOlympiad(st.row.olympiad) }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(st.row.olympiad, style = MaterialTheme.typography.titleMedium)
            Text(o.stages.joinToString(", ") { it.row.stage } + " · " + levelLabel(st.row.level), style = MaterialTheme.typography.bodyMedium, color = muted)
            val deadline = o.deadline
            Text(
                if (deadline != null) "Регистрация до ${withTime(deadline, o.deadlineTime)} (${relative(deadline, u.today)})"
                else "Срок регистрации не указан" + (o.stageEnds?.let { ", этап до ${shortDate(it)}" }.orEmpty()),
                style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
            )
            Text("В таблице: «${st.reg?.status?.text.orEmpty()}»", style = MaterialTheme.typography.bodySmall, color = muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { actions.toggleRegistered(o.stages.map { it.key }) }, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                    Icon(Icons.Filled.Check, null, Modifier.size(18.dp))
                    Text("Я зарегистрировался", Modifier.padding(start = 6.dp))
                }
                LinkButton("Регистрация", o.url)
            }
        }
    }
}

@Composable
private fun OngoingCard(list: List<Ongoing>, actions: Actions) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 6.dp)) {
            list.forEachIndexed { i, o ->
                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 14.dp))
                Row(
                    Modifier.fillMaxWidth().clickable { actions.openOlympiad(o.stage.row.olympiad) }.padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(o.stage.row.olympiad, style = MaterialTheme.typography.titleSmall)
                        Text(o.stage.row.stage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("до " + shortDate(o.until) + (o.untilTime?.let { ", $it" }.orEmpty()), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun LaterScreen(u: UiState, actions: Actions) {
    val tabs = u.tabs ?: return
    LazyColumn(Modifier.fillMaxSize(), contentPadding = LIST_PADDING, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (tabs.later.isEmpty()) item { EmptyHint("Дальше пока ничего нет") }
        for (g in tabs.later) {
            item(key = "m" + g.month) { SectionHeader(monthTitle(g.month)) }
            items(g.events, key = { it.id }) { EventCard(it, u.today, actions) }
        }
        if (tabs.undated.isNotEmpty()) {
            item { SectionHeader("Даты пока не объявлены", "появятся здесь, когда их внесут в таблицу или Claude найдёт на сайте") }
            items(tabs.undated, key = { "u" + it.key }) { st ->
                OutlinedCard(onClick = { actions.openOlympiad(st.row.olympiad) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(st.row.olympiad, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${st.row.stage} · «${st.row.dateText.ifBlank { "—" }}»" + (st.row.dateStatus.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OlympiadsScreen(u: UiState, actions: Actions) {
    val model = u.model ?: return
    val registered = u.state.registered
    val passed = u.state.passed
    LazyColumn(Modifier.fillMaxSize(), contentPadding = LIST_PADDING, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(model.olympiads, key = { it.name }) { o ->
            val next = model.events.firstOrNull { e ->
                e.olympiad == o.name && e.date >= u.today && !e.conditional && !Tabs.hidden(e, registered)
            }
            val regOpen = o.stages.any { st ->
                val reg = st.reg
                reg != null && st.key !in registered && !(st.isFinal && st.prevKey != null && st.prevKey !in passed) &&
                    Registration.isOpen(reg.status, reg.window, u.today)
            }
            val anyRegistered = o.stages.any { it.key in registered }
            val claude = o.stages.any { it.updates.isNotEmpty() }
            OutlinedCard(onClick = { actions.openOlympiad(o.name) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row {
                        Text(o.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.size(8.dp))
                        Tag(levelLabel(o.level), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        next?.let { "${typeLabel(it.type)}: ${eventDate(it)} (${if (it.approx) "примерно" else relative(it.date, u.today)})" }
                            ?: "Ближайших дат пока нет",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (regOpen || anyRegistered || claude) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val c = MaterialTheme.colorScheme
                            if (regOpen) Tag("Регистрация открыта", c.tertiaryContainer, c.onTertiaryContainer)
                            if (anyRegistered) Tag("✓ Зарегистрирован", c.secondaryContainer, c.onSecondaryContainer)
                            if (claude) Tag("🤖 Новое с сайта", c.primaryContainer, c.onPrimaryContainer)
                        }
                    }
                }
            }
        }
    }
}
