package com.bryanh.calendaralarm

import android.app.Application
import com.bryanh.calendaralarm.alarm.CalendarSyncWorker
import com.bryanh.calendaralarm.alarm.Notifications

class CalendarAlarmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 通知頻道要在第一次發通知前建立；背景監看用 KEEP，重複呼叫不會重排
        Notifications.ensureChannels(this)
        CalendarSyncWorker.ensureScheduled(this)
    }
}
