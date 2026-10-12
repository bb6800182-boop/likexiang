package com.c520.likexiang.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class VoiceParserTest {
    private val now = LocalDateTime.of(2026, 10, 11, 10, 0) // 週日上午十點

    private fun p(text: String) = VoiceParser.parse(text, now, 60)

    private fun at(text: String) = LocalDateTime.parse(text)

    @Test
    fun userExample() {
        val r = p("在12月1日上午九點到12點，建立資安會議的行程")
        assertEquals("資安會議", r.title)
        assertEquals(at("2026-12-01T09:00"), r.start)
        assertEquals(at("2026-12-01T12:00"), r.end)
        assertFalse(r.allDay)
        assertFalse(r.alarm)
        assertTrue(r.complete)
    }

    @Test
    fun relativeDaysAndPeriods() {
        p("明天下午三點跟客戶開會").let {
            assertEquals("跟客戶開會", it.title)
            assertEquals(at("2026-10-12T15:00"), it.start)
            assertEquals(at("2026-10-12T16:00"), it.end) // 沒說結束：預設長度
        }
        assertEquals(at("2026-10-11T20:00"), p("今晚8點看電影").start)
        assertEquals(at("2026-10-12T12:30"), p("明天中午12點半午餐").start)
        assertEquals(at("2026-10-12T02:00"), p("凌晨兩點看流星").start)
        assertEquals(at("2026-10-12T09:15"), p("明天九點一刻晨會").start)
        assertEquals(at("2026-10-12T10:30"), p("明天十點三十分面試").start)
    }

    @Test
    fun timeRangesAndDurations() {
        p("明天上午10點到下午2點 研討會").let {
            assertEquals(at("2026-10-12T10:00"), it.start)
            assertEquals(at("2026-10-12T14:00"), it.end)
        }
        // 結束沒說上下午：跟著開始；「到12點」跨過午夜是隔天
        assertEquals(at("2026-10-14T00:00"), p("後天晚上10點到12點 看球賽").end)
        assertEquals(at("2026-10-12T15:30"), p("明天 14:00-15:30 專案會議").end)
        p("下午3點半開會兩個小時").let {
            assertEquals("開會", it.title)
            assertEquals(at("2026-10-11T15:30"), it.start)
            assertEquals(at("2026-10-11T17:30"), it.end)
        }
        assertEquals(at("2026-10-12T16:30"), p("明天下午三點開會一個半小時").end)
    }

    @Test
    fun timeWithoutPeriod() {
        assertEquals(at("2026-10-11T14:00"), p("兩點牙醫").start) // 1～6 點當下午
        assertEquals(at("2026-10-12T09:00"), p("9點開會").start) // 今天九點已過：明天
        assertEquals(at("2026-10-11T10:30"), p("十點半開會").start)
    }

    @Test
    fun weekdays() {
        assertEquals(at("2026-10-14T00:00"), p("下週三去看牙醫").start)
        assertTrue(p("下週三去看牙醫").allDay)
        assertEquals(at("2026-10-16T19:00"), p("星期五晚上7點聚餐").start)
        assertEquals(at("2026-10-20T00:00"), p("下下週二交報告").start)
        assertEquals(at("2026-10-17T00:00"), p("這個禮拜六爬山").start) // 這週六已過：接下來那個
    }

    @Test
    fun datesWithoutYearMoveToTheFuture() {
        assertEquals(at("2027-01-03T00:00"), p("1月3日 尾牙").start)
        assertEquals(at("2026-10-15T00:00"), p("15號繳卡費").start)
        assertEquals(at("2026-11-05T00:00"), p("5號繳房租").start)
        assertEquals(at("2026-11-05T00:00"), p("下個月5號繳學費").start)
        assertEquals(at("2026-12-25T19:00"), p("十二月二十五號晚上聖誕派對").start)
        assertEquals(at("2027-01-05T10:00"), p("2027年1月5日上午10點 看診").start)
        assertEquals(at("2026-12-01T09:00"), p("１２月１日上午９點 健檢").start)
    }

    @Test
    fun multiDayAllDay() {
        p("10月20日到22日 員工旅遊").let {
            assertEquals("員工旅遊", it.title)
            assertTrue(it.allDay)
            assertEquals(at("2026-10-20T00:00"), it.start)
            assertEquals(at("2026-10-23T00:00"), it.end)
        }
        assertEquals(at("2027-01-03T00:00"), p("12月30日到1月2日 旅行").end)
        assertTrue(p("明天整天訓練").allDay)
    }

    @Test
    fun alarmWords() {
        p("提醒我後天早上8點繳電費要響").let {
            assertEquals("繳電費", it.title)
            assertTrue(it.alarm)
        }
        p("明天早上7點叫我起床").let {
            assertEquals("起床", it.title)
            assertTrue(it.alarm)
        }
        assertFalse(p("明天下午三點跟客戶開會").alarm)
    }

    @Test
    fun titleCleanup() {
        assertEquals("會議", p("幫我新增一個明天下午兩點的會議").title)
        assertEquals("Meeting", p("Meeting 明天 10:30").title)
        assertEquals("下午茶", p("明天下午茶").title) // 「下午茶」不是時段
        assertEquals("一日遊", p("一日遊 下週六").title) // 「一日」不是日期
        assertEquals("雙十一", p("11月11日 雙十一").title)
    }

    @Test
    fun incompleteOpensTheEditor() {
        p("開會").let {
            assertNull(it.start)
            assertFalse(it.complete) // 沒有日期時間
        }
        assertFalse(p("明天下午3點").complete) // 沒有標題
        assertNull(p("早一點出門").start) // 「早一點」不是一點
        p("每週一早上9點開週會").let {
            assertTrue(it.repeating)
            assertEquals("開週會", it.title)
            assertFalse(it.complete) // 重複行程讓使用者在新增畫面設定
        }
    }

    @Test
    fun chineseNumbers() {
        assertEquals(12, VoiceParser.cnToInt("十二"))
        assertEquals(23, VoiceParser.cnToInt("二十三"))
        assertEquals(10, VoiceParser.cnToInt("十"))
        assertEquals(2, VoiceParser.cnToInt("兩"))
        assertEquals(2026, VoiceParser.cnToInt("二〇二六"))
        assertNull(VoiceParser.cnToInt("十十"))
    }

    @Test
    fun ignoresControlCharactersAndLongInput() {
        val r = p("明天\u0001下午三點\u0000開會" + "很".repeat(500))
        assertEquals(at("2026-10-12T15:00"), r.start)
        assertTrue(r.title.length <= VoiceParser.MAX_INPUT)
    }
}
