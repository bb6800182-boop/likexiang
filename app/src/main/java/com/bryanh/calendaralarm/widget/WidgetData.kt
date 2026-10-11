package com.bryanh.calendaralarm.widget

import android.content.Context
import com.bryanh.calendaralarm.ServiceLocator
import com.bryanh.calendaralarm.data.AlarmRules
import com.bryanh.calendaralarm.data.DAY_MS
import com.bryanh.calendaralarm.data.EventInstance
import com.bryanh.calendaralarm.data.Lunar
import com.bryanh.calendaralarm.data.Settings
import com.bryanh.calendaralarm.data.WidgetRange
import com.bryanh.calendaralarm.ui.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * 月曆小工具要顯示的資料。讀不到日曆（沒權限或查詢失敗）時 [ok] 為 false，畫面改顯示提示。
 * weeks 是一列一列的日期：「月」「N 週」每列 7 天；「N 天」只有一列 N 天。month 只有「月」才有（別的月日期變淡）。
 */
class MonthModel(
    val ok: Boolean,
    val month: YearMonth?,
    val title: String,
    val range: WidgetRange,
    val today: LocalDate,
    val weeks: List<List<LocalDate>>,
    val instances: List<EventInstance>,
    val alarmOn: Set<String>,
    val lunar: Map<LocalDate, String>,
    val lunarRange: String,
    /** 載入時的上下月位移與重新整理次數：小工具狀態變了才重讀。 */
    val offset: Int,
    val refresh: Int,
)

sealed interface AgendaRow {
    data class Header(val date: LocalDate) : AgendaRow
    data class Event(val inst: EventInstance, val alarm: Boolean) : AgendaRow
}

class AgendaModel(val ok: Boolean, val today: LocalDate, val rows: List<AgendaRow>, val refresh: Int)

object WidgetData {
    /** 行程清單小工具往後看幾天、最多幾筆（太多會超過小工具的資料上限）。 */
    const val AGENDA_DAYS = 30
    private const val AGENDA_MAX = 80

    suspend fun month(context: Context, offset: Int, refresh: Int): MonthModel = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val store = ServiceLocator.store(context)
        val s = store.settingsNow()
        val weekStart = if (s.weekStartsMonday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY
        // 位移的單位跟著範圍：一個月、N 週或 N 天
        val month = if (s.widgetRange == WidgetRange.MONTH) YearMonth.from(today).plusMonths(offset.toLong()) else null
        val days = when (s.widgetRange) {
            WidgetRange.MONTH -> {
                val m = month ?: YearMonth.from(today)
                val first = m.atDay(1).with(TemporalAdjusters.previousOrSame(weekStart))
                val last = m.atEndOfMonth().with(TemporalAdjusters.nextOrSame(weekStart.minus(1)))
                generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
            }
            WidgetRange.WEEKS -> {
                val first = today.with(TemporalAdjusters.previousOrSame(weekStart)).plusWeeks(offset.toLong() * s.customWeeks)
                (0L until 7L * s.customWeeks).map { first.plusDays(it) }
            }
            WidgetRange.DAYS -> {
                val first = today.plusDays(offset.toLong() * s.customDays)
                (0L until s.customDays.toLong()).map { first.plusDays(it) }
            }
        }
        val first = days.first()
        val last = days.last()
        val instances = query(context, s, first.atStartOfDay(zone).toInstant().toEpochMilli(), last.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
        val state = store.alarmStateNow()
        MonthModel(
            ok = instances != null,
            month = month,
            title = month?.let { Fmt.monthTitle(it.year, it.monthValue, it.year != today.year) } ?: Fmt.monthSpan(first, last, today),
            range = s.widgetRange,
            today = today,
            weeks = if (s.widgetRange == WidgetRange.DAYS) listOf(days) else days.chunked(7),
            instances = instances.orEmpty(),
            alarmOn = instances.orEmpty().filter { AlarmRules.isAlarmOn(it, state) }.map { it.key }.toSet(),
            lunar = if (s.showLunar) days.associateWith { Lunar.dayLabel(it) } else emptyMap(),
            lunarRange = if (s.showLunar) Lunar.rangeLabel(month?.atDay(1) ?: first, month?.atEndOfMonth() ?: last) else "",
            offset = offset,
            refresh = refresh,
        )
    }

    suspend fun agenda(context: Context, refresh: Int): AgendaModel = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val store = ServiceLocator.store(context)
        val from = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val instances = query(context, store.settingsNow(), from, from + AGENDA_DAYS * DAY_MS)
            ?: return@withContext AgendaModel(false, today, emptyList(), refresh)
        val state = store.alarmStateNow()
        val rows = ArrayList<AgendaRow>()
        var lastDate: LocalDate? = null
        // 跨日行程只列在開始那天；已經開始的跨日行程列在今天。
        // 昨天的全天行程（UTC 午夜到午夜）跟今天凌晨重疊也會被查到，要去掉
        instances.filter { !it.endDate(zone).isBefore(today) }
            .sortedWith(compareBy({ maxOf(it.startDate(zone), today) }, { !it.allDay }, { it.begin }))
            .take(AGENDA_MAX)
            .forEach { inst ->
                val date = maxOf(inst.startDate(zone), today)
                if (date != lastDate) {
                    rows += AgendaRow.Header(date)
                    lastDate = date
                }
                rows += AgendaRow.Event(inst, AlarmRules.isAlarmOn(inst, state))
            }
        AgendaModel(true, today, rows, refresh)
    }

    /** 跟 App 一樣只顯示勾選的日曆；沒有日曆權限或查詢失敗回傳 null。 */
    private fun query(context: Context, s: Settings, from: Long, to: Long): List<EventInstance>? {
        val repo = ServiceLocator.repo(context)
        if (!repo.hasReadPermission()) return null
        return repo.instancesOrNull(from, to)?.filter { it.calendarId !in s.hiddenCalendarIds }
    }
}
