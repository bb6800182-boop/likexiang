package com.c520.likexiang.alarm

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.c520.likexiang.AppScope
import com.c520.likexiang.ServiceLocator
import com.c520.likexiang.data.MINUTE_MS
import com.c520.likexiang.data.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 響鈴前景服務：用鬧鐘音軌循環播放（靜音模式也會響）並震動，同時掛一則全螢幕通知開啟響鈴畫面。
 * 響滿設定時間沒人理會就自動停止，留下「錯過的鬧鐘」通知。
 */
class RingService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable { onTimeout() }
    private var items: List<RingItem> = emptyList()
    private var player: MediaPlayer? = null
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var focusRequest: AudioFocusRequest? = null
    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_SNOOZE -> snoozeAll(intent.getIntExtra(EXTRA_MINUTES, Settings.SNOOZE_OPTIONS.first()))
            ACTION_DISMISS -> stopRinging()
            else -> stopIfIdle()
        }
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        val incoming = RingItem.read(intent)
        if (incoming.isEmpty()) {
            stopIfIdle()
            return
        }
        items = (items + incoming).distinctBy { it.key }
        val snooze = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, Settings.SNOOZE_OPTIONS.first()).coerceIn(1, 60)
        try {
            startForeground(NOTIFICATION_ID, Notifications.ringing(this, items, snooze), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } catch (e: Exception) {
            Log.w(TAG, "startForeground failed", e)
            Notifications.fallback(this, items)
            items = emptyList()
            stopIfIdle()
            return
        }
        _ringing.value = items
        startSound(intent.getStringExtra(EXTRA_RINGTONE))
        if (intent.getBooleanExtra(EXTRA_VIBRATE, true)) startVibration()
        val minutes = intent.getIntExtra(EXTRA_RING_MINUTES, 5).coerceIn(1, 30)
        handler.removeCallbacks(timeoutRunnable)
        handler.postDelayed(timeoutRunnable, minutes * MINUTE_MS)
    }

    private fun snoozeAll(minutes: Int) {
        val m = minutes.coerceIn(1, 60)
        val keys = items.filterNot { it.isTest }.map { it.key }
        val hasTest = items.any { it.isTest }
        val until = System.currentTimeMillis() + m * MINUTE_MS
        silence()
        // 試響也照樣稍後提醒（時間到再試響一次），才測得出稍後提醒有沒有效
        if (hasTest) AlarmScheduler.scheduleTest(applicationContext, m * MINUTE_MS)
        if (keys.isEmpty()) {
            stopIfIdle()
            return
        }
        val app = applicationContext
        // 稍後提醒時間寫好、下一次排好之後才結束服務，免得程序先被系統回收而漏掉這次稍後提醒
        AppScope.launch {
            try {
                ServiceLocator.store(app).putSnoozes(keys, until)
                AlarmEngine.reschedule(app)
            } finally {
                handler.post { stopIfIdle() }
            }
        }
    }

    private fun onTimeout() {
        Notifications.missed(this, items.filterNot { it.isTest })
        stopRinging()
    }

    /**
     * 選擇鈴聲：自選的只接受系統鈴聲選擇器會給的 content:// 或 android.resource://，
     * 放不出來（檔案被刪、換了手機）就依序退回系統預設鬧鐘鈴聲、預設來電鈴聲。
     */
    private fun startSound(uriString: String?) {
        if (player != null || ringtone != null) return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        requestFocus(attrs)
        val custom = uriString?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?.takeIf { it.scheme == "content" || it.scheme == "android.resource" }
        val defaults = listOfNotNull(
            RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        ).distinct()
        // 通話中把音量壓低，避免嚇到對方
        val level = if (isInCall()) 0.15f else 1f
        if (custom != null) {
            player = createPlayer(custom, attrs, level)
            if (player != null) return
            // 放在手機儲存空間的鈴聲 App 自己可能沒權限讀，交給系統的鈴聲播放器放
            ringtone = playRingtone(custom, attrs, level)
            if (ringtone != null) return
        }
        for (uri in defaults) {
            player = createPlayer(uri, attrs, level)
            if (player != null) return
        }
        // 最後手段：Ringtone 找不到檔案時會改放系統內建的備用鈴聲
        ringtone = defaults.firstOrNull()?.let { playRingtone(it, attrs, level) }
    }

    private fun playRingtone(uri: Uri, attrs: AudioAttributes, level: Float): Ringtone? = try {
        RingtoneManager.getRingtone(this, uri)?.apply {
            audioAttributes = attrs
            isLooping = true
            volume = level
            play()
        }
    } catch (e: Exception) {
        Log.w(TAG, "ringtone failed", e)
        null
    }

    private fun createPlayer(uri: Uri, attrs: AudioAttributes, level: Float): MediaPlayer? {
        val mp = MediaPlayer()
        return try {
            mp.setAudioAttributes(attrs)
            mp.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
            mp.setDataSource(this, uri)
            mp.isLooping = true
            mp.setVolume(level, level)
            mp.prepare()
            mp.start()
            mp
        } catch (e: Exception) {
            Log.w(TAG, "alarm sound failed, trying the next one", e)
            mp.release()
            null
        }
    }

    private fun startVibration() {
        val v = getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
        if (!v.hasVibrator()) return
        v.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0),
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
        )
        vibrator = v
    }

    private fun isInCall(): Boolean {
        val mode = getSystemService(AudioManager::class.java)?.mode ?: return false
        return mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
    }

    private fun requestFocus(attrs: AudioAttributes) {
        val am = getSystemService(AudioManager::class.java) ?: return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attrs).build()
        am.requestAudioFocus(request)
        focusRequest = request
    }

    private fun releaseOutputs() {
        handler.removeCallbacks(timeoutRunnable)
        player?.let {
            try {
                it.stop()
            } catch (_: IllegalStateException) {
            }
            it.release()
        }
        player = null
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        vibrator = null
        focusRequest?.let { getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    /** 立刻停聲音、震動、響鈴畫面與通知，但服務先不結束（稍後提醒還要寫資料）。 */
    private fun silence() {
        releaseOutputs()
        items = emptyList()
        _ringing.value = emptyList()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun stopRinging() {
        silence()
        stopIfIdle()
    }

    /** 這段期間又有新的鬧鐘送進來就不停；用最新的 startId，不會停掉還在排隊的啟動要求。 */
    private fun stopIfIdle() {
        if (items.isEmpty()) stopSelfResult(lastStartId)
    }

    override fun onDestroy() {
        releaseOutputs()
        _ringing.value = emptyList()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "RingService"
        const val ACTION_START = "com.c520.likexiang.action.RING_START"
        const val ACTION_DISMISS = "com.c520.likexiang.action.RING_DISMISS"
        const val ACTION_SNOOZE = "com.c520.likexiang.action.RING_SNOOZE"
        const val EXTRA_MINUTES = "minutes"
        private const val EXTRA_RING_MINUTES = "ring_minutes"
        private const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"
        private const val EXTRA_VIBRATE = "vibrate"
        private const val EXTRA_RINGTONE = "ringtone"
        const val NOTIFICATION_ID = 4201

        private val _ringing = MutableStateFlow<List<RingItem>>(emptyList())

        /** 響鈴畫面與 App 內的響鈴列用來顯示內容，停止時自動關閉。 */
        val ringing: StateFlow<List<RingItem>> = _ringing

        fun start(context: Context, items: List<RingItem>, settings: Settings) {
            if (items.isEmpty()) return
            val intent = RingItem.write(Intent(context, RingService::class.java).setAction(ACTION_START), items)
                .putExtra(EXTRA_RING_MINUTES, settings.ringMinutes)
                .putExtra(EXTRA_SNOOZE_MINUTES, settings.snoozeMin)
                .putExtra(EXTRA_VIBRATE, settings.vibrate)
                .putExtra(EXTRA_RINGTONE, settings.ringtoneUri)
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                // 例如沒有精準鬧鐘權限時無法從背景啟動前景服務：至少留一則通知
                Log.w(TAG, "startForegroundService failed", e)
                Notifications.fallback(context, items)
            }
        }

        fun dismiss(context: Context) = send(context, Intent(context, RingService::class.java).setAction(ACTION_DISMISS))

        fun snooze(context: Context, minutes: Int) =
            send(context, Intent(context, RingService::class.java).setAction(ACTION_SNOOZE).putExtra(EXTRA_MINUTES, minutes))

        private fun send(context: Context, intent: Intent) {
            try {
                context.startService(intent)
            } catch (e: IllegalStateException) {
                // App 在背景時不能啟動服務；服務正在響的話一定在前景，所以這裡只會發生在已經停了的時候
                Log.w(TAG, "ring service not running", e)
            }
        }

        fun actionIntent(context: Context, action: String, minutes: Int, requestCode: Int): PendingIntent =
            PendingIntent.getService(
                context, requestCode,
                Intent(context, RingService::class.java).setAction(action).putExtra(EXTRA_MINUTES, minutes),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
