package com.bryanh.calendaralarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bryanh.calendaralarm.R
import com.bryanh.calendaralarm.data.AlarmRules
import com.bryanh.calendaralarm.data.DAY_MS
import com.bryanh.calendaralarm.data.EventInstance
import com.bryanh.calendaralarm.data.Lunar
import com.bryanh.calendaralarm.data.MINUTE_MS
import com.bryanh.calendaralarm.ui.theme.Tok
import com.bryanh.calendaralarm.ui.theme.TypeTok
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** 點日期進來的當天畫面：上方一週日期列、整天行程、下方時間軸。 */
@Composable
fun DayScreen(vm: MainViewModel, date: LocalDate) {
    val instances by vm.day.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val alarmState by vm.alarmState.collectAsStateWithLifecycle()
    LaunchedEffect(date) { vm.loadDay(date) }
    val zone = ZoneId.systemDefault()
    val todays = instances.filter { !it.startDate(zone).isAfter(date) && !it.endDate(zone).isBefore(date) }
    // 整天行程與超過一天的行程放在上面；跨午夜但不到一天的照樣畫在時間軸上
    val isLong: (EventInstance) -> Boolean = { it.allDay || it.end - it.begin >= DAY_MS }
    val allDay = todays.filter(isLong)
    val timed = todays.filterNot(isLong)
    val isAlarm: (EventInstance) -> Boolean = { AlarmRules.isAlarmOn(it, alarmState) }
    val ringAt: (EventInstance) -> Long = { it.begin - (alarmState.offsetFor(it.eventId, it.originalId) ?: settings.defaultOffsetMin) * MINUTE_MS }
    val open: (EventInstance) -> Unit = { vm.navigate(Screen.Detail(it.eventId, it.begin)) }
    val go: (LocalDate) -> Unit = { vm.replaceTop(Screen.Day(it)) }

    Box(Modifier.fillMaxSize().background(Tok.canvas)) {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                // 星期放副標題：字放大時標題才放得下，不會被截成「10月11日 星期…」
                title = Fmt.dateShort(date),
                subtitle = Fmt.weekday(date) + if (settings.showLunar) " ${Lunar.fullLabel(date)}" else "",
                navIcon = R.drawable.ic_back,
                onNav = { vm.back() },
                actions = {
                    IconBtn(R.drawable.ic_chevron_left, "前一天", onClick = { go(date.minusDays(1)) })
                    IconBtn(R.drawable.ic_chevron_right, "後一天", onClick = { go(date.plusDays(1)) })
                    TodayButton(onClick = { go(LocalDate.now()) })
                    Spacer(Modifier.width(Tok.sXxs))
                },
            )
            WeekStrip(date, vm, go)
            if (allDay.isNotEmpty()) AllDayRow(allDay, isAlarm, open)
            Divider()
            var dragTotal by remember { mutableFloatStateOf(0f) }
            TimeGrid(
                days = listOf(date),
                events = timed,
                dayStartMinute = settings.dayStartMinute,
                isAlarm = isAlarm,
                ringAt = ringAt,
                onEventClick = open,
                onSlotClick = { day, hour -> vm.navigate(Screen.Edit(null, day.atTime(hour, 0))) },
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(date) {
                        detectHorizontalDragGestures(
                            onDragStart = { dragTotal = 0f },
                            onDragEnd = {
                                if (dragTotal > 120f) go(date.minusDays(1)) else if (dragTotal < -120f) go(date.plusDays(1))
                                dragTotal = 0f
                            },
                        ) { _, dx -> dragTotal += dx }
                    },
            )
        }
        Fab(onClick = { vm.navigate(Screen.Edit(null, date.atTime(LocalTime.now().hour.coerceAtMost(22) + 1, 0))) }, modifier = Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
private fun WeekStrip(date: LocalDate, vm: MainViewModel, onPick: (LocalDate) -> Unit) {
    val first = date.with(TemporalAdjusters.previousOrSame(vm.weekStart()))
    val today = LocalDate.now()
    Row(Modifier.fillMaxWidth().padding(horizontal = Tok.sXs, vertical = Tok.sXxs)) {
        (0L until 7L).map { first.plusDays(it) }.forEach { d ->
            val selected = d == date
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Tok.rMd))
                    .clickable(role = Role.Tab) { onPick(d) }
                    .semantics(mergeDescendants = true) {
                        contentDescription = Fmt.dateWithDow(d) + if (d == today) "，今天" else ""
                        this.selected = selected
                    }
                    .padding(vertical = Tok.sXxs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(Fmt.dowChar(d), style = TypeTok.caption, color = Tok.slate, modifier = Modifier.clearAndSetSemantics {})
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (selected) Tok.primary else Tok.canvas)
                        .clearAndSetSemantics {},
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${d.dayOfMonth}",
                        style = TypeTok.body,
                        color = when {
                            selected -> Tok.onPrimary
                            d == today -> Tok.primary
                            else -> Tok.ink
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AllDayRow(events: List<EventInstance>, isAlarm: (EventInstance) -> Boolean, open: (EventInstance) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val shown = if (expanded || events.size <= 3) events else events.take(2)
    Row(Modifier.fillMaxWidth().padding(vertical = Tok.sXxs), verticalAlignment = Alignment.Top) {
        Text(
            "整天",
            style = TypeTok.caption,
            color = Tok.slate,
            textAlign = TextAlign.End,
            modifier = Modifier.width(timeAxisWidth() - Tok.sXs).padding(top = Tok.sXxs),
        )
        Spacer(Modifier.width(Tok.sXs))
        Column(Modifier.weight(1f).padding(end = Tok.sSm), verticalArrangement = Arrangement.spacedBy(Tok.sXxs)) {
            shown.forEach { e ->
                EventChip(
                    e.displayTitle,
                    e.color,
                    isAlarm(e),
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 34.dp)
                        .clickable(role = Role.Button, onClickLabel = "看行程內容") { open(e) },
                    style = TypeTok.body,
                )
            }
            if (events.size > 3) {
                Text(
                    if (expanded) "收起" else "還有 ${events.size - shown.size} 個整天行程",
                    style = TypeTok.caption,
                    color = Tok.primary,
                    modifier = Modifier
                        .heightIn(min = 32.dp)
                        .clickable(role = Role.Button) { expanded = !expanded }
                        .padding(vertical = Tok.sXs),
                )
            }
        }
    }
}
