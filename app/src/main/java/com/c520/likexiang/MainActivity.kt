package com.c520.likexiang

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.c520.likexiang.ui.AppRoot
import com.c520.likexiang.ui.ExternalRequest
import com.c520.likexiang.ui.MainViewModel
import com.c520.likexiang.ui.Screen
import com.c520.likexiang.ui.theme.AppTheme
import java.time.LocalDate
import java.time.ZoneId

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 介面只有淺色：系統深色模式下狀態列也維持深色圖示，才看得到
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        if (savedInstanceState == null) handleIntent(intent)
        setContent { AppTheme { AppRoot(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // 使用者可能剛從系統設定改了權限，或在電腦上改了行程
        vm.onPermissionsChanged()
    }

    /**
     * 自訂動作只認從不對外的 InternalEntry 進來的（我們自己的通知、小工具、狀態列鬧鐘圖示）；
     * 其他 App 送來的只接受標準的月曆請求（加到月曆、開行程、開某一天），內容檢查過才用。
     * 參數不合理就忽略，也只切換畫面、不做任何寫入。
     */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        // 從多工畫面重新開啟時，系統會把當初那個 Intent 再送一次，不要又跳到舊行程或又開新增畫面
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        if (intent.component?.className != INTERNAL_ENTRY) {
            parseExternal(intent)?.let { vm.openExternal(it) }
            return
        }
        when (intent.action) {
            ACTION_SHOW_ALARMS -> vm.navigate(Screen.Alarms)
            ACTION_OPEN_EVENT -> {
                val id = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
                val begin = intent.getLongExtra(EXTRA_BEGIN, 0L)
                if (id > 0) vm.navigate(Screen.Detail(id, begin.coerceAtLeast(0L)))
            }
            // 小工具：點日期開那一天、「＋」新增
            ACTION_OPEN_DAY -> {
                val day = intent.getLongExtra(EXTRA_DATE, Long.MIN_VALUE)
                if (day in MIN_EPOCH_DAY..MAX_EPOCH_DAY) vm.openDay(LocalDate.ofEpochDay(day))
            }
            ACTION_NEW_EVENT -> vm.openNewEvent()
        }
    }

    /** 其他 App 的月曆請求（EVT-05）。Extras 可能被做壞，解析失敗就當沒收到。 */
    private fun parseExternal(intent: Intent): ExternalRequest? = try {
        val zone = ZoneId.systemDefault()
        when (intent.action) {
            Intent.ACTION_INSERT -> ExternalRequest.insert(
                title = intent.getStringExtra(Events.TITLE),
                description = intent.getStringExtra(Events.DESCRIPTION),
                location = intent.getStringExtra(Events.EVENT_LOCATION),
                begin = intent.longExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME),
                end = intent.longExtra(CalendarContract.EXTRA_EVENT_END_TIME),
                allDay = intent.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false),
                rrule = intent.getStringExtra(Events.RRULE),
                zone = zone,
            )
            Intent.ACTION_VIEW -> {
                val uri = intent.data
                if (uri == null || uri.scheme != "content" || uri.authority != CalendarContract.AUTHORITY) {
                    null
                } else {
                    val seg = uri.pathSegments
                    when (seg.firstOrNull()) {
                        "events" -> ExternalRequest.viewEvent(seg.getOrNull(1)?.toLongOrNull(), intent.longExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME))
                        "time" -> ExternalRequest.viewDay(seg.getOrNull(1)?.toLongOrNull(), zone)
                        else -> null
                    }
                }
            }
            else -> null
        }
    } catch (e: RuntimeException) {
        Log.w(TAG, "ignored malformed calendar request", e)
        null
    }

    private fun Intent.longExtra(key: String): Long? =
        if (hasExtra(key)) getLongExtra(key, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE } else null

    companion object {
        private const val TAG = "MainActivity"
        const val ACTION_SHOW_ALARMS = "com.c520.likexiang.action.SHOW_ALARMS"
        const val ACTION_OPEN_EVENT = "com.c520.likexiang.action.OPEN_EVENT"
        const val ACTION_OPEN_DAY = "com.c520.likexiang.action.OPEN_DAY"
        const val ACTION_NEW_EVENT = "com.c520.likexiang.action.NEW_EVENT"
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_BEGIN = "begin"
        const val EXTRA_DATE = "epoch_day"

        // 日期參數的合理範圍：1900～2200 年
        private val MIN_EPOCH_DAY = LocalDate.of(1900, 1, 1).toEpochDay()
        private val MAX_EPOCH_DAY = LocalDate.of(2200, 1, 1).toEpochDay()

        /** AndroidManifest 裡 activity-alias 的完整名稱（以 namespace 為準，不受 applicationId 影響）。 */
        private const val INTERNAL_ENTRY = "com.c520.likexiang.InternalEntry"

        fun internalIntent(context: Context, action: String): Intent =
            Intent(action).setComponent(ComponentName(context, INTERNAL_ENTRY))
    }
}
