package com.c520.likexiang.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class FmtTest {

    @Test
    fun offsetLabels() {
        assertEquals("開始時", Fmt.reminderLabel(0))
        assertEquals("10 分鐘前", Fmt.reminderLabel(10))
        assertEquals("1 小時前", Fmt.reminderLabel(60))
        assertEquals("1 天前", Fmt.reminderLabel(1440))
        assertEquals("2 週前", Fmt.reminderLabel(2 * 7 * 1440))
        assertEquals("1 天 2 小時 5 分鐘", Fmt.duration(1440 + 125))
        assertEquals("準時響", Fmt.leadLabel(0))
        assertEquals("提前 1 小時 30 分鐘", Fmt.leadLabel(90))
        assertEquals("1 小時", Fmt.offsetChip(60))
    }

    @Test
    fun minuteOfDay() {
        assertEquals("08:00", Fmt.minuteOfDay(480))
        assertEquals("23:59", Fmt.minuteOfDay(1439))
    }

    @Test
    fun weekdayCharacter() {
        assertEquals("一", Fmt.dowChar(LocalDate.of(2026, 10, 12)))
        assertEquals("日", Fmt.dowChar(LocalDate.of(2026, 10, 11)))
        assertEquals("星期日", Fmt.weekday(LocalDate.of(2026, 10, 11)))
    }

    @Test
    fun monthSpans() {
        val today = LocalDate.of(2026, 10, 11)
        fun r(a: LocalDate, b: LocalDate) = Fmt.monthSpan(a, b, today)
        assertEquals("10月", r(LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 13)))
        assertEquals("10–11月", r(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 8)))
        assertEquals("2026年12月–2027年1月", r(LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 2)))
        assertEquals("2027年1月", r(LocalDate.of(2027, 1, 4), LocalDate.of(2027, 1, 10)))
    }

    @Test
    fun allDayRange() {
        val begin = LocalDate.of(2026, 10, 12).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertTrue(Fmt.range(begin, begin + 86_400_000L, allDay = true).endsWith("整天"))
    }
}
