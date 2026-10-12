package com.c520.likexiang.data

import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/** 日期規則：比對結果 → 日期（與「今晚」這類順帶的時段）；不合理回傳 null 或丟 DateTimeException。 */
private typealias DateBuilder = (MatchResult, LocalDate, LocalDate?) -> Pair<LocalDate, String?>?

/**
 * 語音新增（EVT-07）：把一句話拆成日期、時間、標題，例如「12月1日上午9點到12點，建立資安會議的行程」。
 * 手機上沒有可用的離線 AI，所以用規則：認得常見的中文日期時間說法，認得的部分拿掉，剩下的去掉贅字當標題。
 * 純邏輯，可在 JVM 上測。
 */
object VoiceParser {

    data class Result(
        val title: String,
        val allDay: Boolean,
        /** 開始；全天行程是第一天 00:00。沒聽到日期也沒聽到時間時是 null。 */
        val start: LocalDateTime?,
        /** 結束（不含）；全天行程是最後一天的隔天 00:00。 */
        val end: LocalDateTime?,
        /** 說了「要響」「鬧鐘」「叫我」：打開鬧鐘。 */
        val alarm: Boolean,
        /** 說了「每天」「每週」這類重複：這一版不自己設，開新增畫面讓使用者設定。 */
        val repeating: Boolean,
    ) {
        /** 聽懂日期或時間、有標題、不是重複行程：可以直接建立；否則開新增畫面讓使用者補。 */
        val complete: Boolean get() = start != null && title.isNotEmpty() && !repeating
    }

    /** 辨識結果最長取幾個字（標題另有上限）。 */
    const val MAX_INPUT = 200

    private const val CUT = '\u0001' // 拿掉的片段換成這個字元，標題由剩下的片段組成

    fun parse(raw: String, now: LocalDateTime, defaultDurationMin: Int): Result {
        val today = now.toLocalDate()
        var s = normalize(raw)

        var alarm = false
        s = ALARM.replace(s) { alarm = true; CUT.toString() }
        var repeating = false
        s = REPEAT.replace(s) { repeating = true; CUT.toString() }
        var allDayWord = false
        s = ALL_DAY.replace(s) { allDayWord = true; CUT.toString() }

        // 日期：第一個認得的日期；後面接「到」再一個日期就是好幾天的全天行程
        var date: LocalDate? = null
        var lastDate: LocalDate? = null
        var datePeriod: String? = null
        findDate(s, today, base = null, from = 0)?.let { first ->
            date = first.date
            datePeriod = first.period
            var cutEnd = first.range.last + 1
            val conn = CONNECTOR_AT.matchAt(s, cutEnd)
            if (conn != null) {
                val second = findDate(s, today, base = first.date, from = conn.range.last + 1)
                if (second != null && second.range.first == conn.range.last + 1 && !second.date.isBefore(first.date)) {
                    lastDate = second.date
                    cutEnd = second.range.last + 1
                }
            }
            s = s.cut(first.range.first, cutEnd)
        }

        // 時間：先找「幾點到幾點」，再找單一時間，都沒有就看有沒有「早上、下午」這類時段
        var startMin: Int? = null
        var endMin: Int? = null
        val range = TIME_RANGE.find(s)
        if (range != null) {
            val p1 = range.groupValues[1].ifEmpty { null } ?: datePeriod
            val p2 = range.groupValues[3].ifEmpty { null } ?: p1
            val a = clock(range.groupValues[2], p1)
            val b = clock(range.groupValues[4], p2)
            if (a != null && b != null) {
                startMin = a
                endMin = b
                s = s.cut(range.range.first, range.range.last + 1)
            }
        }
        if (startMin == null) {
            TIME_ONE.find(s)?.let { m ->
                clock(m.groupValues[2], m.groupValues[1].ifEmpty { null } ?: datePeriod)?.let {
                    startMin = it
                    s = s.cut(m.range.first, m.range.last + 1)
                }
            }
        }
        if (startMin == null) {
            val m = PERIOD_ALONE.find(s)
            val period = m?.value ?: datePeriod
            if (period != null) {
                startMin = DEFAULT_HOUR.getValue(period) * 60
                if (m != null) s = s.cut(m.range.first, m.range.last + 1)
            }
        }
        var durationMin: Int? = null
        if (startMin != null && endMin == null) {
            DURATION.find(s)?.let { m ->
                durationMin = duration(m)
                s = s.cut(m.range.first, m.range.last + 1)
            }
        }

        val title = title(s)
        val d = date
        val sm = startMin
        return when {
            // 全天：有日期、沒說時間（或說了「整天」）
            d != null && (sm == null || allDayWord) ->
                Result(title, true, d.atStartOfDay(), (lastDate ?: d).plusDays(1).atStartOfDay(), alarm, repeating)
            sm == null -> Result(title, false, null, null, alarm, repeating)
            else -> {
                // 只說時間：今天；今天那個時間已經過了就是明天
                var start = (d ?: today).atStartOfDay().plusMinutes(sm.toLong())
                if (d == null && !start.isAfter(now)) start = start.plusDays(1)
                val end = when {
                    endMin != null -> {
                        var e = start.toLocalDate().atStartOfDay().plusMinutes(endMin!!.toLong())
                        // 「上午10點到2點」的 2 點是下午；跨過午夜就是隔天
                        repeat(2) { if (!e.isAfter(start)) e = e.plusHours(12) }
                        e
                    }
                    durationMin != null -> start.plusMinutes(durationMin!!.toLong())
                    else -> start.plusMinutes(defaultDurationMin.toLong().coerceIn(5, 24 * 60L))
                }
                Result(title, false, start, end, alarm, repeating)
            }
        }
    }

    // ── 正規化：全形轉半形、中文數字轉阿拉伯數字（只轉日期時間會用到的） ──

    private fun normalize(raw: String): String {
        val half = raw.take(MAX_INPUT).map { c ->
            when (c) {
                in '０'..'９' -> '0' + (c - '０')
                '：' -> ':'
                '／' -> '/'
                '～' -> '~'
                '　', '\n', '\r', '\t' -> ' '
                else -> c
            }
        }.filterNot { it.isISOControl() }.joinToString("")
        var s = CN_BEFORE_UNIT.replace(half) { cnToInt(it.value)?.toString() ?: it.value }
        s = CN_DAY_AFTER_MONTH.replace(s) { cnToInt(it.value.trim())?.toString() ?: it.value }
        s = CN_AFTER_POINT.replace(s) { cnToInt(it.value.trim())?.toString() ?: it.value }
        return s
    }

    /** 一、十二、二十三、二〇二六（逐位）；不是數字回傳 null。 */
    internal fun cnToInt(s: String): Int? {
        if (s.isEmpty()) return null
        val i = s.indexOf('十')
        if (i < 0) return s.fold(0) { acc, c -> acc * 10 + (CN_DIGITS[c] ?: return null) }
        if (s.indexOf('十', i + 1) >= 0 || i > 1) return null
        val tens = if (i == 0) 1 else CN_DIGITS[s[0]] ?: return null
        val ones = when (s.length - i - 1) {
            0 -> 0
            1 -> CN_DIGITS[s[i + 1]] ?: return null
            else -> return null
        }
        return tens * 10 + ones
    }

    // ── 日期 ──

    private class DateHit(val range: IntRange, val date: LocalDate, val period: String? = null)

    /** 從 [from] 起最前面的日期；[base] 是區間的第一天（第二個日期只說「22日」時用它的月份）。 */
    private fun findDate(s: String, today: LocalDate, base: LocalDate?, from: Int): DateHit? {
        val hits = DATE_PATTERNS.mapNotNull { (regex, build) ->
            var m = regex.find(s, from)
            // 日期不合理（例如 2 月 30 日）就看下一個
            while (m != null) {
                val match = m
                val hit = try {
                    build(match, today, base)?.let { (date, period) -> DateHit(match.range, date, period) }
                } catch (_: DateTimeException) {
                    null
                }
                if (hit != null) return@mapNotNull hit
                m = match.next()
            }
            null
        }
        return hits.minByOrNull { it.range.first }
    }

    private fun monthDay(month: Int, day: Int, today: LocalDate, base: LocalDate?): LocalDate {
        val ref = base ?: today
        val d = LocalDate.of(ref.year, month, day)
        // 沒說年份：已經過了就是明年
        return if (d.isBefore(ref)) d.plusYears(1) else d
    }

    private fun dayOfMonth(day: Int, today: LocalDate, base: LocalDate?): LocalDate {
        val ref = base ?: today
        val d = ref.withDayOfMonth(day)
        // 只說「幾號」：這個月的已經過了就是下個月
        return if (d.isBefore(ref)) ref.plusMonths(1).withDayOfMonth(day) else d
    }

    private fun weekday(c: String): DayOfWeek = when (c) {
        "一", "1" -> DayOfWeek.MONDAY
        "二", "2" -> DayOfWeek.TUESDAY
        "三", "3" -> DayOfWeek.WEDNESDAY
        "四", "4" -> DayOfWeek.THURSDAY
        "五", "5" -> DayOfWeek.FRIDAY
        "六", "6" -> DayOfWeek.SATURDAY
        else -> DayOfWeek.SUNDAY
    }

    private val DATE_PATTERNS: List<Pair<Regex, DateBuilder>> = listOf(
        Regex("""(\d{4})\s*年\s*(\d{1,2})\s*月\s*(\d{1,2})\s*[日號号]?""") to { m, _, _ ->
            LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) to null
        },
        Regex("""(\d{1,2})\s*月\s*(\d{1,2})\s*[日號号]?""") to { m, today, base ->
            monthDay(m.groupValues[1].toInt(), m.groupValues[2].toInt(), today, base) to null
        },
        Regex("""(?<![\d/])(\d{1,2})\s*/\s*(\d{1,2})(?![\d/])""") to { m, today, base ->
            monthDay(m.groupValues[1].toInt(), m.groupValues[2].toInt(), today, base) to null
        },
        Regex("""下\s*個?\s*月\s*(\d{1,2})\s*[日號号]""") to { m, today, _ ->
            today.plusMonths(1).withDayOfMonth(m.groupValues[1].toInt()) to null
        },
        Regex("""大後天|大后天|後天|后天|明天|明日|今天|今日""") to { m, today, _ ->
            when (m.value) {
                "今天", "今日" -> today
                "明天", "明日" -> today.plusDays(1)
                "後天", "后天" -> today.plusDays(2)
                else -> today.plusDays(3)
            } to null
        },
        Regex("""今晚|明晚|明早""") to { m, today, _ ->
            when (m.value) {
                "今晚" -> today to "晚上"
                "明晚" -> today.plusDays(1) to "晚上"
                else -> today.plusDays(1) to "早上"
            }
        },
        Regex("""(下下|下|這|这|本)?\s*個?\s*(?:星期|週|周|禮拜|礼拜)\s*([一二三四五六日天七1-7])""") to { m, today, _ ->
            val dow = weekday(m.groupValues[2])
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val inWeek = { weeks: Long -> monday.plusWeeks(weeks).plusDays((dow.value - 1).toLong()) }
            when (m.groupValues[1]) {
                // 「這週六」在週日說：這週的已經過了，當成接下來那個週六
                "這", "这", "本" -> inWeek(0).takeIf { !it.isBefore(today) } ?: inWeek(1)
                "下" -> inWeek(1)
                "下下" -> inWeek(2)
                else -> today.with(TemporalAdjusters.nextOrSame(dow))
            } to null
        },
        Regex("""(\d{1,3})\s*天\s*[後后]""") to { m, today, _ ->
            m.groupValues[1].toLong().takeIf { it in 1..365 }?.let { today.plusDays(it) to null }
        },
        Regex("""(?<![\d月])(\d{1,2})\s*[號号]""") to { m, today, base ->
            dayOfMonth(m.groupValues[1].toInt(), today, base) to null
        },
        // 區間的第二天只說「22日」（「10月20日到22日」）；單獨的「幾日」不認，免得把「一日遊」當日期
        Regex("""(?<![\d月])(\d{1,2})\s*日""") to { m, today, base ->
            base?.let { dayOfMonth(m.groupValues[1].toInt(), today, it) to null }
        },
    )

    // ── 時間 ──

    /** 「9點」「9點半」「9點15分」「9點1刻」「14:30」加上時段 → 從當天 00:00 起的分鐘數；不合理回傳 null。 */
    private fun clock(text: String, period: String?): Int? {
        val m = CLOCK_PARTS.matchEntire(text.trim()) ?: return null
        val h = m.groupValues[1].toInt()
        val min = when {
            m.groupValues[2].isNotEmpty() -> m.groupValues[2].toInt()
            m.groupValues[3].isNotEmpty() -> 30
            m.groupValues[4].isNotEmpty() -> m.groupValues[4].toInt() * 15
            m.groupValues[5].isNotEmpty() -> m.groupValues[5].toInt()
            else -> 0
        }
        if (h > 24 || min > 59) return null
        return hour24(h, period) * 60 + min
    }

    private fun hour24(h: Int, period: String?): Int = when (period) {
        // 沒說上午下午：1～6 點當下午（一般行程多在白天）
        null -> if (h in 1..6) h + 12 else h
        "凌晨", "清晨" -> if (h == 12) 0 else h
        "早上", "早晨", "上午", "早" -> h
        "中午" -> if (h in 1..5) h + 12 else h
        "午後", "下午", "傍晚" -> if (h < 12) h + 12 else h
        "晚上", "晚間", "夜裡", "夜間" -> if (h < 12) h + 12 else if (h == 12) 24 else h
        "半夜", "深夜" -> if (h == 12) 0 else if (h in 6..11) h + 12 else h
        else -> h
    }

    private fun duration(m: MatchResult): Int {
        if (m.groupValues[4].isNotEmpty()) return m.groupValues[4].toInt().coerceIn(1, 24 * 60)
        val hours = m.groupValues[1].toIntOrNull() ?: if (m.groupValues[2].isNotEmpty()) 0 else 1
        val half = if (m.groupValues[2].isNotEmpty()) 30 else 0
        return (hours * 60 + half).coerceIn(5, 24 * 60)
    }

    // ── 標題 ──

    /** 剩下的片段各自去掉頭尾的贅字與標點，再接起來。 */
    private fun title(s: String): String {
        val pieces = s.split(CUT).map { clean(it) }.filter { it.isNotEmpty() }
        val sb = StringBuilder()
        for (p in pieces) {
            // 英文字之間補一個空白，中文直接接
            if (sb.isNotEmpty() && sb.last().isLetterOrDigit() && sb.last().code < 128 && p.first().code < 128 && p.first().isLetterOrDigit()) sb.append(' ')
            sb.append(p)
        }
        return sb.toString().take(CalendarRepository.MAX_TITLE)
    }

    private fun clean(piece: String): String {
        var t = piece
        while (true) {
            val before = t
            t = t.trim { it.isWhitespace() || it in PUNCT }
            if (t in JUNK) return ""
            COMMAND.firstOrNull { t.startsWith(it) }?.let { t = t.removePrefix(it) }
            TRAIL.firstOrNull { t.endsWith(it) }?.let { t = t.removeSuffix(it) }
            if (t == before) return t
        }
    }

    private fun String.cut(from: Int, to: Int): String = substring(0, from) + CUT + substring(to)

    private fun Regex.matchAt(s: String, index: Int): MatchResult? = find(s, index)?.takeIf { it.range.first == index }

    // ── 規則 ──

    private val CN_DIGITS = mapOf(
        '零' to 0, '〇' to 0, '一' to 1, '二' to 2, '兩' to 2, '两' to 2, '三' to 3,
        '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
    )
    private const val CN = "[零〇一二兩两三四五六七八九十]"

    /** 後面接日期時間單位的中文數字；「早一點」「一點點」「一日遊」這類不是時間的不轉。 */
    private val CN_BEFORE_UNIT = Regex(
        """(?<![早晚快慢多少好差零〇一二兩两三四五六七八九十])$CN+(?=\s*(?:年|月|號|号|[點点](?![點点兒儿])|時|时|刻|分鐘|分钟|天\s*[後后]|[個个]\s*(?:半\s*)?(?:小時|小时|鐘頭|钟头)|(?:半\s*)?(?:小時|小时|鐘頭|钟头)))""",
    )

    /** 「十二月一日」的「一」。 */
    private val CN_DAY_AFTER_MONTH = Regex("""(?<=月)\s*$CN+(?=\s*[日號号])""")

    /** 「三點十五」的「十五」（點的前面要是數字）。 */
    private val CN_AFTER_POINT = Regex("""(?<=\d[點点]|\d\s[點点])\s*$CN+""")

    private val ALARM = Regex("""要響|響鈴|開鬧鐘|設鬧鐘|鬧鐘|要叫我|叫我""")
    private val REPEAT = Regex("""每\s*個?\s*(?:天|日|年|月(?:\s*\d{1,2}\s*[號号日])?|(?:星期|週|周|禮拜|礼拜)\s*[一二三四五六日天七1-7]?)|天天|週週""")
    private val ALL_DAY = Regex("""整天|全天|一整天""")
    private val CONNECTOR_AT = Regex("""\s*(?:到|至|~|-|－|—)\s*""")

    private const val PERIODS = "凌晨|清晨|早上|早晨|上午|中午|午後|下午(?!茶)|傍晚|晚上|晚間|夜裡|夜間|半夜|深夜"
    private const val CLOCK = """\d{1,2}\s*:\s*\d{2}|\d{1,2}\s*[點点時时](?:\s*(?:半|[13]\s*刻|\d{1,2}\s*分?))?"""
    private val TIME_RANGE = Regex("""(?:($PERIODS|早)\s*)?($CLOCK)\s*(?:到|至|~|-|－|—)\s*(?:($PERIODS)\s*)?($CLOCK)""")
    private val TIME_ONE = Regex("""(?:($PERIODS|早)\s*)?($CLOCK)""")
    private val CLOCK_PARTS = Regex("""(\d{1,2})\s*(?::\s*(\d{2})|[點点時时]\s*(?:(半)|([13])\s*刻|(\d{1,2})\s*分?)?)""")
    private val PERIOD_ALONE = Regex(PERIODS)
    private val DEFAULT_HOUR = mapOf(
        "凌晨" to 5, "清晨" to 6, "早上" to 9, "早晨" to 9, "上午" to 9, "中午" to 12, "午後" to 14, "下午" to 14,
        "傍晚" to 17, "晚上" to 19, "晚間" to 19, "夜裡" to 21, "夜間" to 21, "半夜" to 23, "深夜" to 23,
    )
    private val DURATION = Regex("""(\d{1,2})?\s*[個个]?\s*(半)?\s*[個个]?\s*(小時|小时|鐘頭|钟头)|(\d{1,4})\s*(?:分鐘|分钟)""")

    private const val PUNCT = "，,。.、！!？?；;：:~-－—（）()「」『』\"'"
    /** 句首的指令詞（「建立資安會議」的「建立」）；長的放前面。 */
    private val COMMAND = listOf(
        "請幫我建立", "請幫我新增", "幫我建立一個", "幫我新增一個", "幫我建立", "幫我新增", "幫我安排", "請幫我", "幫我",
        "建立一個", "新增一個", "安排一個", "設一個", "加一個", "建立", "新增", "加入", "安排", "設定", "提醒我", "記得", "麻煩", "我要", "我想", "請",
        "的", // 「下午兩點的會議」拿掉時間後剩「的會議」
    )

    /** 整段只剩這些字就丟掉（「在12月1日」的「在」、日期區間中間的「到」）。 */
    private val JUNK = setOf("在", "於", "從", "是", "有", "要", "的", "把", "到", "至", "一個", "個", "和", "跟")
    private val TRAIL = listOf("的行程", "行程", "的事件", "事件", "的提醒", "的", "吧", "喔", "哦", "囉")
}
