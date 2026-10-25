package ru.olimphelper

import android.app.Application
import ru.olimphelper.work.Notifier
import ru.olimphelper.work.Scheduler

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannels(this)
        Scheduler.setup(this)
    }
}
