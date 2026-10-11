package com.bryanh.calendaralarm.data

import java.time.DayOfWeek
import java.time.LocalDate

/** RFC 5545 的 DURATION 與簡單 RRULE 處理。複雜的重複規則一律交給 Google 月曆。 */
object Rfc5545 {
    private val standard = Regex("""^[+]?P(?:(\d+)W)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?$""")
    private val secondsOnly = Regex("""^[+]?P(\d+)S$""")

    /** 支援 P1D、PT1H30M、P1W，以及 Android 常見的 P3600S。 */
    fun durationMillis(value: String?): Long? {
        val s = value?.trim().orEmpty()
        if (s.isEmpty() || s == "P" || s == "PT") return null
        secondsOnly.matchEntire(s)?.let { return it.groupValues[1].toLong() * 1000 }
        val m = standard.matchEntire(s) ?: return null
        fun g(i: Int) = m.groupValues[i].toLongOrNull() ?: 0L
        return g(1) * 7 * DAY_MS + g(2) * DAY_MS + g(3) * HOUR_MS + g(4) * MINUTE_MS + g(5) * 1000
    }

    fun durationString(millis: Long, allDay: Boolean): String =
        if (allDay) "P${maxOf(1L, millis / DAY_MS)}D" else "P${maxOf(60L, millis / 1000)}S"
}

enum class Repeat(val label: String) {
    NONE("不重複"), DAILY("每天"), WEEKLY("每週"), MONTHLY("每月"), YEARLY("每年");

    fun toRrule(start: LocalDate): String? = when (this) {
        NONE -> null
        DAILY -> "FREQ=DAILY"
        WEEKLY -> "FREQ=WEEKLY;BYDAY=${byDay(start.dayOfWeek)}"
        MONTHLY -> "FREQ=MONTHLY;BYMONTHDAY=${start.dayOfMonth}"
        YEARLY -> "FREQ=YEARLY"
    }

    companion object {
        /** 能用簡單選項表示就回傳對應值；複雜規則（間隔、次數、截止日、多個星期）回傳 null。 */
        fun of(rrule: String?): Repeat? {
            if (rrule.isNullOrBlank()) return NONE
            val parts = rrule.split(';').mapNotNull {
                val i = it.indexOf('=')
                if (i > 0) it.substring(0, i).uppercase() to it.substring(i + 1).uppercase() else null
            }.toMap()
            val extra = parts.keys - setOf("FREQ", "WKST", "BYDAY", "BYMONTHDAY", "INTERVAL")
            if (extra.isNotEmpty()) return null
            if ((parts["INTERVAL"] ?: "1") != "1") return null
            val byDay = parts["BYDAY"]
            val byMonthDay = parts["BYMONTHDAY"]
            return when (parts["FREQ"]) {
                "DAILY" -> if (byDay == null && byMonthDay == null) DAILY else null
                "WEEKLY" -> if ((byDay == null || !byDay.contains(',')) && byMonthDay == null) WEEKLY else null
                "MONTHLY" -> if (byDay == null && (byMonthDay == null || !byMonthDay.contains(','))) MONTHLY else null
                "YEARLY" -> if (byDay == null && byMonthDay == null) YEARLY else null
                else -> null
            }
        }

        private fun byDay(d: DayOfWeek) = when (d) {
            DayOfWeek.MONDAY -> "MO"
            DayOfWeek.TUESDAY -> "TU"
            DayOfWeek.WEDNESDAY -> "WE"
            DayOfWeek.THURSDAY -> "TH"
            DayOfWeek.FRIDAY -> "FR"
            DayOfWeek.SATURDAY -> "SA"
            DayOfWeek.SUNDAY -> "SU"
        }
    }
}
