package com.c520.likexiang

import android.app.Application
import com.c520.likexiang.alarm.CalendarSyncWorker
import com.c520.likexiang.alarm.Notifications
import com.c520.likexiang.ui.theme.Tok
import kotlinx.coroutines.launch

class CalendarAlarmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 通知頻道要在第一次發通知前建立；背景監看用 KEEP，重複呼叫不會重排
        Notifications.ensureChannels(this)
        CalendarSyncWorker.ensureScheduled(this)
        // 自選的重點色、底色：響鈴畫面與小工具也在這個程序裡畫，設定一改就跟著換
        AppScope.launch { ServiceLocator.store(this@CalendarAlarmApp).settings.collect { Tok.use(it.accentColor, it.canvasColor) } }
    }
}
