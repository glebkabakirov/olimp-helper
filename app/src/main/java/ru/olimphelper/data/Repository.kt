package ru.olimphelper.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import ru.olimphelper.domain.EventBuilder
import ru.olimphelper.domain.Model
import ru.olimphelper.domain.TableDiff
import java.io.File
import java.time.LocalDate

/** Всё состояние приложения в одном JSON-файле во внутренней памяти. */
class Repository private constructor(private val ctx: Context) {

    private val file = File(ctx.filesDir, "state.json")
    private val mutex = Mutex()
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppState> = _state

    data class SyncResult(
        val tableChanges: List<ChangeEntry>,
        val newFindings: List<UpdateItem>,
        val errors: List<String>,
    )

    private fun load(): AppState {
        var saved = runCatching { JSON.decodeFromString<AppState>(file.readText()) }.getOrNull() ?: AppState()
        // В ранних сборках адрес находок был другим: подставляем нынешний.
        val oldUrls = listOf("/OWNER/", "/olimp-helper/main/data/updates.json")
        if (oldUrls.any { it in saved.settings.updatesUrl }) {
            saved = saved.copy(settings = saved.settings.copy(updatesUrl = DEFAULT_UPDATES_URL))
        }
        if (saved.table != null) return saved
        // Первый запуск: снимок таблицы, вшитый в приложение, чтобы было что показать без интернета.
        val bundled = runCatching { SheetSource.parse(ctx.assets.open("table.xlsx").use { it.readBytes() }) }.getOrNull()
        return saved.copy(table = bundled)
    }

    suspend fun update(f: (AppState) -> AppState): AppState = mutex.withLock {
        val next = f(_state.value)
        _state.value = next
        withContext(Dispatchers.IO) {
            val tmp = File(file.path + ".tmp")
            tmp.writeText(JSON.encodeToString(AppState.serializer(), next))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
        next
    }

    suspend fun sync(): SyncResult = withContext(Dispatchers.IO) {
        val settings = _state.value.settings
        val now = System.currentTimeMillis()
        val table = runCatching { SheetSource.parse(Net.get(SheetSource.exportUrl(settings.sheetUrl))) }
        val updates = if (settings.updatesUrl.isBlank() || "/OWNER/" in settings.updatesUrl) null
        else runCatching {
            try {
                JSON.decodeFromString(UpdatesFile.serializer(), Net.get(settings.updatesUrl).decodeToString())
            } catch (e: Net.HttpError) {
                // Claude ещё ни разу не проверял сайты, файла пока нет
                if (e.code == 404) UpdatesFile() else throw e
            }
        }

        var changes = emptyList<ChangeEntry>()
        var fresh = emptyList<UpdateItem>()
        update { st ->
            var n = st
            table.onSuccess { t ->
                changes = st.table?.let { TableDiff.diff(it.rows, t.rows, now) }.orEmpty()
                n = n.copy(table = t, tableFetchedAt = now, tableError = null)
            }.onFailure { n = n.copy(tableError = it.message ?: it.toString()) }
            updates?.onSuccess { u ->
                fresh = u.items.filter { it.fingerprint !in st.seenUpdates }
                n = n.copy(updates = u, updatesFetchedAt = now, updatesError = null,
                    seenUpdates = st.seenUpdates + fresh.map { it.fingerprint })
            }?.onFailure { n = n.copy(updatesError = it.message ?: it.toString()) }
            val found = fresh.map { ChangeEntry(now, "claude", it.olympiad, it.stage, summary = it.summary, url = it.sourceUrl) }
            val added = found + changes
            n.copy(changes = (added + n.changes).take(300), unseenChanges = n.unseenChanges + added.size)
        }
        SyncResult(
            tableChanges = changes,
            newFindings = fresh,
            errors = listOfNotNull(
                table.exceptionOrNull()?.let { "таблица: ${it.message}" },
                updates?.exceptionOrNull()?.let { "находки Claude: ${it.message}" },
            ),
        )
    }

    suspend fun toggleRegistered(keys: List<String>) = update { st ->
        st.copy(registered = if (keys.all { it in st.registered }) st.registered - keys.toSet() else st.registered + keys)
    }

    suspend fun togglePassed(key: String) = update { st ->
        st.copy(passed = if (key in st.passed) st.passed - key else st.passed + key)
    }

    suspend fun updateSettings(f: (Settings) -> Settings) = update { it.copy(settings = f(it.settings)) }

    suspend fun markChangesSeen() = update { it.copy(unseenChanges = 0) }

    companion object {
        val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }

        @Volatile private var instance: Repository? = null

        fun get(ctx: Context): Repository = instance ?: synchronized(this) {
            instance ?: Repository(ctx.applicationContext).also { instance = it }
        }
    }
}

fun AppState.model(today: LocalDate): Model? =
    table?.let { EventBuilder.build(it, updates, settings.grade, passed, today) }
