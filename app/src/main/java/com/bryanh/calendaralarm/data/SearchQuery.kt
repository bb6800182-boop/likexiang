package com.bryanh.calendaralarm.data

import kotlin.math.abs

/** 搜尋行程用的純邏輯：整理關鍵字、結果太多時只留離現在最近的。 */
object SearchQuery {
    /** 關鍵字最長幾個字（超過的截掉；查詢走系統日曆的搜尋網址，不讓網址無限長）。 */
    const val MAX_LENGTH = 100

    /** 一次最多列幾筆結果。 */
    const val MAX_RESULTS = 500

    /** 搜尋今天前後幾年的行程（重複行程要展開，範圍太大會慢）。 */
    const val YEARS = 1L

    private val SPACES = Regex("\\s+")

    /** 拿掉控制字元與頭尾空白、連續空白合成一個、最多 [MAX_LENGTH] 字；整理後沒有內容回傳 null。 */
    fun normalize(raw: String): String? {
        var s = raw.filterNot { it.isISOControl() }.trim().replace(SPACES, " ")
        if (s.length > MAX_LENGTH) {
            s = s.take(MAX_LENGTH)
            // 截在表情符號（兩個 char）中間時，把剩下的半個拿掉
            if (s.last().isHighSurrogate()) s = s.dropLast(1)
        }
        return s.trim().ifEmpty { null }
    }

    /** 結果超過 [max] 筆時，只留開始時間離 [now] 最近的 [max] 筆；回傳依開始時間排序。 */
    fun nearest(list: List<EventInstance>, now: Long, max: Int = MAX_RESULTS): List<EventInstance> {
        require(max >= 0)
        val kept = if (list.size <= max) list else list.sortedBy { abs(it.begin - now) }.take(max)
        return kept.sortedWith(compareBy({ it.begin }, { it.end }, { it.eventId }))
    }
}
