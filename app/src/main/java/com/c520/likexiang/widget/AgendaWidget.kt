package com.c520.likexiang.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.c520.likexiang.R
import com.c520.likexiang.ui.Fmt
import com.c520.likexiang.ui.theme.Tok

/** 行程清單小工具（WID-02）：今天起的行程，可捲動，標示 ⏰；點行程開詳情，「＋」新增。 */
class AgendaWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val first = WidgetData.agenda(context, prefs[Widgets.REFRESH] ?: 0)
        provideContent {
            val refresh = currentState(Widgets.REFRESH) ?: 0
            var model by remember { mutableStateOf(first) }
            LaunchedEffect(refresh) {
                if (refresh != model.refresh) model = WidgetData.agenda(context, refresh)
            }
            AgendaContent(model)
        }
    }
}

class AgendaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AgendaWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Widgets.scheduleMidnight(context)
    }
}

@Composable
private fun AgendaContent(m: AgendaModel) {
    val context = LocalContext.current
    Column(GlanceModifier.fillMaxSize().background(Widgets.color(Tok.canvas)).cornerRadius(Tok.rXl).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(GlanceModifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "行程",
                style = TextStyle(color = Widgets.color(Tok.ink), fontSize = 17.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.defaultWeight().padding(start = 4.dp).clickable(actionStartActivity(Widgets.openApp(context))),
                maxLines = 1,
            )
            WidgetIconButton(R.drawable.ic_mic, "用語音新增行程", actionStartActivity(Widgets.voice(context)))
            WidgetIconButton(R.drawable.ic_plus, "新增行程", actionStartActivity(Widgets.newEvent(context)), primary = true)
        }
        when {
            !m.ok -> Empty("請先開啟${context.getString(R.string.app_name)}，允許讀取日曆")
            m.rows.isEmpty() -> Empty("未來 ${WidgetData.AGENDA_DAYS} 天沒有行程")
            else -> LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
                items(m.rows, itemId = { row -> rowId(row) }) { row ->
                    when (row) {
                        is AgendaRow.Header -> DateHeader(row, m)
                        is AgendaRow.Event -> EventRow(row)
                    }
                }
            }
        }
    }
}

/** LazyColumn 需要穩定的編號：日期列用負數，行程用「行程＋開始時間」的雜湊（正數）。 */
private fun rowId(row: AgendaRow): Long = when (row) {
    is AgendaRow.Header -> -row.date.toEpochDay() - 1
    is AgendaRow.Event -> row.inst.key.hashCode().toLong() and 0xFFFFFFFFL
}

@Composable
private fun DateHeader(row: AgendaRow.Header, m: AgendaModel) {
    val label = Fmt.dateWithDow(row.date) + when (row.date) {
        m.today -> "・今天"
        m.today.plusDays(1) -> "・明天"
        else -> ""
    }
    Text(
        label,
        style = TextStyle(color = Widgets.color(if (row.date == m.today) Tok.primary else Tok.slate), fontSize = 12.sp, fontWeight = FontWeight.Medium),
        modifier = GlanceModifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
        maxLines = 1,
    )
}

@Composable
private fun EventRow(row: AgendaRow.Event) {
    val context = LocalContext.current
    val inst = row.inst
    val time = if (inst.allDay) "整天" else "${Fmt.time(inst.begin)}–${Fmt.time(inst.end)}"
    val sub = if (inst.location.isNotBlank()) "$time・${inst.location}" else time
    Row(
        GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)
            .clickable(actionStartActivity(Widgets.openEvent(context, inst.eventId, inst.begin)))
            .semantics { contentDescription = "${inst.displayTitle}，$time" + if (row.alarm) "，有鬧鐘" else "" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(GlanceModifier.width(4.dp).height(34.dp).cornerRadius(Tok.rXs).background(Widgets.color(Tok.eventColor(inst.color)))) {}
        Spacer(GlanceModifier.width(8.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(
                if (row.alarm) "⏰ ${inst.displayTitle}" else inst.displayTitle,
                style = TextStyle(color = Widgets.color(Tok.ink), fontSize = 14.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            Text(sub, style = TextStyle(color = Widgets.color(Tok.slate), fontSize = 12.sp), maxLines = 1)
        }
    }
}

@Composable
private fun Empty(text: String) {
    Box(GlanceModifier.fillMaxWidth().fillMaxHeight().clickable(actionStartActivity(Widgets.openApp(LocalContext.current))), contentAlignment = Alignment.Center) {
        Text(text, style = TextStyle(color = Widgets.color(Tok.slate), fontSize = 13.sp, textAlign = TextAlign.Center))
    }
}
