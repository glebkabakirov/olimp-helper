package ru.olimphelper.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ru.olimphelper.R
import ru.olimphelper.data.Repository
import ru.olimphelper.data.model
import ru.olimphelper.domain.Reminders
import ru.olimphelper.ui.MainActivity
import java.time.LocalDate

object Notifier {

    private const val CH_REMIND = "reminders"
    private const val CH_CHANGES = "changes"
    const val EXTRA_TAB = "tab"

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_REMIND, "Напоминания о сроках", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Регистрации и этапы олимпиад: за 3 дня, за день и в день события" }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_CHANGES, "Изменения", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Что поменялось в таблице и что Claude нашёл на сайтах" }
        )
    }

    fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun post(ctx: Context, id: Int, channel: String, title: String, text: String, tab: Int) {
        if (!canPost(ctx)) return
        val intent = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_TAB, tab)
        val pi = PendingIntent.getActivity(ctx, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (_: SecurityException) {
        }
    }

    /** Напоминания о событиях через 3 дня, завтра и сегодня. */
    suspend fun remind(ctx: Context, repo: Repository, today: LocalDate) {
        val st = repo.state.value
        val model = st.model(today) ?: return
        val due = Reminders.due(model, st.registered, st.settings, today, st.notified)
        for (d in due) post(ctx, d.id.hashCode(), CH_REMIND, Reminders.title(d), Reminders.text(d), tab = 0)

        // раз в неделю: то, что по датам не напоминается
        val digestDue = st.settings.weeklyDigest && today.toEpochDay() - st.lastDigestDay >= 7
        val digest = if (digestDue) Reminders.digest(model, st.registered, st.passed, today) else emptyList()
        if (digest.isNotEmpty()) {
            post(ctx, 1003, CH_REMIND, "Не забудь отметить: ${digest.size}",
                digest.take(6).joinToString("\n") + if (digest.size > 6) "\n…" else "", tab = 0)
        }

        if (due.isEmpty() && digest.isEmpty() && st.notified.values.none { it < today.toEpochDay() - 30 }) return
        repo.update { s ->
            val keep = s.notified.filterValues { it >= today.toEpochDay() - 30 }
            s.copy(
                notified = keep + due.associate { it.id to it.event.date.toEpochDay() },
                lastDigestDay = if (digest.isNotEmpty()) today.toEpochDay() else s.lastDigestDay,
            )
        }
    }

    fun changes(ctx: Context, result: Repository.SyncResult, notifyTable: Boolean, notifyClaude: Boolean) {
        if (notifyTable && result.tableChanges.isNotEmpty()) {
            val c = result.tableChanges
            val names = c.map { "${it.olympiad} — ${it.stage}" }.distinct()
            post(ctx, 1001, CH_CHANGES, "Таблица обновилась: ${c.size} " + ru.olimphelper.domain.plural(c.size.toLong(), "изменение", "изменения", "изменений"),
                names.take(4).joinToString("\n") + if (names.size > 4) "\n…" else "", tab = 3)
        }
        if (notifyClaude && result.newFindings.isNotEmpty()) {
            val f = result.newFindings
            post(ctx, 1002, CH_CHANGES, "Claude нашёл новое на сайтах олимпиад",
                f.take(4).joinToString("\n") { "${it.olympiad}: ${it.summary}" } + if (f.size > 4) "\n…" else "", tab = 3)
        }
    }

    fun test(ctx: Context) = post(ctx, 1000, CH_REMIND, "Проверка уведомлений", "Так будут выглядеть напоминания о сроках", tab = 0)
}
