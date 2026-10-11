package com.bryanh.calendaralarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bryanh.calendaralarm.AppScope
import kotlinx.coroutines.launch

/** 開機、改時間或時區、App 更新後，AlarmManager 的排程會失效或過時，這裡重排。 */
class SystemEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> {
                val app = context.applicationContext
                val pending = goAsync()
                AppScope.launch {
                    try {
                        CalendarSyncWorker.ensureScheduled(app)
                        AlarmEngine.reschedule(app)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
