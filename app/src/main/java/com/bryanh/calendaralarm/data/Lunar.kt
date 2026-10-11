package com.bryanh.calendaralarm.data

import android.icu.util.Calendar as IcuCalendar
import android.icu.util.ChineseCalendar
import android.icu.util.TimeZone
import java.time.LocalDate
import java.time.ZoneId

/** 農曆：用 Android 內建的 ICU 農曆曆法（以 UTC+8 計算），不需另加套件。 */
object Lunar {
    data class Date(val cycleYear: Int, val month: Int, val leap: Boolean, val day: Int)

    private val DAYS = arrayOf(
        "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
        "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
        "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十",
    )
    private val MONTHS = arrayOf("正月", "二月", "三月", "四月", "五月", "六月", "七月", "八月", "九月", "十月", "十一月", "十二月")
    private const val STEMS = "甲乙丙丁戊己庚辛壬癸"
    private const val BRANCHES = "子丑寅卯辰巳午未申酉戌亥"
    private val ZONE: ZoneId = ZoneId.of("Asia/Taipei")
    private val cache = object : LinkedHashMap<LocalDate, Date>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<LocalDate, Date>?) = size > 400
    }

    fun of(date: LocalDate): Date = synchronized(cache) {
        cache.getOrPut(date) {
            val cal = ChineseCalendar(TimeZone.getTimeZone("Asia/Taipei"))
            cal.timeInMillis = date.atTime(12, 0).atZone(ZONE).toInstant().toEpochMilli()
            Date(
                cycleYear = cal.get(IcuCalendar.YEAR),
                month = cal.get(IcuCalendar.MONTH) + 1,
                leap = cal.get(IcuCalendar.IS_LEAP_MONTH) == 1,
                day = cal.get(IcuCalendar.DAY_OF_MONTH),
            )
        }
    }

    /** 月曆格用：初一、十五、廿三… */
    fun dayLabel(date: LocalDate): String = DAYS[(of(date).day - 1).coerceIn(0, 29)]

    fun monthLabel(d: Date): String = (if (d.leap) "閏" else "") + MONTHS[(d.month - 1).coerceIn(0, 11)]

    fun yearLabel(d: Date): String {
        val i = (d.cycleYear - 1).mod(60)
        return "${STEMS[i % 10]}${BRANCHES[i % 12]}年"
    }

    /** 詳情與當天畫面用：農曆九月初三 */
    fun fullLabel(date: LocalDate): String = of(date).let { "農曆${monthLabel(it)}${DAYS[(it.day - 1).coerceIn(0, 29)]}" }

    /** 月曆標題用：丙午年 八月－九月。空白是字放大時可以換行的地方（見 TitleBlock）。 */
    fun rangeLabel(first: LocalDate, last: LocalDate): String {
        val a = of(first)
        val b = of(last)
        return when {
            a.cycleYear != b.cycleYear -> "${yearLabel(a)}${monthLabel(a)}－ ${yearLabel(b)}${monthLabel(b)}"
            a.month == b.month && a.leap == b.leap -> "${yearLabel(a)} ${monthLabel(a)}"
            else -> "${yearLabel(a)} ${monthLabel(a)}－${monthLabel(b)}"
        }
    }
}
