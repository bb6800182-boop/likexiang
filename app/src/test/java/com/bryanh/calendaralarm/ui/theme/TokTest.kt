package com.bryanh.calendaralarm.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class TokTest {
    @Test
    fun googleEventColorsUseCurrentPalette() {
        // 石墨黑：資料庫存舊色票淺灰，畫成 Google 月曆現在的深灰
        assertEquals(Color(0xFF616161), Tok.eventColor(0xFFE1E1E1.toInt()))
        // 不是 Google 行程色的顏色原樣（例如日曆顏色）
        assertEquals(Color(0xFFCD74E6), Tok.eventColor(0xFFCD74E6.toInt()))
        // 沒有 alpha 的值也當成不透明
        assertEquals(Color(0xFFD50000), Tok.eventColor(0x00DC2127))
    }
}
