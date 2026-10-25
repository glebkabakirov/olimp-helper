@file:OptIn(ExperimentalMaterial3Api::class)

package ru.olimphelper.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private data class TabDef(val title: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    TabDef("Скоро", "Скоро", Icons.Filled.Home),
    TabDef("Позже", "Позже", Icons.Filled.DateRange),
    TabDef("Олимпиады", "Олимпиады", Icons.AutoMirrored.Filled.List),
    TabDef("Изменения", "Изменения", Icons.Filled.Notifications),
)

@Composable
fun AppScreen(vm: MainViewModel, tabState: MutableIntState, openRequest: MutableState<String?>) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var olympiad by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val tab = tabState.intValue

    LaunchedEffect(message) {
        message?.let {
            vm.message.value = null
            snackbar.showSnackbar(it)
        }
    }
    LaunchedEffect(openRequest.value) {
        openRequest.value?.let {
            settingsOpen = false
            olympiad = it
            openRequest.value = null
        }
    }
    LaunchedEffect(tab, ui?.state?.unseenChanges) {
        if (tab == 3 && (ui?.state?.unseenChanges ?: 0) > 0) vm.markChangesSeen()
    }

    val u = ui
    if (u == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val actions = Actions(
        openOlympiad = { olympiad = it },
        toggleRegistered = { vm.toggleRegistered(it) },
        togglePassed = { vm.togglePassed(it) },
    )

    if (settingsOpen) {
        BackHandler { settingsOpen = false }
        SettingsScreen(u, vm) { settingsOpen = false }
        return
    }
    olympiad?.let { name ->
        BackHandler { olympiad = null }
        DetailScreen(u, name, actions) { olympiad = null }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(TABS[tab].title) },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        if (syncing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.Refresh, "Обновить")
                    }
                    IconButton(onClick = { settingsOpen = true }) { Icon(Icons.Filled.Settings, "Настройки") }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                TABS.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tabState.intValue = i },
                        icon = {
                            val unseen = u.state.unseenChanges
                            if (i == 3 && unseen > 0) BadgedBox(badge = { Badge { Text("$unseen") } }) { Icon(t.icon, null) }
                            else Icon(t.icon, null)
                        },
                        label = { Text(t.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            if (u.model == null) {
                EmptyHint("Таблица ещё не загружена. Проверь интернет и нажми «Обновить».")
            } else when (tab) {
                0 -> SoonScreen(u, actions)
                1 -> LaterScreen(u, actions)
                2 -> OlympiadsScreen(u, actions)
                else -> ChangesScreen(u, actions)
            }
        }
    }
}
