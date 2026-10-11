package com.bryanh.calendaralarm.ui.layout

import com.bryanh.calendaralarm.data.EventInstance
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekLayoutEngineTest {

    @Test
    fun fitsWithoutOverflow() {
        val spans = (0 until 3).map { WeekSpan(it, 0, 1, it.toLong()) }
        val layout = WeekLayoutEngine.layout(spans, maxLanes = 3)
        assertEquals(listOf(0, 1, 2), layout.placed.sortedBy { it.lane }.map { it.id })
        assertTrue(layout.more.isEmpty())
    }

    @Test
    fun lastLaneBecomesMoreCount() {
        val spans = (0 until 4).map { WeekSpan(it, 0, 1, it.toLong()) }
        val layout = WeekLayoutEngine.layout(spans, maxLanes = 3)
        assertEquals(listOf(0, 1), layout.placed.map { it.id })
        assertEquals(mapOf(0 to 2), layout.more)
    }

    @Test
    fun multiDaySpansGoFirstAndAreClipped() {
        val layout = WeekLayoutEngine.layout(
            listOf(WeekSpan(1, 3, 1, 0), WeekSpan(2, 2, 3, 99), WeekSpan(3, 5, 4, 0)),
            maxLanes = 4,
        )
        val byId = layout.placed.associateBy { it.id }
        assertEquals(0, byId.getValue(2).lane)
        assertEquals(1, byId.getValue(1).lane)
        assertEquals(2, byId.getValue(3).span) // 第 5 欄起跨 4 天，只畫到週末
    }

    @Test
    fun everyHiddenEventIsCounted() {
        // A 整週、B 週一二、C 週二三；只放得下兩條時，C 被擠到第三條
        val a = WeekSpan(0, 0, 7, 0)
        val b = WeekSpan(1, 0, 2, 0)
        val c = WeekSpan(2, 1, 2, 0)
        val layout = WeekLayoutEngine.layout(listOf(a, b, c), maxLanes = 2)
        assertEquals(listOf(0), layout.placed.map { it.id })
        assertEquals(mapOf(0 to 1, 1 to 2, 2 to 1), layout.more)
    }

    @Test
    fun singleLaneShowsOnlyCount() {
        val layout = WeekLayoutEngine.layout(listOf(WeekSpan(0, 3, 1, 0), WeekSpan(1, 3, 1, 1)), maxLanes = 1)
        assertTrue(layout.placed.isEmpty())
        assertEquals(mapOf(3 to 2), layout.more)
    }
}

class WeekSpansTest {
    private val zone = ZoneId.of("Asia/Taipei")
    private fun at(day: Int, h: Int) = LocalDateTime.of(2026, 10, day, h, 0).atZone(zone).toInstant().toEpochMilli()
    private fun allDay(day: Int, days: Int) = LocalDate.of(2026, 10, day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli().let { it to it + days * 86_400_000L }

    @Test
    fun clampsToWeekAndSortsAllDayFirst() {
        val monday = LocalDate.of(2026, 10, 12)
        val (a0, a1) = allDay(10, 4) // 10/10（六）到 10/13（二）的全天行程
        val events = listOf(
            EventInstance(1, at(14, 9), at(14, 10), "開會", false, 1, 0, "", 0, 0, false),
            EventInstance(2, a0, a1, "出差", true, 1, 0, "", 0, 0, false),
            EventInstance(3, at(20, 9), at(20, 10), "下週", false, 1, 0, "", 0, 0, false),
        )
        val spans = weekSpans(events, monday, zone).associateBy { it.id }
        assertEquals(WeekSpan(0, 2, 1, (1L shl 50) + at(14, 9)), spans[0]) // 週三
        assertEquals(0, spans.getValue(1).startCol) // 從週一開始截斷
        assertEquals(2, spans.getValue(1).span) // 到週二
        assertTrue(spans.getValue(1).sortKey < spans.getValue(0).sortKey)
        assertTrue(2 !in spans) // 不在這一週
    }

    @Test
    fun clampsToCustomDayCount() {
        val monday = LocalDate.of(2026, 10, 12)
        val (a0, a1) = allDay(12, 5) // 10/12 到 10/16 的全天行程
        val events = listOf(
            EventInstance(1, a0, a1, "出差", true, 1, 0, "", 0, 0, false),
            EventInstance(2, at(14, 9), at(14, 10), "開會", false, 1, 0, "", 0, 0, false),
        )
        // 「2 天」檢視：截到第二天，週三的不算
        assertEquals(listOf(WeekSpan(0, 0, 2, a0)), weekSpans(events, monday, zone, days = 2))
    }
}

class DayLayoutEngineTest {

    @Test
    fun separateEventsUseFullWidth() {
        val placed = DayLayoutEngine.layout(listOf(DayItem(0, 540, 600), DayItem(1, 600, 660)))
        assertTrue(placed.all { it.column == 0 && it.columns == 1 })
    }

    @Test
    fun overlappingEventsShareWidth() {
        val placed = DayLayoutEngine.layout(listOf(DayItem(0, 540, 600), DayItem(1, 570, 630), DayItem(2, 600, 660))).associateBy { it.id }
        assertEquals(0, placed.getValue(0).column)
        assertEquals(1, placed.getValue(1).column)
        assertEquals(0, placed.getValue(2).column) // 第一欄 10:00 空出來了
        assertTrue(placed.values.all { it.columns == 2 })
    }

    @Test
    fun zeroLengthEventGetsMinimumHeight() {
        val placed = DayLayoutEngine.layout(listOf(DayItem(0, 600, 600), DayItem(1, 610, 700)))
        assertEquals(620, placed.first { it.id == 0 }.endMin)
        assertTrue(placed.all { it.columns == 2 })
    }

    @Test
    fun shortEventUsesGivenMinimum() {
        // 字放大時最短 36 分鐘：10:27 的 5 分鐘行程跟 10:40 的行程要並排，標題才不會被蓋住
        val placed = DayLayoutEngine.layout(listOf(DayItem(0, 627, 632), DayItem(1, 640, 700)), minDuration = 36)
        assertEquals(663, placed.first { it.id == 0 }.endMin)
        assertTrue(placed.all { it.columns == 2 })
    }
}
