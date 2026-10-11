package com.bryanh.calendaralarm.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bryanh.calendaralarm.R
import com.bryanh.calendaralarm.data.CalendarInfo
import com.bryanh.calendaralarm.ui.theme.Tok
import com.bryanh.calendaralarm.ui.theme.TypeTok
import kotlinx.coroutines.delay

@Composable
fun AppIcon(@DrawableRes res: Int, contentDescription: String?, tint: Color = Tok.ink, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Icon(painterResource(res), contentDescription = contentDescription, tint = tint, modifier = modifier.size(size))
}

/** 48dp 觸控範圍的圖示按鈕。說明只放在按鈕上，圖示本身不重複朗讀。 */
@Composable
fun IconBtn(@DrawableRes res: Int, contentDescription: String, onClick: () -> Unit, tint: Color = Tok.ink) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(res, null, tint)
    }
}

@Composable
fun TopBar(
    title: String,
    subtitle: String? = null,
    @DrawableRes navIcon: Int? = null,
    navDescription: String = "返回",
    onNav: () -> Unit = {},
    actions: @Composable () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Tok.topBarH)
            .padding(end = Tok.sXxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (navIcon != null) IconBtn(navIcon, navDescription, onNav) else Spacer(Modifier.width(Tok.sMd))
        TitleBlock(title, subtitle, Modifier.weight(1f))
        actions()
    }
}

/**
 * 頂部列的標題＋副標題（標成 heading，讀屏可以直接跳到這裡）。
 * 字放大放不下時換行而不截斷：副標題以空白分段、整段移到下一行（「八月－九月」被截成「八月一…」會看錯）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TitleBlock(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) { heading() }) {
        Text(title, style = TypeTok.titleLg, color = Tok.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (!subtitle.isNullOrBlank()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Tok.sXxs)) {
                subtitle.split(' ').filter { it.isNotEmpty() }.forEach {
                    Text(it, style = TypeTok.caption, color = Tok.slate, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(Tok.rMd))
            .background(if (enabled) Tok.primary else Tok.hairlineStrong)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Tok.buttonPadH, vertical = Tok.buttonPadV),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TypeTok.body, color = if (enabled) Tok.onPrimary else Tok.ink)
    }
}

@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(Tok.rMd))
            .border(1.dp, Tok.hairlineStrong, RoundedCornerShape(Tok.rMd))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Tok.sSm, vertical = Tok.chipPadV),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TypeTok.bodySm, color = Tok.ink)
    }
}

/**
 * 選項按鈕。單選（multi = false）讀屏唸「單選按鈕、已選取」；多選（multi = true）唸「核取方塊、已勾選」。
 * 放在 ChipRow 裡，同一組選項會被當成一個群組。
 */
@Composable
fun OptionChip(text: String, selected: Boolean, multi: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Tok.rMd)
    val interaction = if (multi) {
        Modifier.toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
    } else {
        Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
    }
    Box(
        Modifier
            .heightIn(min = 36.dp)
            .clip(shape)
            .background(if (selected) Tok.primary else Tok.canvas)
            .border(1.dp, if (selected) Tok.primary else Tok.hairline, shape)
            .then(interaction)
            .padding(horizontal = Tok.sSm, vertical = Tok.chipPadV),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TypeTok.bodySm, color = if (selected) Tok.onPrimary else Tok.charcoal)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Tok.sXs),
        verticalArrangement = Arrangement.spacedBy(Tok.sXs),
    ) { content() }
}

/** 開關本身不處理點擊，由整列 SwitchRow 處理，讀屏才唸得出是哪個開關。 */
@Composable
fun AppSwitch(checked: Boolean, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = null,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Tok.onPrimary,
            checkedTrackColor = Tok.primary,
            checkedBorderColor = Tok.primary,
            uncheckedThumbColor = Tok.slate,
            uncheckedTrackColor = Tok.canvas,
            uncheckedBorderColor = Tok.slate,
        ),
    )
}

/** 一整列可點的開關：標題（＋說明）在左、開關在右，整列都是觸控範圍。 */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    titleStyle: androidx.compose.ui.text.TextStyle = TypeTok.body,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(Tok.sSm))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = titleStyle, color = Tok.ink)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = TypeTok.caption, color = Tok.slate)
        }
        Spacer(Modifier.width(Tok.sXs))
        AppSwitch(checked = checked, enabled = enabled)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = TypeTok.caption,
        color = Tok.slate,
        modifier = modifier
            .padding(top = Tok.sLg, bottom = Tok.sXs)
            .semantics { heading() },
    )
}

/** 行程色塊：沿用 Google 月曆行程色的淡色版，文字 charcoal。 */
@Composable
fun EventChip(title: String, argb: Int, alarm: Boolean, modifier: Modifier = Modifier, style: androidx.compose.ui.text.TextStyle = TypeTok.chip) {
    Row(
        modifier
            .clip(RoundedCornerShape(Tok.rXs))
            .background(Tok.eventTint(argb))
            .padding(horizontal = Tok.sXxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (alarm) {
            AppIcon(R.drawable.ic_alarm, "有鬧鐘", Tok.charcoal, 10.dp)
            Spacer(Modifier.width(2.dp))
        }
        Text(title, style = style, color = Tok.charcoal, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
    }
}

/** 日曆勾選清單（左側選單與設定頁共用），依帳號分組。 */
@Composable
fun CalendarToggles(calendars: List<CalendarInfo>, hidden: Set<Long>, onToggle: (CalendarInfo, Boolean) -> Unit) {
    if (calendars.isEmpty()) {
        Text("還沒有讀到日曆，請先允許讀取日曆。", style = TypeTok.caption, color = Tok.slate)
        return
    }
    calendars.groupBy { it.accountName }.forEach { (account, list) ->
        Text(account, style = TypeTok.caption, color = Tok.slate, modifier = Modifier.padding(top = Tok.sXs))
        list.forEach { c ->
            val shown = c.id !in hidden
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(value = shown, role = Role.Checkbox, onValueChange = { onToggle(c, it) }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = shown,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(checkedColor = Tok.primary, uncheckedColor = Tok.slate, checkmarkColor = Tok.onPrimary),
                )
                Spacer(Modifier.width(Tok.sXs))
                Box(Modifier.size(10.dp).clip(CircleShape).background(Tok.eventColor(c.color)))
                Spacer(Modifier.width(Tok.sXs))
                Text(c.displayName, style = TypeTok.body, color = Tok.ink)
            }
        }
    }
}

/** 頂部的提醒橫幅（例如鬧鐘需要的權限沒開），整條可點。 */
@Composable
fun Banner(text: String, action: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Tok.alarmTint)
            .clickable(role = Role.Button, onClickLabel = action, onClick = onClick)
            .padding(horizontal = Tok.sMd, vertical = Tok.sXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(R.drawable.ic_info, null, Tok.ink, 18.dp)
        Spacer(Modifier.width(Tok.sXs))
        Text(text, style = TypeTok.bodySm, color = Tok.ink, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(Tok.sXs))
        Text(action, style = TypeTok.bodySm, color = Tok.primary)
    }
}

/** 畫面底部的提示列（取代系統對話框），幾秒後自動消失；讀屏會主動唸出。 */
@Composable
fun MessageBar(message: String?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    if (message == null) return
    LaunchedEffect(message) {
        delay(5000)
        onDismiss()
    }
    Box(
        modifier
            .padding(Tok.sMd)
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tok.rLg))
            .background(Tok.ink)
            .clickable(onClickLabel = "關閉提示", onClick = onDismiss)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = Tok.sMd, vertical = Tok.sSm),
    ) {
        Text(message, style = TypeTok.bodySm, color = Tok.canvas)
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmText: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Tok.canvas,
        shape = RoundedCornerShape(Tok.rXxl),
        title = { Text(title, style = TypeTok.titleMd, color = Tok.ink) },
        text = { Text(text, style = TypeTok.body, color = Tok.charcoal) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText, style = TypeTok.body, color = Tok.primary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", style = TypeTok.body, color = Tok.slate) } },
    )
}

/** 對話框的「確定」「取消」按鈕文字。 */
@Composable
fun DialogText(text: String, primary: Boolean) {
    Text(text, style = TypeTok.body, color = if (primary) Tok.primary else Tok.slate)
}

@Composable
fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Tok.hairline),
    )
}
