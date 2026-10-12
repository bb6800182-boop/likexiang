package com.c520.likexiang.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmMarkerTest {

    @Test
    fun detectsMark() {
        assertTrue(AlarmMarker.has("⏰ 看牙醫"))
        assertTrue(AlarmMarker.has("看牙醫⏰"))
        assertTrue(AlarmMarker.has("⏰️ 看牙醫"))
        assertFalse(AlarmMarker.has("看牙醫"))
        assertFalse(AlarmMarker.has(null))
    }

    @Test
    fun stripRemovesEveryMarkAndOneFollowingSpace() {
        assertEquals("看牙醫", AlarmMarker.strip("⏰ 看牙醫"))
        assertEquals("看牙醫", AlarmMarker.strip("⏰️ 看牙醫"))
        assertEquals("看牙醫", AlarmMarker.strip("看牙醫 ⏰"))
        assertEquals("看牙醫", AlarmMarker.strip("⏰ ⏰ 看牙醫"))
        assertEquals("看 牙醫", AlarmMarker.strip("看 ⏰ 牙醫"))
        assertEquals("", AlarmMarker.strip("⏰"))
    }

    @Test
    fun addPrependsOnlyOnce() {
        assertEquals("⏰ 看牙醫", AlarmMarker.add("看牙醫"))
        assertEquals("⏰ 看牙醫", AlarmMarker.add("  看牙醫 "))
        assertEquals("看牙醫 ⏰", AlarmMarker.add("看牙醫 ⏰"))
    }
}

class AlarmRulesTest {

    @Test
    fun syncModeDependsOnOwnershipAndGuests() {
        assertEquals(SyncMode.TITLE, AlarmRules.syncMode(canEditTitle = true, hasOtherAttendees = false))
        assertEquals(SyncMode.LOCAL, AlarmRules.syncMode(canEditTitle = true, hasOtherAttendees = true))
        assertEquals(SyncMode.LOCAL, AlarmRules.syncMode(canEditTitle = false, hasOtherAttendees = false))
    }

    @Test
    fun localFlagOverridesTitle() {
        assertTrue(AlarmRules.isAlarmOn("看牙醫", true))
        assertFalse(AlarmRules.isAlarmOn("⏰ 看牙醫", false))
        assertTrue(AlarmRules.isAlarmOn("⏰ 看牙醫", null))
        assertFalse(AlarmRules.isAlarmOn("看牙醫", null))
    }

    @Test
    fun titleModeFollowsToggle() {
        assertEquals("⏰ 看牙醫", AlarmRules.titleToSave("看牙醫", "看牙醫", SyncMode.TITLE, alarmOn = true))
        assertEquals("看牙醫", AlarmRules.titleToSave("看牙醫", "⏰ 看牙醫", SyncMode.TITLE, alarmOn = false))
        assertEquals("⏰ 新行程", AlarmRules.titleToSave("新行程", null, SyncMode.TITLE, alarmOn = true))
        // 表單裡自己打了 ⏰ 但開關是關的：以開關為準
        assertEquals("看牙醫", AlarmRules.titleToSave("⏰ 看牙醫", null, SyncMode.TITLE, alarmOn = false))
    }

    @Test
    fun unchangedTitleIsSentBackAsIs() {
        // 使用者自己把 ⏰ 放在後面：內容沒改就不搬動，也不會多一次同步
        assertEquals("看牙醫 ⏰", AlarmRules.titleToSave("看牙醫", "看牙醫 ⏰", SyncMode.TITLE, alarmOn = true))
        assertEquals("看牙醫 ⏰", AlarmRules.titleToSave("看牙醫", "看牙醫 ⏰", SyncMode.LOCAL, alarmOn = false))
    }

    @Test
    fun localModeNeverAddsOrRemovesMark() {
        assertEquals("專案會議", AlarmRules.titleToSave("專案會議", "專案會議", SyncMode.LOCAL, alarmOn = true))
        assertEquals("⏰ 專案週會", AlarmRules.titleToSave("專案週會", "⏰ 專案會議", SyncMode.LOCAL, alarmOn = false))
    }
}
