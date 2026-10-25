@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package ru.olimphelper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import ru.olimphelper.domain.Olympiad
import ru.olimphelper.domain.RegState
import ru.olimphelper.domain.Registration
import ru.olimphelper.domain.Stage

@Composable
fun DetailScreen(u: UiState, name: String, actions: Actions, onBack: () -> Unit) {
    val o = u.model?.olympiads?.firstOrNull { it.name == name }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name, maxLines = 2) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
            )
        },
    ) { pad ->
        if (o == null) {
            EmptyHint("Этой олимпиады больше нет в таблице")
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row {
                    Tag(levelLabel(o.level), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                    LinkButton("Сайт олимпиады", o.sourceUrl)
                }
            }
            items(o.stages.filter { !it.isRegRow }, key = { it.key }) { StageCard(it, o, u, actions) }
        }
    }
}

@Composable
private fun StageCard(st: Stage, o: Olympiad, u: UiState, actions: Actions) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val model = u.model ?: return
    val events = model.events.filter { st.key in it.stageKeys }
    val regRows = o.stages.filter { it.targetKey == st.key }
    val registered = st.key in u.state.registered
    val passed = st.key in u.state.passed
    val leadsToFinal = o.stages.any { it.prevKey == st.key }
    val waitsForPass = st.isFinal && st.prevKey != null && st.prevKey !in u.state.passed

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text(st.row.stage, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (st.isFinal) Tag("Финал", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
            }
            Text("Даты: ${st.row.dateText.ifBlank { "—" }}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            st.original?.takeIf { st.dateFromClaude }?.let {
                Text("в таблице: ${it.dateText}", style = MaterialTheme.typography.bodySmall, color = muted, textDecoration = TextDecoration.LineThrough)
            }
            if (st.row.dateStatus.isNotBlank()) Text("Статус даты: ${st.row.dateStatus}", style = MaterialTheme.typography.bodySmall, color = muted)

            if (events.isNotEmpty()) {
                Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (e in events) {
                        val past = e.date < u.today
                        Text(
                            "• ${typeLabel(e.type)}: ${eventDate(e)}" + (e.label?.let { " ($it)" }.orEmpty()) + if (past) " — прошло" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (past) muted else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            st.reg?.let { reg ->
                val openNow = Registration.isOpen(reg.status, reg.window, u.today)
                Text(
                    "Регистрация: ${reg.status.text.ifBlank { "—" }}" + if (openNow && reg.status.state != RegState.OPEN) " · сейчас открыта" else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
                reg.windowSource?.let { Text("Сроки: $it", style = MaterialTheme.typography.bodySmall, color = muted) }
            }
            for (r in regRows) if (r.row.note.isNotBlank()) {
                Text("Про регистрацию: ${r.row.note}", style = MaterialTheme.typography.bodySmall, color = muted)
            }
            if (st.row.note.isNotBlank()) Text(st.row.note, style = MaterialTheme.typography.bodySmall, color = muted)

            for (up in st.updates) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(10.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Text(
                            "🤖 Claude, ${up.checkedAt ?: "недавно"}: ${up.summary}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        LinkButton("Где написано", up.sourceUrl)
                    }
                }
            }

            if (waitsForPass) {
                Text(
                    "Финал попадёт в «Скоро» и напоминания, когда отметишь «Прошёл дальше» на предыдущем этапе",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary,
                )
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = registered,
                    onClick = { actions.toggleRegistered(listOf(st.key)) },
                    label = { Text("Зарегистрировался") },
                    leadingIcon = if (registered) ({ Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }) else null,
                )
                if (leadsToFinal) {
                    FilterChip(
                        selected = passed,
                        onClick = { actions.togglePassed(st.key) },
                        label = { Text("Прошёл дальше") },
                        leadingIcon = if (passed) ({ Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }) else null,
                    )
                }
            }
            FlowRow {
                LinkButton("Регистрация", st.reg?.url ?: st.row.regUrl)
                LinkButton("Источник", st.row.sourceUrl)
            }
        }
    }
}
