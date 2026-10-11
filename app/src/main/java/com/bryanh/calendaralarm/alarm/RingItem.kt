package com.bryanh.calendaralarm.alarm

import android.content.Intent

/** 正在響的一筆鬧鐘。用平行陣列放進 Intent，不需要 Parcelable 外掛。 */
data class RingItem(
    val key: String,
    val eventId: Long,
    val title: String,
    val begin: Long,
    val location: String,
    val allDay: Boolean,
) {
    val isTest: Boolean get() = eventId <= 0

    companion object {
        private const val KEYS = "ring_keys"
        private const val IDS = "ring_ids"
        private const val TITLES = "ring_titles"
        private const val BEGINS = "ring_begins"
        private const val LOCATIONS = "ring_locations"
        private const val ALL_DAY = "ring_all_day"
        private const val MAX_ITEMS = 20

        fun from(p: PlannedAlarm) = RingItem(p.key, p.eventId, p.title, p.begin, p.location, p.allDay)

        fun test(now: Long) = RingItem("test", -1, "測試鬧鐘", now + 10 * 60_000L, "這是試響，按關閉就會停", false)

        fun write(intent: Intent, items: List<RingItem>): Intent {
            val list = items.take(MAX_ITEMS)
            return intent
                .putExtra(KEYS, list.map { it.key }.toTypedArray())
                .putExtra(IDS, list.map { it.eventId }.toLongArray())
                .putExtra(TITLES, list.map { it.title.take(200) }.toTypedArray())
                .putExtra(BEGINS, list.map { it.begin }.toLongArray())
                .putExtra(LOCATIONS, list.map { it.location.take(200) }.toTypedArray())
                .putExtra(ALL_DAY, list.map { it.allDay }.toBooleanArray())
        }

        /** 陣列長度不一致或缺欄位時回傳空清單（只接受我們自己送的 Intent）。 */
        fun read(intent: Intent): List<RingItem> {
            val keys = intent.getStringArrayExtra(KEYS) ?: return emptyList()
            val ids = intent.getLongArrayExtra(IDS) ?: return emptyList()
            val titles = intent.getStringArrayExtra(TITLES) ?: return emptyList()
            val begins = intent.getLongArrayExtra(BEGINS) ?: return emptyList()
            val locations = intent.getStringArrayExtra(LOCATIONS) ?: return emptyList()
            val allDay = intent.getBooleanArrayExtra(ALL_DAY) ?: return emptyList()
            val n = keys.size
            if (listOf(ids.size, titles.size, begins.size, locations.size, allDay.size).any { it != n }) return emptyList()
            return (0 until minOf(n, MAX_ITEMS)).map { i ->
                RingItem(keys[i].orEmpty(), ids[i], titles[i].orEmpty(), begins[i], locations[i].orEmpty(), allDay[i])
            }
        }
    }
}
