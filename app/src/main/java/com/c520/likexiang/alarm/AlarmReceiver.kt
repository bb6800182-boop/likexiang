package com.c520.likexiang.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.c520.likexiang.AppScope
import com.c520.likexiang.ServiceLocator
import com.c520.likexiang.widget.Widgets
import kotlinx.coroutines.launch

/** AlarmManager 到點時呼叫。實際判斷在 AlarmEngine.fire，響完再排下一個。 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val action = intent.action
        if (action == AlarmScheduler.ACTION_WIDGET_REFRESH) {
            // 過午夜：小工具換日（Widgets.refresh 會排下一次）
            val pending = goAsync()
            AppScope.launch {
                try {
                    Widgets.refresh(app)
                } finally {
                    pending.finish()
                }
            }
            return
        }
        if (action != AlarmScheduler.ACTION_FIRE && action != AlarmScheduler.ACTION_TEST) return
        val pending = goAsync()
        AppScope.launch {
            try {
                if (action == AlarmScheduler.ACTION_TEST) {
                    RingService.start(app, listOf(RingItem.test(System.currentTimeMillis())), ServiceLocator.store(app).settingsNow())
                } else {
                    AlarmEngine.fire(app)
                }
            } catch (e: Exception) {
                Log.w(TAG, "alarm handling failed", e)
            } finally {
                if (action == AlarmScheduler.ACTION_FIRE) AlarmEngine.reschedule(app)
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "AlarmReceiver"
    }
}
