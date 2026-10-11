package com.bryanh.calendaralarm.ui

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.CalendarContract
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bryanh.calendaralarm.ServiceLocator
import com.bryanh.calendaralarm.alarm.AlarmEngine
import com.bryanh.calendaralarm.alarm.AlarmScheduler
import com.bryanh.calendaralarm.alarm.PlannedAlarm
import com.bryanh.calendaralarm.data.AlarmRules
import com.bryanh.calendaralarm.data.AlarmState
import com.bryanh.calendaralarm.data.Attendee
import com.bryanh.calendaralarm.data.CalendarInfo
import com.bryanh.calendaralarm.data.DAY_MS
import com.bryanh.calendaralarm.data.EventColor
import com.bryanh.calendaralarm.data.EventDetail
import com.bryanh.calendaralarm.data.EventDraft
import com.bryanh.calendaralarm.data.EventInstance
import com.bryanh.calendaralarm.data.Lunar
import com.bryanh.calendaralarm.data.SearchQuery
import com.bryanh.calendaralarm.data.Settings
import com.bryanh.calendaralarm.data.SyncMode
import com.bryanh.calendaralarm.data.alarmKey
import com.bryanh.calendaralarm.widget.Widgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

sealed interface Screen {
    data object Home : Screen
    data class Day(val date: LocalDate) : Screen
    data class Detail(val eventId: Long, val begin: Long) : Screen
    data class Edit(val eventId: Long?, val presetStart: LocalDateTime?, val prefill: Prefill? = null) : Screen
    data object Alarms : Screen
    data object Settings : Screen
    data object Search : Screen
}

/** 搜尋畫面的狀態。query 是整理過、結果對應的關鍵字；error＝讀不到日曆。 */
data class SearchState(
    val query: String = "",
    val results: List<EventInstance> = emptyList(),
    val loading: Boolean = false,
    val error: Boolean = false,
)

/** 首頁的檢視：月、行程清單、自訂天數（時間格）、自訂週數（月曆格）。日檢視是點進去的另一頁。 */
enum class HomeMode { MONTH, AGENDA, DAYS, WEEKS }

data class PermState(
    val calendar: Boolean = false,
    val notifications: Boolean = false,
    val fullScreen: Boolean = false,
    val exactAlarm: Boolean = false,
    val batteryUnrestricted: Boolean = false,
    val alarmVolumeOk: Boolean = true,
) {
    /** 鬧鐘要能響、要看得到響鈴畫面所需的條件；少一項首頁就顯示提醒。電池最佳化只影響背景同步，另外提示。 */
    val alarmReady: Boolean get() = notifications && fullScreen && exactAlarm && alarmVolumeOk
}

/** 月曆格的資料。month 是 null 時是「N 週」檢視（從某週開始連續 N 週，不分月份）。 */
data class MonthData(
    val month: YearMonth?,
    val days: List<LocalDate>,
    val instances: List<EventInstance>,
    val lunar: Map<LocalDate, String>,
    val lunarRange: String,
)

/** 「N 天」時間格的資料。 */
data class DaysData(
    val days: List<LocalDate>,
    val instances: List<EventInstance>,
    val lunar: Map<LocalDate, String>,
)

/** 行程清單：目前載入的日期範圍（含頭不含尾）與行程。 */
data class AgendaData(
    val from: LocalDate,
    val to: LocalDate,
    val events: List<EventInstance>,
    val canLoadMore: Boolean,
)

/** 詳情／編輯畫面用：行程內容＋鬧鐘狀態＋同步方式。 */
data class EventBundle(
    val detail: EventDetail,
    val instanceBegin: Long,
    val syncMode: SyncMode,
    val hasOtherAttendees: Boolean,
    val attendees: List<Attendee>,
    val alarmOn: Boolean,
    val offsetMin: Int?,
    val reminders: List<Int>,
    val skipped: Boolean,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ServiceLocator.repo(app)
    private val store = ServiceLocator.store(app)
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val ctx: Application get() = getApplication()

    val settings: StateFlow<Settings> = store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val alarmState: StateFlow<AlarmState> = store.alarmState.stateIn(viewModelScope, SharingStarted.Eagerly, AlarmState.EMPTY)

    /** 讀到第一筆設定前不畫畫面，避免每次開啟時首次設定精靈閃一下。 */
    val ready: StateFlow<Boolean> = store.settings.map { true }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _stack = MutableStateFlow<List<Screen>>(listOf(Screen.Home))
    val stack: StateFlow<List<Screen>> = _stack.asStateFlow()

    private val _homeMode = MutableStateFlow(HomeMode.MONTH)
    val homeMode: StateFlow<HomeMode> = _homeMode.asStateFlow()

    private val _month = MutableStateFlow<MonthData?>(null)
    val month: StateFlow<MonthData?> = _month.asStateFlow()
    private var currentMonth: YearMonth = YearMonth.now()

    private val _weeks = MutableStateFlow<MonthData?>(null)
    val weeks: StateFlow<MonthData?> = _weeks.asStateFlow()
    private var weeksFirst: LocalDate = LocalDate.now()

    private val _multiDay = MutableStateFlow<DaysData?>(null)
    val multiDay: StateFlow<DaysData?> = _multiDay.asStateFlow()
    private var daysFirst: LocalDate = LocalDate.now()

    private val _day = MutableStateFlow<List<EventInstance>>(emptyList())
    val day: StateFlow<List<EventInstance>> = _day.asStateFlow()

    private var agendaFrom: LocalDate = LocalDate.now()
    private var agendaTo: LocalDate = LocalDate.now().plusDays(AGENDA_CHUNK_DAYS)
    private var agendaJob: Job? = null
    private val _agenda = MutableStateFlow<AgendaData?>(null)
    val agenda: StateFlow<AgendaData?> = _agenda.asStateFlow()

    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search.asStateFlow()
    private var searchJob: Job? = null

    private val _calendars = MutableStateFlow<List<CalendarInfo>>(emptyList())
    val calendars: StateFlow<List<CalendarInfo>> = _calendars.asStateFlow()

    private val _upcoming = MutableStateFlow<List<PlannedAlarm>>(emptyList())
    val upcoming: StateFlow<List<PlannedAlarm>> = _upcoming.asStateFlow()

    private val _perms = MutableStateFlow(PermState())
    val perms: StateFlow<PermState> = _perms.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _dataVersion = MutableStateFlow(0)

    /** 每次重新載入（日曆資料變了、回到前景）就加一：詳情頁靠它重讀，同步進來的修改才看得到。 */
    val dataVersion: StateFlow<Int> = _dataVersion.asStateFlow()

    private var reloadJob: Job? = null
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = scheduleReload()
    }
    private var observing = false

    init {
        refreshPermissions()
        reload()
        viewModelScope.launch {
            // 週起始日、顯示哪些日曆、農曆、自訂天數週數改了就重載；小工具也照新的設定重畫
            var last: Settings? = null
            settings.collect { s ->
                val prev = last
                last = s
                if (prev == null) return@collect
                val view = prev.weekStartsMonday != s.weekStartsMonday || prev.hiddenCalendarIds != s.hiddenCalendarIds ||
                    prev.showLunar != s.showLunar || prev.customDays != s.customDays || prev.customWeeks != s.customWeeks
                if (prev.weekStartsMonday != s.weekStartsMonday) weeksFirst = weekStartOf(weeksFirst)
                if (view) reload()
                val range = prev.widgetRange != s.widgetRange || prev.customDays != s.customDays ||
                    prev.customWeeks != s.customWeeks || prev.weekStartsMonday != s.weekStartsMonday
                if (view || range || prev.dayStartMinute != s.dayStartMinute) Widgets.refresh(ctx, resetOffset = range)
            }
        }
    }

    // ── 導覽 ──────────────────────────────────────────────
    fun navigate(screen: Screen) = _stack.update { it + screen }

    fun back(): Boolean {
        if (_stack.value.size <= 1) return false
        _stack.update { it.dropLast(1) }
        return true
    }

    fun replaceTop(screen: Screen) = _stack.update { it.dropLast(1) + screen }

    fun setHomeMode(mode: HomeMode) {
        _homeMode.value = mode
        _stack.value = listOf(Screen.Home)
        when (mode) {
            HomeMode.AGENDA -> {
                agendaFrom = LocalDate.now()
                agendaTo = agendaFrom.plusDays(AGENDA_CHUNK_DAYS)
            }
            HomeMode.WEEKS -> weeksFirst = weekStartOf(LocalDate.now())
            HomeMode.DAYS -> daysFirst = LocalDate.now()
            HomeMode.MONTH -> currentMonth = YearMonth.now()
        }
        reload()
    }

    /** 左側選單的「日」：回到首頁後打開今天的日檢視。 */
    fun openToday() = openDay(LocalDate.now())

    /** 從小工具點日期：回到首頁後打開那一天的日檢視，按返回回到月曆。 */
    fun openDay(date: LocalDate) {
        _stack.value = listOf(Screen.Home, Screen.Day(date))
    }

    /** 從小工具按「＋」：新增畫面，關掉後回到首頁。 */
    fun openNewEvent() {
        _stack.value = listOf(Screen.Home, Screen.Edit(null, null))
    }

    /** 其他 App 的月曆請求（已檢查過）：加到月曆開新增畫面並預填，開行程、開某一天；按返回回到首頁。 */
    fun openExternal(req: ExternalRequest) {
        when (req) {
            is ExternalRequest.Insert -> {
                _stack.value = listOf(Screen.Home, Screen.Edit(null, null, req.prefill))
                if (req.prefill.droppedRepeat) showMessage("重複規則比較複雜，這裡沒有套用；存檔後可到 Google 月曆設定")
            }
            is ExternalRequest.ViewEvent -> _stack.value = listOf(Screen.Home, Screen.Detail(req.eventId, req.begin))
            is ExternalRequest.ViewDay -> openDay(req.date)
        }
    }

    fun showMessage(text: String) {
        _message.value = text
    }

    fun clearMessage() {
        _message.value = null
    }

    // ── 權限 ──────────────────────────────────────────────
    fun refreshPermissions() {
        fun granted(p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pm = ctx.getSystemService(PowerManager::class.java)
        val audio = ctx.getSystemService(AudioManager::class.java)
        val calendar = granted(Manifest.permission.READ_CALENDAR) && granted(Manifest.permission.WRITE_CALENDAR)
        _perms.value = PermState(
            calendar = calendar,
            notifications = granted(Manifest.permission.POST_NOTIFICATIONS) && nm?.areNotificationsEnabled() == true,
            fullScreen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) nm?.canUseFullScreenIntent() == true else true,
            exactAlarm = am?.canScheduleExactAlarms() == true,
            batteryUnrestricted = pm?.isIgnoringBatteryOptimizations(ctx.packageName) == true,
            alarmVolumeOk = (audio?.getStreamVolume(AudioManager.STREAM_ALARM) ?: 1) > 0,
        )
        if (calendar && !observing) {
            ctx.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
            observing = true
        }
    }

    override fun onCleared() {
        if (observing) ctx.contentResolver.unregisterContentObserver(observer)
        super.onCleared()
    }

    // ── 載入資料 ──────────────────────────────────────────
    private fun scheduleReload() {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            delay(400)
            reload()
        }
    }

    fun reload() {
        _dataVersion.update { it + 1 }
        viewModelScope.launch {
            when (_homeMode.value) {
                HomeMode.MONTH -> loadMonth(currentMonth)
                HomeMode.WEEKS -> loadWeeks(weeksFirst)
                HomeMode.DAYS -> loadDays(daysFirst)
                HomeMode.AGENDA -> Unit
            }
            val top = _stack.value.lastOrNull()
            if (top is Screen.Day) loadDay(top.date)
            if (_homeMode.value == HomeMode.AGENDA) loadAgenda()
            _calendars.value = withContext(Dispatchers.IO) { repo.calendars() }
            if (top is Screen.Alarms) loadUpcoming()
            // 從搜尋結果點進去改了行程，回來時結果也是新的
            if (top is Screen.Search && _search.value.query.isNotEmpty()) runSearch(_search.value.query)
        }
    }

    // ── 搜尋 ──────────────────────────────────────────────
    /** 打開搜尋畫面，從空白開始。 */
    fun openSearch() {
        searchJob?.cancel()
        _search.value = SearchState()
        navigate(Screen.Search)
    }

    /** 輸入框每次變動就呼叫；停 [SEARCH_DEBOUNCE_MS] 沒再打字才真的查，前一次還沒查完就取消。 */
    fun search(raw: String) {
        val q = SearchQuery.normalize(raw)
        searchJob?.cancel()
        if (q == null) {
            _search.value = SearchState()
            return
        }
        if (q == _search.value.query && !_search.value.loading && !_search.value.error) return
        _search.update { it.copy(loading = true) }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            runSearch(q)
        }
    }

    /** 搜尋前後 [SearchQuery.YEARS] 年；只列顯示中的日曆。 */
    private suspend fun runSearch(q: String) {
        val s = settings.value
        val today = LocalDate.now()
        val from = startMillis(today.minusYears(SearchQuery.YEARS))
        val to = startMillis(today.plusYears(SearchQuery.YEARS).plusDays(1))
        val found = withContext(Dispatchers.IO) { repo.searchInstances(q, from, to) }
        _search.value = if (found == null) {
            SearchState(query = q, error = true)
        } else {
            SearchState(query = q, results = SearchQuery.nearest(visible(found, s), System.currentTimeMillis()))
        }
    }

    fun showMonth(month: YearMonth) {
        currentMonth = month
        viewModelScope.launch { loadMonth(month) }
    }

    /** 上一頁／下一頁：月檢視一個月、「N 週」N 週、「N 天」N 天。 */
    fun shift(delta: Int) {
        val s = settings.value
        when (_homeMode.value) {
            HomeMode.MONTH -> showMonth(currentMonth.plusMonths(delta.toLong()))
            HomeMode.WEEKS -> {
                weeksFirst = weeksFirst.plusWeeks(delta.toLong() * s.customWeeks)
                viewModelScope.launch { loadWeeks(weeksFirst) }
            }
            HomeMode.DAYS -> {
                daysFirst = daysFirst.plusDays(delta.toLong() * s.customDays)
                viewModelScope.launch { loadDays(daysFirst) }
            }
            HomeMode.AGENDA -> Unit
        }
    }

    /** 回到今天：月檢視回到這個月，「N 週」從這週開始，「N 天」從今天開始。 */
    fun goToday() {
        when (_homeMode.value) {
            HomeMode.MONTH -> showMonth(YearMonth.now())
            HomeMode.WEEKS -> {
                weeksFirst = weekStartOf(LocalDate.now())
                viewModelScope.launch { loadWeeks(weeksFirst) }
            }
            HomeMode.DAYS -> {
                daysFirst = LocalDate.now()
                viewModelScope.launch { loadDays(daysFirst) }
            }
            HomeMode.AGENDA -> setHomeMode(HomeMode.AGENDA)
        }
    }

    fun weekStart(): DayOfWeek = if (settings.value.weekStartsMonday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY

    private fun weekStartOf(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(weekStart()))

    private suspend fun loadMonth(month: YearMonth) {
        val s = settings.value
        val first = month.atDay(1).with(TemporalAdjusters.previousOrSame(weekStart()))
        val last = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(weekStart().minus(1)))
        val days = generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
        val data = withContext(Dispatchers.IO) {
            val instances = visible(repo.instances(startMillis(first), startMillis(last.plusDays(1))), s)
            val lunar = if (s.showLunar) days.associateWith { Lunar.dayLabel(it) } else emptyMap()
            val range = if (s.showLunar) Lunar.rangeLabel(first, last) else ""
            MonthData(month, days, instances, lunar, range)
        }
        if (month == currentMonth) _month.value = data
    }

    private suspend fun loadWeeks(first: LocalDate) {
        val s = settings.value
        val days = (0L until 7L * s.customWeeks).map { first.plusDays(it) }
        val data = withContext(Dispatchers.IO) {
            val instances = visible(repo.instances(startMillis(days.first()), startMillis(days.last().plusDays(1))), s)
            val lunar = if (s.showLunar) days.associateWith { Lunar.dayLabel(it) } else emptyMap()
            val range = if (s.showLunar) Lunar.rangeLabel(days.first(), days.last()) else ""
            MonthData(null, days, instances, lunar, range)
        }
        if (first == weeksFirst) _weeks.value = data
    }

    private suspend fun loadDays(first: LocalDate) {
        val s = settings.value
        val days = (0L until s.customDays.toLong()).map { first.plusDays(it) }
        val data = withContext(Dispatchers.IO) {
            val instances = visible(repo.instances(startMillis(days.first()), startMillis(days.last().plusDays(1))), s)
            DaysData(days, instances, if (s.showLunar) days.associateWith { Lunar.dayLabel(it) } else emptyMap())
        }
        if (first == daysFirst) _multiDay.value = data
    }

    fun loadDay(date: LocalDate) {
        viewModelScope.launch {
            val s = settings.value
            val weekFirst = date.with(TemporalAdjusters.previousOrSame(weekStart()))
            _day.value = withContext(Dispatchers.IO) {
                visible(repo.instances(startMillis(weekFirst), startMillis(weekFirst.plusDays(7))), s)
            }
        }
    }

    private fun loadAgenda() {
        agendaJob?.cancel()
        agendaJob = viewModelScope.launch {
            val s = settings.value
            val from = agendaFrom
            val to = agendaTo
            val events = withContext(Dispatchers.IO) { visible(repo.instances(startMillis(from), startMillis(to)), s) }
            _agenda.value = AgendaData(from, to, events, canLoadMore = to.isBefore(LocalDate.now().plusYears(AGENDA_MAX_YEARS)))
        }
    }

    /** 行程清單往前或往後多載一段（無限捲動）。正在載入的會被換成新範圍的那一次。 */
    fun extendAgenda(earlier: Boolean) {
        if (earlier) {
            agendaFrom = agendaFrom.minusDays(AGENDA_CHUNK_DAYS)
        } else {
            if (_agenda.value?.canLoadMore == false) return
            agendaTo = agendaTo.plusDays(AGENDA_CHUNK_DAYS)
        }
        loadAgenda()
    }

    fun loadUpcoming() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            _upcoming.value = withContext(Dispatchers.IO) {
                try {
                    AlarmEngine.compute(ctx, now, includeSkipped = true).filter { it.triggerAt < now + 7 * DAY_MS }
                } catch (e: Exception) {
                    Log.w(TAG, "loadUpcoming failed", e)
                    emptyList()
                }
            }
        }
    }

    private fun visible(list: List<EventInstance>, s: Settings) = list.filter { it.calendarId !in s.hiddenCalendarIds }

    private fun startMillis(d: LocalDate): Long = d.atStartOfDay(zone).toInstant().toEpochMilli()

    // ── 行程詳情與鬧鐘 ────────────────────────────────────
    suspend fun loadBundle(eventId: Long, begin: Long): EventBundle? = withContext(Dispatchers.IO) {
        val detail = repo.eventDetail(eventId) ?: return@withContext null
        val attendees = repo.attendees(eventId)
        val others = attendees.any { it.email.isNotBlank() && !it.email.equals(detail.ownerAccount, ignoreCase = true) }
        val state = store.alarmStateNow()
        // 單次行程的時間以最新資料為準（電腦上改了時間，停在詳情頁也會更新）；重複行程用點進來的那一次
        val instanceBegin = if (detail.recurring && begin > 0) begin else detail.dtStart
        EventBundle(
            detail = detail,
            instanceBegin = instanceBegin,
            syncMode = AlarmRules.syncMode(detail.canEditTitle, others),
            hasOtherAttendees = others,
            attendees = attendees,
            alarmOn = AlarmRules.isAlarmOn(detail.title, state.flagFor(eventId, detail.originalId)),
            offsetMin = state.offsetFor(eventId, detail.originalId),
            reminders = repo.alertReminders(eventId),
            skipped = alarmKey(eventId, instanceBegin) in state.skips,
        )
    }

    /**
     * 詳情頁的鬧鐘開關。TITLE 模式改 Google 月曆標題（寫入前重讀一次標題，不蓋掉電腦上剛改的）；
     * LOCAL 模式只記在手機。打開時一併關掉這筆行程的 Google 一般通知，避免叮一聲又響鈴（規格第 3 節）。
     */
    fun setAlarm(bundle: EventBundle, on: Boolean, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val id = bundle.detail.id
            val result = withContext(Dispatchers.IO) {
                val ok = when (bundle.syncMode) {
                    SyncMode.TITLE -> {
                        val current = repo.eventDetail(id)?.title ?: bundle.detail.title
                        val newTitle = AlarmRules.titleToSave(current, current, SyncMode.TITLE, on)
                        val updated = newTitle == current || repo.updateTitle(id, newTitle)
                        if (updated) store.setLocalFlag(id, titleModeFlag(bundle.detail.originalId, on))
                        updated
                    }
                    SyncMode.LOCAL -> {
                        store.setLocalFlag(id, on)
                        true
                    }
                }
                val clearedReminders = ok && on && bundle.detail.calendarWritable && bundle.reminders.isNotEmpty() &&
                    repo.replaceAlertReminders(id, emptyList())
                ok to clearedReminders
            }
            when {
                !result.first -> showMessage("改不了這筆行程的標題，請確認日曆權限")
                result.second -> showMessage("已開鬧鐘，並關掉這筆行程的 Google 一般通知（可在編輯裡加回）")
            }
            AlarmEngine.reschedule(ctx)
            reload()
            onDone()
        }
    }

    /** 跟沿用的值（系列的設定或預設提前時間）一樣就不另外記，之後改預設值時這筆也會跟著變。 */
    fun setOffset(eventId: Long, originalId: Long?, minutes: Int, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            store.setOffset(eventId, minutes.takeIf { it != inheritedOffset(originalId) })
            AlarmEngine.reschedule(ctx)
            onDone()
        }
    }

    /** 重複行程單獨改過的那一次沒有自己的設定時沿用系列的；系列沒設就用預設提前時間。 */
    private suspend fun inheritedOffset(originalId: Long?): Int =
        originalId?.let { store.alarmStateNow().offsets[it] } ?: settings.value.defaultOffsetMin

    /**
     * TITLE 模式通常不留手機上的開關（以標題為準）；但如果是單獨改過的那一次、而系列在手機上有開關，
     * 不留的話會沿用系列的開關、蓋過這一次標題上的 ⏰，所以明確記下。
     */
    private suspend fun titleModeFlag(originalId: Long?, on: Boolean): Boolean? =
        if (originalId != null && originalId in store.alarmStateNow().localFlags) on else null

    fun setSkip(key: String, skip: Boolean, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            store.setSkip(key, skip)
            AlarmEngine.reschedule(ctx)
            if (_stack.value.lastOrNull() is Screen.Alarms) loadUpcoming()
            onDone()
        }
    }

    /** 日曆所屬帳號可用的行程顏色（新增／編輯頁的顏色選項）。 */
    suspend fun eventColors(calendar: CalendarInfo): List<EventColor> =
        withContext(Dispatchers.IO) { repo.eventColors(calendar.accountName, calendar.accountType) }

    /** 輸入地點時的建議：以前行程用過、含有這段文字的地點。 */
    suspend fun locationSuggestions(query: String): List<String> =
        withContext(Dispatchers.IO) { repo.recentLocations(query, LOCATION_SUGGESTIONS) }

    // ── 新增／編輯／刪除 ──────────────────────────────────
    /** original：用跟 draft 相同方式從原行程算出的內容，只寫入兩者不同的欄位。新增時為 null。 */
    data class SaveRequest(
        val existing: EventBundle?,
        val draft: EventDraft,
        val original: EventDraft?,
        val alarmOn: Boolean,
        val offsetMin: Int,
        val reminders: List<Int>?,
    )

    /** ok＝行程已存（可以關掉表單）；message＝要告訴使用者的話（失敗原因或部分失敗）。 */
    private data class SaveResult(val ok: Boolean, val message: String? = null)

    fun save(req: SaveRequest, onSaved: () -> Unit, onFailed: () -> Unit = {}) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { saveBlocking(req) }
            result.message?.let { showMessage(it) }
            if (!result.ok) {
                onFailed()
                return@launch
            }
            AlarmEngine.reschedule(ctx)
            reload()
            onSaved()
        }
    }

    private suspend fun saveBlocking(req: SaveRequest): SaveResult {
        val existing = req.existing
        val mode = existing?.syncMode ?: SyncMode.TITLE
        val title = AlarmRules.titleToSave(req.draft.title, existing?.detail?.title, mode, req.alarmOn)
        val draft = req.draft.copy(title = title)
        val id = if (existing == null) {
            repo.insertEvent(draft) ?: return SaveResult(false, "存檔失敗：請確認已允許讀寫日曆，且選的日曆可以寫入")
        } else {
            val original = req.original ?: return SaveResult(false, "存檔失敗：請重新開啟這筆行程再試一次")
            if (!repo.updateEvent(existing.detail.id, draft, original)) return SaveResult(false, "存檔失敗：這筆行程可能是唯讀的")
            existing.detail.id
        }
        val originalId = existing?.detail?.originalId
        if (req.alarmOn) store.setOffset(id, req.offsetMin.takeIf { it != inheritedOffset(originalId) })
        when (mode) {
            SyncMode.TITLE -> store.setLocalFlag(id, titleModeFlag(originalId, req.alarmOn))
            SyncMode.LOCAL -> store.setLocalFlag(id, req.alarmOn)
        }
        val reminders = req.reminders ?: return SaveResult(true)
        return if (repo.replaceAlertReminders(id, reminders)) SaveResult(true) else SaveResult(true, "行程已儲存，但一般通知沒有改成功")
    }

    fun delete(bundle: EventBundle, onDeleted: () -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { repo.deleteEvent(bundle.detail.id) }
            if (!ok) {
                showMessage("刪除失敗：這筆行程可能是唯讀的")
                return@launch
            }
            store.forgetEvent(bundle.detail.id)
            AlarmEngine.reschedule(ctx)
            reload()
            onDeleted()
        }
    }

    // ── 設定 ──────────────────────────────────────────────
    fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch {
            store.updateSettings(transform)
            AlarmEngine.reschedule(ctx)
        }
    }

    fun toggleCalendar(calendar: CalendarInfo, shown: Boolean) = updateSettings { s ->
        s.copy(hiddenCalendarIds = if (shown) s.hiddenCalendarIds - calendar.id else s.hiddenCalendarIds + calendar.id)
    }

    /** 首次設定完成：預設隱藏原本在 Google 月曆 App 裡就沒勾選的日曆。 */
    fun finishWizard() {
        viewModelScope.launch {
            val cals = withContext(Dispatchers.IO) { repo.calendars() }
            store.updateSettings { s ->
                s.copy(
                    wizardDone = true,
                    hiddenCalendarIds = if (s.wizardDone) s.hiddenCalendarIds else cals.filter { !it.visible }.map { it.id }.toSet(),
                )
            }
            AlarmEngine.reschedule(ctx)
        }
    }

    fun testAlarm() {
        AlarmScheduler.scheduleTest(ctx)
        showMessage("10 秒後會響，可以先關螢幕試試看")
    }

    fun onPermissionsChanged() {
        refreshPermissions()
        reload()
        AlarmEngine.rescheduleAsync(ctx)
    }

    private companion object {
        const val TAG = "MainViewModel"
        const val AGENDA_CHUNK_DAYS = 90L
        const val AGENDA_MAX_YEARS = 3L
        const val LOCATION_SUGGESTIONS = 5
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
