package com.bryanh.calendaralarm.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bryanh.calendaralarm.R
import com.bryanh.calendaralarm.alarm.AlarmActivity
import com.bryanh.calendaralarm.alarm.RingService
import com.bryanh.calendaralarm.ui.theme.Tok
import com.bryanh.calendaralarm.ui.theme.TypeTok
import kotlinx.coroutines.launch

/** 畫面切換：用 ViewModel 裡的一個堆疊，不另外引入導覽套件。 */
@Composable
fun AppRoot(vm: MainViewModel) {
    val ready by vm.ready.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val perms by vm.perms.collectAsStateWithLifecycle()
    val stack by vm.stack.collectAsStateWithLifecycle()
    val homeMode by vm.homeMode.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val ringing by RingService.ringing.collectAsStateWithLifecycle()
    // 每個畫面的狀態（捲動位置、填到一半的表單）在被蓋住時保留，退回來時還原；離開堆疊就丟掉
    val holder = rememberSaveableStateHolder()
    val savedKeys = remember { mutableSetOf<String>() }
    LaunchedEffect(stack) {
        val live = stack.map { it.toString() }.toSet()
        (savedKeys - live).forEach { holder.removeState(it) }
        savedKeys.retainAll(live)
        savedKeys.addAll(live)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Tok.canvas)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(Modifier.fillMaxSize()) {
            // 正在響：不論在哪個畫面都能停（例如通知權限被關掉、看不到響鈴畫面時）
            if (ringing.isNotEmpty()) RingingBar(ringing.first().title, ringing.size, settings.snoozeMin)
            Box(Modifier.weight(1f)) {
                when {
                    !ready -> Unit
                    // 沒有日曆權限什麼都做不了：權限被收回時也回到這裡
                    !settings.wizardDone || !perms.calendar -> WizardScreen(vm)
                    else -> {
                        BackHandler(enabled = stack.size > 1) { vm.back() }
                        val top = stack.last()
                        holder.SaveableStateProvider(top.toString()) {
                            when (top) {
                                Screen.Home -> HomeScreen(vm, homeMode, perms)
                                is Screen.Day -> DayScreen(vm, top.date)
                                is Screen.Detail -> DetailScreen(vm, top.eventId, top.begin)
                                is Screen.Edit -> EditScreen(vm, top.eventId, top.presetStart, top.prefill)
                                Screen.Alarms -> AlarmsScreen(vm)
                                Screen.Settings -> SettingsScreen(vm)
                                Screen.Search -> SearchScreen(vm)
                            }
                        }
                    }
                }
            }
        }
        MessageBar(message, onDismiss = { vm.clearMessage() }, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/** 首頁：左側選單＋（權限提醒）＋月曆或行程清單。 */
@Composable
private fun HomeScreen(vm: MainViewModel, mode: HomeMode, perms: PermState) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val openDrawer: () -> Unit = { scope.launch { drawer.open() } }
    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            AppDrawer(vm, mode) { action ->
                scope.launch { drawer.close() }
                action()
            }
        },
    ) {
        Column(Modifier.fillMaxSize().background(Tok.canvas)) {
            if (!perms.alarmReady) {
                Banner("有鬧鐘需要的權限或設定沒開，可能不會準時響", "去設定") { vm.navigate(Screen.Settings) }
            }
            Box(Modifier.weight(1f)) {
                when (mode) {
                    HomeMode.MONTH, HomeMode.WEEKS -> MonthScreen(vm, mode, openDrawer)
                    HomeMode.DAYS -> DaysScreen(vm, openDrawer)
                    HomeMode.AGENDA -> AgendaScreen(vm, openDrawer)
                }
            }
        }
    }
}

/** 左側選單：切換檢視（行程、日、N 天、月、N 週）、勾選要顯示的日曆、鬧鐘清單與設定。 */
@Composable
private fun AppDrawer(vm: MainViewModel, mode: HomeMode, run: (() -> Unit) -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val calendars by vm.calendars.collectAsStateWithLifecycle()
    val colors = NavigationDrawerItemDefaults.colors(
        selectedContainerColor = Tok.alarmTint,
        unselectedContainerColor = Tok.canvas,
        selectedTextColor = Tok.ink,
        unselectedTextColor = Tok.ink,
        selectedIconColor = Tok.ink,
        unselectedIconColor = Tok.slate,
    )
    ModalDrawerSheet(drawerContainerColor = Tok.canvas) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Tok.sSm, vertical = Tok.sMd)) {
            Text(
                stringResource(R.string.app_name),
                style = TypeTok.titleLg,
                color = Tok.ink,
                modifier = Modifier.padding(horizontal = Tok.sSm, vertical = Tok.sXs).semantics { heading() },
            )
            NavigationDrawerItem(
                label = { Text("行程", style = TypeTok.body) },
                icon = { AppIcon(R.drawable.ic_list, null, size = 20.dp) },
                selected = mode == HomeMode.AGENDA,
                onClick = { run { vm.setHomeMode(HomeMode.AGENDA) } },
                colors = colors,
            )
            NavigationDrawerItem(
                label = { Text("日", style = TypeTok.body) },
                icon = { AppIcon(R.drawable.ic_clock, null, size = 20.dp) },
                selected = false,
                onClick = { run { vm.openToday() } },
                colors = colors,
            )
            NavigationDrawerItem(
                label = { Text(HomeMode.DAYS.label(settings), style = TypeTok.body) },
                icon = { AppIcon(R.drawable.ic_columns, null, size = 20.dp) },
                selected = mode == HomeMode.DAYS,
                onClick = { run { vm.setHomeMode(HomeMode.DAYS) } },
                colors = colors,
            )
            NavigationDrawerItem(
                label = { Text("月", style = TypeTok.body) },
                icon = { AppIcon(R.drawable.ic_calendar, null, size = 20.dp) },
                selected = mode == HomeMode.MONTH,
                onClick = { run { vm.setHomeMode(HomeMode.MONTH) } },
                colors = colors,
            )
            NavigationDrawerItem(
                label = { Text(HomeMode.WEEKS.label(settings), style = TypeTok.body) },
                icon = { AppIcon(R.drawable.ic_rows, null, size = 20.dp) },
                selected = mode == HomeMode.WEEKS,
                onClick = { run { vm.setHomeMode(HomeMode.WEEKS) } },
                colors = colors,
            )
            Spacer(Modifier.padding(top = Tok.sXs))
            Divider()
            Column(Modifier.padding(horizontal = Tok.sSm)) {
                SectionTitle("顯示的日曆")
                CalendarToggles(calendars, settings.hiddenCalendarIds) { c, shown -> vm.toggleCalendar(c, shown) }
            }
            Spacer(Modifier.padding(top = Tok.sXs))
            Divider()
            NavigationDrawerItem(
                label = { Text("鬧鐘清單", style = TypeTok.body) },
                icon = { AppIcon(R.drawable.ic_alarm, null, size = 20.dp) },
                selected = false,
                onClick = { run { vm.navigate(Screen.Alarms) } },
                colors = colors,
                modifier = Modifier.padding(top = Tok.sXs),
            )
            NavigationDrawerItem(
                label = { Text("設定", style = TypeTok.body) },
                icon = { AppIcon(R.drawable.ic_settings, null, size = 20.dp) },
                selected = false,
                onClick = { run { vm.navigate(Screen.Settings) } },
                colors = colors,
            )
        }
    }
}

/** App 內的響鈴列：標題＋稍後提醒＋關閉，點標題開響鈴畫面。 */
@Composable
private fun RingingBar(title: String, count: Int, snoozeMin: Int) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(Tok.ringBg)
            .padding(horizontal = Tok.sMd, vertical = Tok.sXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(R.drawable.ic_alarm, null, Tok.ringGlow, 20.dp)
        Spacer(Modifier.width(Tok.sXs))
        Text(
            if (count > 1) "$title 等 $count 個鬧鐘響鈴中" else "$title 響鈴中",
            style = TypeTok.bodySm,
            color = Tok.ringInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .clickable(role = Role.Button, onClickLabel = "開啟響鈴畫面") {
                    context.startActivity(Intent(context, AlarmActivity::class.java))
                }
                .padding(vertical = Tok.sXs),
        )
        Text(
            "$snoozeMin 分後再響",
            style = TypeTok.bodySm,
            color = Tok.ringInk,
            modifier = Modifier
                .clickable(role = Role.Button) { RingService.snooze(context, snoozeMin) }
                .padding(horizontal = Tok.sXs, vertical = Tok.sXs),
        )
        Text(
            "關閉",
            style = TypeTok.bodySm,
            color = Tok.ringGlow,
            modifier = Modifier
                .clickable(role = Role.Button) { RingService.dismiss(context) }
                .padding(horizontal = Tok.sXs, vertical = Tok.sXs),
        )
    }
}
