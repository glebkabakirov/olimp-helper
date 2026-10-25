@file:OptIn(ExperimentalLayoutApi::class)

package ru.olimphelper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.olimphelper.domain.EventType
import ru.olimphelper.domain.OEvent
import java.time.LocalDate

/** Действия, которые экраны передают наверх, в ViewModel. */
class Actions(
    val openOlympiad: (String) -> Unit,
    val toggleRegistered: (List<String>) -> Unit,
    val togglePassed: (String) -> Unit,
)

@Composable
fun SectionHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 12.dp, bottom = 2.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun DayHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
fun Tag(text: String, bg: Color, fg: Color) {
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(8.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

@Composable
fun typeColors(t: EventType): Pair<Color, Color> {
    val c = MaterialTheme.colorScheme
    return when (t) {
        EventType.REG_CLOSE -> c.errorContainer to c.onErrorContainer
        EventType.REG_OPEN -> c.tertiaryContainer to c.onTertiaryContainer
        EventType.STAGE_END -> c.secondaryContainer to c.onSecondaryContainer
        else -> c.primaryContainer to c.onPrimaryContainer
    }
}

@Composable
fun LinkButton(text: String, url: String?) {
    val uri = LocalUriHandler.current
    if (url.isNullOrBlank()) return
    TextButton(onClick = { runCatching { uri.openUri(url) } }) { Text("$text ↗") }
}

@Composable
fun EventCard(e: OEvent, today: LocalDate, actions: Actions) {
    val (bg, fg) = typeColors(e.type)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    ElevatedCard(onClick = { actions.openOlympiad(e.olympiad) }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tag(typeLabel(e.type), bg, fg)
                Spacer(Modifier.weight(1f))
                Text(if (e.approx) "примерно" else relative(e.date, today), style = MaterialTheme.typography.labelMedium, color = muted)
            }
            Text(e.olympiad, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
            Text(
                listOfNotNull(e.stageName, levelLabel(e.level), e.label).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium, color = muted,
            )
            Text(eventDate(e), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text("В таблице: «${e.raw}»", style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (e.fromClaude) Text("🤖 Обновлено по сайту (Claude)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            if (e.conditional) Text("Только если пройдёшь предыдущий этап", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
                if (e.type.isReg && !e.conditional) {
                    FilledTonalButton(onClick = { actions.toggleRegistered(e.stageKeys) }, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                        Icon(Icons.Filled.Check, null, Modifier.size(18.dp))
                        Text("Я зарегистрировался", Modifier.padding(start = 6.dp))
                    }
                }
                if (e.conditional && e.prevKey != null) {
                    OutlinedButton(onClick = { actions.togglePassed(e.prevKey) }) { Text("Я прошёл дальше") }
                }
                LinkButton(if (e.type.isReg) "Регистрация" else "Сайт", e.url)
            }
        }
    }
}
