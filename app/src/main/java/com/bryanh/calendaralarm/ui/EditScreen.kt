package com.bryanh.calendaralarm.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bryanh.calendaralarm.R
import com.bryanh.calendaralarm.data.AlarmMarker
import com.bryanh.calendaralarm.data.CalendarRepository
import com.bryanh.calendaralarm.data.EventColor
import com.bryanh.calendaralarm.data.EventDetail
import com.bryanh.calendaralarm.data.EventDraft
import com.bryanh.calendaralarm.data.Repeat
import com.bryanh.calendaralarm.data.SyncMode
import com.bryanh.calendaralarm.data.defaultCalendarFor
import com.bryanh.calendaralarm.ui.theme.Tok
import com.bryanh.calendaralarm.ui.theme.TypeTok
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay

private enum class PickerTarget { START_DATE, START_TIME, END_DATE, END_TIME }

/** 點一列跳出的選擇清單。 */
private enum class EditDialog { CALENDAR, REPEAT, LEAD, REMINDER, COLOR }

private data class FormTimes(val startDate: LocalDate, val startTime: LocalTime, val endDate: LocalDate, val endTime: LocalTime)

/** 用來判斷表單有沒有改過（關閉前要不要先確認）。 */
private data class FormSnapshot(
    val title: String,
    val allDay: Boolean,
    val times: FormTimes,
    val repeat: Repeat,
    val location: String,
    val description: String,
    val alarmOn: Boolean,
    val offset: Int,
    val reminders: Set<Int>,
    val colorKey: String?,
)

/** Google 月曆一筆行程最多 5 個通知。 */
private const val MAX_REMINDERS = 5
private const val GOOGLE_ACCOUNT = "com.google"

/** Google 帳號的行程顏色：名稱跟 Google 月曆 App 的繁中名稱一樣，順序也照它的選單。key 是 Colors 表的 key。 */
private val GOOGLE_EVENT_COLORS = linkedMapOf(
    "11" to "番茄紅",
    "4" to "紅鶴色",
    "6" to "橙橘色",
    "5" to "香蕉黃",
    "2" to "鼠尾草綠",
    "10" to "羅勒綠",
    "7" to "孔雀藍",
    "9" to "藍莓色",
    "1" to "薰衣草色",
    "3" to "葡萄紫",
    "8" to "石墨黑",
)

private fun orderedColors(colors: List<EventColor>, google: Boolean): List<EventColor> {
    val order = GOOGLE_EVENT_COLORS.keys.toList()
    return colors.sortedWith(
        compareBy<EventColor>({ if (google) order.indexOf(it.key).let { i -> if (i < 0) Int.MAX_VALUE else i } else 0 }, { it.key.toIntOrNull() ?: Int.MAX_VALUE }),
    )
}

private fun colorName(c: EventColor, ordered: List<EventColor>, google: Boolean): String =
    (if (google) GOOGLE_EVENT_COLORS[c.key] else null) ?: "顏色 ${ordered.indexOf(c) + 1}"

@Composable
fun EditScreen(vm: MainViewModel, eventId: Long?, presetStart: LocalDateTime?, prefill: Prefill? = null) {
    var bundle by remember { mutableStateOf<EventBundle?>(null) }
    var loaded by remember { mutableStateOf(eventId == null) }
    LaunchedEffect(eventId) {
        if (eventId != null) {
            bundle = vm.loadBundle(eventId, 0)
            loaded = true
        }
    }
    if (!loaded) {
        Box(Modifier.fillMaxSize().background(Tok.canvas))
        return
    }
    if (eventId != null && bundle == null) {
        Column(Modifier.fillMaxSize().background(Tok.canvas)) {
            TopBar("編輯行程", navIcon = R.drawable.ic_close, navDescription = "關閉", onNav = { vm.back() })
            Text("找不到這筆行程，可能已在其他裝置刪除。", style = TypeTok.body, color = Tok.slate, modifier = Modifier.padding(Tok.sXl))
        }
        return
    }
    key(bundle) { EditForm(vm, bundle, presetStart, prefill) }
}

/**
 * 新增／編輯表單，版面照 Google 月曆的編輯頁：標題 → 日曆（帳號）→ 全天與時間 → 重複 → 地點 → 鬧鐘 → 一般通知 → 顏色 → 說明。
 * 每一列左邊一個圖示，靠細線分隔；選項都是「點一列跳出清單」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditForm(vm: MainViewModel, bundle: EventBundle?, presetStart: LocalDateTime?, prefill: Prefill?) {
    val calendars by vm.calendars.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()
    val d = bundle?.detail
    // 其他 App「加到月曆」送來的內容只用在新增
    val pre = prefill?.takeIf { d == null }
    val init = remember { initialTimes(d, presetStart, settings.defaultDurationMin, zone, pre) }
    val mode = bundle?.syncMode ?: SyncMode.TITLE
    val originalRepeat = remember { Repeat.of(d?.rrule) }
    val customRepeat = d != null && originalRepeat == null
    // 重複行程的日期時間與規則交給 Google 月曆改：在這裡改整個系列的時間，被單獨改過或刪掉的那幾次會對不上
    val timeLocked = d?.recurring == true
    // 重複行程裡單獨改過的那一次（另一筆 Events）不能自己變成重複行程
    val repeatLocked = timeLocked || d?.originalId != null

    // 表單一開始的內容；用來判斷有沒有改過。欄位用 rememberSaveable，被通知切走再回來時不會清空
    val start0 = remember {
        FormSnapshot(
            title = d?.let { AlarmMarker.strip(it.title) } ?: pre?.title ?: "",
            allDay = d?.allDay ?: pre?.allDay ?: false,
            times = init,
            repeat = originalRepeat ?: pre?.repeat ?: Repeat.NONE,
            location = d?.location ?: pre?.location ?: "",
            description = d?.description ?: pre?.description ?: "",
            alarmOn = bundle?.alarmOn ?: pre?.alarmOn ?: false,
            offset = bundle?.offsetMin ?: settings.defaultOffsetMin,
            reminders = bundle?.reminders?.toSet() ?: setOfNotNull(settings.defaultReminderMin),
            colorKey = d?.colorKey,
        )
    }
    var title by rememberSaveable { mutableStateOf(start0.title) }
    var allDay by rememberSaveable { mutableStateOf(start0.allDay) }
    var startDate by rememberSaveable { mutableStateOf(init.startDate) }
    var startTime by rememberSaveable { mutableStateOf(init.startTime) }
    var endDate by rememberSaveable { mutableStateOf(init.endDate) }
    var endTime by rememberSaveable { mutableStateOf(init.endTime) }
    var calendarId by rememberSaveable { mutableLongStateOf(d?.calendarId ?: -1L) }
    var repeat by rememberSaveable { mutableStateOf(start0.repeat) }
    var location by rememberSaveable { mutableStateOf(start0.location) }
    var description by rememberSaveable { mutableStateOf(start0.description) }
    var alarmOn by rememberSaveable { mutableStateOf(start0.alarmOn) }
    var offset by rememberSaveable { mutableIntStateOf(start0.offset) }
    var reminders by rememberSaveable { mutableStateOf(start0.reminders) }
    var remindersTouched by rememberSaveable { mutableStateOf(bundle == null) }
    var colorKey by rememberSaveable { mutableStateOf(start0.colorKey) }
    var calendarTouched by rememberSaveable { mutableStateOf(false) }
    var picker by rememberSaveable { mutableStateOf<PickerTarget?>(null) }
    var dialog by rememberSaveable { mutableStateOf<EditDialog?>(null) }
    var titleError by rememberSaveable { mutableStateOf<String?>(null) }
    var timeError by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    // 帳號可用的行程顏色；null＝還在讀
    var colors by remember { mutableStateOf<List<EventColor>?>(null) }
    var locationFocused by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }

    // 別的 App 送來、還沒存的內容也算改過：關閉前要先確認，免得一按返回就不見
    val dirty = calendarTouched || pre != null ||
        FormSnapshot(title, allDay, FormTimes(startDate, startTime, endDate, endTime), repeat, location, description, alarmOn, offset, reminders, colorKey) != start0

    // 用跟存檔相同的方式從原行程算出內容，存檔時只寫入有改的欄位
    val originalDraft = remember {
        d?.let {
            EventDraft(
                calendarId = it.calendarId,
                title = it.title,
                description = it.description.trim(),
                location = it.location.trim(),
                allDay = it.allDay,
                start = if (it.allDay) init.startDate.atStartOfDay() else LocalDateTime.of(init.startDate, init.startTime),
                end = if (it.allDay) init.endDate.plusDays(1).atStartOfDay() else LocalDateTime.of(init.endDate, init.endTime),
                rrule = it.rrule,
                colorKey = it.colorKey,
            )
        }
    }

    val writable = calendars.filter { it.writable }
    val current = calendars.firstOrNull { it.id == calendarId }
    val google = current?.accountType == GOOGLE_ACCOUNT
    LaunchedEffect(calendars, settings.defaultCalendarId) {
        if (bundle == null && !calendarTouched) defaultCalendarFor(calendars, settings)?.let { calendarId = it.id }
    }
    LaunchedEffect(current?.accountName, current?.accountType) {
        colors = null
        colors = current?.let { vm.eventColors(it) }.orEmpty()
    }
    // 換到別的帳號的日曆時，原本選的顏色那個帳號不一定有
    LaunchedEffect(colors) {
        val list = colors ?: return@LaunchedEffect
        if (colorKey != null && list.none { it.key == colorKey }) colorKey = null
    }
    // 輸入地點時列出以前用過的地點
    LaunchedEffect(location, locationFocused) {
        if (!locationFocused) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(150)
        suggestions = vm.locationSuggestions(location)
    }

    // 存檔中不能關（存完會自己關），否則會多退一頁
    val close: () -> Unit = {
        when {
            saving -> Unit
            dirty -> confirmDiscard = true
            else -> vm.back()
        }
    }
    BackHandler(enabled = dirty || saving) { if (!saving) confirmDiscard = true }

    /** 改開始時間時保留原本的長度。 */
    fun setStart(newDate: LocalDate = startDate, newTime: LocalTime = startTime) {
        if (allDay) {
            val days = ChronoUnit.DAYS.between(startDate, endDate).coerceAtLeast(0)
            startDate = newDate
            endDate = newDate.plusDays(days)
            return
        }
        val oldStart = LocalDateTime.of(startDate, startTime)
        val oldEnd = LocalDateTime.of(endDate, endTime)
        val dur = Duration.between(oldStart, oldEnd).let { if (it.isNegative || it.isZero) Duration.ofMinutes(settings.defaultDurationMin.toLong()) else it }
        val newEnd = LocalDateTime.of(newDate, newTime).plus(dur)
        startDate = newDate
        startTime = newTime
        endDate = newEnd.toLocalDate()
        endTime = newEnd.toLocalTime()
    }

    /** 開鬧鐘時一般通知預設關掉，避免叮一聲又響鈴（可再手動加回來）。 */
    fun setAlarm(on: Boolean) {
        alarmOn = on
        if (on && reminders.isNotEmpty()) {
            reminders = emptySet()
            remindersTouched = true
        }
    }

    fun save() {
        val t = title.trim()
        val start = if (allDay) startDate.atStartOfDay() else LocalDateTime.of(startDate, startTime)
        val end = if (allDay) endDate.plusDays(1).atStartOfDay() else LocalDateTime.of(endDate, endTime)
        titleError = when {
            t.isEmpty() -> "請輸入標題"
            calendarId <= 0 -> "沒有可以寫入的日曆，請到設定確認已允許讀寫日曆"
            else -> null
        }
        timeError = if (!end.isAfter(start)) "結束時間要晚於開始時間" else null
        if (titleError != null || timeError != null) return
        // 重複選項沒動、開始日也沒動，就原樣保留原本的規則字串（WKST 等細節不會被改掉）
        val rrule = when {
            d != null && repeatLocked -> d.rrule
            d != null && repeat == originalRepeat && startDate == init.startDate -> d.rrule
            else -> repeat.toRrule(startDate)
        }
        val draft = EventDraft(calendarId, t, description.trim(), location.trim(), allDay, start, end, rrule, colorKey)
        saving = true
        vm.save(
            MainViewModel.SaveRequest(
                existing = bundle,
                draft = draft,
                original = originalDraft,
                alarmOn = alarmOn,
                offsetMin = offset,
                reminders = if (remindersTouched) reminders.sorted() else null,
            ),
            onSaved = { vm.back() },
            onFailed = { saving = false },
        )
    }

    Column(Modifier.fillMaxSize().background(Tok.canvas).imePadding()) {
        TopBar(
            title = if (bundle == null) "新增行程" else "編輯行程",
            navIcon = R.drawable.ic_close,
            navDescription = "不儲存，關閉",
            onNav = close,
            actions = { PrimaryButton("儲存", onClick = { save() }, enabled = !saving, modifier = Modifier.padding(end = Tok.sSm)) },
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Tok.sMd),
        ) {
            // 標題：大字、沒有外框
            PlainField(
                value = title,
                onValueChange = {
                    title = it.replace('\n', ' ').take(CalendarRepository.MAX_TITLE)
                    titleError = null
                },
                placeholder = "新增標題",
                textStyle = TypeTok.titleLg,
                isError = titleError != null,
                imeAction = ImeAction.Next,
                capitalize = true,
                modifier = Modifier.fillMaxWidth().padding(start = ICON_COL, top = Tok.sXs),
            )
            titleError?.let { ErrorText(it) }
            Divider()

            // 日曆（帳號）：名稱＋帳號，點了跳出依帳號分組的清單
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(enabled = bundle == null, role = Role.Button, onClickLabel = "選擇日曆") { dialog = EditDialog.CALENDAR }
                    .padding(vertical = Tok.sXs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(ICON_COL)) { ColorDot(current?.color, 18.dp) }
                Column(Modifier.weight(1f)) {
                    Text(current?.displayName ?: "沒有可寫入的日曆", style = TypeTok.body, color = Tok.ink)
                    if (current != null && current.accountName != current.displayName) {
                        Text(current.accountName, style = TypeTok.caption, color = Tok.slate)
                    }
                    if (bundle != null) Text("已建立的行程不能換日曆，要移動請到 Google 月曆", style = TypeTok.caption, color = Tok.slate)
                }
                if (bundle == null) AppIcon(R.drawable.ic_chevron_down, null, Tok.slate, 20.dp)
            }
            Divider()

            FormRow(R.drawable.ic_clock, divider = false) {
                SwitchRow(
                    title = "全天",
                    checked = allDay,
                    enabled = !timeLocked,
                    onChange = {
                        allDay = it
                        if (it && endDate.isBefore(startDate)) endDate = startDate
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            FormRow(null, divider = false) {
                ValueText(Fmt.dateWithDow(startDate), "開始日期", Modifier.weight(1f), enabled = !timeLocked) { picker = PickerTarget.START_DATE }
                if (!allDay) ValueText(Fmt.time(LocalDateTime.of(startDate, startTime)), "開始時間", Modifier, enabled = !timeLocked) { picker = PickerTarget.START_TIME }
            }
            FormRow(null, divider = false) {
                ValueText(Fmt.dateWithDow(endDate), "結束日期", Modifier.weight(1f), enabled = !timeLocked) { picker = PickerTarget.END_DATE }
                if (!allDay) ValueText(Fmt.time(LocalDateTime.of(endDate, endTime)), "結束時間", Modifier, enabled = !timeLocked) { picker = PickerTarget.END_TIME }
            }
            timeError?.let { ErrorText(it) }
            if (timeLocked) {
                Text(
                    "重複行程的日期、時間與重複規則請到 Google 月曆修改；標題、地點、說明、顏色和鬧鐘可以在這裡改。",
                    style = TypeTok.caption,
                    color = Tok.slate,
                    modifier = Modifier.padding(start = ICON_COL, bottom = Tok.sXs),
                )
            }
            FormRow(R.drawable.ic_repeat) {
                val label = when {
                    repeatLocked && !timeLocked -> "不重複（重複行程中單獨修改過的一次）"
                    customRepeat -> "自訂重複規則"
                    else -> repeat.label
                }
                ValueText(label, "重複", Modifier.weight(1f), enabled = !repeatLocked) { dialog = EditDialog.REPEAT }
            }

            // 地點：沒有連網，不能查地圖；輸入時列出以前行程用過的地點
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(ICON_COL)) { AppIcon(R.drawable.ic_pin, null, Tok.slate, 20.dp) }
                PlainField(
                    value = location,
                    onValueChange = { location = it.replace('\n', ' ').take(CalendarRepository.MAX_LOCATION) },
                    placeholder = "新增地點",
                    imeAction = ImeAction.Next,
                    modifier = Modifier.weight(1f).onFocusChanged { locationFocused = it.isFocused },
                )
            }
            if (locationFocused && suggestions.isNotEmpty()) {
                suggestions.forEach { s ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(Tok.rSm))
                            .clickable(role = Role.Button, onClickLabel = "填入這個地點") { location = s }
                            .padding(start = ICON_COL),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(R.drawable.ic_clock, null, Tok.slate, 16.dp)
                        Spacer(Modifier.width(Tok.sXs))
                        Text(s, style = TypeTok.bodySm, color = Tok.charcoal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Divider()

            // 鬧鐘設定卡：開關＋提前時間（提前時間一直顯示，選了就順便打開鬧鐘）
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
                    checked = alarmOn,
                    titleStyle = TypeTok.titleMd,
                    onChange = { setAlarm(it) },
                    leading = {
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(Tok.rMd)).background(Tok.canvas), contentAlignment = Alignment.Center) {
                            AppIcon(R.drawable.ic_alarm, null, Tok.primary, 20.dp)
                        }
                    },
                )
                LeadTimeRow(
                    minutes = offset,
                    active = alarmOn,
                    allDayAt = if (allDay) Fmt.minuteOfDay(settings.dayStartMinute) else null,
                    onClick = { dialog = EditDialog.LEAD },
                )
                if (alarmOn) {
                    val ringAt = if (allDay) null else LocalDateTime.of(startDate, startTime).minusMinutes(offset.toLong())
                    Row(Modifier.padding(top = Tok.sXxs), verticalAlignment = Alignment.Top) {
                        AppIcon(R.drawable.ic_info, null, Tok.slate, 15.dp, Modifier.padding(top = 2.dp))
                        Spacer(Modifier.width(Tok.sXs))
                        Text(
                            (if (ringAt != null) "${Fmt.dateWithDow(ringAt.toLocalDate())} ${Fmt.time(ringAt)} 響鈴；" else "") +
                                if (mode == SyncMode.TITLE) "Google 月曆的標題會加上 ⏰" else "⏰ 只記在這支手機，不會改到標題",
                            style = TypeTok.caption,
                            color = Tok.slate,
                        )
                    }
                }
            }

            // 一般通知（Google 月曆發的）：清單＋「新增通知」，跟 Google 月曆一樣
            Row(Modifier.fillMaxWidth().padding(vertical = Tok.sXxs), verticalAlignment = Alignment.Top) {
                Box(Modifier.width(ICON_COL).padding(top = Tok.sSm)) { AppIcon(R.drawable.ic_bell, null, Tok.slate, 20.dp) }
                Column(Modifier.weight(1f)) {
                    Text("一般通知", style = TypeTok.caption, color = Tok.slate, modifier = Modifier.padding(top = Tok.sSm))
                    reminders.sorted().forEach { m ->
                        val label = Fmt.reminderLabel(m)
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(label, style = TypeTok.body, color = Tok.ink, modifier = Modifier.weight(1f))
                            IconBtn(R.drawable.ic_close, "移除「$label」的通知", onClick = {
                                reminders = reminders - m
                                remindersTouched = true
                            }, tint = Tok.slate)
                        }
                    }
                    if (reminders.size < MAX_REMINDERS) {
                        Text(
                            "新增通知",
                            style = TypeTok.body,
                            color = Tok.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(Tok.rSm))
                                .clickable(role = Role.Button) { dialog = EditDialog.REMINDER }
                                .padding(vertical = Tok.sSm),
                        )
                    }
                    if (alarmOn && reminders.isEmpty()) {
                        Text("開了鬧鐘，一般通知預設關閉；需要的話可以再加。", style = TypeTok.caption, color = Tok.slate, modifier = Modifier.padding(bottom = Tok.sXs))
                    }
                }
            }
            Divider()

            // 顏色：帳號有提供行程顏色才顯示（Google 帳號有 11 種）
            val colorList = colors.orEmpty()
            if (colorList.isNotEmpty()) {
                val ordered = orderedColors(colorList, google)
                val selected = colorList.firstOrNull { it.key == colorKey }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clickable(role = Role.Button, onClickLabel = "選擇顏色") { dialog = EditDialog.COLOR },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(ICON_COL)) { ColorDot(selected?.color ?: current?.color, 18.dp) }
                    Text(
                        selected?.let { colorName(it, ordered, google) } ?: "預設顏色",
                        style = TypeTok.body,
                        color = Tok.ink,
                        modifier = Modifier.semantics { contentDescription = "顏色：${selected?.let { c -> colorName(c, ordered, google) } ?: "預設顏色"}" },
                    )
                }
                Divider()
            }

            // 說明
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.width(ICON_COL).padding(top = Tok.sMd)) { AppIcon(R.drawable.ic_notes, null, Tok.slate, 20.dp) }
                PlainField(
                    value = description,
                    onValueChange = { description = it.take(CalendarRepository.MAX_DESCRIPTION) },
                    placeholder = "新增說明",
                    singleLine = false,
                    minLines = 2,
                    modifier = Modifier.weight(1f),
                )
            }
            Divider()
            if (timeLocked) {
                Text("這是重複行程，修改會套用到整個系列；只改其中一次請到 Google 月曆。", style = TypeTok.caption, color = Tok.slate, modifier = Modifier.padding(vertical = Tok.sSm))
            }
            Spacer(Modifier.size(Tok.sXxl))
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = "放棄這次的修改？",
            text = "還沒儲存的內容會不見。",
            confirmText = "放棄",
            onConfirm = {
                confirmDiscard = false
                vm.back()
            },
            onDismiss = { confirmDiscard = false },
        )
    }

    val dismiss = { dialog = null }
    when (dialog) {
        EditDialog.CALENDAR -> ChoiceDialog("選擇日曆", dismiss) {
            writable.groupBy { it.accountName }.forEach { (account, list) ->
                OptionGroupTitle(account)
                list.forEach { c ->
                    OptionRow(c.displayName, selected = c.id == calendarId, leading = { ColorDot(c.color, 18.dp) }, onClick = {
                        if (c.id != calendarId) {
                            calendarId = c.id
                            calendarTouched = true
                        }
                        dialog = null
                    })
                }
            }
        }
        EditDialog.REPEAT -> ChoiceDialog("重複", dismiss) {
            Repeat.entries.forEach { r ->
                OptionRow(r.label, selected = r == repeat, onClick = {
                    repeat = r
                    dialog = null
                })
            }
        }
        EditDialog.LEAD -> LeadTimeDialog(offset, onPick = { m ->
            offset = m
            if (!alarmOn) setAlarm(true)
            dialog = null
        }, onDismiss = dismiss)
        EditDialog.REMINDER -> ReminderDialog(reminders, onPick = { m ->
            reminders = reminders + m
            remindersTouched = true
            dialog = null
        }, onDismiss = dismiss)
        EditDialog.COLOR -> ChoiceDialog("行程顏色", dismiss) {
            val ordered = orderedColors(colors.orEmpty(), google)
            OptionRow("預設顏色", selected = colorKey == null, subtitle = "跟日曆一樣", leading = { ColorDot(current?.color, 18.dp) }, onClick = {
                colorKey = null
                dialog = null
            })
            ordered.forEach { c ->
                OptionRow(colorName(c, ordered, google), selected = c.key == colorKey, leading = { ColorDot(c.color, 18.dp) }, onClick = {
                    colorKey = c.key
                    dialog = null
                })
            }
        }
        null -> Unit
    }

    // 日期、時間選擇器
    when (val target = picker) {
        PickerTarget.START_DATE, PickerTarget.END_DATE -> {
            val initial = if (target == PickerTarget.START_DATE) startDate else endDate
            val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            DatePickerDialog(
                onDismissRequest = { picker = null },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { ms ->
                            val date = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                            if (target == PickerTarget.START_DATE) setStart(newDate = date) else endDate = date
                            timeError = null
                        }
                        picker = null
                    }) { DialogText("確定", primary = true) }
                },
                dismissButton = { TextButton(onClick = { picker = null }) { DialogText("取消", primary = false) } },
            ) { DatePicker(state = state) }
        }
        PickerTarget.START_TIME, PickerTarget.END_TIME -> {
            val initial = if (target == PickerTarget.START_TIME) startTime else endTime
            val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
            AlertDialog(
                onDismissRequest = { picker = null },
                containerColor = Tok.canvas,
                confirmButton = {
                    TextButton(onClick = {
                        val t = LocalTime.of(state.hour, state.minute)
                        if (target == PickerTarget.START_TIME) setStart(newTime = t) else endTime = t
                        timeError = null
                        picker = null
                    }) { DialogText("確定", primary = true) }
                },
                dismissButton = { TextButton(onClick = { picker = null }) { DialogText("取消", primary = false) } },
                text = { TimePicker(state = state) },
            )
        }
        null -> Unit
    }
}

/** 左邊圖示欄的寬度：每一列的文字都從這裡開始，才對得齊。 */
private val ICON_COL = 36.dp

@Composable
private fun ErrorText(text: String) {
    Text(text, style = TypeTok.caption, color = Tok.error, modifier = Modifier.padding(top = Tok.sXxs, start = ICON_COL))
}

@Composable
private fun FormRow(icon: Int?, alignTop: Boolean = false, divider: Boolean = true, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(vertical = Tok.sXxs),
        verticalAlignment = if (alignTop) Alignment.Top else Alignment.CenterVertically,
    ) {
        Box(Modifier.width(ICON_COL).padding(top = if (alignTop) Tok.sSm else 0.dp)) {
            if (icon != null) AppIcon(icon, null, Tok.slate, 20.dp)
        }
        content()
    }
    if (divider) Divider()
}

/** 可以點開選擇器的值（日期、時間、重複）；讀屏唸「開始日期 10月12日（週一）」。 */
@Composable
private fun ValueText(text: String, label: String, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text,
        style = TypeTok.body,
        color = if (enabled) Tok.ink else Tok.slate,
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(Tok.rSm))
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "修改$label", onClick = onClick)
            .semantics { contentDescription = "$label $text" }
            .padding(vertical = Tok.sSm),
    )
}

/**
 * 照 Google 月曆編輯頁的無外框輸入列：沒有框，聚焦時底下一條重點色的線（DESIGN.md text-field）。
 * 用 Material 的 DecorationBox，提示字與讀屏行為跟一般輸入框一樣；左右不留內距，文字才跟其他列對齊。
 * 新增／編輯頁與搜尋框共用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlainField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = TypeTok.body,
    singleLine: Boolean = true,
    minLines: Int = 1,
    isError: Boolean = false,
    imeAction: ImeAction = ImeAction.Default,
    capitalize: Boolean = false,
    onImeAction: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val colors = TextFieldDefaults.colors(
        focusedContainerColor = Tok.canvas,
        unfocusedContainerColor = Tok.canvas,
        errorContainerColor = Tok.canvas,
        focusedIndicatorColor = Tok.primary,
        unfocusedIndicatorColor = Tok.canvas,
        errorIndicatorColor = Tok.error,
        cursorColor = Tok.primary,
        errorCursorColor = Tok.error,
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = textStyle.copy(color = Tok.ink),
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(
            capitalization = if (capitalize) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
            imeAction = imeAction,
        ),
        keyboardActions = if (onImeAction != null) KeyboardActions(onAny = { onImeAction() }) else KeyboardActions.Default,
        cursorBrush = SolidColor(if (isError) Tok.error else Tok.primary),
        interactionSource = interaction,
    ) { inner ->
        TextFieldDefaults.DecorationBox(
            value = value,
            innerTextField = inner,
            enabled = true,
            singleLine = singleLine,
            visualTransformation = VisualTransformation.None,
            interactionSource = interaction,
            isError = isError,
            placeholder = { Text(placeholder, style = textStyle, color = Tok.steel) },
            colors = colors,
            contentPadding = PaddingValues(vertical = Tok.sSm),
            container = {
                TextFieldDefaults.Container(enabled = true, isError = isError, interactionSource = interaction, colors = colors, shape = RectangleShape)
            },
        )
    }
}

private fun initialTimes(d: EventDetail?, preset: LocalDateTime?, durationMin: Int, zone: ZoneId, prefill: Prefill? = null): FormTimes {
    if (d != null) {
        return if (d.allDay) {
            val s = Instant.ofEpochMilli(d.dtStart).atZone(ZoneOffset.UTC).toLocalDate()
            val endExclusive = Instant.ofEpochMilli(d.endMillis).atZone(ZoneOffset.UTC).toLocalDate()
            val e = if (endExclusive.isAfter(s)) endExclusive.minusDays(1) else s
            FormTimes(s, LocalTime.of(9, 0), e, LocalTime.of(10, 0))
        } else {
            val s = Instant.ofEpochMilli(d.dtStart).atZone(zone).toLocalDateTime()
            val e = Instant.ofEpochMilli(d.endMillis).atZone(zone).toLocalDateTime()
            FormTimes(s.toLocalDate(), s.toLocalTime().withSecond(0).withNano(0), e.toLocalDate(), e.toLocalTime().withSecond(0).withNano(0))
        }
    }
    val pStart = prefill?.start
    if (prefill != null && pStart != null && prefill.allDay) {
        return FormTimes(pStart.toLocalDate(), LocalTime.of(9, 0), (prefill.end ?: pStart).toLocalDate(), LocalTime.of(10, 0))
    }
    val s = pStart ?: preset ?: LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)
    val e = prefill?.end?.takeIf { pStart != null && it.isAfter(s) } ?: s.plusMinutes(durationMin.toLong())
    return FormTimes(s.toLocalDate(), s.toLocalTime(), e.toLocalDate(), e.toLocalTime())
}
