package com.c520.likexiang

import android.app.Application
import com.c520.likexiang.alarm.CalendarSyncWorker
import com.c520.likexiang.alarm.Notifications

class CalendarAlarmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 通知頻道要在第一次發通知前建立；背景監看用 KEEP，重複呼叫不會重排
        Notifications.ensureChannels(this)
        CalendarSyncWorker.ensureScheduled(this)
    }
}
