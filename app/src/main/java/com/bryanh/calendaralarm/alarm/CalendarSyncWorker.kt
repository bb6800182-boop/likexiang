package com.bryanh.calendaralarm.alarm

import android.content.Context
import android.provider.CalendarContract
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * 行程在電腦或其他 App 被改動時重排鬧鐘：
 * 1. 監看日曆資料變更（content URI trigger），每次觸發後重新登記。
 * 2. 每 6 小時補掃一次，以防監看漏掉。
 */
class CalendarSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        AlarmEngine.reschedule(applicationContext)
        if (TAG_OBSERVER in tags) enqueueObserver(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }

    companion object {
        private const val OBSERVER = "calendar-observer"
        private const val PERIODIC = "calendar-periodic"
        private const val TAG_OBSERVER = "calendar-observer-tag"

        fun ensureScheduled(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<CalendarSyncWorker>(6, TimeUnit.HOURS).build(),
            )
            enqueueObserver(context, ExistingWorkPolicy.KEEP)
        }

        private fun enqueueObserver(context: Context, policy: ExistingWorkPolicy) {
            val constraints = Constraints.Builder()
                .addContentUriTrigger(CalendarContract.Events.CONTENT_URI, true)
                .setTriggerContentUpdateDelay(Duration.ofSeconds(3))
                .setTriggerContentMaxDelay(Duration.ofSeconds(20))
                .build()
            val request = OneTimeWorkRequestBuilder<CalendarSyncWorker>()
                .setConstraints(constraints)
                .addTag(TAG_OBSERVER)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(OBSERVER, policy, request)
        }
    }
}
