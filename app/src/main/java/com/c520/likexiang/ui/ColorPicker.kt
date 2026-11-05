package com.c520.likexiang.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.c520.likexiang.R
import com.c520.likexiang.ui.theme.Palette
import com.c520.likexiang.ui.theme.Tok
import com.c520.likexiang.ui.theme.TypeTok
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor

/** 選色對話框裡的一個常用色；argb 是 null 表示「預設」。 */
data class Swatch(val name: String, val argb: Int?)

/** 重點色的常用色：在預設的白底上不用調整（白字、當字用都有 4.5:1）。 */
val ACCENT_SWATCHES = listOf(
    Swatch("預設（紫）", null),
    Swatch("藍", 0xFF1A5FB4.toInt()),
    Swatch("青", 0xFF00707A.toInt()),
    Swatch("綠", 0xFF137333.toInt()),
    Swatch("橘", 0xFFB23F0C.toInt()),
    Swatch("紅", 0xFFC5221F.toInt()),
    Swatch("粉", 0xFFC2185B.toInt()),
    Swatch("葡萄紫", 0xFF8E24AA.toInt()),
    Swatch("石墨", 0xFF3C4043.toInt()),
)

/** 底色的常用色：淺色幾種、深色幾種。 */
val CANVAS_SWATCHES = listOf(
    Swatch("預設（白）", null),
    Swatch("暖白", 0xFFFBF8F1.toInt()),
    Swatch("淡灰", 0xFFF1F3F4.toInt()),
    Swatch("淡藍", 0xFFEAF1FB.toInt()),
    Swatch("淡綠", 0xFFEAF5EC.toInt()),
    Swatch("淡粉", 0xFFFCEFF3.toInt()),
    Swatch("深灰", 0xFF202124.toInt()),
    Swatch("深藍", 0xFF0E1A2B.toInt()),
    Swatch("黑", 0xFF000000.toInt()),
)

/** 日曆顏色的常用色：Google 月曆的 11 種行程顏色（名稱跟 Google 月曆 App 一樣）。 */
val CALENDAR_SWATCHES = listOf(
    Swatch("跟 Google 月曆一樣", null),
    Swatch("番茄紅", 0xFFD50000.toInt()),
    Swatch("紅鶴色", 0xFFE67C73.toInt()),
    Swatch("橙橘色", 0xFFF4511E.toInt()),
    Swatch("香蕉黃", 0xFFF6BF26.toInt()),
    Swatch("鼠尾草綠", 0xFF33B679.toInt()),
    Swatch("羅勒綠", 0xFF0B8043.toInt()),
    Swatch("孔雀藍", 0xFF039BE5.toInt()),
    Swatch("藍莓色", 0xFF3F51B5.toInt()),
    Swatch("薰衣草色", 0xFF7986CB.toInt()),
    Swatch("葡萄紫", 0xFF8E24AA.toInt()),
    Swatch("石墨黑", 0xFF616161.toInt()),
)

/** 六位色碼（不含 #），例如 5645D4。 */
internal fun hexOf(argb: Int): String = "%06X".format(argb and 0xFFFFFF)

private fun hsv(argb: Int): FloatArray = FloatArray(3).also { AndroidColor.colorToHSV(argb, it) }

/**
 * 選顏色：上面預覽，接著常用色，再來是色相、飽和度、亮度三條滑桿與色碼，任何顏色都選得到。
 * [current]、[onPick] 的 null 表示預設（[defaultArgb]）；按「確定」才存。
 * [display] 把存的顏色換成畫面上的樣子（日曆顏色要照行程色塊的畫法加白）。
 */
@Composable
fun ColorPickerDialog(
    title: String,
    current: Int?,
    swatches: List<Swatch>,
    defaultArgb: Int,
    onPick: (Int?) -> Unit,
    onDismiss: () -> Unit,
    display: (Int) -> Color = { Color(it) },
    preview: @Composable (Int?) -> Unit,
) {
    val start = hsv(current ?: defaultArgb)
    var choice by rememberSaveable { mutableStateOf(current) }
    var hue by rememberSaveable { mutableFloatStateOf(start[0]) }
    var sat by rememberSaveable { mutableFloatStateOf(start[1]) }
    var bright by rememberSaveable { mutableFloatStateOf(start[2]) }
    var hex by rememberSaveable { mutableStateOf(hexOf(current ?: defaultArgb)) }

    // 從常用色或色碼選的：滑桿跟著移到那個顏色
    fun pick(argb: Int?) {
        choice = argb
        val h = hsv(argb ?: defaultArgb)
        hue = h[0]
        sat = h[1]
        bright = h[2]
        hex = hexOf(argb ?: defaultArgb)
    }

    // 拉滑桿：顏色跟著變，色碼也跟著改
    fun slide() {
        val argb = AndroidColor.HSVToColor(floatArrayOf(hue, sat, bright))
        choice = argb
        hex = hexOf(argb)
    }

    val hexError = hex.length in 1..5
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Tok.canvas,
        shape = RoundedCornerShape(Tok.rXxl),
        title = { Text(title, style = TypeTok.titleMd, color = Tok.ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                preview(choice)
                Spacer(Modifier.size(Tok.sSm))
                ChipRow {
                    swatches.forEach { s ->
                        SwatchButton(s.name, display(s.argb ?: defaultArgb), selected = choice == s.argb) { pick(s.argb) }
                    }
                }
                HsvSlider("色相", hue, 0f..360f, "${hue.roundToInt()} 度", Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f, 1f, 1f) })) {
                    hue = it
                    slide()
                }
                HsvSlider("飽和度", sat, 0f..1f, "${(sat * 100).roundToInt()}%", Brush.horizontalGradient(listOf(Color.hsv(hue, 0f, bright), Color.hsv(hue, 1f, bright)))) {
                    sat = it
                    slide()
                }
                HsvSlider("亮度", bright, 0f..1f, "${(bright * 100).roundToInt()}%", Brush.horizontalGradient(listOf(Color.Black, Color.hsv(hue, sat, 1f)))) {
                    bright = it
                    slide()
                }
                Spacer(Modifier.size(Tok.sXs))
                OutlinedTextField(
                    value = hex,
                    onValueChange = { v ->
                        // 只收 0–9、A–F，最多 6 位；滿 6 位就換成那個顏色
                        hex = v.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }.take(6)
                        if (hex.length == 6) pick(hex.toInt(16) or 0xFF000000.toInt())
                    },
                    label = { Text("色碼", style = TypeTok.caption) },
                    prefix = { Text("#", style = TypeTok.body, color = Tok.slate) },
                    singleLine = true,
                    isError = hexError,
                    supportingText = if (hexError) {
                        { Text("色碼要 6 位（0–9、A–F），例如 1A73E8", style = TypeTok.caption, color = Tok.error) }
                    } else {
                        null
                    },
                    textStyle = TypeTok.body.copy(color = Tok.ink),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done,
                    ),
                    colors = outlinedFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onPick(choice) }) { DialogText("確定", primary = true) } },
        dismissButton = { TextButton(onClick = onDismiss) { DialogText("取消", primary = false) } },
    )
}

/** 一個常用色：48dp 的觸控範圍，選中時加粗外框和勾勾。 */
@Composable
private fun SwatchButton(name: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color)
                .border(if (selected) 3.dp else 1.dp, if (selected) Tok.ink else Tok.hairlineStrong, CircleShape),
        )
        // 色塊可能是任何顏色：勾勾用黑或白，哪個看得清楚用哪個
        if (selected) AppIcon(R.drawable.ic_check, null, if (Palette.isLight(color)) Color.Black else Color.White, 18.dp)
    }
}

/** 一條顏色滑桿：上面是名稱與數值，軌道畫成漸層，讀屏唸名稱與數值。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.HsvSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    brush: Brush,
    onChange: (Float) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(top = Tok.sXs)) {
        Text(label, style = TypeTok.caption, color = Tok.slate, modifier = Modifier.weight(1f))
        Text(valueText, style = TypeTok.caption, color = Tok.slate)
    }
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = range,
        modifier = Modifier.semantics {
            contentDescription = label
            stateDescription = valueText
        },
        thumb = {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Tok.canvas)
                    .border(2.dp, Tok.ink, CircleShape),
            )
        },
        track = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(Tok.rFull))
                    .background(brush)
                    .border(1.dp, Tok.hairline, RoundedCornerShape(Tok.rFull)),
            )
        },
    )
}

/** 重點色、底色的預覽：用算好的整組顏色畫今天的圓、兩行字和一個按鈕（只給眼睛看，讀屏略過）。 */
@Composable
fun PalettePreview(p: Palette) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tok.rLg))
            .background(p.canvas)
            .border(1.dp, p.hairline, RoundedCornerShape(Tok.rLg))
            .padding(Tok.sSm)
            .clearAndSetSemantics {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(p.primary), contentAlignment = Alignment.Center) {
            Text("6", style = TypeTok.caption, color = p.onPrimary)
        }
        Spacer(Modifier.width(Tok.sSm))
        Column(Modifier.weight(1f)) {
            Text("今天的行程", style = TypeTok.body, color = p.ink)
            Text("提前 10 分鐘響鈴", style = TypeTok.caption, color = p.slate)
        }
        Spacer(Modifier.width(Tok.sXs))
        Box(
            Modifier
                .clip(RoundedCornerShape(Tok.rMd))
                .background(p.primary)
                .padding(horizontal = Tok.sSm, vertical = Tok.sXxs),
        ) {
            Text("儲存", style = TypeTok.bodySm, color = p.onPrimary)
        }
    }
}

/** 日曆顏色的預覽：照行程色塊的畫法畫一條（只給眼睛看，讀屏略過）。 */
@Composable
fun CalendarColorPreview(name: String, argb: Int) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tok.rLg))
            .background(Tok.canvas)
            .border(1.dp, Tok.hairline, RoundedCornerShape(Tok.rLg))
            .padding(Tok.sSm)
            .clearAndSetSemantics {},
    ) {
        EventChip(name, argb, alarm = false, modifier = Modifier.fillMaxWidth().height(28.dp), style = TypeTok.body)
    }
}
