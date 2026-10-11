package com.bryanh.calendaralarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bryanh.calendaralarm.R
import com.bryanh.calendaralarm.data.EventInstance
import com.bryanh.calendaralarm.data.MINUTE_MS
import com.bryanh.calendaralarm.ui.layout.DayItem
import com.bryanh.calendaralarm.ui.layout.DayLayoutEngine
import com.bryanh.calendaralarm.ui.layout.DayPlaced
import com.bryanh.calendaralarm.ui.theme.Tok
import com.bryanh.calendaralarm.ui.theme.TypeTok
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** 時間軸左邊的時間欄寬度：放得下「00:00」，字放大就跟著變寬；多日檢視的日期列、整天行程也從這裡開始對齊。 */
@Composable
fun timeAxisWidth(): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(measurer, density) {
        with(density) { measurer.measure("00:00", TypeTok.caption, maxLines = 1).size.width.toDp() } + Tok.sXs * 2
    }
}
private val EVENT_PAD_V = 3.dp

/** 一欄（一天）裡排好位置的行程。startMin／endMin 從當天 00:00 起算。 */
private class DayCol(val day: LocalDate, val events: List<EventInstance>, val placed: List<DayPlaced>)

/**
 * 日檢視與「N 天」檢視共用的時間格：左邊時間、右邊每天一欄。
 * 從「一天的開始時間」畫到 24:00；有更早的行程就往前延伸到那個整點（例外照樣顯示）。
 * 一欄（日檢視）的字比較大，多欄時欄寬窄，用小一號的字。
 */
@Composable
fun TimeGrid(
    days: List<LocalDate>,
    events: List<EventInstance>,
    dayStartMinute: Int,
    isAlarm: (EventInstance) -> Boolean,
    ringAt: (EventInstance) -> Long,
    onEventClick: (EventInstance) -> Unit,
    onSlotClick: (LocalDate, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    val density = LocalDensity.current
    val axisW = timeAxisWidth()
    // 整點的時間字置中在那條線上；最上面留半行，第一個時間才不會被切掉
    val labelH = with(density) { TypeTok.caption.lineHeight.toDp() }
    val single = days.size == 1
    val titleStyle = if (single) TypeTok.body.copy(fontWeight = FontWeight.Medium) else TypeTok.caption
    val lineStyle = if (single) TypeTok.caption else TypeTok.chip
    // 字放大時一小時也跟著變高；上下寬鬆一點，往下多滑沒關係
    val hourH = if (single) maxOf(64.dp, 52.dp * density.fontScale) else maxOf(60.dp, 46.dp * density.fontScale)
    // 最短的行程也要放得下一行標題
    val minMinutes = with(density) { ((titleStyle.lineHeight.toDp() + EVENT_PAD_V * 2 + 2.dp) / hourH * 60).toInt() + 1 }
    val columns = remember(days, events, minMinutes) {
        days.map { d ->
            val start = d.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val list = events.filter { it.begin < end && it.end > start }
            val items = list.mapIndexed { i, e ->
                DayItem(i, ((e.begin - start) / MINUTE_MS).toInt().coerceIn(0, 24 * 60), ((e.end - start) / MINUTE_MS).toInt().coerceIn(0, 24 * 60))
            }
            DayCol(d, list, DayLayoutEngine.layout(items, minMinutes))
        }
    }
    val earliest = columns.flatMap { c -> c.placed.map { it.startMin } }.minOrNull() ?: Int.MAX_VALUE
    val startHour = (minOf(dayStartMinute, earliest) / 60).coerceIn(0, 23)
    val hours = 24 - startHour

    val today = LocalDate.now()
    val todayCol = days.indexOf(today)
    var nowMin by remember { mutableIntStateOf(LocalTime.now().toSecondOfDay() / 60) }
    if (todayCol >= 0) {
        // 目前時間線每分鐘往下移
        LaunchedEffect(Unit) {
            while (true) {
                nowMin = LocalTime.now().toSecondOfDay() / 60
                delay(MINUTE_MS - System.currentTimeMillis() % MINUTE_MS)
            }
        }
    }
    val scroll = rememberScrollState()
    LaunchedEffect(days.first(), startHour) {
        // 今天：捲到現在的前一小時；其他天：從一天的開始時間看起
        val target = if (todayCol >= 0) (LocalTime.now().hour - 1 - startHour).coerceAtLeast(0) else 0
        scroll.scrollTo(with(density) { (hourH * target).roundToPx() })
    }

    Box(modifier.fillMaxWidth().verticalScroll(scroll)) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = labelH / 2).height(hourH * hours + Tok.sXs)) {
            val area = maxWidth - axisW - Tok.sXs
            val colW = area / days.size
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(hourH * hours)
                    .pointerInput(days, startHour) {
                        detectTapGestures { pos ->
                            val col = ((pos.x - axisW.toPx()) / colW.toPx()).toInt()
                            if (col in days.indices) onSlotClick(days[col], (startHour + (pos.y / hourH.toPx()).toInt()).coerceIn(0, 23))
                        }
                    },
            )
            for (h in startHour..23) {
                val y = hourH * (h - startHour)
                Text(
                    Fmt.minuteOfDay(h * 60),
                    style = TypeTok.caption,
                    color = Tok.slate,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .offset(y = y - labelH / 2)
                        .width(axisW - Tok.sXs)
                        .clearAndSetSemantics {},
                )
                Box(Modifier.offset(y = y).padding(start = axisW).fillMaxWidth().height(1.dp).background(Tok.hairline))
            }
            // 多日：每天之間一條直線
            for (i in 1 until days.size) {
                Box(Modifier.offset(x = axisW + colW * i).width(1.dp).height(hourH * hours).background(Tok.hairline))
            }
            columns.forEachIndexed { ci, c ->
                c.placed.forEach { p ->
                    val e = c.events[p.id]
                    val w = colW / p.columns
                    val alarm = isAlarm(e)
                    EventBlock(
                        e = e,
                        alarm = alarm,
                        ringLabel = if (alarm) "${Fmt.time(ringAt(e))} 響鈴" else null,
                        titleStyle = titleStyle,
                        lineStyle = lineStyle,
                        modifier = Modifier
                            .offset(x = axisW + colW * ci + w * p.column + 1.dp, y = hourH * ((p.startMin - startHour * 60) / 60f))
                            .width(w - 2.dp)
                            .height(hourH * ((p.endMin - p.startMin) / 60f) - 2.dp)
                            .clickable(role = Role.Button, onClickLabel = "看行程內容") { onEventClick(e) },
                    )
                }
            }
            if (todayCol >= 0) {
                Box(
                    Modifier
                        // 每分鐘變一次：用 lambda 版的 offset，只重新擺放、不重新組合
                        .offset { IntOffset((axisW + colW * todayCol).roundToPx() - if (single) Tok.sXxs.roundToPx() else 0, (hourH * ((nowMin - startHour * 60) / 60f)).roundToPx()) }
                        .width(if (single) colW + Tok.sXxs else colW)
                        .height(2.dp)
                        .background(Tok.primary)
                        .clearAndSetSemantics {},
                )
            }
        }
    }
}

@Composable
private fun EventBlock(e: EventInstance, alarm: Boolean, ringLabel: String?, titleStyle: TextStyle, lineStyle: TextStyle, modifier: Modifier) {
    val desc = "${e.displayTitle}，${Fmt.time(e.begin)} 到 ${Fmt.time(e.end)}" +
        (if (e.location.isNotBlank()) "，${e.location}" else "") +
        (ringLabel?.let { "，$it" } ?: "")
    val fg = Tok.onEventColor(e.color)
    Box(
        modifier
            .clip(RoundedCornerShape(Tok.rSm))
            .background(Tok.eventColor(e.color))
            .semantics { contentDescription = desc },
    ) {
        // 只畫放得下的整行：高度不夠時依序省略響鈴時間、行程時間，不會出現被切一半的字
        BoxWithConstraints(Modifier.padding(horizontal = Tok.sXxs + 2.dp, vertical = EVENT_PAD_V).clearAndSetSemantics {}) {
            val density = LocalDensity.current
            val titleH = with(density) { titleStyle.lineHeight.toDp() }
            val lineH = with(density) { lineStyle.lineHeight.toDp() }
            val showTime = maxHeight >= titleH + lineH
            val showRing = maxHeight >= titleH + lineH * 2
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(e.displayTitle, style = titleStyle, color = fg, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.weight(1f, fill = false))
                    if (alarm) {
                        Spacer(Modifier.width(Tok.sXxs))
                        AppIcon(R.drawable.ic_alarm, null, fg, iconSize(titleStyle))
                    }
                }
                if (showTime) {
                    Text(
                        "${Fmt.time(e.begin)}–${Fmt.time(e.end)}" + if (e.location.isNotBlank()) "・${e.location}" else "",
                        style = lineStyle,
                        color = fg,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
                if (ringLabel != null && showRing) {
                    Text(ringLabel, style = lineStyle, color = fg, maxLines = 1, overflow = TextOverflow.Clip)
                }
            }
        }
    }
}

private fun iconSize(style: TextStyle): Dp = if (style.fontSize.value >= 14f) 14.dp else 12.dp
