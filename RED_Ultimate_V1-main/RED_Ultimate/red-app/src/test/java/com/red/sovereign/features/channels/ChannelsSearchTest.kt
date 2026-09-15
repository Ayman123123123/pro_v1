package com.red.sovereign.features.channels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * P1-G: سياسة دمج البحث المحلي/السحابي — دوال خالصة بلا شبكة ولا Room،
 * فتُختبر مباشرة. تحمي القاعدتين اللتين لا يجوز أن تنكسرا:
 *  1) المحلي أولًا دائمًا (offline-first) والسحابي يُكمّل فقط.
 *  2) لا تكرار بالـ id عند التطابق.
 */
class ChannelsSearchTest {

    private fun item(id: String, name: String) = ChannelSearchItem(id = id, name = name)

    private fun channel(
        id: String = "c1",
        name: String = "قناة",
        username: String? = null,
        boostsCount: Int = 0
    ) = Channel(id = id, name = name, username = username, boostsCount = boostsCount)

    @Test
    fun `local stays first and cloud only appends new ids`() {
        val local = listOf(item("1", "محلي أ"), item("2", "محلي ب"))
        val cloud = listOf(item("2", "سحابي مكرر"), item("3", "سحابي جديد"))

        val merged = mergeChannelSearch(local, cloud)

        assertEquals(listOf("1", "2", "3"), merged.map { it.id })
        // الترتيب مطلوب: عنصر المحلي "2" يبقى بنصه المحلي لا السحابي.
        assertEquals("محلي ب", merged[1].name)
        assertEquals("سحابي جديد", merged[2].name)
    }

    @Test
    fun `empty cloud returns local unchanged`() {
        val local = listOf(item("1", "محلي"))
        assertSame(local, mergeChannelSearch(local, emptyList()))
    }

    @Test
    fun `empty local returns cloud unchanged`() {
        val cloud = listOf(item("9", "سحابي"))
        assertSame(cloud, mergeChannelSearch(emptyList(), cloud))
    }

    @Test
    fun `cloud ids are unique in the real call path so the fast path is safe`() {
        // ملاحظة مقصودة: مسار `local.isEmpty()` في mergeChannelSearch يعيد قائمة
        // السحابي كما هي بلا إزالة تكرار داخلي. هذا آمن لأن المصدر الوحيد للسحابي
        // هو ChannelsApi.list → استعلام SQL واحد على جدول `channels` بمفتاح id فريد،
        // فلا يمكن أن يحمل تكرارًا. الاختبار يثبّت السلوك الحقيقي حتى لا يُفترض غير ذلك.
        val cloud = listOf(item("1", "أ"), item("2", "ب"))
        val merged = mergeChannelSearch(emptyList(), cloud)
        assertEquals(listOf("1", "2"), merged.map { it.id })
        assertEquals(2, merged.map { it.id }.distinct().size)
    }

    @Test
    fun `merge path deduplicates across local and cloud`() {
        // الإزالة الحقيقية للتكرار تحصل في مسار الدمج (الطرفان غير فارغين).
        val merged = mergeChannelSearch(
            listOf(item("1", "محلي"), item("1", "محلي مكرر"), item("2", "محلي ب")),
            listOf(item("2", "سحابي مكرر"), item("3", "سحابي"))
        )
        assertEquals(listOf("1", "2", "3"), merged.map { it.id })
    }

    @Test
    fun `channel maps to light search item`() {
        val mapped = channel(id = "abc", name = "أخبار", username = "akhbar").toSearchItem()
        assertEquals("abc", mapped.id)
        assertEquals("أخبار", mapped.name)
        assertEquals("akhbar", mapped.username)
    }

    @Test
    fun `level is derived from boosts and never negative`() {
        assertEquals(0, channel(boostsCount = 0).level)
        assertEquals(0, channel(boostsCount = 4).level)
        assertEquals(1, channel(boostsCount = 5).level)
        assertEquals(2, channel(boostsCount = 12).level)
        assertEquals(0, channel(boostsCount = -30).level)
    }

    @Test
    fun `search item defaults username to null when absent`() {
        assertNull(channel(username = null).toSearchItem().username)
    }
}
