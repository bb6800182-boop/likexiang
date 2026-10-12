package com.c520.likexiang.ui

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

object Fmt {
    private val locale: Locale = Locale.TRADITIONAL_CHINESE
    private val time = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val dateShort = DateTimeFormatter.ofPattern("M月d日", locale)
    private val dateWithDow = DateTimeFormatter.ofPattern("M月d日（E）", locale)
    private val dateLong = DateTimeFormatter.ofPattern("M月d日 EEEE", locale)
    private val dowShort = DateTimeFormatter.ofPattern("E", locale)
    private val weekday = DateTimeFormatter.ofPattern("EEEE", locale)

    fun time(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String = Instant.ofEpochMilli(millis).atZone(zone).format(time)
    fun time(t: LocalDateTime): String = t.format(time)
    fun dateShort(d: LocalDate): String = d.format(dateShort)
    fun dateWithDow(d: LocalDate): String = d.format(dateWithDow)
    fun dateLong(d: LocalDate): String = d.format(dateLong)

    /** 「星期日」。 */
    fun weekday(d: LocalDate): String = d.format(weekday)

    /** 「一」「二」…（中文星期縮寫的最後一個字）。 */
    fun dowChar(d: LocalDate): String = d.format(dowShort).takeLast(1)

    fun monthTitle(year: Int, month: Int, showYear: Boolean): String = if (showYear) "${year}年${month}月" else "${month}月"

    /** 「N 天」「N 週」檢視的標題只寫月份（「10月」「10–11月」），日期看格子；不是今年才加年份。 */
    fun monthSpan(first: LocalDate, last: LocalDate, today: LocalDate = LocalDate.now()): String = when {
        first.year != last.year -> "${first.year}年${first.monthValue}月–${last.year}年${last.monthValue}月"
        first.month == last.month -> monthTitle(first.year, first.monthValue, first.year != today.year)
        else -> (if (first.year != today.year) "${first.year}年" else "") + "${first.monthValue}–${last.monthValue}月"
    }

    /** 行程時間的完整描述，全天行程用 UTC 換日期。 */
    fun range(begin: Long, end: Long, allDay: Boolean, zone: ZoneId = ZoneId.systemDefault()): String {
        if (allDay) {
            val s = Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate()
            val e = Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate().minusDays(1)
            return if (!e.isAfter(s)) "${dateWithDow(s)} 整天" else "${dateWithDow(s)} – ${dateWithDow(e)}"
        }
        val s = Instant.ofEpochMilli(begin).atZone(zone)
        val e = Instant.ofEpochMilli(end).atZone(zone)
        return if (s.toLocalDate() == e.toLocalDate() || end <= begin) {
            "${dateWithDow(s.toLocalDate())} ${s.format(time)} – ${e.format(time)}"
        } else {
            "${dateWithDow(s.toLocalDate())} ${s.format(time)} – ${dateWithDow(e.toLocalDate())} ${e.format(time)}"
        }
    }

    /** 一段時間：「1 週」「2 天」「1 小時 30 分鐘」「45 分鐘」。 */
    fun duration(minutes: Int): String {
        if (minutes <= 0) return "0 分鐘"
        if (minutes % WEEK_MIN == 0) return "${minutes / WEEK_MIN} 週"
        val d = minutes / DAY_MIN
        val h = minutes % DAY_MIN / 60
        val m = minutes % 60
        return listOfNotNull(
            "$d 天".takeIf { d > 0 },
            "$h 小時".takeIf { h > 0 },
            "$m 分鐘".takeIf { m > 0 },
        ).joinToString(" ")
    }

    /** 一般通知：「開始時」「10 分鐘前」「1 天前」。 */
    fun reminderLabel(minutes: Int): String = if (minutes == 0) "開始時" else "${duration(minutes)}前"

    /** 鬧鐘提前時間：「準時響」「提前 10 分鐘」。 */
    fun leadLabel(minutes: Int): String = if (minutes == 0) "準時響" else "提前 ${duration(minutes)}"

    fun offsetChip(minutes: Int): String = when {
        minutes == 0 -> "準時"
        minutes % 60 == 0 -> "${minutes / 60} 小時"
        else -> "$minutes 分"
    }

    fun minuteOfDay(m: Int): String = "%02d:%02d".format(m / 60, m % 60)

    private const val DAY_MIN = 24 * 60
    private const val WEEK_MIN = 7 * DAY_MIN
}
