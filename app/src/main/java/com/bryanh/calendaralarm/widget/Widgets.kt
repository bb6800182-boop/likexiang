package com.bryanh.calendaralarm.widget

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.unit.ColorProvider
import com.bryanh.calendaralarm.MainActivity
import com.bryanh.calendaralarm.alarm.AlarmScheduler
import java.time.LocalDate
import java.time.ZoneId

/** 兩個小工具共用：重新整理、過午夜換日、開啟 App 的入口。 */
object Widgets {
    private const val TAG = "Widgets"

    /** 小工具狀態：上下月位移（只有月曆小工具用）、重新整理次數（加一就重讀資料）。 */
    val OFFSET = intPreferencesKey("month_offset")
    val REFRESH = intPreferencesKey("refresh")

    /**
     * 行程或鬧鐘狀態變了、過了午夜、改了時區時呼叫：讓每個小工具重讀資料。
     * 小工具的錯誤不能影響鬧鐘，所以這裡吞掉例外只記 log。
     */
    suspend fun refresh(context: Context, resetOffset: Boolean = false) {
        val app = context.applicationContext
        try {
            val manager = GlanceAppWidgetManager(app)
            var any = false
            any = refreshAll(app, manager.getGlanceIds(MonthWidget::class.java), MonthWidget(), resetOffset) || any
            any = refreshAll(app, manager.getGlanceIds(AgendaWidget::class.java), AgendaWidget()) || any
            scheduleMidnight(app, any)
        } catch (e: Exception) {
            Log.w(TAG, "widget refresh failed", e)
        }
    }

    /** resetOffset：小工具的範圍改了（月、N 天、N 週），原本的上下頁位移就沒意義，回到今天。 */
    private suspend fun refreshAll(context: Context, ids: List<androidx.glance.GlanceId>, widget: GlanceAppWidget, resetOffset: Boolean = false): Boolean {
        ids.forEach { id ->
            updateAppWidgetState(context, id) {
                it[REFRESH] = (it[REFRESH] ?: 0) + 1
                if (resetOffset) it[OFFSET] = 0
            }
            widget.update(context, id)
        }
        return ids.isNotEmpty()
    }

    /** 過午夜換日：排一個不叫醒手機的鬧鐘，手機下次醒來（看得到小工具時）就更新。沒有小工具就取消。 */
    fun scheduleMidnight(context: Context, enabled: Boolean = true) {
        val zone = ZoneId.systemDefault()
        val next = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() + 1_000L
        AlarmScheduler.scheduleWidgetRefresh(context, if (enabled) next else null)
    }

    // ── 點小工具開 App：一律走不對外的 InternalEntry ──
    fun openApp(context: Context): Intent = Intent(context, MainActivity::class.java)

    fun openDay(context: Context, date: LocalDate): Intent =
        MainActivity.internalIntent(context, MainActivity.ACTION_OPEN_DAY)
            .putExtra(MainActivity.EXTRA_DATE, date.toEpochDay())
            // 每一天要不同的 data，PendingIntent 才不會被當成同一個而共用 extras
            .setData(android.net.Uri.parse("calendaralarm://day/${date.toEpochDay()}"))

    fun openEvent(context: Context, eventId: Long, begin: Long): Intent =
        MainActivity.internalIntent(context, MainActivity.ACTION_OPEN_EVENT)
            .putExtra(MainActivity.EXTRA_EVENT_ID, eventId)
            .putExtra(MainActivity.EXTRA_BEGIN, begin)

    fun newEvent(context: Context): Intent = MainActivity.internalIntent(context, MainActivity.ACTION_NEW_EVENT)

    fun color(c: Color): ColorProvider = ColorProvider(c)
}
