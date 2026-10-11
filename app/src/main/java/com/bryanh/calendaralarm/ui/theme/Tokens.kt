package com.bryanh.calendaralarm.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** 與 DESIGN.md 同名的設計 token。改顏色、字級、間距、圓角時先改 DESIGN.md，再同步這裡。 */
object Tok {
    // colors
    val primary = Color(0xFF5645D4)
    val onPrimary = Color(0xFFFFFFFF)
    val canvas = Color(0xFFFFFFFF)
    val surface = Color(0xFFF6F5F4)
    val hairline = Color(0xFFE5E3DF)
    val hairlineStrong = Color(0xFFC8C4BE)
    val ink = Color(0xFF1A1A1A)
    val charcoal = Color(0xFF37352F)
    val slate = Color(0xFF5D5B54)
    val steel = Color(0xFF787671)
    val alarmTint = Color(0xFFE6E0F5)
    val todayTint = Color(0xFFEEECFB)
    val error = Color(0xFFB3261E)
    val ringBg = Color(0xFF0A1530)
    val ringSurface = Color(0xFF1A2A52)
    val ringInk = Color(0xFFFFFFFF)
    val ringInkMuted = Color(0xFFA4A097)
    val ringGlow = Color(0xFFD6B6F6)
    val deco = listOf(Color(0xFFDD5B00), Color(0xFFFF64C8), Color(0xFFF5D75E), Color(0xFF2A9D99), Color(0xFF1AAE39))

    // rounded
    val rXs = 4.dp
    val rSm = 6.dp
    val rMd = 8.dp
    val rLg = 12.dp
    val rXl = 16.dp
    val rXxl = 20.dp
    val rFull = 9999.dp

    // spacing
    val sXxs = 4.dp
    val sXs = 8.dp
    val sSm = 12.dp
    val sMd = 16.dp
    val sLg = 20.dp
    val sXl = 24.dp
    val sXxl = 32.dp

    // 元件尺寸（DESIGN.md components）
    val buttonPadH = 18.dp // button-primary：padding 10px 18px
    val buttonPadV = 10.dp
    val chipPadV = 6.dp // button-outline、option-chip：padding 6px 12px
    val cardPadH = 14.dp // alarm-card：padding 12px 14px
    val topBarH = 56.dp
    val fabElevation = 6.dp // 只有新增鍵有柔和陰影（DESIGN.md「Elevation & Depth」）
    val ringSnoozeH = 44.dp
    val ringDismissH = 52.dp

    /**
     * 行程顏色（DESIGN.md「行程色塊」）：照 Google 月曆小工具的畫法，Google 現在的色票在 OKLab 加兩成白，
     * 鮮明但不刺眼（跟使用者截圖量到的 Google 顏色幾乎一樣）。色塊、色點、色條都用這個顏色。
     */
    fun eventColor(argb: Int): Color = lerp(Color(googleColor(argb)).copy(alpha = 1f), Color.White, LIGHTEN)

    /**
     * 色塊上的字色，也照 Google 月曆小工具：底色不太亮用白字，亮的（黃、淺綠、紅鶴…）用同色系的深色字。
     * 中間色上的白字對比約 2.6～4:1，低於 WCAG AA：使用者看過對照圖後選擇跟 Google 一樣（DESIGN.md 記為例外）。
     */
    fun onEventColor(argb: Int): Color {
        val bg = eventColor(argb)
        return if (bg.luminance() < WHITE_TEXT_BELOW) onPrimary else lerp(bg, Color.Black, DARK_TEXT)
    }

    /** WCAG 對比值（1～21）。 */
    fun contrast(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }

    /** 加多少白（OKLab）。 */
    private const val LIGHTEN = 0.2f

    /** 底色的相對亮度低於這個值用白字（Google 小工具在 0.35 用白字、0.42 用深色字）。 */
    private const val WHITE_TEXT_BELOW = 0.4f

    /** 深色字＝底色往黑走多少（OKLab）：同色系，對比 8:1 以上。 */
    private const val DARK_TEXT = 0.85f

    /**
     * 手機的日曆資料庫裡，Google 帳號的行程顏色（11 種）與日曆顏色（24 種）都還是舊色票
     * （例如石墨黑存成淺灰 #E1E1E1），Google 月曆畫的是同名的新色票；顯示時換成新色票，才跟 Google 月曆看到的一樣。
     * 舊色票與名稱的對應見 Google Calendar API 的 colors；不在表裡的顏色（自訂色）原樣。
     */
    private val GOOGLE_COLORS = mapOf(
        // 行程顏色
        0xFFA4BDFC.toInt() to 0xFF7986CB.toInt(), // Lavender 薰衣草色
        0xFF7AE7BF.toInt() to 0xFF33B679.toInt(), // Sage 鼠尾草綠
        0xFFDBADFF.toInt() to 0xFF8E24AA.toInt(), // Grape 葡萄紫
        0xFFFF887C.toInt() to 0xFFE67C73.toInt(), // Flamingo 紅鶴色
        0xFFFBD75B.toInt() to 0xFFF6BF26.toInt(), // Banana 香蕉黃
        0xFFFFB878.toInt() to 0xFFF4511E.toInt(), // Tangerine 橙橘色
        0xFF46D6DB.toInt() to 0xFF039BE5.toInt(), // Peacock 孔雀藍
        0xFFE1E1E1.toInt() to 0xFF616161.toInt(), // Graphite 石墨黑
        0xFF5484ED.toInt() to 0xFF3F51B5.toInt(), // Blueberry 藍莓色
        0xFF51B749.toInt() to 0xFF0B8043.toInt(), // Basil 羅勒綠
        0xFFDC2127.toInt() to 0xFFD50000.toInt(), // Tomato 番茄紅
        // 日曆顏色
        0xFFAC725E.toInt() to 0xFF795548.toInt(), // Cocoa
        0xFFD06B64.toInt() to 0xFFE67C73.toInt(), // Flamingo
        0xFFF83A22.toInt() to 0xFFD50000.toInt(), // Tomato
        0xFFFA573C.toInt() to 0xFFF4511E.toInt(), // Tangerine
        0xFFFF7537.toInt() to 0xFFEF6C00.toInt(), // Pumpkin
        0xFFFFAD46.toInt() to 0xFFF09300.toInt(), // Mango
        // 名稱對照表寫 #42D692 是尤加利、#16A765 是羅勒綠，但使用者手機上 Google 月曆把 #16A765 的日曆（國定假日）
        // 畫成尤加利樹綠（截圖量到的顏色完全吻合）；照實際顯示對調。#42D692 沒有實例可對照，跟著對調。
        0xFF42D692.toInt() to 0xFF0B8043.toInt(), // Basil
        0xFF16A765.toInt() to 0xFF009688.toInt(), // Eucalyptus
        0xFF7BD148.toInt() to 0xFF7CB342.toInt(), // Pistachio
        0xFFB3DC6C.toInt() to 0xFFC0CA33.toInt(), // Avocado
        0xFFFBE983.toInt() to 0xFFE4C441.toInt(), // Citron
        0xFFFAD165.toInt() to 0xFFF6BF26.toInt(), // Banana
        0xFF92E1C0.toInt() to 0xFF33B679.toInt(), // Sage
        0xFF9FE1E7.toInt() to 0xFF039BE5.toInt(), // Peacock
        0xFF9FC6E7.toInt() to 0xFF4285F4.toInt(), // Cobalt
        0xFF4986E7.toInt() to 0xFF3F51B5.toInt(), // Blueberry
        0xFF9A9CFF.toInt() to 0xFF7986CB.toInt(), // Lavender
        0xFFB99AFF.toInt() to 0xFFB39DDB.toInt(), // Wisteria
        0xFFC2C2C2.toInt() to 0xFF616161.toInt(), // Graphite
        0xFFCABDBF.toInt() to 0xFFA79B8E.toInt(), // Birch
        0xFFCCA6AC.toInt() to 0xFFAD1457.toInt(), // Radicchio
        0xFFF691B2.toInt() to 0xFFD81B60.toInt(), // Cherry blossom
        0xFFCD74E6.toInt() to 0xFF8E24AA.toInt(), // Grape
        0xFFA47AE2.toInt() to 0xFF9E69AF.toInt(), // Amethyst
    )

    private fun googleColor(argb: Int): Int = (argb or 0xFF000000.toInt()).let { GOOGLE_COLORS[it] ?: it }
}

object TypeTok {
    val titleLg = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp, letterSpacing = (-0.5).sp)
    val titleMd = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp, letterSpacing = (-0.2).sp)
    val body = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp)
    val bodySm = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 19.sp)
    val caption = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 17.sp)
    val chip = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp)
    val lunar = TextStyle(fontSize = 9.5.sp, fontWeight = FontWeight.Normal, lineHeight = 1.2.em)
    val ringTitle = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp, letterSpacing = (-0.5).sp)
    val clock = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.SemiBold, lineHeight = 68.sp, letterSpacing = (-2.5).sp)
}

/** Material 元件（日期時間選擇器、輸入框、選單、對話框）也套用同一組 token。 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Tok.primary,
            onPrimary = Tok.onPrimary,
            primaryContainer = Tok.alarmTint,
            onPrimaryContainer = Tok.ink,
            secondary = Tok.primary,
            onSecondary = Tok.onPrimary,
            secondaryContainer = Tok.alarmTint,
            onSecondaryContainer = Tok.ink,
            error = Tok.error,
            onError = Tok.onPrimary,
            background = Tok.canvas,
            onBackground = Tok.ink,
            surface = Tok.canvas,
            onSurface = Tok.ink,
            surfaceVariant = Tok.surface,
            onSurfaceVariant = Tok.slate,
            surfaceContainerLowest = Tok.canvas,
            surfaceContainerLow = Tok.canvas,
            surfaceContainer = Tok.canvas,
            surfaceContainerHigh = Tok.canvas,
            surfaceContainerHighest = Tok.surface,
            outline = Tok.steel,
            outlineVariant = Tok.hairline,
        ),
        typography = Typography(
            titleLarge = TypeTok.titleLg,
            titleMedium = TypeTok.titleMd,
            bodyLarge = TypeTok.body,
            bodyMedium = TypeTok.bodySm,
            bodySmall = TypeTok.caption,
            labelLarge = TypeTok.bodySm.copy(fontWeight = FontWeight.Medium),
            labelMedium = TypeTok.caption,
            labelSmall = TypeTok.chip,
        ),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(Tok.rMd), // 輸入框、選單
            small = RoundedCornerShape(Tok.rMd),
            medium = RoundedCornerShape(Tok.rLg),
            large = RoundedCornerShape(Tok.rXl),
            extraLarge = RoundedCornerShape(Tok.rXxl), // 對話框、日期時間選擇器
        ),
        content = content,
    )
}
