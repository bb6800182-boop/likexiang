package com.bryanh.calendaralarm.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class EventInstanceTest {

    private val taipei = ZoneId.of("Asia/Taipei")

    private fun utcMidnight(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun inst(begin: Long, end: Long, allDay: Boolean = false, title: String = "行程") =
        EventInstance(1, begin, end, title, allDay, 1, 0, "", 0, 0, false)

    @Test
    fun allDayUsesUtcDatesInAnyZone() {
        val begin = utcMidnight(2026, 10, 12)
        val e = inst(begin, begin + DAY_MS, allDay = true)
        assertEquals(LocalDate.of(2026, 10, 12), e.startDate(taipei))
        assertEquals(LocalDate.of(2026, 10, 12), e.endDate(taipei))
        assertEquals(LocalDate.of(2026, 10, 12), e.startDate(ZoneId.of("America/Los_Angeles")))
    }

    @Test
    fun multiDayAllDayEndsOnLastDay() {
        val begin = utcMidnight(2026, 10, 12)
        assertEquals(LocalDate.of(2026, 10, 14), inst(begin, begin + 3 * DAY_MS, allDay = true).endDate(taipei))
    }

    @Test
    fun timedEventEndingAtMidnightStaysOnSameDay() {
        val begin = LocalDateTime.of(2026, 10, 12, 22, 0).atZone(taipei).toInstant().toEpochMilli()
        val end = LocalDateTime.of(2026, 10, 13, 0, 0).atZone(taipei).toInstant().toEpochMilli()
        assertEquals(LocalDate.of(2026, 10, 12), inst(begin, end).endDate(taipei))
    }

    @Test
    fun displayTitleHidesMark() {
        assertEquals("看牙醫", inst(0, 0, title = "⏰ 看牙醫").displayTitle)
        assertEquals("（無標題）", inst(0, 0, title = "⏰").displayTitle)
    }
}

class DefaultCalendarTest {

    private fun cal(id: Long, writable: Boolean = true, primary: Boolean = false) =
        CalendarInfo(id, "日曆$id", "me@example.com", "me@example.com", 0, writable, primary)

    @Test
    fun prefersChosenThenPrimaryThenFirstShown() {
        val list = listOf(cal(1), cal(2, primary = true), cal(3), cal(4, writable = false))
        assertEquals(3L, defaultCalendarFor(list, Settings(defaultCalendarId = 3))?.id)
        assertEquals(2L, defaultCalendarFor(list, Settings(defaultCalendarId = 4))?.id) // 唯讀的不算
        assertEquals(2L, defaultCalendarFor(list, Settings())?.id)
        assertEquals(1L, defaultCalendarFor(list, Settings(hiddenCalendarIds = setOf(2L)))?.id)
        assertEquals(null, defaultCalendarFor(listOf(cal(4, writable = false)), Settings()))
    }
}
