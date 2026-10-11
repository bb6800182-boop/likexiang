package com.bryanh.calendaralarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bryanh.calendaralarm.R
import com.bryanh.calendaralarm.ui.theme.Tok
import com.bryanh.calendaralarm.ui.theme.TypeTok
import java.time.Instant
import java.time.ZoneId

/** 鬧鐘清單：未來 7 天會響的鬧鐘，可單次略過。 */
@Composable
fun AlarmsScreen(vm: MainViewModel) {
    val list by vm.upcoming.collectAsStateWithLifecycle()
    val alarmState by vm.alarmState.collectAsStateWithLifecycle()
    LaunchedEffect(alarmState) { vm.loadUpcoming() }
    val zone = ZoneId.systemDefault()
    Column(Modifier.fillMaxSize().background(Tok.canvas)) {
        TopBar("鬧鐘清單", subtitle = "未來 7 天", navIcon = R.drawable.ic_back, onNav = { vm.back() })
        Divider()
        if (list.isEmpty()) {
            Text("未來 7 天沒有鬧鐘。新增行程時打開「鬧鐘提醒」就會出現在這裡。", style = TypeTok.body, color = Tok.slate, modifier = Modifier.padding(Tok.sXl))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(list, key = { it.key + if (it.snoozed) "#s" else "" }) { a ->
                val date = Instant.ofEpochMilli(a.triggerAt).atZone(zone).toLocalDate()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClickLabel = "看行程內容") { vm.navigate(Screen.Detail(a.eventId, a.begin)) }
                        .padding(horizontal = Tok.sMd, vertical = Tok.sSm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.widthIn(min = 72.dp)) {
                        Text(Fmt.time(a.triggerAt), style = TypeTok.titleMd, color = if (a.skipped) Tok.steel else Tok.ink)
                        Text(Fmt.dateWithDow(date), style = TypeTok.caption, color = Tok.slate)
                    }
                    Spacer(Modifier.width(Tok.sSm))
                    Column(Modifier.weight(1f)) {
                        Text(
                            a.title,
                            style = TypeTok.body,
                            color = if (a.skipped) Tok.steel else Tok.ink,
                            textDecoration = if (a.skipped) TextDecoration.LineThrough else null,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            when {
                                a.skipped -> "這次略過"
                                a.snoozed -> "稍後再響"
                                a.allDay -> "整天行程"
                                else -> "${Fmt.time(a.begin)} 開始"
                            },
                            style = TypeTok.caption,
                            color = Tok.slate,
                        )
                    }
                    if (!a.snoozed) {
                        OutlineButton(if (a.skipped) "恢復" else "略過這次", onClick = { vm.setSkip(a.key, !a.skipped) })
                    }
                }
                Divider()
            }
        }
    }
}
