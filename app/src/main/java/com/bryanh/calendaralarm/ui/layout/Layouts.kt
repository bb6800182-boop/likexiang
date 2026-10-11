package com.bryanh.calendaralarm.ui.layout

import com.bryanh.calendaralarm.data.EventInstance
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// 純邏輯的版面計算，可在 JVM 上測試。

/**
 * 月曆格、「N 天」檢視與月曆小工具共用：從 weekFirst 起 [days] 天裡，每筆行程從第幾欄開始、跨幾欄。
 * id＝在 instances 裡的位置；全天在前，再依開始時間。
 */
fun weekSpans(instances: List<EventInstance>, weekFirst: LocalDate, zone: ZoneId, days: Int = 7): List<WeekSpan> {
    require(days in 1..7)
    val last = weekFirst.plusDays(days - 1L)
    return instances.mapIndexedNotNull { i, inst ->
        val s = inst.startDate(zone)
        val e = inst.endDate(zone)
        if (e.isBefore(weekFirst) || s.isAfter(last)) return@mapIndexedNotNull null
        val startCol = ChronoUnit.DAYS.between(weekFirst, maxOf(s, weekFirst)).toInt()
        val endCol = ChronoUnit.DAYS.between(weekFirst, minOf(e, last)).toInt()
        WeekSpan(i, startCol, endCol - startCol + 1, (if (inst.allDay) 0L else 1L shl 50) + inst.begin)
    }
}

/** 一週內的一條行程色塊：從第 startCol 欄開始，跨 span 欄（0 = 一週第一天）。 */
data class WeekSpan(val id: Int, val startCol: Int, val span: Int, val sortKey: Long)

data class PlacedSpan(val id: Int, val startCol: Int, val span: Int, val lane: Int)

/** placed：要畫的色塊；more：某欄（key）放不下時顯示的「+N」。 */
data class WeekLayout(val placed: List<PlacedSpan>, val more: Map<Int, Int>)

object WeekLayoutEngine {
    /**
     * 把色塊排進最多 maxLanes 條車道：跨日的先排、同一天依排序鍵（全天在前、再依時間）。
     * 某天行程數超過 maxLanes 時，最後一條車道改顯示「+N」。
     */
    fun layout(spans: List<WeekSpan>, maxLanes: Int): WeekLayout {
        require(maxLanes >= 1)
        val sorted = spans.filter { it.span >= 1 && it.startCol in 0..6 }
            .map { it.copy(span = minOf(it.span, 7 - it.startCol)) }
            .sortedWith(compareBy<WeekSpan>({ -it.span }, { it.startCol }, { it.sortKey }, { it.id }))
        val lanes = ArrayList<BooleanArray>()
        val assigned = ArrayList<PlacedSpan>()
        for (s in sorted) {
            val cols = s.startCol until s.startCol + s.span
            var lane = lanes.indexOfFirst { occ -> cols.none { occ[it] } }
            if (lane < 0) {
                lanes += BooleanArray(7)
                lane = lanes.lastIndex
            }
            cols.forEach { lanes[lane][it] = true }
            assigned += PlacedSpan(s.id, s.startCol, s.span, lane)
        }
        val count = perColumn(assigned)
        // 有藏起來的行程的欄位，最後一條車道讓給「+N」；藏掉跨進這些欄的色塊後可能又多出新的，所以重複到穩定
        val overflow = HashSet<Int>()
        while (true) {
            val placed = assigned.filter { p ->
                p.lane < maxLanes - 1 || (p.lane == maxLanes - 1 && (p.startCol until p.startCol + p.span).none { it in overflow })
            }
            val visible = perColumn(placed)
            val hidden = (0..6).filter { count[it] > visible[it] }
            if (overflow.containsAll(hidden)) {
                return WeekLayout(placed, hidden.associateWith { c -> count[c] - visible[c] })
            }
            overflow += hidden
        }
    }

    private fun perColumn(spans: List<PlacedSpan>): IntArray =
        IntArray(7).also { c -> spans.forEach { p -> for (col in p.startCol until p.startCol + p.span) c[col]++ } }
}

/** 當天時間軸上的一筆行程（分鐘，從當天 00:00 起算）。 */
data class DayItem(val id: Int, val startMin: Int, val endMin: Int)

/** column / columns：同一群重疊行程裡排第幾欄、共幾欄。 */
data class DayPlaced(val id: Int, val startMin: Int, val endMin: Int, val column: Int, val columns: Int)

object DayLayoutEngine {
    private const val MIN_DURATION = 20

    /**
     * 重疊的行程並排：同一群互相重疊的行程平分寬度。
     * 很短的行程至少佔 [minDuration] 分鐘高（畫面依字級傳入放得下一行標題的長度），重疊也照這個高度算，標題才不會被下一筆蓋住。
     */
    fun layout(items: List<DayItem>, minDuration: Int = MIN_DURATION): List<DayPlaced> {
        val sorted = items.map { it.copy(endMin = maxOf(it.endMin, it.startMin + minDuration)) }
            .sortedWith(compareBy<DayItem>({ it.startMin }, { -it.endMin }, { it.id }))
        val out = ArrayList<DayPlaced>()
        var group = ArrayList<DayItem>()
        var groupEnd = -1
        fun flush() {
            if (group.isEmpty()) return
            val colEnds = ArrayList<Int>()
            val cols = HashMap<Int, Int>()
            for (item in group) {
                var c = colEnds.indexOfFirst { it <= item.startMin }
                if (c < 0) {
                    colEnds += item.endMin
                    c = colEnds.lastIndex
                } else {
                    colEnds[c] = item.endMin
                }
                cols[item.id] = c
            }
            group.forEach { out += DayPlaced(it.id, it.startMin, it.endMin, cols.getValue(it.id), colEnds.size) }
            group = ArrayList()
        }
        for (item in sorted) {
            if (item.startMin >= groupEnd) {
                flush()
                groupEnd = item.endMin
            } else {
                groupEnd = maxOf(groupEnd, item.endMin)
            }
            group += item
        }
        flush()
        return out
    }
}
