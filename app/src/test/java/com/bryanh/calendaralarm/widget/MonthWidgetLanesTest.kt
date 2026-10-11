package com.bryanh.calendaralarm.widget

import androidx.compose.ui.unit.dp
import com.bryanh.calendaralarm.ui.layout.PlacedSpan
import com.bryanh.calendaralarm.ui.layout.WeekLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class MonthWidgetLanesTest {
    private val large = GridDims(1.5f) // 測試機的字級

    @Test
    fun fullPageLayoutShowsMoreLanes() {
        assertEquals(5, lanesFor(740.dp, 5, large))
        assertEquals(4, lanesFor(740.dp, 6, large))
    }

    @Test
    fun smallerLayoutsGetFewerLanes() {
        assertEquals(3, lanesFor(560.dp, 5, large))
        assertEquals(1, lanesFor(400.dp, 5, large))
        assertEquals(0, lanesFor(260.dp, 5, large)) // 只放得下日期
    }

    @Test
    fun viewBudgetCapsLanes() {
        val normal = GridDims(1f)
        assertEquals(4, lanesFor(5000.dp, 6, normal))
        assertEquals(6, lanesFor(5000.dp, 5, normal))
        assertEquals(9, lanesFor(5000.dp, 4, normal)) // 一格最多 10 個子元件
    }

    @Test
    fun customRangesGetMoreLanes() {
        assertEquals(7, lanesFor(260.dp, 1, large, cols = 3)) // 3 天一列：矮的小工具也放得下
        assertEquals(6, lanesFor(400.dp, 2, large)) // 2 週
    }

    @Test
    fun multiDayBarsAddAnOverlayRowPerLane() {
        assertEquals(0, barViews(null, 7))
        // 只有單日行程：不用上層
        assertEquals(0, barViews(WeekLayout(listOf(PlacedSpan(0, 2, 1, 0)), emptyMap()), 7))
        // 兩筆跨日在同一條車道、一筆在另一條：外框＋Column，每條車道 Row＋Spacer＋7 段
        val two = WeekLayout(listOf(PlacedSpan(0, 0, 3, 0), PlacedSpan(1, 4, 2, 0), PlacedSpan(2, 1, 2, 1)), emptyMap())
        assertEquals(2 + 2 * 9, barViews(two, 7))
    }

    @Test
    fun monthViewsCountOnlyDrawnLanes() {
        // 一週：週一到三跨日（格子裡只畫週三那段）、週五第 2 條車道一筆單日、週日「+N」佔最後一條
        val week = WeekLayout(listOf(PlacedSpan(0, 0, 3, 0), PlacedSpan(1, 4, 1, 1)), mapOf(6 to 2))
        val cells = 7 * 6 + 1 + 2 + 3 // 每格的日期列＋週三 1 條、週五 2 條、週日 3 條
        assertEquals(32 + 3 + cells + barViews(week, 7), monthViews(listOf(week), 7, lanes = 3))
    }
}
