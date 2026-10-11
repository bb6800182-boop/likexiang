package com.bryanh.calendaralarm.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchQueryTest {

    @Test
    fun blankBecomesNull() {
        assertNull(SearchQuery.normalize(""))
        assertNull(SearchQuery.normalize("   \t\n "))
    }

    @Test
    fun trimsCollapsesSpacesAndDropsControlChars() {
        assertEquals("牙醫 回診", SearchQuery.normalize("  牙醫 \t  回診\n"))
        assertEquals("週會", SearchQuery.normalize("週\u0000會\u0007"))
    }

    @Test
    fun capsLengthWithoutSplittingEmoji() {
        assertEquals(SearchQuery.MAX_LENGTH, SearchQuery.normalize("a".repeat(500))!!.length)
        // 第 100 個 char 是表情符號的前半個：整個拿掉，不留半個
        val cut = SearchQuery.normalize("a".repeat(SearchQuery.MAX_LENGTH - 1) + "😀b")!!
        assertEquals("a".repeat(SearchQuery.MAX_LENGTH - 1), cut)
    }

    @Test
    fun keepsResultsNearestToNow() {
        fun e(id: Long, begin: Long) = EventInstance(id, begin, begin + 10, "x", false, 1, 0, "", 0, 0, false)
        val list = listOf(e(1, 0), e(2, 100), e(3, 200), e(4, 300), e(5, 1000))
        // 現在是 250：最近的三筆是 200、300、100，依時間排好
        assertEquals(listOf(2L, 3L, 4L), SearchQuery.nearest(list, now = 250, max = 3).map { it.eventId })
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), SearchQuery.nearest(list.reversed(), now = 250).map { it.eventId })
    }
}
