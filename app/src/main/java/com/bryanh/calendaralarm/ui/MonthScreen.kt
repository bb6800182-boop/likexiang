package com.bryanh.calendaralarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bryanh.calendaralarm.R
import com.bryanh.calendaralarm.data.AlarmRules
import com.bryanh.calendaralarm.data.EventInstance
import com.bryanh.calendaralarm.data.Settings
import com.bryanh.calendaralarm.ui.layout.WeekLayoutEngine
import com.bryanh.calendaralarm.ui.layout.weekSpans
import com.bryanh.calendaralarm.ui.theme.Tok
import com.bryanh.calendaralarm.ui.theme.TypeTok
import com.bryanh.calendaralarm.voice.VoiceActivity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/**
 * 首頁的月曆格：「月」整個月，或「N 週」從某週開始連續 N 週（週數少、格子就大）。
 * 每格直接顯示行程名稱，點日期進入當天時間軸，左右滑換頁。
 */
@Composable
fun MonthScreen(vm: MainViewModel, mode: HomeMode, onMenu: () -> Unit) {
    val data by (if (mode == HomeMode.WEEKS) vm.weeks else vm.month).collectAsStateWithLifecycle()
    val alarmState by vm.alarmState.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    Box(Modifier.fillMaxSize().background(Tok.canvas)) {
        Column(Modifier.fillMaxSize()) {
            val d = data
            val month = d?.month
            HomeTopBar(
                vm = vm,
                title = when {
                    d == null -> ""
                    month != null -> Fmt.monthTitle(month.year, month.monthValue, month.year != today.year)
                    else -> Fmt.monthSpan(d.days.first(), d.days.last(), today)
                },
                subtitle = d?.lunarRange,
                mode = mode,
                onMenu = onMenu,
            )
            WeekdayHeader(vm.weekStart())
            if (d != null) {
                var dragTotal by remember { mutableFloatStateOf(0f) }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(bottom = Tok.sXxs)
                        .pointerInput(mode) {
                            detectHorizontalDragGestures(
                                onDragStart = { dragTotal = 0f },
                                onDragEnd = {
                                    if (dragTotal > 120f) vm.shift(-1) else if (dragTotal < -120f) vm.shift(1)
                                    dragTotal = 0f
                                },
                            ) { _, dx -> dragTotal += dx }
                        },
                ) {
                    d.days.chunked(7).forEach { week ->
                        WeekRow(
                            week = week,
                            // 「N 週」不分月份，不把別的月的日期變淡
                            inMonth = { month == null || it.month == month.month },
                            today = today,
                            instances = d.instances,
                            lunar = d.lunar,
                            isAlarm = { inst -> AlarmRules.isAlarmOn(inst, alarmState) },
                            onDayClick = { day -> vm.navigate(Screen.Day(day)) },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                }
            }
        }
        Fab(onClick = { vm.navigate(Screen.Edit(null, null)) }, modifier = Modifier.align(Alignment.BottomEnd))
    }
}

/** 檢視選單與左側選單共用的名稱：「3 天」「2 週」跟著設定的數字。 */
fun HomeMode.label(s: Settings): String = when (this) {
    HomeMode.AGENDA -> "行程"
    HomeMode.MONTH -> "月"
    HomeMode.DAYS -> "${s.customDays} 天"
    HomeMode.WEEKS -> "${s.customWeeks} 週"
}

/** 首頁共用的頂部列：左側選單、標題（農曆）、檢視切換、上下一頁、今天。 */
@Composable
fun HomeTopBar(vm: MainViewModel, title: String, subtitle: String?, mode: HomeMode, onMenu: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var viewMenu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = Tok.topBarH), verticalAlignment = Alignment.CenterVertically) {
        IconBtn(R.drawable.ic_menu, "選單", onClick = onMenu)
        TitleBlock(title, subtitle, Modifier.weight(1f))
        IconBtn(R.drawable.ic_search, "搜尋行程", onClick = { vm.openSearch() })
        Box {
            Row(
                Modifier
                    .heightIn(min = 36.dp)
                    .clip(RoundedCornerShape(Tok.rMd))
                    .border(1.dp, Tok.hairlineStrong, RoundedCornerShape(Tok.rMd))
                    .clickable(role = Role.Button, onClickLabel = "切換檢視") { viewMenu = true }
                    .padding(start = Tok.sSm, end = Tok.sXs, top = Tok.chipPadV, bottom = Tok.chipPadV),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(mode.label(settings), style = TypeTok.bodySm, color = Tok.ink)
                AppIcon(R.drawable.ic_chevron_down, null, Tok.slate, 16.dp)
            }
            DropdownMenu(expanded = viewMenu, onDismissRequest = { viewMenu = false }, containerColor = Tok.canvas) {
                MenuItem(HomeMode.AGENDA.label(settings)) { viewMenu = false; vm.setHomeMode(HomeMode.AGENDA) }
                MenuItem("日") { viewMenu = false; vm.openToday() }
                MenuItem(HomeMode.DAYS.label(settings)) { viewMenu = false; vm.setHomeMode(HomeMode.DAYS) }
                MenuItem(HomeMode.MONTH.label(settings)) { viewMenu = false; vm.setHomeMode(HomeMode.MONTH) }
                MenuItem(HomeMode.WEEKS.label(settings)) { viewMenu = false; vm.setHomeMode(HomeMode.WEEKS) }
            }
        }
        val unit = when (mode) {
            HomeMode.MONTH -> "個月"
            HomeMode.WEEKS -> "${settings.customWeeks} 週"
            HomeMode.DAYS -> "${settings.customDays} 天"
            HomeMode.AGENDA -> null
        }
        if (unit != null) {
            IconBtn(R.drawable.ic_chevron_left, if (mode == HomeMode.MONTH) "上個月" else "前 $unit", onClick = { vm.shift(-1) })
            IconBtn(R.drawable.ic_chevron_right, if (mode == HomeMode.MONTH) "下個月" else "後 $unit", onClick = { vm.shift(1) })
        } else {
            Spacer(Modifier.width(Tok.sXs))
        }
        TodayButton(onClick = { vm.goToday() })
        Spacer(Modifier.width(Tok.sXxs))
    }
}

@Composable
private fun MenuItem(text: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text, style = TypeTok.body, color = Tok.ink) }, onClick = onClick)
}

@Composable
fun WeekdayHeader(weekStart: DayOfWeek) {
    val labels = listOf("一", "二", "三", "四", "五", "六", "日")
    val ordered = if (weekStart == DayOfWeek.MONDAY) labels else listOf("日") + labels.dropLast(1)
    Row(Modifier.fillMaxWidth().heightIn(min = 22.dp).clearAndSetSemantics {}, verticalAlignment = Alignment.CenterVertically) {
        ordered.forEach { Text(it, style = TypeTok.caption, color = Tok.slate, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
    }
}

// 月曆格的版面常數（DESIGN.md「Layout」：行程色塊 16dp 高、間距 2dp）；字體放大時跟著放大
private val LABEL_H = 20.dp
private val LANE_H = 16.dp
private val LANE_GAP = 2.dp
private val CHIP_INSET = 1.5.dp

@Composable
private fun WeekRow(
    week: List<LocalDate>,
    inMonth: (LocalDate) -> Boolean,
    today: LocalDate,
    instances: List<EventInstance>,
    lunar: Map<LocalDate, String>,
    isAlarm: (EventInstance) -> Boolean,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier,
) {
    val zone = ZoneId.systemDefault()
    val first = week.first()
    val density = LocalDensity.current
    val laneH = with(density) { maxOf(LANE_H, TypeTok.chip.lineHeight.toDp()) }
    val labelH = with(density) { maxOf(LABEL_H, TypeTok.caption.lineHeight.toDp() + 3.dp) }
    val spans = remember(instances, first) { weekSpans(instances, first, zone) }
    // 每一天有哪些行程：給讀屏唸「幾個行程：標題…」
    val perDay = remember(spans) {
        List(7) { col -> spans.filter { col in it.startCol until it.startCol + it.span }.sortedBy { it.sortKey }.map { instances[it.id] } }
    }
    BoxWithConstraints(
        modifier.drawBehind {
            val stroke = 1.dp.toPx()
            drawLine(Tok.hairline, Offset(0f, 0f), Offset(size.width, 0f), stroke)
            for (i in 1..6) {
                val x = size.width * i / 7f
                drawLine(Tok.hairline, Offset(x, 0f), Offset(x, size.height), stroke)
            }
        },
    ) {
        val lanesFit = (((maxHeight - labelH - Tok.sXxs) / (laneH + LANE_GAP)).toInt()).coerceIn(1, 20)
        val layout = remember(spans, lanesFit) { WeekLayoutEngine.layout(spans, lanesFit) }
        val colW = maxWidth / 7
        Row(Modifier.fillMaxWidth().height(labelH).padding(top = 2.dp).clearAndSetSemantics {}) {
            week.forEach { day -> DayLabel(day, inMonth(day), day == today, lunar[day], Modifier.weight(1f)) }
        }
        layout.placed.forEach { p ->
            val inst = instances[p.id]
            EventChip(
                title = inst.displayTitle,
                argb = inst.color,
                alarm = isAlarm(inst),
                modifier = Modifier
                    .offset(x = colW * p.startCol + CHIP_INSET, y = labelH + 2.dp + (laneH + LANE_GAP) * p.lane)
                    .width(colW * p.span - CHIP_INSET * 2)
                    .height(laneH)
                    .clearAndSetSemantics {},
            )
        }
        layout.more.forEach { (col, n) ->
            Text(
                "+$n",
                style = TypeTok.chip,
                color = Tok.slate,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .offset(x = colW * col, y = labelH + 2.dp + (laneH + LANE_GAP) * (lanesFit - 1))
                    .width(colW)
                    .height(laneH)
                    .clearAndSetSemantics {},
            )
        }
        // 點日期進入當天；讀屏一次唸完日期、農曆與當天行程
        Row(Modifier.matchParentSize()) {
            week.forEachIndexed { i, day ->
                val events = perDay[i]
                val label = buildString {
                    append(Fmt.dateWithDow(day))
                    lunar[day]?.let { append("，農曆").append(it) }
                    if (events.isEmpty()) {
                        append("，沒有行程")
                    } else {
                        append("，${events.size} 個行程：")
                        append(events.take(5).joinToString("、") { if (isAlarm(it)) "${it.displayTitle}（鬧鐘）" else it.displayTitle })
                        if (events.size > 5) append(" 等")
                    }
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(role = Role.Button, onClickLabel = "看當天行程") { onDayClick(day) }
                        .semantics { contentDescription = label },
                )
            }
        }
    }
}

@Composable
private fun DayLabel(day: LocalDate, inMonth: Boolean, isToday: Boolean, lunar: String?, modifier: Modifier) {
    Row(modifier.fillMaxHeight(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .defaultMinSize(minWidth = 18.dp, minHeight = 17.dp)
                .clip(RoundedCornerShape(Tok.rFull))
                .background(if (isToday) Tok.primary else Tok.canvas)
                .padding(horizontal = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${day.dayOfMonth}",
                style = TypeTok.caption,
                color = when {
                    isToday -> Tok.onPrimary
                    inMonth -> Tok.ink
                    else -> Tok.steel
                },
            )
        }
        if (!lunar.isNullOrEmpty()) {
            Spacer(Modifier.width(2.dp))
            Text(
                lunar,
                style = TypeTok.lunar,
                color = when {
                    isToday -> Tok.primary
                    inMonth -> Tok.slate
                    else -> Tok.steel
                },
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * 右下角新增鍵：DESIGN.md 裡唯一有陰影的元件。上方的麥克風是語音新增（fab-voice）：
 * 次要動作，白底細框、不加陰影。
 */
@Composable
fun Fab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier.padding(end = Tok.sMd, bottom = Tok.sLg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Tok.sSm),
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Tok.canvas)
                .border(1.dp, Tok.hairlineStrong, CircleShape)
                .clickable(role = Role.Button) { context.startActivity(VoiceActivity.intent(context)) }
                .semantics { contentDescription = "用語音新增行程" },
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(R.drawable.ic_mic, null, Tok.primary, 24.dp)
        }
        Box(
            Modifier
                .shadow(Tok.fabElevation, RoundedCornerShape(Tok.rXl))
                .clip(RoundedCornerShape(Tok.rXl))
                .background(Tok.primary)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = "新增行程" }
                .padding(Tok.sMd),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(R.drawable.ic_plus, null, Tok.onPrimary, 24.dp)
        }
    }
}
