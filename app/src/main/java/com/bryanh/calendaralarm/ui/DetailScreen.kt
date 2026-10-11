package com.bryanh.calendaralarm.ui

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Attendees
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bryanh.calendaralarm.R
import com.bryanh.calendaralarm.data.AlarmMarker
import com.bryanh.calendaralarm.data.Attendee
import com.bryanh.calendaralarm.data.Settings
import com.bryanh.calendaralarm.data.SyncMode
import com.bryanh.calendaralarm.data.alarmKey
import com.bryanh.calendaralarm.ui.theme.Tok
import com.bryanh.calendaralarm.ui.theme.TypeTok

private const val GOOGLE_CALENDAR = "com.google.android.calendar"

@Composable
fun DetailScreen(vm: MainViewModel, eventId: Long, begin: Long) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    // 停在這頁時電腦上改了（或同步進來）也跟著重讀
    val dataVersion by vm.dataVersion.collectAsStateWithLifecycle()
    var refresh by remember { mutableIntStateOf(0) }
    var bundle by remember { mutableStateOf<EventBundle?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(eventId, begin, refresh, dataVersion) {
        bundle = vm.loadBundle(eventId, begin)
        loaded = true
    }
    val b = bundle
    Column(Modifier.fillMaxSize().background(Tok.canvas)) {
        TopBar(
            title = "行程內容",
            navIcon = R.drawable.ic_back,
            onNav = { vm.back() },
            actions = {
                // 受邀、主辦人沒開放修改的行程不給編輯；只有主辦人能刪（受邀的交給 Google 月曆回覆或移除）
                if (b != null && b.detail.canEditTitle) IconBtn(R.drawable.ic_edit, "編輯", onClick = { vm.navigate(Screen.Edit(b.detail.id, null)) })
                if (b != null && b.detail.canDelete) IconBtn(R.drawable.ic_trash, "刪除", onClick = { confirmDelete = true })
            },
        )
        if (b == null) {
            if (loaded) Text("找不到這筆行程，可能已在其他裝置刪除。", style = TypeTok.body, color = Tok.slate, modifier = Modifier.padding(Tok.sXl))
            return@Column
        }
        val d = b.detail
        val instanceEnd = b.instanceBegin + (d.endMillis - d.dtStart)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Tok.sMd, vertical = Tok.sXs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Tok.eventColor(d.color)))
                Spacer(Modifier.width(Tok.sXs))
                Text(d.calendarName, style = TypeTok.caption, color = Tok.slate)
            }
            Spacer(Modifier.height(Tok.sXs))
            Text(AlarmMarker.strip(d.title).ifBlank { "（無標題）" }, style = TypeTok.titleLg, color = Tok.ink)
            Spacer(Modifier.height(Tok.sXs))
            InfoRow(R.drawable.ic_clock, Fmt.range(b.instanceBegin, instanceEnd, d.allDay))
            if (d.recurring) InfoRow(R.drawable.ic_repeat, "重複行程（修改會套用到整個系列）")
            if (d.location.isNotBlank()) {
                InfoRow(R.drawable.ic_pin, d.location, actionLabel = "在地圖上開啟") {
                    if (!openMap(context, d.location)) vm.showMessage("找不到可以開啟地圖的 App")
                }
            }
            if (b.attendees.isNotEmpty()) AttendeeList(b.attendees, d.ownerAccount)
            if (d.description.isNotBlank()) {
                Spacer(Modifier.height(Tok.sXs))
                Text(d.description, style = TypeTok.bodySm, color = Tok.charcoal)
            }

            AlarmCard(
                bundle = b,
                settings = settings,
                onToggle = { on -> vm.setAlarm(b, on) { refresh++ } },
                onOffset = { m -> vm.setOffset(d.id, d.originalId, m) { refresh++ } },
                onSkip = { skip -> vm.setSkip(alarmKey(d.id, b.instanceBegin), skip) { refresh++ } },
            )

            InfoRow(
                R.drawable.ic_bell,
                if (b.reminders.isEmpty()) "沒有一般通知" else "一般通知：" + b.reminders.joinToString("、") { Fmt.reminderLabel(it) },
            )

            if (isGoogleCalendarInstalled(context)) {
                Spacer(Modifier.height(Tok.sMd))
                OutlineButton("在 Google 月曆開啟", onClick = {
                    if (!openInGoogleCalendar(context, d.id, b.instanceBegin, instanceEnd)) vm.showMessage("開不了 Google 月曆")
                })
            }
            Spacer(Modifier.height(Tok.sXxl))
        }
    }
    if (confirmDelete && b != null) {
        ConfirmDialog(
            title = "刪除這筆行程？",
            text = when {
                b.detail.recurring -> "這是重複行程，會刪掉整個系列。只想刪這一次，請按下方的「在 Google 月曆開啟」。"
                b.hasOtherAttendees -> "這筆行程有其他與會者，刪除後 Google 月曆會通知他們行程取消。"
                else -> "刪除後電腦上的 Google 月曆也會一起刪除。"
            },
            confirmText = "刪除",
            onConfirm = {
                confirmDelete = false
                vm.delete(b) { vm.back() }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun InfoRow(icon: Int, text: String, actionLabel: String? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = actionLabel, onClick = onClick) else Modifier)
            .padding(vertical = Tok.sXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(icon, null, Tok.slate, 20.dp)
        Spacer(Modifier.width(Tok.sSm))
        Text(text, style = TypeTok.body, color = Tok.ink, modifier = Modifier.weight(1f))
        if (onClick != null) AppIcon(R.drawable.ic_open, null, Tok.slate, 18.dp)
    }
}

/** 與會者名單與回覆狀態（回覆邀請交給 Google 月曆）。 */
@Composable
private fun AttendeeList(list: List<Attendee>, ownerAccount: String?) {
    Row(Modifier.fillMaxWidth().padding(vertical = Tok.sXs), verticalAlignment = Alignment.Top) {
        AppIcon(R.drawable.ic_calendar, null, Tok.slate, 20.dp)
        Spacer(Modifier.width(Tok.sSm))
        Column(Modifier.weight(1f)) {
            Text("${list.size} 位與會者", style = TypeTok.body, color = Tok.ink)
            list.forEach { a ->
                val me = a.email.isNotBlank() && a.email.equals(ownerAccount, ignoreCase = true)
                val name = a.name.ifBlank { a.email }.ifBlank { "（未命名）" } + if (me) "（你）" else ""
                val status = when (a.status) {
                    Attendees.ATTENDEE_STATUS_ACCEPTED -> "已接受"
                    Attendees.ATTENDEE_STATUS_DECLINED -> "已拒絕"
                    Attendees.ATTENDEE_STATUS_TENTATIVE -> "未定"
                    Attendees.ATTENDEE_STATUS_INVITED -> "未回覆"
                    else -> ""
                }
                Text(if (status.isEmpty()) name else "$name・$status", style = TypeTok.bodySm, color = Tok.slate)
            }
        }
    }
}

/** 鬧鐘設定卡（詳情頁）：開關、提前時間、同步方式說明、單次略過。 */
@Composable
private fun AlarmCard(
    bundle: EventBundle,
    settings: Settings,
    onToggle: (Boolean) -> Unit,
    onOffset: (Int) -> Unit,
    onSkip: (Boolean) -> Unit,
) {
    val d = bundle.detail
    Column(
        Modifier
            .padding(vertical = Tok.sSm)
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tok.rLg))
            .background(Tok.alarmTint)
            .padding(horizontal = Tok.cardPadH, vertical = Tok.sSm),
    ) {
        SwitchRow(
            title = "鬧鐘提醒",
            subtitle = "響到你按掉為止",
            checked = bundle.alarmOn,
            onChange = onToggle,
            titleStyle = TypeTok.titleMd,
            leading = {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(Tok.rMd)).background(Tok.canvas), contentAlignment = Alignment.Center) {
                    AppIcon(R.drawable.ic_alarm, null, Tok.primary, 20.dp)
                }
            },
        )
        if (bundle.alarmOn) {
            // 跟新增／編輯頁同一個「提前 10 分鐘 ⌄」，點了選常用時間或自訂
            var leadDialog by remember { mutableStateOf(false) }
            val current = bundle.offsetMin ?: settings.defaultOffsetMin
            LeadTimeRow(
                minutes = current,
                active = true,
                allDayAt = if (d.allDay) Fmt.minuteOfDay(settings.dayStartMinute) else null,
                onClick = { leadDialog = true },
            )
            if (leadDialog) {
                LeadTimeDialog(current, onPick = { m ->
                    leadDialog = false
                    onOffset(m)
                }, onDismiss = { leadDialog = false })
            }
            SwitchRow(title = "略過這一次", checked = bundle.skipped, onChange = onSkip, modifier = Modifier.padding(top = Tok.sXs))
        }
        Row(Modifier.padding(top = Tok.sXs), verticalAlignment = Alignment.Top) {
            AppIcon(R.drawable.ic_info, null, Tok.slate, 15.dp, Modifier.padding(top = 2.dp))
            Spacer(Modifier.width(Tok.sXs))
            Text(
                when (bundle.syncMode) {
                    SyncMode.TITLE -> "鬧鐘會同步到 Google 月曆：標題前面加上 ⏰，電腦上也看得到。"
                    SyncMode.LOCAL -> if (bundle.hasOtherAttendees) "這個行程有其他與會者，⏰ 只記在這支手機，不會改到大家看到的標題。"
                    else "這個行程不能修改標題，⏰ 只記在這支手機。"
                },
                style = TypeTok.caption,
                color = Tok.slate,
            )
        }
    }
}

private fun openMap(context: Context, location: String): Boolean = start(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(location))))

private fun isGoogleCalendarInstalled(context: Context): Boolean = try {
    context.packageManager.getPackageInfo(GOOGLE_CALENDAR, 0)
    true
} catch (_: Exception) {
    false
}

/** 明確指定 Google 月曆 App 開啟，避免日後本 App 接手行事曆 Intent 時繞回自己。 */
private fun openInGoogleCalendar(context: Context, eventId: Long, begin: Long, end: Long): Boolean = start(
    context,
    Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId))
        .setPackage(GOOGLE_CALENDAR)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end),
)

private fun start(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
}
