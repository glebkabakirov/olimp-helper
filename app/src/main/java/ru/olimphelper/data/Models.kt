package ru.olimphelper.data

import kotlinx.serialization.Serializable

/** Строка таблицы как есть, без интерпретации. */
@Serializable
data class SheetRow(
    val index: Int,
    val olympiad: String,
    val level: String = "",
    val stage: String = "",
    val dateText: String = "",
    val dateStatus: String = "",
    val note: String = "",
    val sourceUrl: String? = null,
    val regStatus: String = "",
    val regUrl: String? = null,
    val checked: String = "",
)

@Serializable
data class SheetTable(
    val rows: List<SheetRow>,
    /** Год начала учебного года из шапки («2026/27» → 2026). */
    val seasonStartYear: Int? = null,
    /** Сноски под таблицей («Проверено 23.10.2026» и т. п.). */
    val footnote: String? = null,
)

/** Файл, который Claude кладёт в репозиторий после проверки сайтов: только отличия сайтов от таблицы. */
@Serializable
data class UpdatesFile(
    val generatedAt: String? = null,
    val items: List<UpdateItem> = emptyList(),
)

@Serializable
data class UpdateItem(
    val id: String,
    val olympiad: String,
    val stage: String,
    /** "update" — поправка существующего этапа, "new_stage" — этапа нет в таблице, "info" — просто заметка. */
    val kind: String = "update",
    /** Новая «Дата этапа» в формате таблицы. */
    val date: String? = null,
    /** Новый статус регистрации: «ОТКРЫТА», «ОТКРОЕТСЯ 01.12.2026», «ЗАКРЫТА»… */
    val registration: String? = null,
    /** Окно регистрации: «01.11.2026 – 20.11.2026, 12:00 МСК». */
    val registrationDates: String? = null,
    val level: String? = null,
    val summary: String = "",
    val sourceUrl: String? = null,
    val checkedAt: String? = null,
) {
    /** Меняется, когда меняется содержание находки, чтобы о ней сообщить заново. */
    val fingerprint: String get() = "$id@" + listOf(date, registration, registrationDates, summary).hashCode()
}

@Serializable
data class FieldChange(val field: String, val old: String, val new: String)

@Serializable
data class ChangeEntry(
    val at: Long,
    /** "changed", "added", "removed" — из таблицы; "claude" — находка на сайте. */
    val kind: String,
    val olympiad: String,
    val stage: String,
    val fields: List<FieldChange> = emptyList(),
    val summary: String = "",
    val url: String? = null,
)

@Serializable
data class Settings(
    val horizonDays: Int = 14,
    /** null — показывать даты для всех классов. */
    val grade: Int? = 11,
    val remind3: Boolean = true,
    val remind1: Boolean = true,
    val remind0: Boolean = true,
    val notifyTableChanges: Boolean = true,
    val notifyClaude: Boolean = true,
    val sheetUrl: String = DEFAULT_SHEET_URL,
    val updatesUrl: String = DEFAULT_UPDATES_URL,
)

const val DEFAULT_SHEET_URL =
    "https://docs.google.com/spreadsheets/d/16wKSRx8b7BC8YQeT9ulPl3gHKJ7hHWq0/edit?gid=749938116"
const val DEFAULT_UPDATES_URL =
    "https://raw.githubusercontent.com/glebkabakirov/olimp-helper/main/data/updates.json"

@Serializable
data class AppState(
    val table: SheetTable? = null,
    val tableFetchedAt: Long = 0,
    val tableError: String? = null,
    val updates: UpdatesFile = UpdatesFile(),
    val updatesFetchedAt: Long = 0,
    val updatesError: String? = null,
    val registered: Set<String> = emptySet(),
    val passed: Set<String> = emptySet(),
    val settings: Settings = Settings(),
    val changes: List<ChangeEntry> = emptyList(),
    val unseenChanges: Int = 0,
    val seenUpdates: Set<String> = emptySet(),
    /** id напоминания → день события (epochDay), чтобы не слать одно и то же дважды. */
    val notified: Map<String, Long> = emptyMap(),
)
