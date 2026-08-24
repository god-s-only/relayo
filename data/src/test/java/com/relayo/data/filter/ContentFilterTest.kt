package com.relayo.data.filter

import org.junit.Assert.*
import org.junit.Test

class ContentFilterTest {

    private val filter = DefaultContentFilter()

    @Test
    fun allowsCleanText() {
        assertTrue(filter.isAllowed("Hello world"))
        assertTrue(filter.isAllowed("Water point on 5th street is open"))
        assertTrue(filter.isAllowed("Classic example")) // should not trigger 'ass' inside classic
    }

    @Test
    fun blocksProfanity() {
        assertFalse(filter.isAllowed("This is fuck"))
        assertFalse(filter.isAllowed("FUCK"))
        assertFalse(filter.isAllowed("you are an ass"))
        assertFalse(filter.isAllowed("bitch please"))
    }

    @Test
    fun blocksPhrase() {
        assertFalse(filter.isAllowed("kill yourself"))
        assertFalse(filter.isAllowed("KYS is bad"))
        assertFalse(filter.isAllowed("You should kYs"))
    }

    @Test
    fun blocksHarmful() {
        assertFalse(filter.isAllowed("bomb threat"))
        assertFalse(filter.isAllowed("rape is a crime"))
    }

    @Test
    fun sanitizeReplacesWithStars() {
        val sanitized = filter.sanitize("fuck you bitch")
        assertTrue(sanitized.contains("****"))
        assertFalse(sanitized.contains("fuck"))
        assertFalse(sanitized.contains("bitch"))
    }

    @Test
    fun findViolationReturnsWord() {
        assertEquals("fuck", filter.findViolation("fuck this"))
        assertEquals("kill yourself", filter.findViolation("please kill yourself now"))
        assertNull(filter.findViolation("hello nice day"))
    }

    @Test
    fun allowsEmptyAndBlank() {
        assertTrue(filter.isAllowed(""))
        assertTrue(filter.isAllowed("   "))
    }
}
