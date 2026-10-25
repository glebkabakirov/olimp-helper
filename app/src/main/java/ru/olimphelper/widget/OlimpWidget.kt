package ru.olimphelper.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import ru.olimphelper.data.Repository
import ru.olimphelper.data.WidgetConfig
import ru.olimphelper.data.WidgetContent
import ru.olimphelper.data.model
import ru.olimphelper.domain.WidgetData
import ru.olimphelper.domain.WidgetItem
import ru.olimphelper.ui.MainActivity
import ru.olimphelper.ui.relative
import ru.olimphelper.ui.shortDate
import java.time.LocalDate

/** Виджет на рабочий стол: список событий, что именно показывать — в настройке виджета. */
class OlimpWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = Repository.get(context)
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        provideContent {
            val state by repo.state.collectAsState()
            val today = LocalDate.now()
            val cfg = state.widgets[widgetId] ?: WidgetConfig()
            val items = remember(state, cfg, today) {
                state.model(today)?.let { WidgetData.items(it, state.registered, state.passed, cfg, today) }.orEmpty()
            }
            GlanceTheme { Body(context, cfg, items, today) }
        }
    }

    companion object {
        suspend fun refresh(context: Context) = OlimpWidget().updateAll(context)
    }
}

class OlimpWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OlimpWidget()
}

private fun openApp(ctx: Context, olympiad: String? = null): Intent {
    val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    if (olympiad != null) {
        // у каждой строки свой data, чтобы нажатия не склеивались в один PendingIntent
        i.data = Uri.parse("olimp://olympiad/" + Uri.encode(olympiad))
        i.putExtra(MainActivity.EXTRA_OLYMPIAD, olympiad)
    }
    return i
}

@Composable
private fun Body(ctx: Context, cfg: WidgetConfig, items: List<WidgetItem>, today: LocalDate) {
    val c = GlanceTheme.colors
    val small = TextStyle(color = c.onSurfaceVariant, fontSize = 12.sp)
    Column(
        GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(16.dp).background(c.background)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(GlanceModifier.fillMaxWidth().clickable(actionStartActivity(openApp(ctx))), verticalAlignment = Alignment.CenterVertically) {
            Text(
                cfg.content.title,
                style = TextStyle(color = c.onBackground, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            if (cfg.content != WidgetContent.OPEN_REGS) Text("${cfg.days} дн.", style = small)
        }
        Spacer(GlanceModifier.height(4.dp))
        if (items.isEmpty()) {
            Text(
                if (cfg.content == WidgetContent.OPEN_REGS) "Открытых регистраций без отметки нет" else "В ближайшие ${cfg.days} дн. ничего нет",
                style = small,
            )
        } else {
            LazyColumn(GlanceModifier.fillMaxSize()) {
                items(items, itemId = { it.key.hashCode().toLong() }) { Item(ctx, it, today) }
            }
        }
    }
}

@Composable
private fun Item(ctx: Context, item: WidgetItem, today: LocalDate) {
    val c = GlanceTheme.colors
    val muted = TextStyle(color = c.onSurfaceVariant, fontSize = 12.sp)
    val date = item.date
    val whenText = when {
        date == null -> "срок не указан"
        item.until -> "до " + shortDate(date)
        else -> relative(date, today)
    }
    val detail = item.stage + if (date != null && !item.until) " · " + shortDate(date) + (item.time?.let { ", $it" }.orEmpty()) else ""
    Column(GlanceModifier.fillMaxWidth().padding(vertical = 5.dp).clickable(actionStartActivity(openApp(ctx, item.olympiad)))) {
        Row(GlanceModifier.fillMaxWidth()) {
            Text(
                item.badge,
                style = TextStyle(color = if (item.urgent) c.error else c.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(whenText, style = muted, maxLines = 1)
        }
        Text(item.olympiad, style = TextStyle(color = c.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        Text(detail, style = muted, maxLines = 1)
    }
}
