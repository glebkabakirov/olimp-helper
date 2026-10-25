@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package ru.olimphelper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ru.olimphelper.data.DEFAULT_SHEET_URL
import ru.olimphelper.data.Settings
import ru.olimphelper.work.Notifier

@Composable
fun SettingsScreen(u: UiState, vm: MainViewModel, onBack: () -> Unit) {
    val s = u.state.settings
    val ctx = LocalContext.current
    fun set(f: (Settings) -> Settings) = vm.updateSettings(f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionHeader("Вкладка «Скоро»", "сколько дней вперёд показывать")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (d in listOf(7, 14, 30)) {
                    FilterChip(selected = s.horizonDays == d, onClick = { set { it.copy(horizonDays = d) } }, label = { Text("$d дней") })
                }
            }

            SectionHeader("Класс", "у некоторых олимпиад даты для разных классов разные")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((label, g) in listOf("6–9" to 9, "10" to 10, "11" to 11, "Все" to null)) {
                    FilterChip(selected = s.grade == g, onClick = { set { it.copy(grade = g) } }, label = { Text(label) })
                }
            }

            SectionHeader("Напоминания", "приходят утром после 9:00")
            SwitchRow("За 3 дня", s.remind3) { v -> set { it.copy(remind3 = v) } }
            SwitchRow("За день", s.remind1) { v -> set { it.copy(remind1 = v) } }
            SwitchRow("В день события", s.remind0) { v -> set { it.copy(remind0 = v) } }
            SwitchRow("Раз в неделю: где не отметился", s.weeklyDigest) { v -> set { it.copy(weeklyDigest = v) } }
            SwitchRow("Изменения в таблице", s.notifyTableChanges) { v -> set { it.copy(notifyTableChanges = v) } }
            SwitchRow("Находки Claude на сайтах", s.notifyClaude) { v -> set { it.copy(notifyClaude = v) } }
            if (!Notifier.canPost(ctx)) {
                Text("Уведомления запрещены в настройках Android: включи их для приложения, иначе напоминаний не будет.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            OutlinedButton(onClick = { vm.testNotification() }) { Text("Проверить уведомление") }

            SectionHeader("Виджет")
            Text(
                "Долго нажми на пустое место рабочего стола → «Виджеты» → «Олимп-хелпер». При добавлении выберешь, " +
                    "что показывать: всё ближайшее, только регистрации, только этапы или открытые регистрации, " +
                    "на сколько дней и по каким олимпиадам. Поменять потом: долгое нажатие на виджет → «Настроить».",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionHeader("Источники")
            UrlField("Google-таблица", s.sheetUrl, reset = DEFAULT_SHEET_URL) { v -> set { it.copy(sheetUrl = v) } }
            UrlField("Файл находок Claude (updates.json)", s.updatesUrl, reset = null) { v -> set { it.copy(updatesUrl = v) } }
            Text("Таблица обновлена: ${stamp(u.state.tableFetchedAt)}", style = MaterialTheme.typography.bodySmall)
            Button(onClick = { vm.refresh() }) { Text("Обновить сейчас") }
            val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() }
            Text("Олимп-хелпер ${version.orEmpty()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp))
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun UrlField(label: String, value: String, reset: String?, onSave: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        maxLines = 3,
    )
    Row {
        if (text.trim() != value) TextButton(onClick = { onSave(text.trim()) }) { Text("Сохранить") }
        if (reset != null && value != reset) TextButton(onClick = { onSave(reset) }) { Text("По умолчанию") }
    }
}
