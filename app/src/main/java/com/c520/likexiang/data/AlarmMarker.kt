package com.c520.likexiang.data

/**
 * 標題裡的 ⏰ 標記：App 內開關與 Google 月曆同步的方式。
 * 規則見 docs/spec.html 第 3 節。
 */
object AlarmMarker {
    const val MARK = "⏰"
    private val markWithSpace = Regex("⏰️?\\s?")

    fun has(title: String?): Boolean = title != null && title.contains(MARK)

    /** 去掉所有 ⏰（含變體選擇符與後面一個空白）。 */
    fun strip(title: String): String = markWithSpace.replace(title, "").trim()

    /** 在標題前加「⏰ 」；已經有就不動。 */
    fun add(title: String): String = if (has(title)) title else "$MARK ${title.trim()}"
}

enum class SyncMode {
    /** 把 ⏰ 寫進 Google 月曆標題（自己的、沒有其他與會者的行程）。 */
    TITLE,

    /** 只記在手機（有其他與會者、受邀、唯讀的行程）。 */
    LOCAL,
}

object AlarmRules {
    fun syncMode(canEditTitle: Boolean, hasOtherAttendees: Boolean): SyncMode =
        if (canEditTitle && !hasOtherAttendees) SyncMode.TITLE else SyncMode.LOCAL

    /** 手機上的明確設定優先，沒有就看標題有沒有 ⏰。 */
    fun isAlarmOn(title: String?, localFlag: Boolean?): Boolean = localFlag ?: AlarmMarker.has(title)

    fun isAlarmOn(inst: EventInstance, state: AlarmState): Boolean =
        isAlarmOn(inst.title, state.flagFor(inst.eventId, inst.originalId))

    /**
     * 存檔時決定送回 Google 月曆的標題。
     * TITLE 模式由開關決定加不加 ⏰；LOCAL 模式完全不動原本標題裡的 ⏰（不是我們加的）。
     */
    fun titleToSave(formTitle: String, originalTitle: String?, mode: SyncMode, alarmOn: Boolean): String {
        val clean = AlarmMarker.strip(formTitle)
        val wantMark = when (mode) {
            SyncMode.TITLE -> alarmOn
            SyncMode.LOCAL -> AlarmMarker.has(originalTitle)
        }
        // 文字和 ⏰ 都沒變就原樣送回：不搬動使用者自己放的 ⏰，也不製造多餘的同步
        if (originalTitle != null && AlarmMarker.strip(originalTitle) == clean && AlarmMarker.has(originalTitle) == wantMark) {
            return originalTitle
        }
        return if (wantMark) AlarmMarker.add(clean) else clean
    }
}
