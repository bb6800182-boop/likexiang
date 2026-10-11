package com.bryanh.calendaralarm.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
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

    /** 行程色塊＝Google 月曆行程色以 24% 不透明度疊在白底上，文字一律 charcoal（DESIGN.md「行程色塊」）。 */
    private const val EVENT_TINT = 0.24f
    fun eventTint(argb: Int): Color = Color(googleColor(argb)).copy(alpha = EVENT_TINT).compositeOver(canvas)
    fun eventColor(argb: Int): Color = Color(googleColor(argb)).copy(alpha = 1f)

    /**
     * Google 帳號的 11 種行程顏色，手機的日曆資料庫裡還是舊色票（例如石墨黑存成淺灰 #E1E1E1），
     * Google 月曆畫的是新色票；顯示時換成新色票，才跟 Google 月曆看到的一樣。其他顏色原樣。
     */
    private val GOOGLE_EVENT_COLORS = mapOf(
        0xFFA4BDFC.toInt() to 0xFF7986CB.toInt(), // 薰衣草色
        0xFF7AE7BF.toInt() to 0xFF33B679.toInt(), // 鼠尾草綠
        0xFFDBADFF.toInt() to 0xFF8E24AA.toInt(), // 葡萄紫
        0xFFFF887C.toInt() to 0xFFE67C73.toInt(), // 紅鶴色
        0xFFFBD75B.toInt() to 0xFFF6BF26.toInt(), // 香蕉黃
        0xFFFFB878.toInt() to 0xFFF4511E.toInt(), // 橙橘色
        0xFF46D6DB.toInt() to 0xFF039BE5.toInt(), // 孔雀藍
        0xFFE1E1E1.toInt() to 0xFF616161.toInt(), // 石墨黑
        0xFF5484ED.toInt() to 0xFF3F51B5.toInt(), // 藍莓色
        0xFF51B749.toInt() to 0xFF0B8043.toInt(), // 羅勒綠
        0xFFDC2127.toInt() to 0xFFD50000.toInt(), // 番茄紅
    )

    private fun googleColor(argb: Int): Int = (argb or 0xFF000000.toInt()).let { GOOGLE_EVENT_COLORS[it] ?: it }
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
