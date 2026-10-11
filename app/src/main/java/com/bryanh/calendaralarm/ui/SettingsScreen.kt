package com.bryanh.calendaralarm.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import com.bryanh.calendaralarm.data.WidgetRange
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.border
import androidx.annotation.DrawableRes
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bryanh.calendaralarm.BuildConfig
import com.bryanh.calendaralarm.R
import com.bryanh.calendaralarm.data.Settings
import com.bryanh.calendaralarm.data.defaultCalendarFor
import com.bryanh.calendaralarm.ui.theme.Tok
import com.bryanh.calendaralarm.ui.theme.TypeTok
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val DEFAULT_RINGTONE = "系統預設鬧鐘鈴聲"

/** 設定頁點一列跳出的對話框。 */
private enum class SettingsDialog { OFFSET, DAY_START, CALENDAR, LICENSES }

/**
 * 設定：依用途分成幾張卡片（鬧鐘、新增行程、檢視、月曆小工具、顯示的日曆、權限、關於），
 * 卡片之間留白、各有標題，不會全部擠在一起。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val calendars by vm.calendars.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    val ringtoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val uri = res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            vm.updateSettings { it.copy(ringtoneUri = uri?.toString()) }
        }
    }
    // 鈴聲名稱要查媒體資料庫，放到背景執行緒；查完把 Ringtone 停掉，不留著播放器
    val ringtoneName by produceState(DEFAULT_RINGTONE, settings.ringtoneUri) {
        value = withContext(Dispatchers.IO) {
            settings.ringtoneUri?.let { s ->
                runCatching {
                    RingtoneManager.getRingtone(context, Uri.parse(s))?.let { r -> r.getTitle(context).also { r.stop() } }
                }.getOrNull()
            } ?: DEFAULT_RINGTONE
        }
    }
    val defaultCalendar = defaultCalendarFor(calendars, settings)

    Column(Modifier.fillMaxSize().background(Tok.surface)) {
        Box(Modifier.background(Tok.canvas)) { TopBar("設定", navIcon = R.drawable.ic_back, onNav = { vm.back() }) }
        Divider()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Tok.sMd)) {
            SettingsCard("鬧鐘", R.drawable.ic_alarm) {
                SettingRow("預設提前時間", Fmt.leadLabel(settings.defaultOffsetMin), "電腦上加 ⏰ 的行程也用這個") { dialog = SettingsDialog.OFFSET }
                CardDivider()
                ChoiceBlock("稍後提醒", "響鈴時按「幾分後再響」，隔多久再響一次") {
                    Settings.SNOOZE_OPTIONS.forEach { m ->
                        OptionChip("$m 分鐘", m == settings.snoozeMin) { vm.updateSettings { it.copy(snoozeMin = m) } }
                    }
                }
                CardDivider()
                ChoiceBlock("沒人理會時響多久") {
                    Settings.RING_MINUTE_OPTIONS.forEach { m ->
                        OptionChip("$m 分鐘", m == settings.ringMinutes) { vm.updateSettings { it.copy(ringMinutes = m) } }
                    }
                }
                CardDivider()
                SettingRow("鈴聲", ringtoneName) {
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "鬧鐘鈴聲")
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, settings.ringtoneUri?.let(Uri::parse))
                    try {
                        ringtoneLauncher.launch(intent)
                    } catch (_: ActivityNotFoundException) {
                        vm.showMessage("這支手機沒有鈴聲選擇器")
                    }
                }
                CardDivider()
                SwitchRow("震動", settings.vibrate, onChange = { on -> vm.updateSettings { it.copy(vibrate = on) } })
                CardDivider()
                Box(Modifier.padding(vertical = Tok.sSm)) { OutlineButton("10 秒後試響", onClick = { vm.testAlarm() }) }
            }

            SettingsCard("新增行程", R.drawable.ic_plus) {
                SettingRow("預設日曆", defaultCalendar?.displayName ?: "沒有可寫入的日曆", defaultCalendar?.accountName?.takeIf { it != defaultCalendar.displayName }) {
                    dialog = SettingsDialog.CALENDAR
                }
                CardDivider()
                ChoiceBlock("預設行程長度") {
                    Settings.DURATION_OPTIONS.forEach { m ->
                        OptionChip(if (m % 60 == 0) "${m / 60} 小時" else "$m 分鐘", m == settings.defaultDurationMin) {
                            vm.updateSettings { it.copy(defaultDurationMin = m) }
                        }
                    }
                }
                CardDivider()
                ChoiceBlock("預設一般通知", "沒開鬧鐘的行程，由 Google 月曆發通知") {
                    OptionChip("無", settings.defaultReminderMin == null) { vm.updateSettings { it.copy(defaultReminderMin = null) } }
                    Settings.REMINDER_OPTIONS.forEach { m ->
                        OptionChip(Fmt.reminderLabel(m), m == settings.defaultReminderMin) { vm.updateSettings { it.copy(defaultReminderMin = m) } }
                    }
                }
            }

            SettingsCard("檢視", R.drawable.ic_calendar) {
                SettingRow("一天從幾點開始", Fmt.minuteOfDay(settings.dayStartMinute), "日檢視與「N 天」從這裡開始，更早的行程照樣顯示；整天行程的鬧鐘也在這時候響") {
                    dialog = SettingsDialog.DAY_START
                }
                CardDivider()
                ChoiceBlock("自訂天數", "「N 天」檢視一次看幾天，天數少格子就寬") {
                    Settings.CUSTOM_DAYS.forEach { n ->
                        OptionChip("$n 天", n == settings.customDays) { vm.updateSettings { it.copy(customDays = n) } }
                    }
                }
                CardDivider()
                ChoiceBlock("自訂週數", "「N 週」檢視一次看幾週，週數少格子就大") {
                    Settings.CUSTOM_WEEKS.forEach { n ->
                        OptionChip("$n 週", n == settings.customWeeks) { vm.updateSettings { it.copy(customWeeks = n) } }
                    }
                }
                CardDivider()
                ChoiceBlock("一週的第一天") {
                    OptionChip("週一", settings.weekStartsMonday) { vm.updateSettings { it.copy(weekStartsMonday = true) } }
                    OptionChip("週日", !settings.weekStartsMonday) { vm.updateSettings { it.copy(weekStartsMonday = false) } }
                }
                CardDivider()
                SwitchRow("顯示農曆", settings.showLunar, onChange = { on -> vm.updateSettings { it.copy(showLunar = on) } })
            }

            SettingsCard("月曆小工具", R.drawable.ic_rows) {
                ChoiceBlock("顯示範圍", "天數、週數跟上面的自訂檢視一樣；改了之後小工具回到今天") {
                    OptionChip("整個月", settings.widgetRange == WidgetRange.MONTH) { vm.updateSettings { it.copy(widgetRange = WidgetRange.MONTH) } }
                    OptionChip("${settings.customDays} 天", settings.widgetRange == WidgetRange.DAYS) { vm.updateSettings { it.copy(widgetRange = WidgetRange.DAYS) } }
                    OptionChip("${settings.customWeeks} 週", settings.widgetRange == WidgetRange.WEEKS) { vm.updateSettings { it.copy(widgetRange = WidgetRange.WEEKS) } }
                }
            }

            SettingsCard("顯示的日曆", R.drawable.ic_list) {
                Box(Modifier.padding(bottom = Tok.sXs)) {
                    Column {
                        CalendarToggles(calendars, settings.hiddenCalendarIds) { c, shown -> vm.toggleCalendar(c, shown) }
                        Text("隱藏的日曆仍會照常響鬧鐘（鬧鐘是逐筆設定的）。", style = TypeTok.caption, color = Tok.slate, modifier = Modifier.padding(top = Tok.sXxs))
                    }
                }
            }

            SettingsCard("權限", R.drawable.ic_check) {
                PermissionSection(vm)
            }

            SettingsCard("關於", R.drawable.ic_info) {
                Column(Modifier.padding(vertical = Tok.sSm)) {
                    Text("${stringResource(R.string.app_name)} ${BuildConfig.VERSION_NAME}", style = TypeTok.body, color = Tok.ink)
                    Text("行程存在你的 Google 月曆；這支 App 不連網路，資料不會離開手機。免費、開放原始碼（MIT 授權）。", style = TypeTok.caption, color = Tok.slate)
                }
                CardDivider()
                SettingRow("開放原始碼授權", "MIT・Apache 2.0") { dialog = SettingsDialog.LICENSES }
            }
            Spacer(Modifier.size(Tok.sXxl))
        }
    }

    val dismiss = { dialog = null }
    when (dialog) {
        SettingsDialog.OFFSET -> LeadTimeDialog(settings.defaultOffsetMin, onPick = { m ->
            vm.updateSettings { it.copy(defaultOffsetMin = m) }
            dialog = null
        }, onDismiss = dismiss)
        SettingsDialog.CALENDAR -> ChoiceDialog("預設日曆", dismiss) {
            calendars.filter { it.writable }.groupBy { it.accountName }.forEach { (account, list) ->
                OptionGroupTitle(account)
                list.forEach { c ->
                    OptionRow(c.displayName, selected = c.id == defaultCalendar?.id, leading = { ColorDot(c.color, 18.dp) }, onClick = {
                        vm.updateSettings { it.copy(defaultCalendarId = c.id) }
                        dialog = null
                    })
                }
            }
        }
        SettingsDialog.DAY_START -> {
            val m = settings.dayStartMinute
            val state = rememberTimePickerState(initialHour = m / 60, initialMinute = m % 60, is24Hour = true)
            AlertDialog(
                onDismissRequest = dismiss,
                containerColor = Tok.canvas,
                title = { Text("一天從幾點開始", style = TypeTok.titleMd, color = Tok.ink) },
                confirmButton = {
                    TextButton(onClick = {
                        // 最晚中午：再晚的話，早上的行程大多會變成「例外」往前延伸
                        vm.updateSettings { it.copy(dayStartMinute = (state.hour * 60 + state.minute).coerceAtMost(12 * 60)) }
                        dialog = null
                    }) { DialogText("確定", primary = true) }
                },
                dismissButton = { TextButton(onClick = dismiss) { DialogText("取消", primary = false) } },
                text = { TimePicker(state = state) },
            )
        }
        SettingsDialog.LICENSES -> LicensesDialog(dismiss)
        null -> Unit
    }
}

/** 一張設定卡片：上面是圖示＋標題，下面白底圓角卡片，卡片裡的項目用細線分開。 */
@Composable
private fun SettingsCard(title: String, @DrawableRes icon: Int, content: @Composable ColumnScope.() -> Unit) {
    Row(
        Modifier.padding(start = Tok.sXxs, top = Tok.sXl, bottom = Tok.sXs).semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(icon, null, Tok.primary, 18.dp)
        Spacer(Modifier.width(Tok.sXs))
        Text(title, style = TypeTok.titleMd, color = Tok.ink)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tok.rLg))
            .background(Tok.canvas)
            .border(1.dp, Tok.hairline, RoundedCornerShape(Tok.rLg))
            .padding(horizontal = Tok.sMd),
        content = content,
    )
}

@Composable
private fun CardDivider() = Divider()

/** 一組單選選項：標題（＋說明）在上、選項在下。 */
@Composable
private fun ChoiceBlock(title: String, caption: String? = null, options: @Composable () -> Unit) {
    Column(Modifier.padding(vertical = Tok.sSm)) {
        Text(title, style = TypeTok.body, color = Tok.ink)
        if (caption != null) Text(caption, style = TypeTok.caption, color = Tok.slate)
        Spacer(Modifier.size(Tok.sXs))
        ChipRow(options)
    }
}

/** 點了開對話框或其他畫面的一列：標題、目前的值（重點色）、說明，右邊一個箭頭。 */
@Composable
private fun SettingRow(title: String, value: String, caption: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Tok.sXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 目前的值放在標題下面：字放大時長的日曆名稱也不會被擠成兩截
        Column(Modifier.weight(1f)) {
            Text(title, style = TypeTok.body, color = Tok.ink)
            Text(value, style = TypeTok.body, color = Tok.primary)
            if (caption != null) Text(caption, style = TypeTok.caption, color = Tok.slate)
        }
        Spacer(Modifier.width(Tok.sXs))
        AppIcon(R.drawable.ic_chevron_right, null, Tok.slate, 18.dp)
    }
}

/** 開放原始碼授權：本 App（MIT）與用到的套件（Apache 2.0、BSD 3-Clause），條文從 res/raw/licenses.txt 讀。 */
@Composable
private fun LicensesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val text by produceState("") {
        value = withContext(Dispatchers.IO) {
            runCatching { context.resources.openRawResource(R.raw.licenses).bufferedReader().use { it.readText() } }.getOrDefault("")
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Tok.canvas,
        shape = RoundedCornerShape(Tok.rXxl),
        title = { Text("開放原始碼授權", style = TypeTok.titleMd, color = Tok.ink) },
        text = { Text(text, style = TypeTok.caption, color = Tok.charcoal, modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = onDismiss) { DialogText("關閉", primary = true) } },
    )
}

/** 權限狀態與一鍵前往設定。設定頁與首次設定精靈共用。 */
@Composable
fun PermissionSection(vm: MainViewModel) {
    val perms by vm.perms.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val calendarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.onPermissionsChanged() }
    val notifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.onPermissionsChanged() }

    PermissionRow("讀寫日曆", "讀取與新增 Google 月曆的行程", perms.calendar) {
        calendarLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
    }
    PermissionRow("通知", "沒有通知權限，響鈴時看不到畫面、也沒有地方按停", perms.notifications) {
        notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        PermissionRow("全螢幕通知", "鎖定畫面時直接跳出響鈴畫面", perms.fullScreen) {
            open(context, Intent(AndroidSettings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")))
        }
    }
    if (!perms.exactAlarm) {
        PermissionRow("鬧鐘與提醒", "準時觸發鬧鐘", false) {
            open(context, Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
        }
    }
    PermissionRow("鬧鐘音量", if (perms.alarmVolumeOk) "鬧鐘音量大於 0" else "鬧鐘音量是 0，鬧鐘不會出聲", perms.alarmVolumeOk, okText = "正常", fixText = "調整") {
        open(context, Intent(AndroidSettings.ACTION_SOUND_SETTINGS))
    }
    PermissionRow("電池用量「不受限制」", "讓電腦上改的行程更快同步到鬧鐘", perms.batteryUnrestricted) {
        open(context, Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
    }
    Text(
        "按了沒反應？可能是先前選過「不允許」，請到系統設定開啟。",
        style = TypeTok.caption,
        color = Tok.slate,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(role = Role.Button, onClickLabel = "開啟系統的 App 設定") {
                open(context, Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            }
            .padding(vertical = Tok.sXs),
    )
}

@Composable
private fun PermissionRow(
    title: String,
    desc: String,
    granted: Boolean,
    okText: String = "已開啟",
    fixText: String = "開啟",
    onRequest: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = Tok.sXxs), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(if (granted) R.drawable.ic_check else R.drawable.ic_info, null, if (granted) Tok.primary else Tok.ink, 20.dp)
        Spacer(Modifier.width(Tok.sSm))
        Column(Modifier.weight(1f)) {
            Text(title, style = TypeTok.body, color = Tok.ink)
            Text(desc, style = TypeTok.caption, color = Tok.slate)
        }
        Spacer(Modifier.width(Tok.sXs))
        if (granted) Text(okText, style = TypeTok.caption, color = Tok.slate) else OutlineButton(fixText, onClick = onRequest)
    }
}

private fun open(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        } catch (_: ActivityNotFoundException) {
        }
    }
}

/** 第一次開啟：把鬧鐘需要的權限開好、試響一次。日曆與通知是必要的，其他建議開。 */
@Composable
fun WizardScreen(vm: MainViewModel) {
    val perms by vm.perms.collectAsStateWithLifecycle()
    val essentials = perms.calendar && perms.notifications
    Column(Modifier.fillMaxSize().background(Tok.canvas).verticalScroll(rememberScrollState()).padding(Tok.sMd)) {
        Spacer(Modifier.size(Tok.sXl))
        Box(Modifier.size(56.dp).clip(CircleShape).background(Tok.primary), contentAlignment = Alignment.Center) {
            AppIcon(R.drawable.ic_alarm, null, Tok.onPrimary, 30.dp)
        }
        Spacer(Modifier.size(Tok.sMd))
        TitleBlock("歡迎使用${stringResource(R.string.app_name)}", null)
        Text(
            "行程照樣存在你的 Google 月曆；建立行程時打開「鬧鐘提醒」，時間到就會像鬧鐘一樣響到你按掉。先把下面幾項打開，前兩項是必要的：",
            style = TypeTok.body,
            color = Tok.charcoal,
            modifier = Modifier.padding(top = Tok.sXs, bottom = Tok.sMd),
        )
        PermissionSection(vm)
        Spacer(Modifier.size(Tok.sMd))
        if (perms.notifications) {
            OutlineButton("10 秒後試響", onClick = { vm.testAlarm() })
        } else {
            Text("開了通知權限後，可以先試響一次。", style = TypeTok.caption, color = Tok.slate)
        }
        Spacer(Modifier.size(Tok.sXl))
        PrimaryButton(
            when {
                !perms.calendar -> "請先允許讀寫日曆"
                !perms.notifications -> "請先允許通知"
                else -> "開始使用"
            },
            onClick = { vm.finishWizard() },
            enabled = essentials,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
