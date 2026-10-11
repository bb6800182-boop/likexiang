package com.bryanh.calendaralarm.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

// 檔案損毀時重置成預設值，不讓 App 一開就閃退（只會失去手機上的鬧鐘偏好，行程在 Google 月曆不受影響）
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "calendar_alarm",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** 只存在手機上的資料：鬧鐘設定與偏好。行程本身一律在 Google 月曆。 */
class LocalStore(private val context: Context) {

    private object K {
        val offsets = stringPreferencesKey("offsets")
        val localFlags = stringPreferencesKey("local_flags")
        val skips = stringSetPreferencesKey("skips")
        val fired = stringSetPreferencesKey("fired")
        val snoozes = stringPreferencesKey("snoozes")
        val defaultOffset = intPreferencesKey("default_offset")
        val ringMinutes = intPreferencesKey("ring_minutes")
        val snoozeMin = intPreferencesKey("snooze_minutes")
        val dayStart = intPreferencesKey("day_start_minute")
        val customDays = intPreferencesKey("custom_days")
        val customWeeks = intPreferencesKey("custom_weeks")
        val widgetRange = stringPreferencesKey("widget_range")
        val vibrate = booleanPreferencesKey("vibrate")
        val ringtone = stringPreferencesKey("ringtone")
        val weekMonday = booleanPreferencesKey("week_monday")
        val showLunar = booleanPreferencesKey("show_lunar")
        val hidden = stringSetPreferencesKey("hidden_calendars")
        val defaultCalendar = longPreferencesKey("default_calendar")
        val defaultDuration = intPreferencesKey("default_duration")
        val defaultReminder = intPreferencesKey("default_reminder") // -1＝不要一般通知
        val wizardDone = booleanPreferencesKey("wizard_done")
        val pendingTrigger = longPreferencesKey("pending_trigger")
    }

    private val data: Flow<Preferences> = context.dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val settings: Flow<Settings> = data.map { it.toSettings() }
    val alarmState: Flow<AlarmState> = data.map { it.toAlarmState() }

    suspend fun settingsNow(): Settings = settings.first()
    suspend fun alarmStateNow(): AlarmState = alarmState.first()

    /** 交給 AlarmManager 的下一次觸發時間：重開機後拿來判斷關機期間有沒有錯過鬧鐘。 */
    suspend fun pendingTriggerNow(): Long? = data.first()[K.pendingTrigger]

    suspend fun setPendingTrigger(at: Long?) = edit { p ->
        if (at == null) p.remove(K.pendingTrigger) else p[K.pendingTrigger] = at
    }

    /** minutes = null：不另外設定，跟著預設提前時間走。 */
    suspend fun setOffset(eventId: Long, minutes: Int?) = edit { p ->
        val m = Codec.decodeLongInt(p[K.offsets]).toMutableMap()
        if (minutes == null) m.remove(eventId) else m[eventId] = minutes.coerceIn(0, Settings.MAX_OFFSET_MIN)
        p[K.offsets] = Codec.encodeLongInt(m)
    }

    suspend fun setLocalFlag(eventId: Long, on: Boolean?) = edit { p ->
        val m = Codec.decodeLongBool(p[K.localFlags]).toMutableMap()
        if (on == null) m.remove(eventId) else m[eventId] = on
        p[K.localFlags] = Codec.encodeLongBool(m)
    }

    /** 行程被刪除時清掉它在手機上的設定。 */
    suspend fun forgetEvent(eventId: Long) = edit { p ->
        p[K.offsets] = Codec.encodeLongInt(Codec.decodeLongInt(p[K.offsets]) - eventId)
        p[K.localFlags] = Codec.encodeLongBool(Codec.decodeLongBool(p[K.localFlags]) - eventId)
        val prefix = "$eventId@"
        p[K.skips] = (p[K.skips] ?: emptySet()).filterNot { it.startsWith(prefix) }.toSet()
        p[K.snoozes] = Codec.encodeStringLong(Codec.decodeStringLong(p[K.snoozes]).filterKeys { !it.startsWith(prefix) })
    }

    suspend fun setSkip(key: String, skip: Boolean) = edit { p ->
        val s = (p[K.skips] ?: emptySet()).toMutableSet()
        if (skip) s += key else s -= key
        p[K.skips] = s
    }

    /** 記下已響過的，並清掉三天前的舊紀錄與早就過期的稍後提醒（行程被刪掉時會留下）。 */
    suspend fun markFired(keys: Collection<String>, now: Long) = edit { p ->
        val keep = (p[K.fired] ?: emptySet()).filter { (Codec.beginOf(it) ?: 0L) > now - 3 * DAY_MS }
        p[K.fired] = (keep + keys).toSet()
        p[K.skips] = (p[K.skips] ?: emptySet()).filter { (Codec.beginOf(it) ?: 0L) > now - 3 * DAY_MS }.toSet()
        p[K.snoozes] = Codec.encodeStringLong(Codec.decodeStringLong(p[K.snoozes]).filterValues { it > now - DAY_MS })
    }

    suspend fun putSnoozes(keys: Collection<String>, until: Long) = edit { p ->
        val m = Codec.decodeStringLong(p[K.snoozes]).toMutableMap()
        keys.filter(Codec::isSafeKey).forEach { m[it] = until }
        p[K.snoozes] = Codec.encodeStringLong(m)
    }

    suspend fun clearSnoozes(keys: Collection<String>) = edit { p ->
        p[K.snoozes] = Codec.encodeStringLong(Codec.decodeStringLong(p[K.snoozes]) - keys.toSet())
    }

    suspend fun updateSettings(transform: (Settings) -> Settings) = edit { p ->
        val s = transform(p.toSettings())
        p[K.defaultOffset] = s.defaultOffsetMin.coerceIn(0, Settings.MAX_OFFSET_MIN)
        p[K.ringMinutes] = s.ringMinutes.coerceIn(1, 30)
        p[K.snoozeMin] = s.snoozeMin.coerceIn(1, 60)
        p[K.dayStart] = s.dayStartMinute.coerceIn(0, 12 * 60)
        p[K.customDays] = s.customDays.coerceIn(Settings.CUSTOM_DAYS.first, Settings.CUSTOM_DAYS.last)
        p[K.customWeeks] = s.customWeeks.coerceIn(Settings.CUSTOM_WEEKS.first, Settings.CUSTOM_WEEKS.last)
        p[K.widgetRange] = s.widgetRange.name
        p[K.vibrate] = s.vibrate
        if (s.ringtoneUri == null) p.remove(K.ringtone) else p[K.ringtone] = s.ringtoneUri
        p[K.weekMonday] = s.weekStartsMonday
        p[K.showLunar] = s.showLunar
        p[K.hidden] = s.hiddenCalendarIds.map { it.toString() }.toSet()
        val cal = s.defaultCalendarId
        if (cal == null || cal <= 0) p.remove(K.defaultCalendar) else p[K.defaultCalendar] = cal
        p[K.defaultDuration] = s.defaultDurationMin.coerceIn(5, 24 * 60)
        p[K.defaultReminder] = s.defaultReminderMin?.coerceIn(0, 4 * 7 * 24 * 60) ?: -1
        p[K.wizardDone] = s.wizardDone
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    private fun Preferences.toSettings(): Settings {
        val d = Settings()
        return Settings(
            defaultOffsetMin = this[K.defaultOffset] ?: d.defaultOffsetMin,
            ringMinutes = this[K.ringMinutes] ?: d.ringMinutes,
            snoozeMin = this[K.snoozeMin] ?: d.snoozeMin,
            dayStartMinute = this[K.dayStart] ?: d.dayStartMinute,
            vibrate = this[K.vibrate] ?: d.vibrate,
            ringtoneUri = this[K.ringtone],
            weekStartsMonday = this[K.weekMonday] ?: d.weekStartsMonday,
            showLunar = this[K.showLunar] ?: d.showLunar,
            hiddenCalendarIds = (this[K.hidden] ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet(),
            defaultCalendarId = this[K.defaultCalendar],
            defaultDurationMin = this[K.defaultDuration] ?: d.defaultDurationMin,
            defaultReminderMin = when (val r = this[K.defaultReminder]) {
                null -> d.defaultReminderMin
                -1 -> null
                else -> r
            },
            wizardDone = this[K.wizardDone] ?: d.wizardDone,
            customDays = (this[K.customDays] ?: d.customDays).coerceIn(Settings.CUSTOM_DAYS.first, Settings.CUSTOM_DAYS.last),
            customWeeks = (this[K.customWeeks] ?: d.customWeeks).coerceIn(Settings.CUSTOM_WEEKS.first, Settings.CUSTOM_WEEKS.last),
            widgetRange = WidgetRange.entries.firstOrNull { it.name == this[K.widgetRange] } ?: d.widgetRange,
        )
    }

    private fun Preferences.toAlarmState() = AlarmState(
        offsets = Codec.decodeLongInt(this[K.offsets]),
        localFlags = Codec.decodeLongBool(this[K.localFlags]),
        skips = this[K.skips] ?: emptySet(),
        fired = this[K.fired] ?: emptySet(),
        snoozes = Codec.decodeStringLong(this[K.snoozes]),
    )
}
