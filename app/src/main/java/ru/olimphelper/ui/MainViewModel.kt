package ru.olimphelper.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.olimphelper.data.AppState
import ru.olimphelper.data.Repository
import ru.olimphelper.data.Settings
import ru.olimphelper.data.model
import ru.olimphelper.domain.Model
import ru.olimphelper.domain.Tabs
import ru.olimphelper.domain.TabsData
import ru.olimphelper.work.Notifier
import java.time.LocalDate

data class UiState(val state: AppState, val model: Model?, val tabs: TabsData?, val today: LocalDate)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository.get(app)
    private val today = MutableStateFlow(LocalDate.now())

    val syncing = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    val ui: StateFlow<UiState?> = combine(repo.state, today) { s, d ->
        val m = s.model(d)
        UiState(s, m, m?.let { Tabs.build(it, s.registered, s.passed, d, s.settings.horizonDays) }, d)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun onResume() {
        today.value = LocalDate.now()
        if (System.currentTimeMillis() - repo.state.value.tableFetchedAt > 30 * 60_000) refresh(silent = true)
    }

    fun refresh(silent: Boolean = false) {
        if (syncing.value) return
        viewModelScope.launch {
            syncing.value = true
            val r = repo.sync()
            syncing.value = false
            if (!silent || r.errors.isNotEmpty()) {
                message.value = when {
                    r.errors.isNotEmpty() -> "Не удалось обновить " + r.errors.joinToString("; ")
                    r.tableChanges.isEmpty() && r.newFindings.isEmpty() -> "Обновлено, изменений нет"
                    else -> "Обновлено: изменений в таблице ${r.tableChanges.size}, новых находок ${r.newFindings.size}"
                }
            }
        }
    }

    fun toggleRegistered(keys: List<String>) = viewModelScope.launch { repo.toggleRegistered(keys) }
    fun togglePassed(key: String) = viewModelScope.launch { repo.togglePassed(key) }
    fun updateSettings(f: (Settings) -> Settings) = viewModelScope.launch { repo.updateSettings(f) }
    fun markChangesSeen() = viewModelScope.launch { repo.markChangesSeen() }
    fun testNotification() = Notifier.test(getApplication())
}
