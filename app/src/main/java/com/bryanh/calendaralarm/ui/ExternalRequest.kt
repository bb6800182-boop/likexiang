package com.bryanh.calendaralarm.ui

import com.bryanh.calendaralarm.data.AlarmMarker
import com.bryanh.calendaralarm.data.CalendarRepository
import com.bryanh.calendaralarm.data.DAY_MS
import com.bryanh.calendaralarm.data.Repeat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** 其他 App「加到月曆」送來的內容，已檢查過，可直接預填到新增畫面（使用者按儲存才會寫入）。 */
data class Prefill(
    val title: String,
    val description: String,
    val location: String,
    val allDay: Boolean,
    val start: LocalDateTime?,
    /** 一般行程是結束時間；全天行程是最後一天（含當天）的 00:00。 */
    val end: LocalDateTime?,
    val repeat: Repeat,
    val alarmOn: Boolean,
    /** 重複規則太複雜（間隔、次數、截止日…），新增畫面表示不出來，所以沒套用。 */
    val droppedRepeat: Boolean,
)

/**
 * 其他 App 的月曆請求（EVT-05）：加到月曆、開某個行程、開某一天。
 * 這裡只做檢查與轉換，是純邏輯；不合理的參數一律丟掉，不會因為外部輸入而寫入任何資料。
 */
sealed interface ExternalRequest {
    data class Insert(val prefill: Prefill) : ExternalRequest
    data class ViewEvent(val eventId: Long, val begin: Long) : ExternalRequest
    data class ViewDay(val date: LocalDate) : ExternalRequest

    companion object {
        // 時間參數的合理範圍：1900～2200 年
        private val MIN_MILLIS = LocalDate.of(1900, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        private val MAX_MILLIS = LocalDate.of(2200, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        private const val MAX_RRULE = 500

        fun insert(
            title: String?,
            description: String?,
            location: String?,
            begin: Long?,
            end: Long?,
            allDay: Boolean,
            rrule: String?,
            zone: ZoneId,
        ): Insert {
            val rawTitle = singleLine(title, CalendarRepository.MAX_TITLE)
            val b = begin?.takeIf { it in MIN_MILLIS..MAX_MILLIS }
            val e = end?.takeIf { it in MIN_MILLIS..MAX_MILLIS && b != null && it > b }
            val start: LocalDateTime?
            val endTime: LocalDateTime?
            if (allDay && b != null) {
                val first = allDayDate(b, zone)
                // 結束是「隔天 00:00」（不含），換成最後一天；不合理就當成單日
                val last = e?.let { allDayDate(it, zone).minusDays(1) }?.takeIf { !it.isBefore(first) } ?: first
                start = first.atStartOfDay()
                endTime = last.atStartOfDay()
            } else {
                start = b?.let { local(it, zone) }
                endTime = e?.let { local(it, zone) }
            }
            val rule = rrule?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_RRULE }
            val repeat = Repeat.of(rule)
            return Insert(
                Prefill(
                    title = AlarmMarker.strip(rawTitle),
                    description = multiLine(description, CalendarRepository.MAX_DESCRIPTION),
                    location = singleLine(location, CalendarRepository.MAX_LOCATION),
                    allDay = allDay && b != null,
                    start = start,
                    end = endTime,
                    repeat = repeat ?: Repeat.NONE,
                    alarmOn = AlarmMarker.has(rawTitle),
                    droppedRepeat = rrule != null && rrule.isNotBlank() && repeat == null,
                ),
            )
        }

        fun viewEvent(id: Long?, begin: Long?): ViewEvent? =
            if (id == null || id <= 0) null else ViewEvent(id, begin?.takeIf { it in MIN_MILLIS..MAX_MILLIS } ?: 0L)

        fun viewDay(millis: Long?, zone: ZoneId): ViewDay? =
            millis?.takeIf { it in MIN_MILLIS..MAX_MILLIS }?.let { ViewDay(Instant.ofEpochMilli(it).atZone(zone).toLocalDate()) }

        /** 全天行程照 CalendarContract 慣例是 UTC 午夜；也有 App 給當地午夜，兩種都要對。 */
        private fun allDayDate(millis: Long, zone: ZoneId): LocalDate =
            Instant.ofEpochMilli(millis).atZone(if (millis % DAY_MS == 0L) ZoneOffset.UTC else zone).toLocalDate()

        private fun local(millis: Long, zone: ZoneId): LocalDateTime =
            Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime().withSecond(0).withNano(0)

        /** 去掉控制字元與前後空白；單行欄位把換行換成空白。 */
        private fun singleLine(s: String?, max: Int): String =
            s.orEmpty().map { if (it == '\n' || it == '\r' || it == '\t') ' ' else it }.filter { !it.isISOControl() }
                .joinToString("").trim().take(max)

        private fun multiLine(s: String?, max: Int): String =
            s.orEmpty().replace("\r\n", "\n").filter { it == '\n' || !it.isISOControl() }.trim().take(max)
    }
}
