package com.c520.likexiang.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * 介面的顏色（DESIGN.md「Colors」）。預設是 DESIGN.md 的 token；使用者在設定裡自選重點色或底色時，
 * 其他顏色照底色自動算（[of]），字的對比一律維持 WCAG AA。行程色塊與響鈴畫面的顏色不在這裡，不跟著換。
 */
data class Palette(
    val primary: Color,
    val onPrimary: Color,
    val canvas: Color,
    val surface: Color,
    val hairline: Color,
    val hairlineStrong: Color,
    val ink: Color,
    val charcoal: Color,
    val slate: Color,
    val steel: Color,
    val alarmTint: Color,
    val todayTint: Color,
    val error: Color,
) {
    /** 底色是淺色（字用深色）；狀態列、導覽列的圖示也跟著用深色。 */
    val isLight: Boolean get() = isLight(canvas)

    companion object {
        val DEFAULT = Palette(
            primary = Color(0xFF5645D4),
            onPrimary = Color(0xFFFFFFFF),
            canvas = Color(0xFFFFFFFF),
            surface = Color(0xFFF6F5F4),
            hairline = Color(0xFFE5E3DF),
            hairlineStrong = Color(0xFFC8C4BE),
            ink = Color(0xFF1A1A1A),
            charcoal = Color(0xFF37352F),
            slate = Color(0xFF5D5B54),
            steel = Color(0xFF787671),
            alarmTint = Color(0xFFE6E0F5),
            todayTint = Color(0xFFEEECFB),
            error = Color(0xFFB3261E),
        )

        /**
         * 使用者自選的重點色、底色（ARGB；null＝預設）算出整組顏色：
         * - 底色：太中間的灰（配黑字、白字都不到 7:1）先調淺或調深一點，字才有層次；
         *   字、格線、淡底都從底色往黑（淺底）或往白（深底）調，帶一點底色的色調，跟底色的對比照預設在白底上的層次：
         *   主要字 15:1、次要字 6:1、最淡的提示字 4.5:1（到不了就用純黑或純白），淡底、細線、深一點的細線各約 1.08、1.27、1.68。
         * - 重點色：淺底配白字、深底配深色字；往深（淺底）或往淺（深底）調到上面的字有 4.5:1，
         *   本身當字用時在底色和淡底上也有 4.5:1（跟底色至少 4.5 × [TINT]）。
         * - 淡底（今天那一格、鬧鐘設定卡）：見 [tint]。
         */
        fun of(accent: Int?, canvas: Int?): Palette {
            if (accent == null && canvas == null) return DEFAULT
            val bg = canvas?.let { readableCanvas(opaque(it)) } ?: DEFAULT.canvas
            val far = if (isLight(bg)) Color.Black else Color.White
            val base = if (canvas == null) DEFAULT else DEFAULT.copy(
                canvas = bg,
                surface = bg.toward(far) { Tok.contrast(it, bg) >= SURFACE },
                hairline = bg.toward(far) { Tok.contrast(it, bg) >= HAIRLINE },
                hairlineStrong = bg.toward(far) { Tok.contrast(it, bg) >= HAIRLINE_STRONG },
                ink = bg.toward(far) { Tok.contrast(it, bg) >= 15f },
                charcoal = bg.toward(far) { Tok.contrast(it, bg) >= 10f },
                slate = bg.toward(far) { Tok.contrast(it, bg) >= 6f },
                steel = bg.toward(far) { Tok.contrast(it, bg) >= AA },
                error = DEFAULT.error.toward(far) { Tok.contrast(it, bg) >= AA },
            )
            val onPrimary = if (far == Color.Black) Color.White else DEFAULT.ink
            val primary = (accent?.let(::opaque) ?: DEFAULT.primary).toward(far) { c ->
                Tok.contrast(c, onPrimary) >= AA && Tok.contrast(c, bg) >= AA * TINT
            }
            val tint = tint(bg, primary, base.slate)
            return base.copy(primary = primary, onPrimary = onPrimary, alarmTint = tint, todayTint = tint)
        }

        /**
         * 重點色疊在底色上的淡底（今天那一格、鬧鐘設定卡）：照「跟底色的對比」疊到 [TINT]，什麼顏色都一樣看得出來。
         * 照比例只疊一成的話，暖色（橘色只有 1.15:1）和黑底（1.01:1）幾乎看不到。
         * 次要字或重點色字在上面會不到 4.5:1 時就停在前一步。
         */
        private fun tint(bg: Color, primary: Color, slate: Color): Color {
            var tint = bg
            for (i in 1..STEPS) {
                val c = lerp(bg, primary, i / STEPS.toFloat())
                if (Tok.contrast(slate, c) < AA || Tok.contrast(primary, c) < AA) break
                tint = c
                if (Tok.contrast(c, bg) >= TINT) break
            }
            return tint
        }

        /** 淺色底：配黑字比配白字清楚。 */
        fun isLight(c: Color): Boolean = Tok.contrast(c, Color.Black) >= Tok.contrast(c, Color.White)

        /** 底色跟黑字（淺底）或白字（深底）至少 7:1：太中間的灰往淺（或深）調。 */
        private fun readableCanvas(c: Color): Color =
            if (isLight(c)) c.toward(Color.White) { Tok.contrast(it, Color.Black) >= CANVAS_HEADROOM }
            else c.toward(Color.Black) { Tok.contrast(it, Color.White) >= CANVAS_HEADROOM }

        private fun opaque(argb: Int) = Color(argb or 0xFF000000.toInt())

        /** 從這個顏色往 [far] 一點一點調（OKLab），回傳第一個符合 [ok] 的；到 [far] 都不行就用 [far]。 */
        private inline fun Color.toward(far: Color, ok: (Color) -> Boolean): Color {
            for (i in 0..STEPS) {
                val c = lerp(this, far, i / STEPS.toFloat())
                if (ok(c)) return c
            }
            return far
        }

        private const val AA = 4.5f
        private const val CANVAS_HEADROOM = 7f
        private const val STEPS = 100

        // 淡底、細線、深一點的細線跟底色的對比：跟預設的 surface、hairline、hairline-strong 在白底上一樣
        private const val SURFACE = 1.08f
        private const val HAIRLINE = 1.27f
        private const val HAIRLINE_STRONG = 1.68f

        // 淡底跟底色的對比：跟預設的鬧鐘設定卡（alarm-tint）在白底上一樣
        private const val TINT = 1.28f
    }
}
