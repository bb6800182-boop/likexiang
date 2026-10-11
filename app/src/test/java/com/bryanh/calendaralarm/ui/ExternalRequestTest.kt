package com.bryanh.calendaralarm.ui

import com.bryanh.calendaralarm.data.Repeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class ExternalRequestTest {
    private val zone = ZoneId.of("Asia/Taipei")
    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, day, h, m).atZone(zone).toInstant().toEpochMilli()
    private fun utcMidnight(day: Int) = LocalDate.of(2026, 10, day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun insert(
        title: String? = "開會",
        begin: Long? = at(12, 10),
        end: Long? = at(12, 11),
        allDay: Boolean = false,
        rrule: String? = null,
        description: String? = null,
    ) = ExternalRequest.insert(title, description, null, begin, end, allDay, rrule, zone).prefill

    @Test
    fun timedEventKeepsLocalTimes() {
        val p = insert()
        assertEquals(LocalDateTime.of(2026, 10, 12, 10, 0), p.start)
        assertEquals(LocalDateTime.of(2026, 10, 12, 11, 0), p.end)
        assertFalse(p.allDay)
    }

    @Test
    fun allDayAcceptsUtcAndLocalMidnight() {
        // CalendarContract 慣例：UTC 午夜，結束是隔天（不含）
        val utc = insert(begin = utcMidnight(12), end = utcMidnight(14), allDay = true)
        assertEquals(LocalDate.of(2026, 10, 12), utc.start?.toLocalDate())
        assertEquals(LocalDate.of(2026, 10, 13), utc.end?.toLocalDate())
        // 也有 App 給當地午夜
        val local = insert(begin = at(12, 0), end = at(13, 0), allDay = true)
        assertEquals(LocalDate.of(2026, 10, 12), local.start?.toLocalDate())
        assertEquals(LocalDate.of(2026, 10, 12), local.end?.toLocalDate())
    }

    @Test
    fun badTimesAreDropped() {
        val endBeforeStart = insert(end = at(12, 9))
        assertNull(endBeforeStart.end)
        val absurd = insert(begin = Long.MAX_VALUE, end = null)
        assertNull(absurd.start)
        assertFalse(insert(begin = null, allDay = true).allDay) // 沒有日期就不能是全天
    }

    @Test
    fun textIsCleanedAndLimited() {
        val p = insert(title = "  ⏰ 開會\n第二行\u0007  ", description = "a\r\nb\u0000")
        assertEquals("開會 第二行", p.title)
        assertTrue(p.alarmOn) // 標題裡的 ⏰ 變成鬧鐘開關
        assertEquals("a\nb", p.description)
        assertEquals(200, insert(title = "長".repeat(500)).title.length)
    }

    @Test
    fun onlySimpleRepeatRulesApply() {
        assertEquals(Repeat.DAILY, insert(rrule = "FREQ=DAILY").repeat)
        val complex = insert(rrule = "FREQ=WEEKLY;COUNT=5")
        assertEquals(Repeat.NONE, complex.repeat)
        assertTrue(complex.droppedRepeat)
    }

    @Test
    fun viewRequestsValidateIds() {
        assertNull(ExternalRequest.viewEvent(-1, null))
        assertEquals(ExternalRequest.ViewEvent(42, 0), ExternalRequest.viewEvent(42, Long.MIN_VALUE))
        assertEquals(LocalDate.of(2026, 10, 12), ExternalRequest.viewDay(at(12, 23, 30), zone)?.date)
        assertNull(ExternalRequest.viewDay(Long.MAX_VALUE, zone))
    }
}
