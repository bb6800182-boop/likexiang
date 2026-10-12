package com.c520.likexiang.data

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Colors
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import android.provider.CalendarContract.Reminders
import android.util.Log
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * 讀寫手機上的系統日曆資料庫（CalendarContract）。Google 帳號的同步由系統負責，
 * 所以這裡寫入的行程會自動出現在電腦版 Google 月曆，不需要 Google API 或網路權限。
 * 所有查詢都用 selectionArgs 帶參數，不拼接字串；修改一律用 Events/<id> 指定單筆。
 */
class CalendarRepository(private val context: Context) {

    private val cr get() = context.contentResolver

    fun hasReadPermission(): Boolean = granted(Manifest.permission.READ_CALENDAR)
    fun hasWritePermission(): Boolean = granted(Manifest.permission.WRITE_CALENDAR)

    private fun granted(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    fun calendars(): List<CalendarInfo> {
        if (!hasReadPermission()) return emptyList()
        val proj = arrayOf(
            Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME,
            Calendars.OWNER_ACCOUNT, Calendars.CALENDAR_COLOR, Calendars.CALENDAR_ACCESS_LEVEL,
            Calendars.IS_PRIMARY, Calendars.VISIBLE, Calendars.ACCOUNT_TYPE,
        )
        return query(Calendars.CONTENT_URI, proj, "${Calendars.SYNC_EVENTS}=?", arrayOf("1"), "${Calendars.ACCOUNT_NAME}, ${Calendars.CALENDAR_DISPLAY_NAME}") { c ->
            val account = c.getString(2).orEmpty()
            val owner = c.getString(3).orEmpty()
            CalendarInfo(
                id = c.getLong(0),
                displayName = c.getString(1).orEmpty(),
                accountName = account,
                ownerAccount = owner,
                color = c.getInt(4),
                writable = c.getInt(5) >= Calendars.CAL_ACCESS_CONTRIBUTOR,
                // 有些帳號不填 IS_PRIMARY：擁有者就是帳號本人的日曆也算主要日曆
                isPrimary = (!c.isNull(6) && c.getInt(6) == 1) || (owner.isNotEmpty() && owner.equals(account, ignoreCase = true)),
                visible = c.isNull(7) || c.getInt(7) == 1,
                accountType = c.getString(8).orEmpty(),
            )
        }
    }

    /** 某段時間內的所有行程（重複行程已展開）。顯示哪些日曆由呼叫端依本 App 的設定過濾。 */
    fun instances(from: Long, to: Long): List<EventInstance> = instancesOrNull(from, to).orEmpty()

    /** 同上，但查詢失敗時回傳 null，讓鬧鐘排程能分辨「沒有行程」和「讀不到」。 */
    fun instancesOrNull(from: Long, to: Long): List<EventInstance>? {
        if (!hasReadPermission()) return null
        if (to <= from) return emptyList()
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
        }.build()
        return queryOrNull(uri, INSTANCE_PROJECTION, null, null, "${Instances.BEGIN} ASC", ::instanceOf)
    }

    /**
     * 搜尋某段時間內的行程（重複行程已展開）：用系統日曆的搜尋網址，比對標題、說明、地點（也會比對與會者）；
     * 關鍵字用空白分開時每個都要符合。關鍵字先用 [SearchQuery.normalize] 整理，放進網址時由 appendPath 編碼。
     * 讀不到回傳 null；顯示哪些日曆由呼叫端過濾。
     */
    fun searchInstances(query: String, from: Long, to: Long): List<EventInstance>? {
        if (!hasReadPermission()) return null
        val q = SearchQuery.normalize(query) ?: return emptyList()
        if (to <= from) return emptyList()
        val uri = Instances.CONTENT_SEARCH_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
            it.appendPath(q)
        }.build()
        return queryOrNull(uri, INSTANCE_PROJECTION, null, null, "${Instances.BEGIN} ASC", ::instanceOf)
    }

    private fun instanceOf(c: Cursor) = EventInstance(
        eventId = c.getLong(0),
        begin = c.getLong(1),
        end = c.getLong(2),
        title = c.getString(3).orEmpty(),
        allDay = c.getInt(4) == 1,
        calendarId = c.getLong(5),
        color = c.getInt(6),
        location = c.getString(7).orEmpty(),
        selfStatus = if (c.isNull(8)) Attendees.ATTENDEE_STATUS_NONE else c.getInt(8),
        status = if (c.isNull(9)) Events.STATUS_TENTATIVE else c.getInt(9),
        recurring = !c.getString(10).isNullOrBlank(),
        originalId = if (c.isNull(11)) null else c.getLong(11),
    )

    fun eventDetail(id: Long): EventDetail? {
        if (!hasReadPermission() || id <= 0) return null
        val proj = arrayOf(
            Events._ID, Events.CALENDAR_ID, Events.CALENDAR_DISPLAY_NAME, Events.DISPLAY_COLOR,
            Events.TITLE, Events.DESCRIPTION, Events.EVENT_LOCATION, Events.DTSTART, Events.DTEND,
            Events.DURATION, Events.ALL_DAY, Events.RRULE, Events.ORGANIZER, Events.OWNER_ACCOUNT,
            Events.CALENDAR_ACCESS_LEVEL, Events.GUESTS_CAN_MODIFY, Events.ORIGINAL_ID, Events.EVENT_COLOR_KEY,
        )
        return query(ContentUris.withAppendedId(Events.CONTENT_URI, id), proj, null, null, null) { c ->
            EventDetail(
                id = c.getLong(0),
                calendarId = c.getLong(1),
                calendarName = c.getString(2).orEmpty(),
                color = c.getInt(3),
                title = c.getString(4).orEmpty(),
                description = c.getString(5).orEmpty(),
                location = c.getString(6).orEmpty(),
                dtStart = c.getLong(7),
                dtEnd = if (c.isNull(8)) null else c.getLong(8),
                duration = c.getString(9),
                allDay = c.getInt(10) == 1,
                rrule = c.getString(11),
                organizer = c.getString(12),
                ownerAccount = c.getString(13),
                calendarWritable = c.getInt(14) >= Calendars.CAL_ACCESS_CONTRIBUTOR,
                guestsCanModify = c.getInt(15) == 1,
                originalId = if (c.isNull(16)) null else c.getLong(16),
                colorKey = c.getString(17)?.takeIf { it.isNotEmpty() },
            )
        }.firstOrNull()
    }

    fun attendees(eventId: Long): List<Attendee> {
        if (!hasReadPermission()) return emptyList()
        val proj = arrayOf(Attendees.ATTENDEE_NAME, Attendees.ATTENDEE_EMAIL, Attendees.ATTENDEE_STATUS)
        return query(Attendees.CONTENT_URI, proj, "${Attendees.EVENT_ID}=?", arrayOf(eventId.toString()), null) { c ->
            Attendee(c.getString(0).orEmpty(), c.getString(1).orEmpty(), if (c.isNull(2)) Attendees.ATTENDEE_STATUS_NONE else c.getInt(2))
        }
    }

    /** 一般通知（彈出通知）的提前分鐘數。 */
    fun alertReminders(eventId: Long): List<Int> {
        if (!hasReadPermission()) return emptyList()
        val proj = arrayOf(Reminders.MINUTES, Reminders.METHOD)
        return query(Reminders.CONTENT_URI, proj, "${Reminders.EVENT_ID}=?", arrayOf(eventId.toString()), "${Reminders.MINUTES} ASC") { c ->
            if (c.getInt(1) == Reminders.METHOD_ALERT || c.getInt(1) == Reminders.METHOD_DEFAULT) c.getInt(0) else null
        }.filterNotNull().distinct()
    }

    /** 這個帳號可用的行程顏色；沒有（例如手機本機的日曆）就是空清單，畫面不顯示顏色選項。 */
    fun eventColors(accountName: String, accountType: String): List<EventColor> {
        if (!hasReadPermission() || accountName.isEmpty()) return emptyList()
        return query(
            Colors.CONTENT_URI,
            arrayOf(Colors.COLOR_KEY, Colors.COLOR),
            "${Colors.ACCOUNT_NAME}=? AND ${Colors.ACCOUNT_TYPE}=? AND ${Colors.COLOR_TYPE}=?",
            arrayOf(accountName, accountType, Colors.TYPE_EVENT.toString()),
            null,
        ) { c -> c.getString(0)?.let { EventColor(it, c.getInt(1)) } }.filterNotNull()
    }

    /**
     * 以前行程用過的地點（新的在前、不重複），新增行程時當建議。query 是部分文字；
     * LIKE 的萬用字元先跳脫，參數一律走 selectionArgs。最多看 [MAX_LOCATION_SCAN] 筆，不拖慢輸入。
     */
    fun recentLocations(query: String, limit: Int): List<String> {
        if (!hasReadPermission() || limit <= 0) return emptyList()
        val q = query.trim().take(MAX_LOCATION)
        var selection = "${Events.EVENT_LOCATION}!='' AND ${Events.DELETED}=0"
        val args = ArrayList<String>()
        if (q.isNotEmpty()) {
            selection += " AND ${Events.EVENT_LOCATION} LIKE ? ESCAPE '\\'"
            args += "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        }
        return try {
            cr.query(Events.CONTENT_URI, arrayOf(Events.EVENT_LOCATION), selection, args.toTypedArray(), "${Events.DTSTART} DESC")?.use { c ->
                val out = LinkedHashSet<String>()
                var scanned = 0
                while (out.size < limit && scanned < MAX_LOCATION_SCAN && c.moveToNext()) {
                    scanned++
                    c.getString(0)?.trim()?.takeIf { it.isNotEmpty() && !it.equals(q, ignoreCase = true) }?.let { out += it }
                }
                out.toList()
            }.orEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "recentLocations failed", e)
            emptyList()
        }
    }

    /** 新增行程，回傳新的 eventId；失敗回傳 null。 */
    fun insertEvent(draft: EventDraft): Long? {
        if (!hasWritePermission()) return null
        val values = draft.timeValues().apply {
            put(Events.CALENDAR_ID, draft.calendarId)
            put(Events.TITLE, draft.title.take(MAX_TITLE))
            put(Events.DESCRIPTION, draft.description.take(MAX_DESCRIPTION))
            put(Events.EVENT_LOCATION, draft.location.take(MAX_LOCATION))
            draft.colorKey?.takeIf { it.isNotEmpty() }?.let { put(Events.EVENT_COLOR_KEY, it) }
        }
        return try {
            cr.insert(Events.CONTENT_URI, values)?.let { ContentUris.parseId(it) }?.takeIf { it > 0 }
        } catch (e: Exception) {
            Log.w(TAG, "insertEvent failed", e)
            null
        }
    }

    /**
     * 只寫入跟原本不一樣的欄位：沒動到的說明、時區、重複規則原封不動，
     * 也不會蓋掉表單開著時電腦上改的其他欄位。original 要用跟 draft 相同的方式從原行程算出來。
     */
    fun updateEvent(id: Long, draft: EventDraft, original: EventDraft): Boolean {
        val values = ContentValues()
        if (draft.title != original.title) values.put(Events.TITLE, draft.title.take(MAX_TITLE))
        if (draft.description != original.description) values.put(Events.DESCRIPTION, draft.description.take(MAX_DESCRIPTION))
        if (draft.location != original.location) values.put(Events.EVENT_LOCATION, draft.location.take(MAX_LOCATION))
        if (draft.colorKey.orEmpty() != original.colorKey.orEmpty()) {
            // 空字串＝回到日曆的顏色（跟 AOSP 月曆一樣），顏色值也一併清掉
            val key = draft.colorKey.orEmpty()
            values.put(Events.EVENT_COLOR_KEY, key)
            if (key.isEmpty()) values.putNull(Events.EVENT_COLOR)
        }
        val timesChanged = draft.allDay != original.allDay || draft.start != original.start || draft.end != original.end
        if (timesChanged || draft.rrule != original.rrule) values.putAll(draft.timeValues())
        if (values.size() == 0) return true
        return update(id, values)
    }

    fun updateTitle(id: Long, title: String): Boolean = update(id, ContentValues().apply { put(Events.TITLE, title.take(MAX_TITLE)) })

    /** 刪除整筆行程（重複行程會刪掉整個系列）。 */
    fun deleteEvent(id: Long): Boolean {
        if (!hasWritePermission() || id <= 0) return false
        return try {
            cr.delete(ContentUris.withAppendedId(Events.CONTENT_URI, id), null, null) > 0
        } catch (e: Exception) {
            Log.w(TAG, "deleteEvent failed", e)
            false
        }
    }

    /** 把一般通知換成指定的分鐘數（空清單＝不要一般通知）。刪除與新增在同一個交易裡，不會只做一半。 */
    fun replaceAlertReminders(eventId: Long, minutes: List<Int>): Boolean {
        if (!hasWritePermission() || eventId <= 0) return false
        val ops = ArrayList<ContentProviderOperation>()
        ops += ContentProviderOperation.newDelete(Reminders.CONTENT_URI)
            .withSelection(
                "${Reminders.EVENT_ID}=? AND (${Reminders.METHOD}=? OR ${Reminders.METHOD}=?)",
                arrayOf(eventId.toString(), Reminders.METHOD_ALERT.toString(), Reminders.METHOD_DEFAULT.toString()),
            )
            .build()
        minutes.map { it.coerceIn(0, MAX_REMINDER_MIN) }.distinct().forEach { m ->
            ops += ContentProviderOperation.newInsert(Reminders.CONTENT_URI)
                .withValue(Reminders.EVENT_ID, eventId)
                .withValue(Reminders.MINUTES, m)
                .withValue(Reminders.METHOD, Reminders.METHOD_ALERT)
                .build()
        }
        return try {
            cr.applyBatch(CalendarContract.AUTHORITY, ops)
            true
        } catch (e: Exception) {
            Log.w(TAG, "replaceAlertReminders failed", e)
            false
        }
    }

    private fun update(id: Long, values: ContentValues): Boolean {
        if (!hasWritePermission() || id <= 0) return false
        return try {
            cr.update(ContentUris.withAppendedId(Events.CONTENT_URI, id), values, null, null) > 0
        } catch (e: Exception) {
            Log.w(TAG, "updateEvent failed", e)
            false
        }
    }

    /** 時間相關欄位。全天行程存 UTC 午夜；重複行程用 DURATION、不能有 DTEND（CalendarContract 規定）。 */
    private fun EventDraft.timeValues(): ContentValues {
        val zone = ZoneId.systemDefault()
        val dtStart: Long
        val dtEnd: Long
        val tz: String
        if (allDay) {
            dtStart = start.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            dtEnd = end.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            tz = "UTC"
        } else {
            dtStart = start.atZone(zone).toInstant().toEpochMilli()
            dtEnd = end.atZone(zone).toInstant().toEpochMilli()
            tz = zone.id
        }
        return ContentValues().apply {
            put(Events.ALL_DAY, if (allDay) 1 else 0)
            put(Events.DTSTART, dtStart)
            put(Events.EVENT_TIMEZONE, tz)
            if (rrule.isNullOrBlank()) {
                put(Events.DTEND, dtEnd)
                putNull(Events.RRULE)
                putNull(Events.DURATION)
            } else {
                put(Events.RRULE, rrule)
                put(Events.DURATION, Rfc5545.durationString(dtEnd - dtStart, allDay))
                putNull(Events.DTEND)
            }
        }
    }

    private inline fun <T> query(
        uri: Uri,
        projection: Array<String>,
        selection: String?,
        args: Array<String>?,
        sort: String?,
        map: (Cursor) -> T,
    ): List<T> = queryOrNull(uri, projection, selection, args, sort, map).orEmpty()

    /** 權限被收回、日曆資料庫出錯或回傳 null 時回傳 null（不讓畫面閃退）。 */
    private inline fun <T> queryOrNull(
        uri: Uri,
        projection: Array<String>,
        selection: String?,
        args: Array<String>?,
        sort: String?,
        map: (Cursor) -> T,
    ): List<T>? = try {
        cr.query(uri, projection, selection, args, sort)?.use { c ->
            buildList { while (c.moveToNext()) add(map(c)) }
        }
    } catch (e: Exception) {
        Log.w(TAG, "query failed", e)
        null
    }

    companion object {
        private val INSTANCE_PROJECTION = arrayOf(
            Instances.EVENT_ID, Instances.BEGIN, Instances.END, Instances.TITLE, Instances.ALL_DAY,
            Instances.CALENDAR_ID, Instances.DISPLAY_COLOR, Instances.EVENT_LOCATION,
            Instances.SELF_ATTENDEE_STATUS, Instances.STATUS, Instances.RRULE, Instances.ORIGINAL_ID,
        )

        private const val TAG = "CalendarRepo"
        const val MAX_TITLE = 200
        const val MAX_LOCATION = 200

        /** Google 月曆說明欄位的上限。 */
        const val MAX_DESCRIPTION = 8192
        const val MAX_REMINDER_MIN = 4 * 7 * 24 * 60
        private const val MAX_LOCATION_SCAN = 1000
    }
}
