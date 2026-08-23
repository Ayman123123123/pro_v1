package com.red.sovereign.stories

import org.junit.Assert.assertEquals
import org.junit.Test

class StoryPresentationTest {
    @Test
    fun `story age renders Arabic minute and hour labels`() {
        val now = 1_725_000_000_000L

        assertEquals("منذ 5 دقيقة", storyAgeLabel((now - 5 * 60_000L).toString(), now))
        assertEquals("منذ 2 ساعة", storyAgeLabel((now - 2 * 3_600_000L).toString(), now))
    }

    @Test
    fun `story age handles invalid and future timestamps safely`() {
        val now = 1_725_000_000_000L

        assertEquals("منذ قليل", storyAgeLabel("غير صالح", now))
        assertEquals("الآن", storyAgeLabel((now + 60_000L).toString(), now))
    }
}
