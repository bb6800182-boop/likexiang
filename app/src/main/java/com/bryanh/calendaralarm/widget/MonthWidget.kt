package com.bryanh.calendaralarm.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.bryanh.calendaralarm.R
import com.bryanh.calendaralarm.data.EventInstance
import com.bryanh.calendaralarm.data.WidgetRange
import com.bryanh.calendaralarm.ui.Fmt
import com.bryanh.calendaralarm.ui.layout.WeekLayoutEngine
import com.bryanh.calendaralarm.ui.layout.weekSpans
import com.bryanh.calendaralarm.ui.theme.Tok
import java.time.LocalDate
import java.time.ZoneId

/**
 * 月曆小工具（WID-01）：跟 App 月檢視一樣的整頁月曆格，每天直接列出行程。
 * 可切換上下月、「＋」新增，點日期開那一天的日檢視。
 */
class MonthWidget : GlanceAppWidget() {
    /**
     * 給桌面幾種高度的版面，桌面依小工具「實際」大小挑放得下的最大一種（Android 12 起由系統挑）。
     * 不用 Exact：Nova 回報的尺寸常常是整個螢幕，照回報算會多排車道、被切掉。
     * 版面越多，每次更新傳給桌面的資料越大，所以只給四種；寬度都用最小值，實際寬度由權重平分。
     */
    override val sizeMode = SizeMode.Responsive(setOf(260.dp, 400.dp, 560.dp, 740.dp).map { DpSize(180.dp, it) }.toSet())
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // 先載好再顯示，更新時不會閃一下「載入中」
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val first = WidgetData.month(context, prefs[Widgets.OFFSET] ?: 0, prefs[Widgets.REFRESH] ?: 0)
        provideContent {
            val offset = currentState(Widgets.OFFSET) ?: 0
            val refresh = currentState(Widgets.REFRESH) ?: 0
            var model by remember { mutableStateOf(first) }
            LaunchedEffect(offset, refresh) {
                if (offset != model.offset || refresh != model.refresh) model = WidgetData.month(context, offset, refresh)
            }
            MonthContent(model)
        }
    }
}

class MonthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Widgets.scheduleMidnight(context)
    }
}

/** 上下月與回到這個月。位移限制在前後十年內。 */
class ShiftMonthAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val delta = parameters[DELTA] ?: 0
        updateAppWidgetState(context, glanceId) { p ->
            p[Widgets.OFFSET] = if (delta == 0) 0 else ((p[Widgets.OFFSET] ?: 0) + delta).coerceIn(-120, 120)
        }
        MonthWidget().update(context, glanceId)
    }

    companion object {
        val DELTA = ActionParameters.Key<Int>("delta")
    }
}

// 版面：跟 App 的月曆格同一組 token（DESIGN.md）
private val HEADER_H = 40.dp
private val ROOT_PAD = 2.dp

// Glance 一個版面最多 500 個 view，車道數要讓「每格都排滿」時也不超過（留一點餘裕）
private const val VIEW_BUDGET = 480
private const val FIXED_VIEWS = 31 // 頂部列、星期列、格線背景、今天的圓
private const val WEEK_VIEWS = 3 // 每週：外框、週格線、七天那一列
private const val CELL_VIEWS = 6 // 每格：可點的格子（Glance 會多包 2 層）＋日期列 3 個
private const val MAX_LANES = 9 // 一格最多 10 個子元件：日期列＋9 條

/** 月曆格的尺寸，跟著系統字級放大（小工具的字會跟著字級變大，高度也要跟著）。 */
internal class GridDims(fontScale: Float) {
    /** 今天的紫色圓。 */
    val mark = 16.dp * fontScale

    /** 日期＋農曆那一行。 */
    val label = mark + 2.dp

    /** 一條色塊（9sp 一行字）。 */
    val slot = 11.dp * fontScale + 4.dp

    /** 星期列（估計值，寧可估大，車道才不會排太多）。 */
    val weekday = 10.dp * fontScale * 1.5f + 4.dp
}

/**
 * 一格放幾條色塊：這個版面高度放得下幾條，再受 view 數量上限限制。放不下的行程在最後一條顯示「+N」。
 * 0 表示連一條都放不下，改用色點（每格最多 4 個 view，6 週也不會超過上限）。
 */
internal fun lanesFor(height: Dp, weeks: Int, d: GridDims, cols: Int = 7): Int {
    val grid = height - ROOT_PAD * 2 - HEADER_H - d.weekday
    val perWeek = grid / weeks - 1.dp - d.label // 每週上方 1dp 格線
    val fit = (perWeek / d.slot).toInt()
    val budget = (VIEW_BUDGET - FIXED_VIEWS - WEEK_VIEWS * weeks) / (cols * weeks) - CELL_VIEWS
    return minOf(fit, budget, MAX_LANES).coerceAtLeast(0)
}

/**
 * 日與日之間的直線：小工具不能畫線，用一張「欄數×100」×1 的小圖當月曆格背景拉伸到整格，
 * 每 100 像素（一欄）最右邊 2 像素是格線色。色塊蓋在線上，跨日行程才接得起來。
 */
private val columnLines = HashMap<Int, Bitmap>()

private fun columnLines(cols: Int): Bitmap = synchronized(columnLines) {
    columnLines.getOrPut(cols) {
        Bitmap.createBitmap(cols * 100, 1, Bitmap.Config.ARGB_8888).apply {
            val line = Tok.hairline.toArgb()
            for (col in 1 until cols) {
                setPixel(col * 100 - 2, 0, line)
                setPixel(col * 100 - 1, 0, line)
            }
        }
    }
}

/** 整個小工具用權重平分，一定滿版；高度只決定一格放幾條色塊。 */
@Composable
private fun MonthContent(m: MonthModel) {
    val dims = GridDims(LocalContext.current.resources.configuration.fontScale)
    Column(
        GlanceModifier.fillMaxSize().background(Widgets.color(Tok.canvas)).cornerRadius(Tok.rXl).padding(ROOT_PAD),
    ) {
        Header(m)
        if (!m.ok) {
            Message("請先開啟${LocalContext.current.getString(R.string.app_name)}，允許讀取日曆")
            return@Column
        }
        val cols = m.weeks.first().size
        WeekdayRow(m.weeks.first())
        val lanes = lanesFor(LocalSize.current.height, m.weeks.size, dims, cols)
        Column(GlanceModifier.fillMaxWidth().defaultWeight().background(ImageProvider(columnLines(cols)))) {
            m.weeks.forEach { week -> Week(m, week, dims, lanes) }
        }
    }
}

@Composable
private fun Header(m: MonthModel) {
    val context = LocalContext.current
    val monthTitle = m.title
    Row(GlanceModifier.fillMaxWidth().height(HEADER_H).padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            GlanceModifier.defaultWeight().padding(start = 6.dp).clickable(actionStartActivity(Widgets.openApp(context))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(monthTitle, style = TextStyle(color = Widgets.color(Tok.ink), fontSize = 17.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            if (m.lunarRange.isNotEmpty()) {
                Text("  ${m.lunarRange}", style = TextStyle(color = Widgets.color(Tok.slate), fontSize = 11.sp), maxLines = 1)
            }
        }
        if (m.offset != 0) {
            // App 頂部的「今天」（button-outline）；小工具畫不了外框，改用 surface 底色
            Text(
                "今天",
                style = TextStyle(color = Widgets.color(Tok.ink), fontSize = 13.sp, fontWeight = FontWeight.Medium),
                modifier = GlanceModifier.padding(horizontal = Tok.sSm, vertical = 6.dp).cornerRadius(Tok.rMd)
                    .background(Widgets.color(Tok.surface)).clickable(shiftMonth(0))
                    .semantics { contentDescription = "回到今天" },
                maxLines = 1,
            )
        }
        val unit = when (m.range) {
            WidgetRange.MONTH -> null
            WidgetRange.WEEKS -> "${m.weeks.size} 週"
            WidgetRange.DAYS -> "${m.weeks.first().size} 天"
        }
        IconButton(R.drawable.ic_chevron_left, unit?.let { "前 $it" } ?: "上個月", shiftMonth(-1))
        IconButton(R.drawable.ic_chevron_right, unit?.let { "後 $it" } ?: "下個月", shiftMonth(1))
        IconButton(R.drawable.ic_plus, "新增行程", actionStartActivity(Widgets.newEvent(context)), primary = true)
    }
}

private fun shiftMonth(delta: Int): Action = actionRunCallback<ShiftMonthAction>(actionParametersOf(ShiftMonthAction.DELTA to delta))

/** 頂部的圖示按鈕：40dp 見方、圖示 24dp 置中（跟 App 同一組圖示）。只用一個 Image，省 view。 */
@Composable
private fun IconButton(@DrawableRes icon: Int, description: String, action: Action, primary: Boolean = false) {
    Image(
        provider = ImageProvider(icon),
        contentDescription = description,
        colorFilter = ColorFilter.tint(Widgets.color(if (primary) Tok.onPrimary else Tok.ink)),
        modifier = GlanceModifier.size(40.dp).padding(8.dp).cornerRadius(Tok.rFull)
            .then(if (primary) GlanceModifier.background(Widgets.color(Tok.primary)) else GlanceModifier)
            .clickable(action),
    )
}

/** 星期列：照第一列的日期寫（「N 天」從哪天開始都對得上）。 */
@Composable
private fun WeekdayRow(firstRow: List<LocalDate>) {
    // 高度不寫死：字級放大時才不會被切掉
    Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)) {
        firstRow.map { Fmt.dowChar(it) }.forEach {
            Text(
                it,
                style = TextStyle(color = Widgets.color(Tok.slate), fontSize = 10.sp, textAlign = TextAlign.Center),
                modifier = GlanceModifier.defaultWeight(),
                maxLines = 1,
            )
        }
    }
}

/** 一格裡的一條車道要畫什麼。 */
private sealed interface Piece {
    /** 行程色塊的一段；跨日行程在每一天各畫一段，接起來像一條。標題只寫在這週的第一段。 */
    class Event(val inst: EventInstance, val first: Boolean, val multiDay: Boolean) : Piece
    class More(val n: Int) : Piece
}

/**
 * 一週：上方一條淡格線，下面七天平分寬度。車道由 WeekLayoutEngine 排好，
 * 同一筆跨日行程在每一天都放在同一條車道，色塊就會連成一條。
 */
@Composable
private fun ColumnScope.Week(m: MonthModel, week: List<LocalDate>, d: GridDims, lanes: Int) {
    val context = LocalContext.current
    val spans = weekSpans(m.instances, week.first(), ZoneId.systemDefault(), week.size)
    // pieces[col][lane]
    val pieces = Array(week.size) { arrayOfNulls<Piece>(lanes) }
    if (lanes > 0) {
        val layout = WeekLayoutEngine.layout(spans, lanes)
        layout.placed.forEach { p ->
            for (col in p.startCol until p.startCol + p.span) {
                pieces[col][p.lane] = Piece.Event(m.instances[p.id], col == p.startCol, p.span > 1)
            }
        }
        layout.more.forEach { (col, n) -> pieces[col][lanes - 1] = Piece.More(n) }
    }
    Column(GlanceModifier.fillMaxWidth().defaultWeight()) {
        Spacer(GlanceModifier.fillMaxWidth().height(1.dp).background(Widgets.color(Tok.hairline)))
        Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
            week.forEachIndexed { col, day ->
                val events = spans.filter { col in it.startCol until it.startCol + it.span }.sortedBy { it.sortKey }.map { m.instances[it.id] }
                Column(
                    GlanceModifier.defaultWeight().fillMaxHeight()
                        // 今天整格淡紫底，一眼找得到
                        .then(if (day == m.today) GlanceModifier.background(Widgets.color(Tok.todayTint)) else GlanceModifier)
                        // 右邊留 1dp 露出背景的直線
                        .padding(end = 1.dp)
                        .clickable(actionStartActivity(Widgets.openDay(context, day)))
                        .semantics { contentDescription = dayDescription(m, day, events) },
                ) {
                    DayLabel(m, day, d)
                    if (lanes == 0) {
                        Dots(events)
                    } else {
                        // 最後一個有東西的車道之後不放空白，省 view
                        val cells = pieces[col]
                        for (lane in 0..cells.indexOfLast { it != null }) LanePiece(m, cells[lane], d.slot)
                    }
                }
            }
        }
    }
}

private fun dayDescription(m: MonthModel, day: LocalDate, events: List<EventInstance>): String = buildString {
    append(Fmt.dateWithDow(day))
    if (day == m.today) append("，今天")
    m.lunar[day]?.let { append("，農曆").append(it) }
    if (events.isEmpty()) {
        append("，沒有行程")
    } else {
        append("，${events.size} 個行程：")
        append(events.take(5).joinToString("、") { if (it.key in m.alarmOn) "${it.displayTitle}（鬧鐘）" else it.displayTitle })
        if (events.size > 5) append(" 等")
    }
}

@Composable
private fun DayLabel(m: MonthModel, day: LocalDate, d: GridDims) {
    val isToday = day == m.today
    val inMonth = m.month == null || day.month == m.month.month
    val lunar = m.lunar[day]?.takeIf { it.isNotEmpty() }
    val number = "${day.dayOfMonth}"
    Row(GlanceModifier.fillMaxWidth().height(d.label), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
        if (isToday) {
            // 今天：紫色圓（DESIGN.md today-marker），數字放在圓的正中央
            Box(GlanceModifier.size(d.mark).cornerRadius(Tok.rFull).background(Widgets.color(Tok.primary)), contentAlignment = Alignment.Center) {
                Text(number, style = TextStyle(color = Widgets.color(Tok.onPrimary), fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
        } else {
            Text(
                number,
                style = TextStyle(color = Widgets.color(if (inMonth) Tok.ink else Tok.steel), fontSize = 11.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
        if (lunar != null) {
            val color = when {
                isToday -> Tok.primary
                inMonth -> Tok.slate
                else -> Tok.steel
            }
            Text(lunar, style = TextStyle(color = Widgets.color(color), fontSize = 8.sp), modifier = GlanceModifier.padding(start = 2.dp), maxLines = 1)
        }
    }
}

/** 一條車道的一格：行程色塊、「+N」或留白（留白也佔高度，車道才對得齊）。 */
@Composable
private fun LanePiece(m: MonthModel, piece: Piece?, slot: Dp) {
    when (piece) {
        null -> Spacer(GlanceModifier.fillMaxWidth().height(slot))
        is Piece.More -> Text(
            "+${piece.n}",
            style = TextStyle(color = Widgets.color(Tok.slate), fontSize = 9.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
            modifier = GlanceModifier.fillMaxWidth().height(slot),
            maxLines = 1,
        )
        is Piece.Event -> {
            val inst = piece.inst
            val title = if (!piece.first) "" else if (inst.key in m.alarmOn) "⏰${inst.displayTitle}" else inst.displayTitle
            Text(
                title,
                style = TextStyle(color = Widgets.color(Tok.charcoal), fontSize = 9.sp, fontWeight = FontWeight.Medium),
                // 單日行程用圓角（event-chip），上下左右相鄰的色塊才分得開；跨日行程直角，每天接起來像一條
                modifier = GlanceModifier.fillMaxWidth().height(slot)
                    .then(if (piece.multiDay) GlanceModifier else GlanceModifier.cornerRadius(Tok.rXs))
                    .background(Widgets.color(Tok.eventTint(inst.color))).padding(horizontal = 2.dp),
                maxLines = 1,
            )
        }
    }
}

/** 小工具太矮、放不下色塊時改用色點（WID-01）：最多三個，顏色跟行程一樣。 */
@Composable
private fun Dots(events: List<EventInstance>) {
    if (events.isEmpty()) return
    Row(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        events.take(3).forEach { e ->
            Text("●", style = TextStyle(color = Widgets.color(Tok.eventColor(e.color)), fontSize = 7.sp), maxLines = 1)
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(GlanceModifier.fillMaxSize().clickable(actionStartActivity(Widgets.openApp(LocalContext.current))), contentAlignment = Alignment.Center) {
        Text(text, style = TextStyle(color = Widgets.color(Tok.slate), fontSize = 13.sp, textAlign = TextAlign.Center))
    }
}
