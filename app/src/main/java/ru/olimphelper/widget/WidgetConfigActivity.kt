@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package ru.olimphelper.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ru.olimphelper.data.Repository
import ru.olimphelper.data.WidgetConfig
import ru.olimphelper.data.WidgetContent
import ru.olimphelper.domain.norm
import ru.olimphelper.ui.OlimpTheme
import ru.olimphelper.ui.SectionHeader

/** Открывается, когда виджет добавляют на экран или выбирают «Настроить» по долгому нажатию. */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        setResult(RESULT_CANCELED, result)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val repo = Repository.get(this)
        val state = repo.state.value
        val olympiads = state.table?.rows?.map { it.olympiad }?.distinct().orEmpty()
        enableEdgeToEdge()
        setContent {
            OlimpTheme {
                WidgetConfigScreen(state.widgets[widgetId] ?: WidgetConfig(), olympiads, onCancel = { finish() }) { cfg ->
                    lifecycleScope.launch {
                        repo.update { it.copy(widgets = it.widgets + (widgetId to cfg)) }
                        val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(widgetId)
                        OlimpWidget().update(this@WidgetConfigActivity, glanceId)
                        setResult(RESULT_OK, result)
                        finish()
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetConfigScreen(initial: WidgetConfig, olympiads: List<String>, onCancel: () -> Unit, onSave: (WidgetConfig) -> Unit) {
    var cfg by remember { mutableStateOf(initial) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройка виджета") },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, "Отмена") } },
            )
        },
        bottomBar = {
            Button(onClick = { onSave(cfg) }, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                Text("Готово")
            }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            item { SectionHeader("Что показывать") }
            items(WidgetContent.entries) { c ->
                Row(
                    Modifier.fillMaxWidth().clickable { cfg = cfg.copy(content = c) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = cfg.content == c, onClick = { cfg = cfg.copy(content = c) })
                    Column {
                        Text(c.title, style = MaterialTheme.typography.bodyLarge)
                        Text(c.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (cfg.content != WidgetContent.OPEN_REGS) {
                item { SectionHeader("На сколько дней вперёд") }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (d in listOf(7, 14, 30, 60)) {
                            FilterChip(selected = cfg.days == d, onClick = { cfg = cfg.copy(days = d) }, label = { Text("$d дней") })
                        }
                    }
                }
            }
            item { SectionHeader("Олимпиады") }
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Все олимпиады", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(
                        checked = cfg.olympiads.isEmpty(),
                        onCheckedChange = { all -> cfg = cfg.copy(olympiads = if (all) emptySet() else olympiads.map(::norm).toSet()) },
                    )
                }
            }
            if (cfg.olympiads.isNotEmpty()) {
                items(olympiads) { name ->
                    val key = norm(name)
                    val checked = key in cfg.olympiads
                    // последнюю галочку не снимаем: пустой набор значит «все»
                    val toggle = {
                        if (!checked) cfg = cfg.copy(olympiads = cfg.olympiads + key)
                        else if (cfg.olympiads.size > 1) cfg = cfg.copy(olympiads = cfg.olympiads - key)
                    }
                    Row(Modifier.fillMaxWidth().clickable { toggle() }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = checked, onCheckedChange = { toggle() })
                        Text(name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
