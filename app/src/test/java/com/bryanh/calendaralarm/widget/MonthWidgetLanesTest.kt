package com.bryanh.calendaralarm.widget

import androidx.compose.ui.unit.dp
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
}
