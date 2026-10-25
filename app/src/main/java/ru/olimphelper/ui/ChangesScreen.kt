package ru.olimphelper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.olimphelper.data.ChangeEntry
import ru.olimphelper.data.UpdateItem

@Composable
fun ChangesScreen(u: UiState, actions: Actions) {
    val s = u.state
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (s.tableFetchedAt > 0) "Таблица обновлена: ${stamp(s.tableFetchedAt)}"
                        else "Таблица: встроенный снимок от 25 окт., из интернета ещё не обновлялась",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    s.tableError?.let { Text("Последняя попытка не удалась: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    val notConfigured = "/OWNER/" in s.settings.updatesUrl
                    Text(
                        when {
                            notConfigured -> "Проверка сайтов Claude ещё не подключена"
                            s.updates.generatedAt != null -> "Claude проверял сайты: ${s.updates.generatedAt!!.take(16).replace('T', ' ')}"
                            s.updatesFetchedAt > 0 -> "Claude ещё не проверял сайты"
                            else -> "Находки Claude ещё не загружались"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    s.updatesError?.let { Text("Файл находок не скачался: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    s.table?.footnote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted) }
                }
            }
        }
        if (s.updates.items.isNotEmpty()) {
            item { SectionHeader("Находки Claude на сайтах", "действуют поверх таблицы, пока её не обновят") }
            items(s.updates.items, key = { "f" + it.id }) { FindingCard(it, actions) }
        }
        item { SectionHeader("История изменений") }
        if (s.changes.isEmpty()) item { EmptyHint("Пока ничего не менялось") }
        items(s.changes) { ChangeCard(it, actions) }
    }
}

@Composable
private fun FindingCard(f: UpdateItem, actions: Actions) {
    OutlinedCard(onClick = { actions.openOlympiad(f.olympiad) }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${f.olympiad} — ${f.stage}", style = MaterialTheme.typography.titleSmall)
            Text(f.summary, style = MaterialTheme.typography.bodyMedium)
            val details = listOfNotNull(
                f.date?.let { "дата: $it" },
                f.registration?.let { "регистрация: $it" },
                f.registrationDates?.let { "сроки регистрации: $it" },
            )
            if (details.isNotEmpty()) Text(details.joinToString("\n"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row {
                f.checkedAt?.let { Text("проверено $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 14.dp)) }
                Spacer(Modifier.weight(1f))
                LinkButton("Сайт", f.sourceUrl)
            }
        }
    }
}

@Composable
private fun ChangeCard(c: ChangeEntry, actions: Actions) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val label = when (c.kind) {
        "changed" -> "Таблица: изменено"
        "added" -> "Таблица: новый этап"
        "removed" -> "Таблица: строка удалена"
        "claude" -> "🤖 Claude нашёл на сайте"
        else -> c.kind
    }
    OutlinedCard(onClick = { actions.openOlympiad(c.olympiad) }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f))
                Text(stamp(c.at), style = MaterialTheme.typography.labelSmall, color = muted)
            }
            Text("${c.olympiad} — ${c.stage}", style = MaterialTheme.typography.titleSmall)
            for (f in c.fields) {
                Text(
                    "${f.field}: «${f.old.ifBlank { "—" }}» → «${f.new.ifBlank { "—" }}»",
                    style = MaterialTheme.typography.bodySmall, maxLines = 6, overflow = TextOverflow.Ellipsis,
                )
            }
            if (c.summary.isNotBlank()) Text(c.summary, style = MaterialTheme.typography.bodySmall)
        }
    }
}
