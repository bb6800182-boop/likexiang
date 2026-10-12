package com.c520.likexiang.alarm

import com.c520.likexiang.data.AlarmState
import com.c520.likexiang.data.EventInstance
import com.c520.likexiang.data.HOUR_MS
import com.c520.likexiang.data.MINUTE_MS
import com.c520.likexiang.data.Settings
import com.c520.likexiang.data.alarmKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class AlarmPlannerTest {

    private val zone = ZoneId.of("Asia/Taipei")
    private fun at(h: Int, m: Int = 0, day: Int = 12) = LocalDateTime.of(2026, 10, day, h, m).atZone(zone).toInstant().toEpochMilli()
    private val now = at(9)
    private val settings = Settings(dayStartMinute = 8 * 60) // 預設提前 10 分；一天從 08:00 開始（整天行程 08:00 響）

    private fun ev(
        id: Long,
        begin: Long,
        title: String = "⏰ 開會",
        allDay: Boolean = false,
        status: Int = 0,
        selfStatus: Int = 0,
    ) = EventInstance(id, begin, begin + if (allDay) 24 * HOUR_MS else HOUR_MS, title, allDay, 1L, 0, "", selfStatus, status, false)

    private fun plan(
        vararg events: EventInstance,
        state: AlarmState = AlarmState.EMPTY,
        s: Settings = settings,
        includeSkipped: Boolean = false,
    ) = AlarmPlanner.plan(events.toList(), state, s, now, zone, includeSkipped)

    private fun state(
        offsets: Map<Long, Int> = emptyMap(),
        localFlags: Map<Long, Boolean> = emptyMap(),
        skips: Set<String> = emptySet(),
        fired: Set<String> = emptySet(),
        snoozes: Map<String, Long> = emptyMap(),
    ) = AlarmState(offsets, localFlags, skips, fired, snoozes)

    @Test
    fun missedAlarmsSplitByAge() {
        // 10:00 的行程 09:50 該響，手機剛好關機：10:05 開機就補響，13:00 才開機只留錯過通知
        val e = ev(1, at(10))
        val (ring, late) = AlarmPlanner.missed(listOf(e), AlarmState.EMPTY, settings, since = at(9, 50), now = at(10, 5), zone = zone)
        assertEquals(listOf(e.key), ring.map { it.key })
        assertTrue(late.isEmpty())
        val (ring2, late2) = AlarmPlanner.missed(listOf(e), AlarmState.EMPTY, settings, since = at(9, 50), now = at(13), zone = zone)
        assertTrue(ring2.isEmpty())
        assertEquals(listOf(e.key), late2.map { it.key })
        // 已經響過的不算
        val (ring3, late3) = AlarmPlanner.missed(listOf(e), state(fired = setOf(e.key)), settings, since = at(9, 50), now = at(10, 5), zone = zone)
        assertTrue(ring3.isEmpty() && late3.isEmpty())
    }

    @Test
    fun ringsDefaultOffsetBeforeStart() {
        assertEquals(listOf(at(9, 50)), plan(ev(1, at(10))).map { it.triggerAt })
    }

    @Test
    fun perEventOffsetWins() {
        assertEquals(listOf(at(9, 30)), plan(ev(1, at(10)), state = state(offsets = mapOf(1L to 30))).map { it.triggerAt })
    }

    @Test
    fun offsetAlreadyPassedRingsAtStart() {
        // 9:05 開始、提前 10 分＝8:55 已經過了：改在 9:05 響
        assertEquals(listOf(at(9, 5)), plan(ev(1, at(9, 5))).map { it.triggerAt })
    }

    @Test
    fun justMissedWithinGraceIsStillDue() {
        val p = plan(ev(1, at(9, 9))) // 8:59 該響，現在 9:00
        assertEquals(listOf(at(8, 59)), p.map { it.triggerAt })
        assertEquals(1, AlarmPlanner.due(p, now).size)
    }

    @Test
    fun startedEventsAreNotRung() {
        assertTrue(plan(ev(1, at(8, 50))).isEmpty())
    }

    @Test
    fun onlyMarkedOrFlaggedEventsRing() {
        assertTrue(plan(ev(1, at(10), title = "開會")).isEmpty())
        assertEquals(1, plan(ev(1, at(10), title = "開會"), state = state(localFlags = mapOf(1L to true))).size)
        // 手機上明確關掉，標題有 ⏰ 也不響
        assertTrue(plan(ev(1, at(10)), state = state(localFlags = mapOf(1L to false))).isEmpty())
    }

    @Test
    fun declinedOrCanceledNeverRing() {
        assertTrue(plan(ev(1, at(10), selfStatus = AlarmPlanner.ATTENDEE_STATUS_DECLINED)).isEmpty())
        assertTrue(plan(ev(1, at(10), status = AlarmPlanner.STATUS_CANCELED)).isEmpty())
    }

    @Test
    fun firedIsNotRepeatedButNextOccurrenceIs() {
        val today = ev(1, at(10))
        val tomorrow = ev(1, at(10, day = 13))
        val p = plan(today, tomorrow, state = state(fired = setOf(today.key)))
        assertEquals(listOf(tomorrow.key), p.map { it.key })
    }

    @Test
    fun skippedOnlyShownWhenAsked() {
        val e = ev(1, at(10))
        val st = state(skips = setOf(e.key))
        assertTrue(plan(e, state = st).isEmpty())
        val withSkipped = plan(e, state = st, includeSkipped = true)
        assertTrue(withSkipped.single().skipped)
        assertNull(AlarmPlanner.nextTrigger(withSkipped))
        assertTrue(AlarmPlanner.due(withSkipped, at(9, 50)).isEmpty())
    }

    @Test
    fun allDayRingsAtConfiguredTimeOnItsDate() {
        val begin = LocalDate.of(2026, 10, 13).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(listOf(at(8, 0, day = 13)), plan(ev(1, begin, allDay = true)).map { it.triggerAt })
        val early = settings.copy(dayStartMinute = 7 * 60 + 30)
        assertEquals(listOf(at(7, 30, day = 13)), plan(ev(1, begin, allDay = true), s = early).map { it.triggerAt })
    }

    @Test
    fun allDayUsesWallClockOnDaylightSavingDay() {
        // 洛杉磯 2026-03-08 凌晨 2 點跳到 3 點：鬧鐘仍是當地 08:00，而不是午夜後 480 分鐘（09:00）
        val la = ZoneId.of("America/Los_Angeles")
        val begin = LocalDate.of(2026, 3, 8).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val now = LocalDateTime.of(2026, 3, 7, 12, 0).atZone(la).toInstant().toEpochMilli()
        val expected = LocalDateTime.of(2026, 3, 8, 8, 0).atZone(la).toInstant().toEpochMilli()
        assertEquals(expected, AlarmPlanner.triggerFor(ev(1, begin, allDay = true), AlarmState.EMPTY, settings, now, la))
    }

    @Test
    fun allDayTodayAfterAlarmTimeDoesNotRing() {
        val begin = LocalDate.of(2026, 10, 12).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertTrue(plan(ev(1, begin, allDay = true)).isEmpty())
    }

    @Test
    fun snoozedAlarmRingsAgain() {
        val e = ev(1, at(9, 10))
        val p = plan(e, state = state(fired = setOf(e.key), snoozes = mapOf(e.key to at(9, 5))))
        assertEquals(at(9, 5), p.single().triggerAt)
        assertTrue(p.single().snoozed)
    }

    @Test
    fun snoozeIsDroppedWhenAlarmTurnedOffOrEventGone() {
        val e = ev(1, at(9, 10))
        val snoozed = state(fired = setOf(e.key), snoozes = mapOf(e.key to at(9, 5)), localFlags = mapOf(1L to false))
        assertTrue(plan(e, state = snoozed).isEmpty())
        assertTrue(plan(state = state(snoozes = mapOf(alarmKey(9, at(9, 10)) to at(9, 5)))).isEmpty())
    }

    @Test
    fun sortedAndNextTriggerIsEarliest() {
        val p = plan(ev(1, at(12)), ev(2, at(10)), ev(3, at(11)))
        assertEquals(listOf(2L, 3L, 1L), p.map { it.eventId })
        assertEquals(at(9, 50), AlarmPlanner.nextTrigger(p))
    }

    @Test
    fun modifiedOccurrenceInheritsSeriesSettings() {
        // 有與會者的週會（只記在手機）：主辦人把這週改了時間，那一次變成另一筆 id=99、originalId=1
        val moved = ev(99, at(11), title = "週會").copy(originalId = 1)
        val st = state(localFlags = mapOf(1L to true), offsets = mapOf(1L to 30))
        assertEquals(listOf(at(10, 30)), plan(moved, state = st).map { it.triggerAt })
        // 單獨對那一次關掉，以那一次為準
        assertTrue(plan(moved, state = st.copy(localFlags = mapOf(1L to true, 99L to false))).isEmpty())
    }

    @Test
    fun dueAllowsOneMinuteEarly() {
        val soon = ev(1, now + 10 * MINUTE_MS + 30_000) // 30 秒後該響
        val later = ev(2, now + 12 * MINUTE_MS) // 2 分後該響
        val due = AlarmPlanner.due(plan(soon, later), now)
        assertEquals(listOf(1L), due.map { it.eventId })
    }
}
