package com.c520.likexiang.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsTest {
    private fun inst(id: Long, calendarId: Long, color: Int, ownColor: Boolean = false) =
        EventInstance(id, 0L, 1L, "行程", false, calendarId, color, "", 0, 0, false, ownColor = ownColor)

    @Test
    fun shownHidesCalendarsAndRecolorsOnlyCalendarColoredEvents() {
        val blue = 0xFF039BE5.toInt()
        val green = 0xFF0B8043.toInt()
        val red = 0xFFD50000.toInt()
        val s = Settings(hiddenCalendarIds = setOf(2L), calendarColors = mapOf(1L to green))
        val out = s.shown(listOf(inst(1, 1, blue), inst(2, 1, red, ownColor = true), inst(3, 2, blue), inst(4, 3, blue)))
        assertEquals(listOf(1L, 2L, 4L), out.map { it.eventId }) // 隱藏的日曆不顯示
        assertEquals(green, out[0].color) // 跟著日曆顏色的換成本 App 選的
        assertEquals(red, out[1].color) // 行程自己設的顏色不換
        assertEquals(blue, out[2].color) // 沒換顏色的日曆照 Google 月曆
    }
}
