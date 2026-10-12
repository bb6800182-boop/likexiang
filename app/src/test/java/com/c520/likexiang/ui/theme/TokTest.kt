package com.c520.likexiang.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TokTest {
    @Test
    fun oldGooglePaletteMapsToCurrentPalette() {
        // 石墨黑（行程顏色）：資料庫存舊色票淺灰 #E1E1E1，照 Google 現在的深灰 #616161 畫
        assertEquals(Tok.eventColor(0xFF616161.toInt()), Tok.eventColor(0xFFE1E1E1.toInt()))
        // 日曆顏色也換：葡萄紫
        assertEquals(Tok.eventColor(0xFF8E24AA.toInt()), Tok.eventColor(0xFFCD74E6.toInt()))
        // 國定假日日曆的舊色票 #16A765：Google 月曆畫成尤加利樹綠
        assertEquals(Tok.eventColor(0xFF009688.toInt()), Tok.eventColor(0xFF16A765.toInt()))
        // 沒有 alpha 的值也當成不透明
        assertEquals(Tok.eventColor(0xFFD50000.toInt()), Tok.eventColor(0x00DC2127))
    }

    @Test
    fun matchesColorsMeasuredFromGoogleWidget() {
        // 原色 → 使用者截圖裡 Google 月曆小工具的顏色；每個通道差 8 以內（0～255）
        val measured = mapOf(
            0xFFF4511E to 0xFFFA7B50, // 橙橘
            0xFF7986CB to 0xFF959DD5, // 薰衣草
            0xFF33B679 to 0xFF6AC594, // 鼠尾草綠
            0xFFE67C73 to 0xFFED978E, // 紅鶴
            0xFF616161 to 0xFF7E7E7E, // 石墨黑
            0xFF0B8043 to 0xFF509967, // 羅勒綠
            0xFF009688 to 0xFF54AB9F, // 尤加利樹綠
        )
        for ((base, google) in measured) {
            val c = Tok.eventColor(base.toInt())
            val g = Color(google.toInt())
            val diff = maxOf(abs(c.red - g.red), abs(c.green - g.green), abs(c.blue - g.blue)) * 255
            assertTrue("${base.toString(16)} 差 $diff", diff <= 8f)
        }
    }

    @Test
    fun textColorFollowsGoogleWidget() {
        // 截圖裡 Google 用白字的：橙橘、薰衣草、鈷藍、羅勒綠、尤加利、石墨、番茄紅
        for (c in listOf(0xFFF4511E, 0xFF7986CB, 0xFF4285F4, 0xFF0B8043, 0xFF009688, 0xFF616161, 0xFFD50000)) {
            assertEquals(c.toString(16), Tok.onPrimary, Tok.onEventColor(c.toInt()))
        }
        // 用深色字的：鼠尾草綠、紅鶴、酪梨綠
        for (c in listOf(0xFF33B679, 0xFFE67C73, 0xFFC0CA33)) {
            assertNotEquals(c.toString(16), Tok.onPrimary, Tok.onEventColor(c.toInt()))
        }
    }

    @Test
    fun darkTextPassesAaAndWhiteTextOnlyOnDarkerColors() {
        // 任何顏色（含自訂色）：深色字一定過 4.5:1；白字只用在亮度 0.4 以下的底色（至少 2.3:1，跟 Google 一樣）
        // 每個顏色通道取 16 階，共 4096 色
        for (r in 0..255 step 17) for (g in 0..255 step 17) for (b in 0..255 step 17) {
            val argb = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            val bg = Tok.eventColor(argb)
            val fg = Tok.onEventColor(argb)
            val ratio = Tok.contrast(fg, bg)
            val min = if (fg == Tok.onPrimary) 2.3f else 4.5f
            assertTrue("rgb($r,$g,$b) 對比 $ratio", ratio >= min)
        }
    }
}
