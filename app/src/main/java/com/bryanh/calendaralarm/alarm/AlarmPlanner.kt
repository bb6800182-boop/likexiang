package com.bryanh.calendaralarm.alarm

import com.bryanh.calendaralarm.data.AlarmRules
import com.bryanh.calendaralarm.data.AlarmState
import com.bryanh.calendaralarm.data.EventInstance
import com.bryanh.calendaralarm.data.MINUTE_MS
import com.bryanh.calendaralarm.data.Settings
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

data class PlannedAlarm(
    val key: String,
    val eventId: Long,
    val begin: Long,
    val end: Long,
    val title: String,
    val location: String,
    val allDay: Boolean,
    val triggerAt: Long,
    val snoozed: Boolean,
    val skipped: Boolean = false,
)

/**
 * 純邏輯：從行程與手機上的鬧鐘狀態算出什麼時候要響。不碰 Android API，可在 JVM 上測試。
 * 每次觸發、開機、行程變動都重算一次，所以行程在電腦上被改時間或刪掉，鬧鐘會跟著改或消失。
 */
object AlarmPlanner {
    // 與 CalendarContract 相同的值；這裡自己定義，讓單元測試不依賴 android.jar
    const val STATUS_CANCELED = 2
    const val ATTENDEE_STATUS_DECLINED = 2

    /** 觸發時間已過多久內仍算「現在該響」（處理廣播延遲、開機補響）。 */
    const val GRACE_MS = 2 * MINUTE_MS

    /** 排程時容許提早多久算到點。 */
    const val DUE_TOLERANCE_MS = MINUTE_MS

    /** 錯過的鬧鐘在多久內還補響；更早的只留錯過通知（補響一個早就過去的行程只會嚇人）。 */
    const val CATCH_UP_RING_MS = 30 * MINUTE_MS

    fun plan(
        instances: List<EventInstance>,
        state: AlarmState,
        settings: Settings,
        now: Long,
        zone: ZoneId,
        includeSkipped: Boolean = false,
    ): List<PlannedAlarm> {
        val out = ArrayList<PlannedAlarm>()
        val byKey = HashMap<String, EventInstance>()
        for (inst in instances) {
            byKey[inst.key] = inst
            if (!eligible(inst, state)) continue
            val key = inst.key
            if (key in state.fired || state.snoozes.containsKey(key)) continue
            val skipped = key in state.skips
            if (skipped && !includeSkipped) continue
            val trigger = triggerFor(inst, state, settings, now, zone) ?: continue
            out += inst.toPlanned(trigger, snoozed = false, skipped = skipped)
        }
        for ((key, until) in state.snoozes) {
            // 稍後提醒後行程被刪、被取消，或鬧鐘被關掉，就不再響
            val inst = byKey[key] ?: continue
            if (!eligible(inst, state)) continue
            out += inst.toPlanned(maxOf(until, now), snoozed = true, skipped = false)
        }
        return out.sortedWith(compareBy<PlannedAlarm>({ it.triggerAt }, { it.begin }, { it.eventId }))
    }

    /** 現在該響的（不含略過的）。 */
    fun due(plan: List<PlannedAlarm>, now: Long): List<PlannedAlarm> =
        plan.filter { !it.skipped && it.triggerAt <= now + DUE_TOLERANCE_MS }

    /**
     * 排好的鬧鐘沒在時間內觸發（關機、重開機後還沒收到開機廣播、App 被強制停止）時，
     * 從原本該觸發的時間 [since] 到現在該響而沒響的鬧鐘。first＝要補響，second＝太久了只留錯過通知。
     */
    fun missed(
        instances: List<EventInstance>,
        state: AlarmState,
        settings: Settings,
        since: Long,
        now: Long,
        zone: ZoneId,
    ): Pair<List<PlannedAlarm>, List<PlannedAlarm>> =
        due(plan(instances, state, settings, since, zone), now).partition { it.triggerAt >= now - CATCH_UP_RING_MS }

    /** 下一次要叫醒 AlarmManager 的時間。 */
    fun nextTrigger(plan: List<PlannedAlarm>): Long? = plan.firstOrNull { !it.skipped }?.triggerAt

    private fun eligible(inst: EventInstance, state: AlarmState): Boolean =
        inst.status != STATUS_CANCELED &&
            inst.selfStatus != ATTENDEE_STATUS_DECLINED &&
            AlarmRules.isAlarmOn(inst, state)

    fun triggerFor(inst: EventInstance, state: AlarmState, settings: Settings, now: Long, zone: ZoneId): Long? {
        if (inst.allDay) {
            val date = Instant.ofEpochMilli(inst.begin).atZone(ZoneOffset.UTC).toLocalDate()
            // 整天行程在「一天的開始時間」響；用當天的牆上時間（夏令時間切換那天也是 06:00，不是午夜後 360 分鐘）
            val t = date.atTime(LocalTime.ofSecondOfDay(settings.dayStartMinute.coerceIn(0, 24 * 60 - 1) * 60L)).atZone(zone).toInstant().toEpochMilli()
            return if (t > now - GRACE_MS) t else null
        }
        val offset = state.offsetFor(inst.eventId, inst.originalId) ?: settings.defaultOffsetMin
        val t = inst.begin - offset * MINUTE_MS
        return when {
            t > now - GRACE_MS -> t
            // 提前時間已經過了、但行程還沒開始（例如剛建立的行程）：改在開始時響
            inst.begin > now - GRACE_MS -> inst.begin
            else -> null
        }
    }

    private fun EventInstance.toPlanned(trigger: Long, snoozed: Boolean, skipped: Boolean) = PlannedAlarm(
        key = key,
        eventId = eventId,
        begin = begin,
        end = end,
        title = displayTitle,
        location = location,
        allDay = allDay,
        triggerAt = trigger,
        snoozed = snoozed,
        skipped = skipped,
    )
}
