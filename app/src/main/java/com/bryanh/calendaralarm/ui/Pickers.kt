package com.bryanh.calendaralarm.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bryanh.calendaralarm.R
import com.bryanh.calendaralarm.data.CalendarRepository
import com.bryanh.calendaralarm.data.Settings
import com.bryanh.calendaralarm.ui.theme.Tok
import com.bryanh.calendaralarm.ui.theme.TypeTok

// 新增／編輯頁與詳情頁共用的選擇器（照 Google 月曆：點一列跳出清單，點選項就套用）

/**
 * 對話框裡的一列，至少 48dp 高。selected 不是 null 時是單選（選中的右邊打勾），null 時是一般按鈕。
 * emphasis：「自訂…」這類會再開一層的選項，用重點色。
 */
@Composable
fun OptionRow(
    text: String,
    selected: Boolean?,
    onClick: () -> Unit,
    subtitle: String? = null,
    emphasis: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    val interaction = if (selected != null) {
        Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
    } else {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(Tok.rMd))
            .then(interaction)
            .padding(horizontal = Tok.sXxs, vertical = Tok.sXxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(Tok.sSm))
        }
        Column(Modifier.weight(1f)) {
            Text(text, style = TypeTok.body, color = if (emphasis) Tok.primary else Tok.ink)
            if (subtitle != null) Text(subtitle, style = TypeTok.caption, color = Tok.slate)
        }
        if (selected == true) AppIcon(R.drawable.ic_check, null, Tok.primary, 20.dp)
    }
}

/** 清單對話框裡的分組標題（例如帳號）。 */
@Composable
fun OptionGroupTitle(text: String) {
    Text(
        text,
        style = TypeTok.caption,
        color = Tok.slate,
        modifier = Modifier.padding(start = Tok.sXxs, top = Tok.sSm, bottom = Tok.sXxs).semantics { heading() },
    )
}

/** 選一個值的對話框：內容可捲動；點選項就套用並關閉，所以只有「取消」。 */
@Composable
fun ChoiceDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Tok.canvas,
        shape = RoundedCornerShape(Tok.rXxl),
        title = { Text(title, style = TypeTok.titleMd, color = Tok.ink) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { content() } },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { DialogText("取消", primary = false) } },
    )
}

/** 色塊旁的小圓點；顏色很淺時靠細外框看得出來（色點只是輔助，名稱另外寫出來）。 */
@Composable
fun ColorDot(argb: Int?, size: Dp = 16.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (argb != null) Tok.eventColor(argb) else Tok.canvas)
            .border(1.dp, Tok.hairlineStrong, CircleShape),
    )
}

/** 自訂時間的單位。 */
enum class TimeUnitChoice(val label: String, val minutes: Int) {
    MINUTE("分鐘", 1),
    HOUR("小時", 60),
    DAY("天", 24 * 60),
    WEEK("週", 7 * 24 * 60),
}

/** 輸入「幾分鐘／小時／天／週」，換算成分鐘；超過 [maxMinutes] 不給確定。 */
@Composable
fun CustomDurationDialog(
    title: String,
    units: List<TimeUnitChoice>,
    maxMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf(units.first()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    fun confirm() {
        val total = text.toLongOrNull()?.times(unit.minutes)
        error = when {
            total == null -> "請輸入數字"
            total > maxMinutes -> "最多 ${Fmt.duration(maxMinutes)}"
            else -> null
        }
        if (error == null && total != null) onConfirm(total.toInt())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Tok.canvas,
        shape = RoundedCornerShape(Tok.rXxl),
        title = { Text(title, style = TypeTok.titleMd, color = Tok.ink) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { v ->
                        text = v.filter { it.isDigit() }.take(5)
                        error = null
                    },
                    label = { Text("數字", style = TypeTok.caption) },
                    singleLine = true,
                    isError = error != null,
                    textStyle = TypeTok.body.copy(color = Tok.ink),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                    colors = outlinedFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, style = TypeTok.caption, color = Tok.error, modifier = Modifier.padding(top = Tok.sXxs)) }
                Spacer(Modifier.size(Tok.sSm))
                ChipRow {
                    units.forEach { u ->
                        OptionChip(u.label, u == unit) {
                            unit = u
                            error = null
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = ::confirm) { DialogText("確定", primary = true) } },
        dismissButton = { TextButton(onClick = onDismiss) { DialogText("取消", primary = false) } },
    )
}

/** 鬧鐘提前多久：常用的幾個加上自訂（最多 24 小時）。 */
@Composable
fun LeadTimeDialog(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    var custom by remember { mutableStateOf(false) }
    if (custom) {
        CustomDurationDialog("自訂提前時間", listOf(TimeUnitChoice.MINUTE, TimeUnitChoice.HOUR), Settings.MAX_OFFSET_MIN, onPick, onDismiss)
        return
    }
    ChoiceDialog("鬧鐘提前多久", onDismiss) {
        // 自訂過的值也列出來並打勾
        (Settings.OFFSET_OPTIONS + current).distinct().sorted().forEach { m ->
            OptionRow(Fmt.leadLabel(m), selected = m == current, onClick = { onPick(m) })
        }
        OptionRow("自訂…", selected = null, onClick = { custom = true }, emphasis = true)
    }
}

/** 新增一個一般通知：常用的幾個（已經有的不列）加上自訂（最多 4 週，跟 Google 月曆一樣）。 */
@Composable
fun ReminderDialog(existing: Set<Int>, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    var custom by remember { mutableStateOf(false) }
    if (custom) {
        CustomDurationDialog("自訂通知時間", TimeUnitChoice.entries, CalendarRepository.MAX_REMINDER_MIN, onPick, onDismiss)
        return
    }
    ChoiceDialog("新增通知", onDismiss) {
        Settings.REMINDER_OPTIONS.filter { it !in existing }.forEach { m ->
            OptionRow(Fmt.reminderLabel(m), selected = null, onClick = { onPick(m) })
        }
        OptionRow("自訂…", selected = null, onClick = { custom = true }, emphasis = true)
    }
}

/**
 * 鬧鐘卡裡的「提前 10 分鐘 ⌄」：點了開 [LeadTimeDialog]。鬧鐘關著時字比較淡。
 * 整天行程的響鈴時刻在設定裡統一調整，這裡只顯示、不能點。
 */
@Composable
fun LeadTimeRow(minutes: Int, active: Boolean, allDayAt: String?, onClick: () -> Unit) {
    val text = allDayAt?.let { "整天行程在當天 $it 響（可在設定調整）" } ?: Fmt.leadLabel(minutes)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(Tok.rMd))
            .clickable(enabled = allDayAt == null, role = Role.Button, onClickLabel = "選擇提前時間", onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "鬧鐘時間：$text" }
            .padding(horizontal = Tok.sXxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(R.drawable.ic_clock, null, Tok.slate, 18.dp)
        Spacer(Modifier.width(Tok.sXs))
        Text(text, style = TypeTok.body, color = if (active) Tok.ink else Tok.slate, modifier = Modifier.weight(1f))
        if (allDayAt == null) AppIcon(R.drawable.ic_chevron_down, null, Tok.slate, 18.dp)
    }
}

/** 一般的外框輸入框（DESIGN.md text-field：未選取 steel、選取 primary）。 */
@Composable
fun outlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Tok.primary,
    unfocusedBorderColor = Tok.steel,
    errorBorderColor = Tok.error,
    cursorColor = Tok.primary,
    focusedLabelColor = Tok.primary,
    unfocusedLabelColor = Tok.slate,
    focusedContainerColor = Tok.canvas,
    unfocusedContainerColor = Tok.canvas,
    errorContainerColor = Tok.canvas,
)
