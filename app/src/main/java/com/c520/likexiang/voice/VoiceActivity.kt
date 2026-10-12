package com.c520.likexiang.voice

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import android.speech.RecognizerIntent
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.c520.likexiang.MainActivity
import com.c520.likexiang.R
import com.c520.likexiang.ServiceLocator
import com.c520.likexiang.alarm.AlarmEngine
import com.c520.likexiang.alarm.Notifications
import com.c520.likexiang.data.AlarmMarker
import com.c520.likexiang.data.AlarmRules
import com.c520.likexiang.data.EventDraft
import com.c520.likexiang.data.SyncMode
import com.c520.likexiang.data.VoiceParser
import com.c520.likexiang.data.defaultCalendarFor
import com.c520.likexiang.ui.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * 語音新增（EVT-07）：開系統的語音輸入，聽完用 [VoiceParser] 拆成日期、時間、標題。
 * 聽得懂就直接存進預設日曆、跳一則通知（點了開行程內容，可以改或刪）；缺日期時間、缺標題或要重複，開新增畫面讓使用者補。
 * 小工具的麥克風、App 裡新增鍵上方的麥克風都開這裡。透明不顯示畫面，做完就關，從小工具開的會直接回到桌面。
 * 錄音與辨識由系統的語音輸入（Google）負責，本 App 不需要麥克風或網路權限。
 */
class VoiceActivity : ComponentActivity() {

    private val recognize = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
        if (result.resultCode != RESULT_OK || text.isNullOrEmpty()) finish() else handle(text)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 等語音輸入時被系統回收、之後重建：結果還是會送回來，不要再開一次
        if (savedInstanceState != null) return
        try {
            recognize.launch(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-TW")
                    // 不加 EXTRA_PREFER_OFFLINE：它的意思是「只用離線」，手機沒下載中文（台灣）離線語言包就直接失敗；
                    // 不加時由系統自己選，有離線包就用離線
                    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    .putExtra(RecognizerIntent.EXTRA_PROMPT, "說日期、時間和行程，例如「明天下午三點開會」"),
            )
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "這支手機沒有語音輸入，請改用＋新增", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun handle(text: String) {
        lifecycleScope.launch {
            // 寫入日曆與通知做到一半不能被打斷（例如使用者剛好離開）
            val app = applicationContext
            val outcome = withContext(Dispatchers.IO + NonCancellable) {
                process(app, text).also { if (it is Outcome.Created) notifyCreated(app, it) }
            }
            when (outcome) {
                // 用 applicationContext：Toast 跟著 Activity 的話，下一行 finish() 會把它提早收掉
                is Outcome.Created -> Toast.makeText(app, "已建立：${outcome.title}\n${outcome.time}\n存到「${outcome.calendar}」", Toast.LENGTH_LONG).show()
                is Outcome.NeedsEditor -> openEditor(outcome.result)
            }
            finish()
        }
    }

    /** 聽不完整：開新增畫面並預填（走一般「加到月曆」的入口，內容由 MainActivity 檢查）。 */
    private fun openEditor(r: VoiceParser.Result) {
        val start = r.start
        val end = r.end
        val intent = Intent(Intent.ACTION_INSERT)
            .setClass(this, MainActivity::class.java)
            // 要開鬧鐘：標題帶 ⏰，新增畫面會把鬧鐘打開、標題去掉 ⏰
            .putExtra(Events.TITLE, if (r.alarm) AlarmMarker.add(r.title) else r.title)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (start != null && end != null) {
            intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, millis(start, r.allDay))
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, millis(end, r.allDay))
                .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, r.allDay)
        }
        startActivity(intent)
    }

    sealed interface Outcome {
        class Created(val eventId: Long, val begin: Long, val title: String, val time: String, val alarm: Boolean, val calendar: String) : Outcome
        class NeedsEditor(val result: VoiceParser.Result) : Outcome
    }

    companion object {
        private const val TAG = "VoiceActivity"

        fun intent(context: Context): Intent = Intent(context, VoiceActivity::class.java)

        /**
         * 拆好就存進預設日曆。存檔方式跟新增畫面一樣（MainViewModel.saveBlocking 的新增那一支）：
         * 自己的新行程用 TITLE 模式，開鬧鐘就在標題加「⏰ 」；開鬧鐘時不加一般通知，免得叮一聲又響鈴。
         */
        internal suspend fun process(app: Context, text: String): Outcome {
            val store = ServiceLocator.store(app)
            val repo = ServiceLocator.repo(app)
            val settings = store.settingsNow()
            val r = VoiceParser.parse(text, LocalDateTime.now(), settings.defaultDurationMin)
            val start = r.start
            val end = r.end
            if (!r.complete || start == null || end == null || !repo.hasWritePermission()) return Outcome.NeedsEditor(r)
            val calendar = defaultCalendarFor(repo.calendars(), settings) ?: return Outcome.NeedsEditor(r)
            val draft = EventDraft(
                calendarId = calendar.id,
                title = AlarmRules.titleToSave(r.title, null, SyncMode.TITLE, r.alarm),
                description = "",
                location = "",
                allDay = r.allDay,
                start = start,
                end = end,
                rrule = null,
            )
            val id = repo.insertEvent(draft) ?: return Outcome.NeedsEditor(r)
            store.setLocalFlag(id, null)
            if (!repo.replaceAlertReminders(id, if (r.alarm) emptyList() else listOfNotNull(settings.defaultReminderMin))) {
                Log.w(TAG, "voice event saved but reminders failed")
            }
            AlarmEngine.reschedule(app)
            val begin = millis(start, r.allDay)
            return Outcome.Created(id, begin, r.title, Fmt.range(begin, millis(end, r.allDay), r.allDay), r.alarm, calendar.displayName)
        }

        /** 全天行程照 CalendarContract 慣例用 UTC 午夜。 */
        private fun millis(t: LocalDateTime, allDay: Boolean): Long =
            (if (allDay) t.toLocalDate().atStartOfDay(ZoneOffset.UTC) else t.atZone(ZoneId.systemDefault())).toInstant().toEpochMilli()

        /** 建好的通知：點了開行程內容（可以改或刪）。鎖定畫面上只顯示「已用語音建立行程」。 */
        private fun notifyCreated(context: Context, c: Outcome.Created) {
            if (!Notifications.canNotify(context)) return
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            val open = PendingIntent.getActivity(
                context, c.eventId.hashCode(),
                MainActivity.internalIntent(context, MainActivity.ACTION_OPEN_EVENT)
                    .putExtra(MainActivity.EXTRA_EVENT_ID, c.eventId)
                    .putExtra(MainActivity.EXTRA_BEGIN, c.begin),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val publicVersion = Notification.Builder(context, Notifications.CH_VOICE)
                .setSmallIcon(R.drawable.ic_stat_alarm)
                .setContentTitle("已用語音建立行程")
                .build()
            val detail = c.time + if (c.alarm) "・鬧鐘" else ""
            nm.notify(
                c.eventId.hashCode(),
                Notification.Builder(context, Notifications.CH_VOICE)
                    .setSmallIcon(R.drawable.ic_stat_alarm)
                    .setContentTitle("已建立：${c.title}")
                    .setContentText(detail)
                    // 上傳到 Google 由 Android 的同步負責，通常幾十秒到幾分鐘，手機過熱或沒網路時會更久
                    .setStyle(Notification.BigTextStyle().bigText("$detail\n存到「${c.calendar}」，同步後會出現在 Google 月曆\n點這裡修改或刪除"))
                    .setVisibility(Notification.VISIBILITY_PRIVATE)
                    .setPublicVersion(publicVersion)
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }
}
