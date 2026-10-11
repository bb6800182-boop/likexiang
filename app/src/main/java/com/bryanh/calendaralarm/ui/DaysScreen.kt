package com.bryanh.calendaralarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bryanh.calendaralarm.data.AlarmRules
import com.bryanh.calendaralarm.data.DAY_MS
import com.bryanh.calendaralarm.data.EventInstance
import com.bryanh.calendaralarm.data.MINUTE_MS
import com.bryanh.calendaralarm.ui.layout.WeekLayoutEngine
import com.bryanh.calendaralarm.ui.layout.weekSpans
import com.bryanh.calendaralarm.ui.theme.Tok
import com.bryanh.calendaralarm.ui.theme.TypeTok
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** 整天行程區最多幾條，多的在最後一條顯示「+N」（點日期看當天全部）。 */
private const val ALL_DAY_LANES = 3

/**
 * 「N 天」檢視（自訂天數）：跟日檢視一樣的時間格，一次看 N 天。
 * 上面每天一欄的日期（點了進那一天），接著整天行程，下面時間格；左右滑換 N 天。
 */
@Composable
fun DaysScreen(vm: MainViewModel, onMenu: () -> Unit) {
    val data by vm.multiDay.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val alarmState by vm.alarmState.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    val isAlarm: (EventInstance) -> Boolean = { AlarmRules.isAlarmOn(it, alarmState) }
    val ringAt: (EventInstance) -> Long = { it.begin - (alarmState.offsetFor(it.eventId, it.originalId) ?: settings.defaultOffsetMin) * MINUTE_MS }
    val open: (EventInstance) -> Unit = { vm.navigate(Screen.Detail(it.eventId, it.begin)) }
    Box(Modifier.fillMaxSize().background(Tok.canvas)) {
        Column(Modifier.fillMaxSize()) {
            val d = data
            HomeTopBar(
                vm = vm,
                title = d?.let { Fmt.monthSpan(it.days.first(), it.days.last(), today) } ?: "",
                subtitle = null,
                mode = HomeMode.DAYS,
                onMenu = onMenu,
            )
            if (d != null) {
                // 整天行程與超過一天的放上面；跨午夜但不到一天的照樣畫在時間格上
                val isLong: (EventInstance) -> Boolean = { it.allDay || it.end - it.begin >= DAY_MS }
                DayHeader(d.days, d.lunar, today) { day -> vm.navigate(Screen.Day(day)) }
                AllDayLanes(d.days, d.instances.filter(isLong), isAlarm, zone, open)
                Divider()
                var dragTotal by remember { mutableFloatStateOf(0f) }
                TimeGrid(
                    days = d.days,
                    events = d.instances.filterNot(isLong),
                    dayStartMinute = settings.dayStartMinute,
                    isAlarm = isAlarm,
                    ringAt = ringAt,
                    onEventClick = open,
                    onSlotClick = { day, hour -> vm.navigate(Screen.Edit(null, day.atTime(hour, 0))) },
                    modifier = Modifier
                        .weight(1f)
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = { dragTotal = 0f },
                                onDragEnd = {
                                    if (dragTotal > 120f) vm.shift(-1) else if (dragTotal < -120f) vm.shift(1)
                                    dragTotal = 0f
                                },
                            ) { _, dx -> dragTotal += dx }
                        },
                )
            }
        }
        Fab(
            onClick = { vm.navigate(Screen.Edit(null, today.atTime(LocalTime.now().hour.coerceAtMost(22) + 1, 0))) },
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

/** 每天一欄的日期：星期、日期（今天用重點色圓）、農曆；欄位跟時間格對齊。 */
@Composable
private fun DayHeader(days: List<LocalDate>, lunar: Map<LocalDate, String>, today: LocalDate, onPick: (LocalDate) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(end = Tok.sXs, top = Tok.sXxs, bottom = Tok.sXxs)) {
        Spacer(Modifier.width(timeAxisWidth()))
        days.forEach { d ->
            val isToday = d == today
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Tok.rMd))
                    .clickable(role = Role.Button, onClickLabel = "看這一天") { onPick(d) }
                    .semantics(mergeDescendants = true) {
                        contentDescription = Fmt.dateWithDow(d) + (if (isToday) "，今天" else "") + (lunar[d]?.let { "，農曆$it" } ?: "")
                    }
                    .padding(vertical = Tok.sXxs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(Fmt.dowChar(d), style = TypeTok.caption, color = if (isToday) Tok.primary else Tok.slate, modifier = Modifier.clearAndSetSemantics {})
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(if (isToday) Tok.primary else Tok.canvas).clearAndSetSemantics {},
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${d.dayOfMonth}", style = TypeTok.body, color = if (isToday) Tok.onPrimary else Tok.ink)
                }
                lunar[d]?.let {
                    Text(it, style = TypeTok.lunar, color = if (isToday) Tok.primary else Tok.slate, maxLines = 1, modifier = Modifier.clearAndSetSemantics {})
                }
            }
        }
    }
}

/** 整天行程：跨日的橫跨好幾欄，跟月曆格同一套排車道的方法；最多 [ALL_DAY_LANES] 條。 */
@Composable
private fun AllDayLanes(
    days: List<LocalDate>,
    events: List<EventInstance>,
    isAlarm: (EventInstance) -> Boolean,
    zone: ZoneId,
    open: (EventInstance) -> Unit,
) {
    if (events.isEmpty()) return
    val spans = remember(events, days) { weekSpans(events, days.first(), zone, days.size) }
    val layout = remember(spans) { WeekLayoutEngine.layout(spans, ALL_DAY_LANES) }
    val density = LocalDensity.current
    val laneH = with(density) { maxOf(24.dp, TypeTok.bodySm.lineHeight.toDp() + 4.dp) }
    val lanes = ((layout.placed.maxOfOrNull { it.lane } ?: -1) + 1).coerceAtLeast(if (layout.more.isEmpty()) 0 else ALL_DAY_LANES)
    if (lanes == 0) return
    val axisW = timeAxisWidth()
    BoxWithConstraints(Modifier.fillMaxWidth().height((laneH + 2.dp) * lanes + Tok.sXxs).padding(end = Tok.sXs)) {
        val colW = (maxWidth - axisW) / days.size
        Text(
            "整天",
            style = TypeTok.caption,
            color = Tok.slate,
            textAlign = TextAlign.End,
            modifier = Modifier.width(axisW - Tok.sXs).padding(top = 2.dp).clearAndSetSemantics {},
        )
        layout.placed.forEach { p ->
            val e = events[p.id]
            EventChip(
                e.displayTitle,
                e.color,
                isAlarm(e),
                Modifier
                    .offset(x = axisW + colW * p.startCol + 1.5.dp, y = (laneH + 2.dp) * p.lane)
                    .width(colW * p.span - 3.dp)
                    .height(laneH)
                    .clickable(role = Role.Button, onClickLabel = "看行程內容") { open(e) }
                    .semantics { contentDescription = e.displayTitle + if (isAlarm(e)) "，有鬧鐘" else "" },
                style = TypeTok.bodySm,
            )
        }
        layout.more.forEach { (col, n) ->
            Text(
                "+$n",
                style = TypeTok.chip,
                color = Tok.slate,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .offset(x = axisW + colW * col, y = (laneH + 2.dp) * (ALL_DAY_LANES - 1))
                    .width(colW)
                    .height(laneH)
                    .padding(top = 3.dp)
                    .semantics { contentDescription = "還有 $n 個整天行程" },
            )
        }
    }
}
