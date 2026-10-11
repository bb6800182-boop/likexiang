package com.bryanh.calendaralarm.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

// 這個檔案只放純 Kotlin 資料結構（不碰 Android API），方便在 JVM 上做單元測試。

data class CalendarInfo(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val ownerAccount: String,
    val color: Int,
    val writable: Boolean,
    /** 帳號的主要日曆（新增行程的預設值）。 */
    val isPrimary: Boolean = false,
    /** 在 Google 月曆 App 裡是否勾選顯示；只用來決定第一次使用時本 App 預設隱藏哪些日曆。 */
    val visible: Boolean = true,
    /** 帳號類型（Google 帳號是 com.google）；查帳號可用的行程顏色時要用。 */
    val accountType: String = "",
)

/** 帳號可用的一種行程顏色（CalendarContract.Colors）；key 寫進 Events.EVENT_COLOR_KEY，Google 會同步。 */
data class EventColor(val key: String, val color: Int)

/** 某一筆行程的某一次（重複行程每次各一筆）。title 是 Google 月曆上的原始標題，可能含 ⏰。 */
data class EventInstance(
    val eventId: Long,
    val begin: Long,
    val end: Long,
    val title: String,
    val allDay: Boolean,
    val calendarId: Long,
    val color: Int,
    val location: String,
    val selfStatus: Int,
    val status: Int,
    val recurring: Boolean,
    /** 重複行程被單獨改過的那一次是另一筆 Events，這裡記著它屬於哪個系列。 */
    val originalId: Long? = null,
) {
    val key: String get() = alarmKey(eventId, begin)
    val displayTitle: String get() = AlarmMarker.strip(title).ifBlank { "（無標題）" }

    /** 全天行程在資料庫裡是 UTC 午夜，要用 UTC 換日期；一般行程用手機時區。 */
    fun startDate(zone: ZoneId): LocalDate =
        if (allDay) Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate()
        else Instant.ofEpochMilli(begin).atZone(zone).toLocalDate()

    /** 最後一天（含當天）。 */
    fun endDate(zone: ZoneId): LocalDate {
        val start = startDate(zone)
        val last = if (allDay) {
            Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate().minusDays(1)
        } else {
            Instant.ofEpochMilli(maxOf(begin, end - 1)).atZone(zone).toLocalDate()
        }
        return if (last.isBefore(start)) start else last
    }
}

fun alarmKey(eventId: Long, begin: Long): String = "$eventId@$begin"

/** 單一行程的完整內容（詳情、編輯用）。 */
data class EventDetail(
    val id: Long,
    val calendarId: Long,
    val calendarName: String,
    val color: Int,
    val title: String,
    val description: String,
    val location: String,
    val dtStart: Long,
    val dtEnd: Long?,
    val duration: String?,
    val allDay: Boolean,
    val rrule: String?,
    val organizer: String?,
    val ownerAccount: String?,
    val calendarWritable: Boolean,
    val guestsCanModify: Boolean,
    val originalId: Long? = null,
    /** 行程自己的顏色（Colors 表的 key）；null 或空字串＝跟著日曆的顏色。 */
    val colorKey: String? = null,
) {
    val recurring: Boolean get() = !rrule.isNullOrBlank()

    /** 自己是不是主辦人（自己建立的行程沒有主辦人欄位，也算）。 */
    val isOrganizer: Boolean
        get() = organizer.isNullOrBlank() || organizer.equals(ownerAccount, ignoreCase = true)

    /** 自己能不能修改這筆行程（受邀、且主辦人沒開放與會者修改的不行，改了也會被 Google 蓋回去）。 */
    val canEditTitle: Boolean
        get() = calendarWritable && (isOrganizer || guestsCanModify)

    /** 只有主辦人能刪：受邀的行程在這裡刪掉等於幫所有人取消，交給 Google 月曆處理。 */
    val canDelete: Boolean
        get() = calendarWritable && isOrganizer

    val endMillis: Long
        get() = dtEnd ?: (dtStart + (Rfc5545.durationMillis(duration) ?: if (allDay) DAY_MS else HOUR_MS))
}

data class Attendee(val name: String, val email: String, val status: Int)

/** 新增或修改行程時送進 Google 月曆的內容。全天行程的 end 是最後一天的隔天 00:00（不含）。 */
data class EventDraft(
    val calendarId: Long,
    val title: String,
    val description: String,
    val location: String,
    val allDay: Boolean,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val rrule: String?,
    /** 行程顏色（Colors 表的 key）；null＝跟著日曆的顏色。 */
    val colorKey: String? = null,
)

/** 月曆小工具顯示的範圍：整個月、自訂天數（一列）、自訂週數（從這週開始）。 */
enum class WidgetRange { MONTH, DAYS, WEEKS }

data class Settings(
    val defaultOffsetMin: Int = 10,
    val ringMinutes: Int = 5,
    /** 稍後提醒（響鈴時按了多久後再響）。 */
    val snoozeMin: Int = 5,
    /** 一天從幾點開始（分鐘）：日檢視與多日檢視從這裡開始顯示，整天行程的鬧鐘也在這個時間響。 */
    val dayStartMinute: Int = 6 * 60,
    val vibrate: Boolean = true,
    val ringtoneUri: String? = null,
    val weekStartsMonday: Boolean = true,
    val showLunar: Boolean = true,
    val hiddenCalendarIds: Set<Long> = emptySet(),
    /** 新增行程的預設日曆；null 或已不能寫入時用帳號的主要日曆。 */
    val defaultCalendarId: Long? = null,
    val defaultDurationMin: Int = 60,
    /** 新增行程的預設一般通知（分鐘）；null＝不要。 */
    val defaultReminderMin: Int? = 10,
    val wizardDone: Boolean = false,
    /** 自訂檢視：「N 天」時間格的天數、「N 週」月曆格的週數。 */
    val customDays: Int = 3,
    val customWeeks: Int = 2,
    /** 月曆小工具顯示的範圍。 */
    val widgetRange: WidgetRange = WidgetRange.MONTH,
) {
    companion object {
        val SNOOZE_OPTIONS = listOf(5, 10, 15)
        val OFFSET_OPTIONS = listOf(0, 5, 10, 15, 30, 60)

        /** 鬧鐘最多提前 24 小時（到點時只往後查兩天的行程）。 */
        const val MAX_OFFSET_MIN = 24 * 60
        val RING_MINUTE_OPTIONS = listOf(1, 3, 5, 10)
        val DURATION_OPTIONS = listOf(30, 60, 90, 120)

        /** 一般通知可選的提前分鐘數（1440＝一天前）。 */
        val REMINDER_OPTIONS = listOf(0, 5, 10, 30, 60, 1440)
        val CUSTOM_DAYS = 2..7
        val CUSTOM_WEEKS = 1..6
    }
}

/**
 * 新增行程時預設放進哪個日曆：設定裡指定的（還能寫入的話）→ 帳號的主要日曆 → 第一個沒被隱藏的可寫入日曆。
 * 避免預設放進被隱藏的日曆，存完卻在畫面上看不到。
 */
fun defaultCalendarFor(calendars: List<CalendarInfo>, settings: Settings): CalendarInfo? {
    val writable = calendars.filter { it.writable }
    val shown = writable.filter { it.id !in settings.hiddenCalendarIds }
    return writable.firstOrNull { it.id == settings.defaultCalendarId }
        ?: shown.firstOrNull { it.isPrimary }
        ?: shown.firstOrNull()
        ?: writable.firstOrNull()
}

/** 只存在手機上的鬧鐘狀態。 */
data class AlarmState(
    /** 每筆行程的提前分鐘數；沒有就用預設值。 */
    val offsets: Map<Long, Int>,
    /** 只記在手機的鬧鐘開關（有其他與會者或唯讀的行程）。true 開、false 明確關閉。 */
    val localFlags: Map<Long, Boolean>,
    /** 單次略過，key = eventId@begin。 */
    val skips: Set<String>,
    /** 已經響過的，避免重排時又響一次。 */
    val fired: Set<String>,
    /** 稍後提醒中：key → 再響的時間。 */
    val snoozes: Map<String, Long>,
) {
    /** 重複行程被單獨改過的那一次沒有自己的設定時，沿用整個系列的。 */
    fun flagFor(eventId: Long, originalId: Long?): Boolean? = localFlags[eventId] ?: originalId?.let { localFlags[it] }

    fun offsetFor(eventId: Long, originalId: Long?): Int? = offsets[eventId] ?: originalId?.let { offsets[it] }

    companion object {
        val EMPTY = AlarmState(emptyMap(), emptyMap(), emptySet(), emptySet(), emptyMap())
    }
}

const val MINUTE_MS = 60_000L
const val HOUR_MS = 60 * MINUTE_MS
const val DAY_MS = 24 * HOUR_MS
