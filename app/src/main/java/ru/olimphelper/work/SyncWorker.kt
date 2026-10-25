package ru.olimphelper.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import ru.olimphelper.data.Repository
import ru.olimphelper.widget.OlimpWidget
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Синхронизация с таблицей и находками Claude, уведомления об изменениях и утренние напоминания. */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val repo = Repository.get(ctx)
        val result = repo.sync()
        val s = repo.state.value.settings
        Notifier.changes(ctx, result, s.notifyTableChanges, s.notifyClaude)
        val now = LocalDateTime.now()
        if (now.hour >= 9) Notifier.remind(ctx, repo, now.toLocalDate())
        // «через 3 дня» на виджете должно стать «через 2 дня», даже если данные не менялись
        runCatching { OlimpWidget.refresh(ctx) }
        if (inputData.getBoolean(KEY_MORNING, false)) Scheduler.scheduleMorning(ctx, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }

    companion object {
        const val KEY_MORNING = "morning"
    }
}

object Scheduler {

    fun setup(ctx: Context) {
        val periodic = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("sync", ExistingPeriodicWorkPolicy.KEEP, periodic)
        scheduleMorning(ctx, ExistingWorkPolicy.KEEP)
    }

    /** Следующий запуск в 9:05: напоминания приходят утром, даже если интернета нет. */
    fun scheduleMorning(ctx: Context, policy: ExistingWorkPolicy) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(9, 5)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val req = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(SyncWorker.KEY_MORNING to true))
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("morning", policy, req)
    }
}
