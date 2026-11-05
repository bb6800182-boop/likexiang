package com.c520.likexiang.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.c520.likexiang.ui.ACCENT_SWATCHES
import com.c520.likexiang.ui.CANVAS_SWATCHES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteTest {
    @Test
    fun defaultsAreTheDesignTokens() {
        assertEquals(Palette.DEFAULT, Palette.of(null, null))
        // 自己選了預設的紫：重點色不變
        assertEquals(Palette.DEFAULT.primary, Palette.of(0xFF5645D4.toInt(), null).primary)
    }

    @Test
    fun swatchesNeedNoAdjustment() {
        // 重點色的常用色在預設白底上、底色的常用色本身，都不用自動調整（選什麼就是什麼）
        for (s in ACCENT_SWATCHES) {
            val a = s.argb ?: continue
            assertEquals(s.name, Color(a), Palette.of(a, null).primary)
        }
        for (s in CANVAS_SWATCHES) {
            val c = s.argb ?: continue
            assertEquals(s.name, Color(c), Palette.of(null, c).canvas)
        }
    }

    @Test
    fun darkCanvasFlipsTextAndAccent() {
        val p = Palette.of(null, 0xFF202124.toInt())
        assertFalse(p.isLight)
        assertTrue(Palette.isLight(p.ink)) // 字是淺色
        assertTrue(Palette.isLight(p.primary)) // 紫色調淺，配深色字
        assertFalse(Palette.isLight(p.onPrimary))
    }

    // 一圈顏色（各種色相、飽和度、亮度，加上黑、白、幾種灰），兩兩當重點色與底色
    private val samples = buildList {
        for (h in 0 until 360 step 30) for (s in listOf(0.3f, 1f)) for (v in listOf(0.15f, 0.5f, 0.85f, 1f)) {
            add(Color.hsv(h.toFloat(), s, v).toArgb())
        }
        addAll(listOf(0xFF000000, 0xFFFFFFFF, 0xFF777777, 0xFF808080, 0xFF3A3A3A, 0xFFBDBDBD).map { it.toInt() })
    }
    private val canvases = samples + CANVAS_SWATCHES.mapNotNull { it.argb }
    private val accents = samples.filterIndexed { i, _ -> i % 5 == 0 } + ACCENT_SWATCHES.mapNotNull { it.argb }

    private fun name(accent: Int, canvas: Int?) = "accent ${accent.toUInt().toString(16)} canvas ${canvas?.toUInt()?.toString(16)}"

    @Test
    fun tintsStandOut() {
        // 今天那一格、鬧鐘設定卡的淡底要看得出來。以前照比例疊一成：橘色在白底上只有 1.15:1、黑底上 1.01:1，等於看不到
        for (canvas in listOf<Int?>(null) + canvases) for (accent in accents) {
            val p = Palette.of(accent, canvas)
            for ((tint, what) in listOf(p.todayTint to "今天那一格", p.alarmTint to "鬧鐘設定卡")) {
                val c = Tok.contrast(tint, p.canvas)
                assertTrue("${name(accent, canvas)}：$what $c", c >= 1.2f)
            }
        }
    }

    @Test
    fun anyColorsKeepTextReadable() {
        // 字一律 4.5:1 以上
        for (canvas in canvases) for (accent in accents) {
            val p = Palette.of(accent, canvas)
            val label = name(accent, canvas)
            fun aa(fg: Color, bg: Color, what: String, min: Float = 4.5f) =
                assertTrue("$label：$what ${Tok.contrast(fg, bg)}", Tok.contrast(fg, bg) >= min)
            aa(p.onPrimary, p.primary, "重點色上的字")
            aa(p.primary, p.canvas, "重點色當字")
            aa(p.primary, p.alarmTint, "鬧鐘卡上的重點色字")
            aa(p.primary, p.todayTint, "今天那一格的農曆（重點色）")
            aa(p.primary, p.surface, "淡底上的重點色圖示", 3f)
            for ((fg, what) in listOf(p.ink to "主要字", p.charcoal to "選項字", p.slate to "次要字", p.steel to "提示字", p.error to "錯誤訊息")) {
                aa(fg, p.canvas, what)
            }
            for ((fg, what) in listOf(p.ink to "主要字", p.slate to "次要字")) {
                aa(fg, p.surface, "淡底上的$what")
                aa(fg, p.todayTint, "今天那一格的$what")
                aa(fg, p.alarmTint, "鬧鐘卡上的$what")
            }
        }
    }
}
