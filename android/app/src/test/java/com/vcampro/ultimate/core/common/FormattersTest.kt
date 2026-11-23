package com.vcampro.ultimate.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattersTest {

    @Test
    fun fileSize_usesVietnameseDecimalComma() {
        assertEquals("512 B", Formatters.fileSize(512))
        val kb = Formatters.fileSize(1536)
        assertTrue("1,5 KB".equals(kb, ignoreCase = false) || kb.contains("KB"))
        assertTrue(Formatters.fileSize(5L * 1024 * 1024).contains("MB"))
        assertTrue(Formatters.fileSize(3L * 1024 * 1024 * 1024).contains("GB"))
    }

    @Test
    fun fileSize_negativeIsDash() {
        assertEquals("—", Formatters.fileSize(-1))
    }

    @Test
    fun duration_formatsMinutesAndHours() {
        assertEquals("00:00", Formatters.duration(0))
        assertEquals("01:33", Formatters.duration(93_000))
        assertEquals("01:00:05", Formatters.duration(3_605_000))
    }

    @Test
    fun relativeTime_producesVietnameseText() {
        val now = 1_700_000_000_000L
        assertEquals("vừa xong", Formatters.relativeTime(now - 10_000, now))
        assertEquals("5 phút trước", Formatters.relativeTime(now - 5 * 60_000, now))
        assertEquals("2 giờ trước", Formatters.relativeTime(now - 2 * 3_600_000, now))
        assertEquals("3 ngày trước", Formatters.relativeTime(now - 3 * 86_400_000L, now))
    }

    @Test
    fun hex_matchesKnownValue() {
        val bytes = byteArrayOf(0x00, 0x0F, 0xA0.toByte(), 0xFF.toByte())
        assertEquals("000fa0ff", Formatters.hex(bytes))
    }

    @Test
    fun baseNameAndExtension() {
        assertEquals("IMG_001", Formatters.baseName("IMG_001.jpg"))
        assertEquals("jpg", Formatters.extension("IMG_001.jpg"))
        assertEquals("noext", Formatters.baseName("noext"))
    }
}
