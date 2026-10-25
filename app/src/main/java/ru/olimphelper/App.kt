package ru.olimphelper

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import ru.olimphelper.data.Repository
import ru.olimphelper.widget.OlimpWidget
import ru.olimphelper.work.Notifier
import ru.olimphelper.work.Scheduler

class App : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannels(this)
        Scheduler.setup(this)
        // Отметки, синхронизация и настройки сразу отражаются на виджетах.
        scope.launch {
            Repository.get(this@App).state.drop(1).debounce(500).collect {
                runCatching { OlimpWidget.refresh(this@App) }
            }
        }
    }
}
