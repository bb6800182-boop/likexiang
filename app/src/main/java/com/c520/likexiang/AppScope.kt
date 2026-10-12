package com.c520.likexiang

import android.annotation.SuppressLint
import android.content.Context
import com.c520.likexiang.data.CalendarRepository
import com.c520.likexiang.data.LocalStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** 給 BroadcastReceiver、Service 用的程序層級協程範圍（IO 執行緒）。 */
object AppScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** 手動相依注入：整個程序共用一個 LocalStore（DataStore 必須單一實例）。 */
object ServiceLocator {
    // 只存 applicationContext（見下方），不會洩漏 Activity
    @SuppressLint("StaticFieldLeak")
    @Volatile
    private var storeRef: LocalStore? = null

    fun store(context: Context): LocalStore =
        storeRef ?: synchronized(this) {
            storeRef ?: LocalStore(context.applicationContext).also { storeRef = it }
        }

    fun repo(context: Context): CalendarRepository = CalendarRepository(context.applicationContext)
}
