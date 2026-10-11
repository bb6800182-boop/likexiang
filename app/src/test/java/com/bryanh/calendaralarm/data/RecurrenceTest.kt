package com.bryanh.calendaralarm.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RecurrenceTest {

    @Test
    fun parsesDurations() {
        assertEquals(3_600_000L, Rfc5545.durationMillis("P3600S"))
        assertEquals(5_400_000L, Rfc5545.durationMillis("PT1H30M"))
        assertEquals(DAY_MS, Rfc5545.durationMillis("P1D"))
        assertEquals(7 * DAY_MS, Rfc5545.durationMillis("P1W"))
        assertNull(Rfc5545.durationMillis(null))
        assertNull(Rfc5545.durationMillis("P"))
        assertNull(Rfc5545.durationMillis("1 hour"))
    }

    @Test
    fun writesDurations() {
        assertEquals("P3600S", Rfc5545.durationString(3_600_000L, allDay = false))
        assertEquals("P1D", Rfc5545.durationString(DAY_MS, allDay = true))
        assertEquals("P60S", Rfc5545.durationString(0L, allDay = false))
    }

    @Test
    fun simpleRulesMapToOptions() {
        assertEquals(Repeat.NONE, Repeat.of(null))
        assertEquals(Repeat.DAILY, Repeat.of("FREQ=DAILY"))
        assertEquals(Repeat.WEEKLY, Repeat.of("FREQ=WEEKLY;BYDAY=MO;WKST=MO"))
        assertEquals(Repeat.MONTHLY, Repeat.of("FREQ=MONTHLY;BYMONTHDAY=11"))
        assertEquals(Repeat.YEARLY, Repeat.of("freq=yearly"))
    }

    @Test
    fun complexRulesAreLeftToGoogle() {
        assertNull(Repeat.of("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO"))
        assertNull(Repeat.of("FREQ=DAILY;COUNT=5"))
        assertNull(Repeat.of("FREQ=WEEKLY;UNTIL=20261231T000000Z"))
        assertNull(Repeat.of("FREQ=WEEKLY;BYDAY=MO,WE"))
        assertNull(Repeat.of("FREQ=MONTHLY;BYDAY=2TU"))
    }

    @Test
    fun rulesFollowStartDate() {
        val wednesday = LocalDate.of(2026, 10, 14)
        assertEquals("FREQ=WEEKLY;BYDAY=WE", Repeat.WEEKLY.toRrule(wednesday))
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=14", Repeat.MONTHLY.toRrule(wednesday))
        assertNull(Repeat.NONE.toRrule(wednesday))
    }
}
