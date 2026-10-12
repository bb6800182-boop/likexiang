package com.c520.likexiang.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodecTest {

    @Test
    fun roundTrips() {
        val offsets = mapOf(1L to 10, 42L to 0)
        assertEquals(offsets, Codec.decodeLongInt(Codec.encodeLongInt(offsets)))
        val flags = mapOf(7L to true, 8L to false)
        assertEquals(flags, Codec.decodeLongBool(Codec.encodeLongBool(flags)))
        val snoozes = mapOf("5@1760000000000" to 1760000600000L)
        assertEquals(snoozes, Codec.decodeStringLong(Codec.encodeStringLong(snoozes)))
    }

    @Test
    fun ignoresGarbage() {
        assertEquals(emptyMap<Long, Int>(), Codec.decodeLongInt(null))
        assertEquals(emptyMap<Long, Int>(), Codec.decodeLongInt(""))
        assertEquals(mapOf(3L to 5), Codec.decodeLongInt("x=1;3=5;4=;=9;5=abc"))
        assertEquals(mapOf(1L to true), Codec.decodeLongBool("1=1;2=yes"))
        assertEquals(emptyMap<String, Long>(), Codec.decodeStringLong("a=b"))
    }

    @Test
    fun unsafeKeysAreDropped() {
        assertTrue(Codec.isSafeKey("5@1760000000000"))
        assertFalse(Codec.isSafeKey("a=b"))
        assertFalse(Codec.isSafeKey("a;b"))
        assertFalse(Codec.isSafeKey(""))
        assertEquals("", Codec.encodeStringLong(mapOf("a;b" to 1L)))
    }

    @Test
    fun beginOfKey() {
        assertEquals(1760000000000L, Codec.beginOf("5@1760000000000"))
        assertNull(Codec.beginOf("5"))
        assertNull(Codec.beginOf("5@abc"))
    }
}
