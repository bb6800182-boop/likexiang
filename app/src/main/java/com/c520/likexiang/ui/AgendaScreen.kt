package com.c520.likexiang.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.c520.likexiang.R
import com.c520.likexiang.data.AlarmRules
import com.c520.likexiang.data.EventInstance
import com.c520.likexiang.ui.theme.Tok
import com.c520.likexiang.ui.theme.TypeTok
import java.time.LocalDate
import java.time.ZoneId

/** 行程清單：依日期分組的連續清單，往下捲到底自動載入更多，最上面可以載入更早的行程。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AgendaScreen(vm: MainViewModel, onMenu: () -> Unit) {
    val data by vm.agenda.collectAsStateWithLifecycle()
    val alarmState by vm.alarmState.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    val listState = rememberLazyListState()
    val d = data
    // 進行中的跨日行程歸在範圍的第一天
    val groups: Map<LocalDate, List<EventInstance>> = remember(d) {
        if (d == null) emptyMap() else d.events.groupBy { e -> e.startDate(zone).let { if (it.isBefore(d.from)) d.from else it } }.toSortedMap()
    }
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 3
        }
    }
    // 每次載完（d 換新）都再看一次是不是已經捲到底
    LaunchedEffect(nearEnd, d) {
        if (nearEnd && d?.canLoadMore == true) vm.extendAgenda(earlier = false)
    }
    Box(Modifier.fillMaxSize().background(Tok.canvas)) {
        Column(Modifier.fillMaxSize()) {
            HomeTopBar(vm, title = "行程", subtitle = d?.let { "${Fmt.dateShort(it.from)} 起" }, mode = HomeMode.AGENDA, onMenu = onMenu)
            Divider()
            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                item(key = "earlier") {
                    Text(
                        "顯示更早的行程",
                        style = TypeTok.bodySm,
                        color = Tok.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(role = Role.Button) { vm.extendAgenda(earlier = true) }
                            .padding(vertical = Tok.sSm),
                    )
                }
                groups.forEach { (date, events) ->
                    stickyHeader(key = "h$date") { DateListHeader(date, today) }
                    items(events, key = { "${date}_${it.key}" }) { e ->
                        EventListRow(e, AlarmRules.isAlarmOn(e, alarmState), zone) { vm.navigate(Screen.Detail(e.eventId, e.begin)) }
                    }
                }
                item(key = "end") {
                    Text(
                        when {
                            d == null -> ""
                            d.canLoadMore -> "載入中…"
                            groups.isEmpty() -> "${Fmt.dateShort(d.from)} 之後沒有行程"
                            else -> "已顯示到 ${Fmt.dateShort(d.to.minusDays(1))}"
                        },
                        style = TypeTok.caption,
                        color = Tok.slate,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(Tok.sXl),
                    )
                }
            }
        }
        Fab(onClick = { vm.navigate(Screen.Edit(null, null)) }, modifier = Modifier.align(Alignment.BottomEnd))
    }
}

/** 依日期分組的清單裡，每天的標題列（捲動時黏在上面）。行程清單與搜尋結果共用。 */
@Composable
fun DateListHeader(date: LocalDate, today: LocalDate) {
    Text(
        (if (date == today) "今天・" else "") + Fmt.dateWithDow(date),
        style = TypeTok.caption,
        color = if (date == today) Tok.primary else Tok.slate,
        modifier = Modifier
            .fillMaxWidth()
            .background(Tok.surface)
            .padding(horizontal = Tok.sMd, vertical = Tok.chipPadV)
            .semantics { heading() },
    )
}

/** 清單裡的一筆行程：色條、標題、時間（搜尋結果再加地點）、鬧鐘圖示；點了看內容。 */
@Composable
fun EventListRow(e: EventInstance, alarm: Boolean, zone: ZoneId, showLocation: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "看行程內容", onClick = onClick)
            .padding(horizontal = Tok.sMd, vertical = Tok.sSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(Tok.sXxs).height(36.dp).clip(RoundedCornerShape(Tok.rXs)).background(Tok.eventColor(e.color)))
        Spacer(Modifier.width(Tok.sSm))
        Column(Modifier.weight(1f)) {
            Text(e.displayTitle, style = TypeTok.body, color = Tok.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (e.allDay || e.startDate(zone) != e.endDate(zone)) Fmt.range(e.begin, e.end, e.allDay) else "${Fmt.time(e.begin)} – ${Fmt.time(e.end)}",
                style = TypeTok.caption,
                color = Tok.slate,
            )
            if (showLocation && e.location.isNotBlank()) {
                Text(e.location, style = TypeTok.caption, color = Tok.slate, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (alarm) AppIcon(R.drawable.ic_alarm, "有鬧鐘", Tok.ink, 18.dp)
    }
}
