package com.bryanh.calendaralarm.alarm

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.bryanh.calendaralarm.AppScope
import com.bryanh.calendaralarm.MainActivity
import com.bryanh.calendaralarm.ServiceLocator
import com.bryanh.calendaralarm.data.DAY_MS
import com.bryanh.calendaralarm.data.MINUTE_MS
import com.bryanh.calendaralarm.widget.Widgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.ZoneId

/** 重算鬧鐘並交給 AlarmManager。只排「下一個」，響完或資料變動再算下一個。 */
object AlarmEngine {
    private const val TAG = "AlarmEngine"
    private const val LOOK_BACK_MS = DAY_MS
    private const val LOOK_AHEAD_MS = 40 * DAY_MS

    /** 到點時只看附近的行程：查詢範圍小，才能在背景啟動的寬限時間內把響鈴服務叫起來。 */
    private const val FIRE_LOOK_AHEAD_MS = 2 * DAY_MS

    /** 讀不到日曆時多久後重試：第一次在響鈴寬限時間內，之後加倍，最多 30 分鐘。 */
    private const val RETRY_MIN_MS = MINUTE_MS
    private const val RETRY_MAX_MS = 30 * MINUTE_MS
    private val mutex = Mutex()

    @Volatile
    private var retryDelay = RETRY_MIN_MS

    /** 讀不到日曆時丟出例外，讓呼叫端保留已排好的鬧鐘，而不是當成「沒有行程」把它取消。 */
    suspend fun compute(
        context: Context,
        now: Long = System.currentTimeMillis(),
        includeSkipped: Boolean = false,
        lookAheadMs: Long = LOOK_AHEAD_MS,
    ): List<PlannedAlarm> {
        val repo = ServiceLocator.repo(context)
        if (!repo.hasReadPermission()) return emptyList()
        val store = ServiceLocator.store(context)
        val instances = repo.instancesOrNull(now - LOOK_BACK_MS, now + lookAheadMs)
            ?: throw IllegalStateException("calendar query failed")
        return AlarmPlanner.plan(instances, store.alarmStateNow(), store.settingsNow(), now, ZoneId.systemDefault(), includeSkipped)
    }

    /**
     * 重排下一個鬧鐘。在 IO 執行緒執行，而且不會因為呼叫端被取消（例如使用者剛好離開畫面）而中斷。
     * 讀不到日曆時不當成「沒有行程」，而是排一次重試（剛響過的那個鬧鐘已經用掉了，不重試就什麼都不會響）。
     * 先處理錯過的鬧鐘（見 [catchUpMissed]），再排下一個。回傳是否成功算出排程。
     */
    suspend fun reschedule(context: Context): Boolean {
        val app = context.applicationContext
        return withContext(Dispatchers.IO + NonCancellable) {
            mutex.withLock {
                val store = ServiceLocator.store(app)
                val now = System.currentTimeMillis()
                val plan = try {
                    catchUpMissed(app, now)
                    compute(app, now)
                } catch (e: Exception) {
                    Log.w(TAG, "compute failed, retrying later", e)
                    // 不更新 pendingTrigger：原本那個鬧鐘若因此錯過，重試成功時還會補響
                    AlarmScheduler.scheduleNext(app, now + retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(RETRY_MAX_MS)
                    return@withLock false
                }
                retryDelay = RETRY_MIN_MS
                val next = AlarmPlanner.nextTrigger(plan)
                AlarmScheduler.scheduleNext(app, next)
                store.setPendingTrigger(next)
                true
            }.also {
                // 行程或鬧鐘狀態變了：小工具一起更新（在鎖外做，不拖慢排鬧鐘）
                Widgets.refresh(app)
            }
        }
    }

    /**
     * 上次交給 AlarmManager 的鬧鐘沒在時間內觸發（關機、重開機後還沒收到開機廣播、App 被強制停止、廣播太晚到），
     * 就把那之後該響而沒響的找出來：30 分鐘內的改成「稍後提醒到現在」，交給一般的到點流程馬上響
     * （開機廣播裡不能直接啟動播放用的前景服務，要經過鬧鐘廣播）；更早的只留錯過通知。
     */
    private suspend fun catchUpMissed(app: Context, now: Long) {
        val store = ServiceLocator.store(app)
        val since = store.pendingTriggerNow() ?: return
        if (since > now - AlarmPlanner.GRACE_MS) return
        val repo = ServiceLocator.repo(app)
        if (!repo.hasReadPermission()) return
        val instances = repo.instancesOrNull(since - LOOK_BACK_MS, now + FIRE_LOOK_AHEAD_MS)
            ?: throw IllegalStateException("calendar query failed")
        val (ring, tooLate) = AlarmPlanner.missed(instances, store.alarmStateNow(), store.settingsNow(), since, now, ZoneId.systemDefault())
        if (ring.isNotEmpty()) store.putSnoozes(ring.map { it.key }, now)
        if (tooLate.isNotEmpty()) {
            Notifications.missed(app, tooLate.map { RingItem.from(it) })
            store.markFired(tooLate.map { it.key }, now)
            store.clearSnoozes(tooLate.filter { it.snoozed }.map { it.key })
        }
    }

    fun rescheduleAsync(context: Context) {
        val app = context.applicationContext
        AppScope.launch { reschedule(app) }
    }

    /**
     * 鬧鐘到點：再確認一次該響的行程（還在、時間沒變、沒被拒絕或取消），先叫起響鈴服務再記下已響。
     * 跟重排共用同一把鎖，同一個鬧鐘不會被算成該響兩次。
     */
    suspend fun fire(context: Context, now: Long = System.currentTimeMillis()) {
        val app = context.applicationContext
        withContext(Dispatchers.IO + NonCancellable) {
            mutex.withLock {
                val due = AlarmPlanner.due(compute(app, now, lookAheadMs = FIRE_LOOK_AHEAD_MS), now)
                if (due.isEmpty()) return@withLock
                val store = ServiceLocator.store(app)
                RingService.start(app, due.map { RingItem.from(it) }, store.settingsNow())
                store.markFired(due.map { it.key }, now)
                store.clearSnoozes(due.filter { it.snoozed }.map { it.key })
            }
        }
    }
}

object AlarmScheduler {
    const val ACTION_FIRE = "com.bryanh.calendaralarm.action.FIRE"
    const val ACTION_TEST = "com.bryanh.calendaralarm.action.TEST"
    const val ACTION_WIDGET_REFRESH = "com.bryanh.calendaralarm.action.WIDGET_REFRESH"
    private const val REQ_FIRE = 1001
    private const val REQ_SHOW = 1002
    private const val REQ_TEST = 1003
    private const val REQ_WIDGET = 1004

    fun scheduleNext(context: Context, triggerAt: Long?) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val fire = broadcast(context, ACTION_FIRE, REQ_FIRE)
        if (triggerAt == null) {
            am.cancel(fire)
            return
        }
        setAlarm(context, am, triggerAt, fire)
    }

    /** 小工具過午夜換日用：不叫醒手機的非精準鬧鐘，at = null 時取消。 */
    fun scheduleWidgetRefresh(context: Context, at: Long?) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val op = broadcast(context, ACTION_WIDGET_REFRESH, REQ_WIDGET)
        if (at == null) am.cancel(op) else am.set(AlarmManager.RTC, at, op)
    }

    /** 「10 秒後試響」：走跟真的鬧鐘完全一樣的路徑。 */
    fun scheduleTest(context: Context, delayMs: Long = 10_000L) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        setAlarm(context, am, System.currentTimeMillis() + delayMs, broadcast(context, ACTION_TEST, REQ_TEST))
    }

    // 有 USE_EXACT_ALARM（月曆與鬧鐘類 App 安裝即授權），呼叫前也檢查 canScheduleExactAlarms，被拒時退回非精準鬧鐘
    @SuppressLint("MissingPermission")
    private fun setAlarm(context: Context, am: AlarmManager, triggerAt: Long, operation: PendingIntent) {
        // 狀態列鬧鐘圖示、鎖定畫面的「下一個鬧鐘」點下去會開鬧鐘清單
        val show = PendingIntent.getActivity(
            context, REQ_SHOW,
            MainActivity.internalIntent(context, MainActivity.ACTION_SHOW_ALARMS),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        try {
            if (am.canScheduleExactAlarms()) {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, show), operation)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        }
    }

    private fun broadcast(context: Context, action: String, req: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, req,
            Intent(context, AlarmReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
