package com.red.server

import com.mongodb.client.DistinctIterable
import com.mongodb.client.MongoCollection
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.database.ChannelDocument
import com.red.server.groups.GroupDocument
import com.red.server.media.MediaGrantService
import com.red.server.media.MediaService
import com.red.server.social.Poll
import com.red.server.social.PollOption
import com.red.server.social.PostDocument
import com.red.server.social.PostMedia
import com.red.server.social.PostVisibility
import com.red.server.stories.StoryDocument
import com.red.server.storage.OrphanCleanupScheduler
import com.red.server.storage.StorageMonitorService
import io.minio.ListObjectsArgs
import io.minio.MinioClient
import io.minio.RemoveObjectArgs
import io.minio.Result
import io.minio.messages.Item
import org.bson.Document
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Duration
import java.time.Instant

/**
 * اختبارات حقيقية ( mocks فقط — لا حذف حقيقي أبدًا):
 * حماية المراجع (استعادة)، dryRun الافتراضي، فترة السماح، وبوابة الجردة الناقصة.
 */
class StorageOrphanTest {

    private fun minioWith(vararg names: String): MinioClient {
        val minio = mock<MinioClient>()
        val results = names.map { name ->
            val item = mock<Item>()
            whenever(item.objectName()).thenReturn(name)
            val res = mock<Result<Item>>()
            whenever(res.get()).thenReturn(item)
            res
        }
        whenever(minio.listObjects(any<ListObjectsArgs>())).thenReturn(results)
        return minio
    }

    private fun distinctOf(vararg keys: String): DistinctIterable<String> {
        val iterable = mock<DistinctIterable<String>>()
        whenever(iterable.iterator()).thenReturn(keys.toList().iterator())
        return iterable
    }

    private fun oldSpy(minio: MinioClient, vararg unknown: String): MediaService {
        val svc = spy(MediaService(minio, "test-bucket"))
        val old = Instant.now().minus(Duration.ofDays(30))
        doReturn(old).whenever(svc).objectLastModified(any())
        unknown.forEach { key ->
            doReturn(null).whenever(svc).objectLastModified(eq(key))
        }
        return svc
    }

    // ── 1) منطق الترشيح النقي ──────────────────────────────────────────
    @Test
    fun `findOrphanKeys detects unreferenced`() {
        val svc = MediaService(mock<MinioClient>(), "test-bucket")
        val all = listOf("users/a/1.jpg", "users/a/2.jpg", "users/a/3.jpg", "thumbs/users/a/1.jpg")
        val referenced = setOf("users/a/1.jpg", "thumbs/users/a/1.jpg")
        assertEquals(listOf("users/a/2.jpg", "users/a/3.jpg"), svc.findOrphanKeys(all, referenced))
    }

    @Test
    fun `no orphans when all referenced`() {
        val svc = MediaService(mock<MinioClient>(), "test-bucket")
        assertTrue(svc.findOrphanKeys(listOf("a", "b"), setOf("a", "b")).isEmpty())
    }

    // ── 2) dryRun الافتراضي: معاينة بلا حذف ────────────────────────────
    @Test
    fun `dryRun default holds candidates without deleting`() {
        val minio = minioWith("users/u/orphan.jpg", "users/u/kept.jpg")
        val svc = oldSpy(minio)
        // الدالة ثنائية الوسائط: dryRun=true افتراضيًا
        val preview = svc.deleteOrphans(setOf("users/u/kept.jpg"))
        assertEquals(listOf("users/u/orphan.jpg"), preview)
        verify(minio, never()).removeObject(any<RemoveObjectArgs>())
    }

    @Test
    fun `dryRun true explicitly never deletes`() {
        val minio = minioWith("users/u/orphan.jpg")
        val svc = oldSpy(minio)
        val preview = svc.deleteOrphans(emptySet(), dryRun = true, gracePeriod = Duration.ZERO)
        assertEquals(listOf("users/u/orphan.jpg"), preview)
        verify(minio, never()).removeObject(any<RemoveObjectArgs>())
    }

    // ── 3) الحذف الحقيقي: القديم اليتيم فقط ────────────────────────────
    @Test
    fun `real run deletes only old true orphans`() {
        val minio = minioWith("users/u/old-orphan.jpg", "users/u/kept.jpg", "users/u/fresh.jpg", "users/u/unknown.jpg")
        val svc = spy(MediaService(minio, "test-bucket"))
        val now = Instant.now()
        doReturn(now.minus(Duration.ofDays(30))).whenever(svc).objectLastModified(eq("users/u/old-orphan.jpg"))
        doReturn(now.minus(Duration.ofDays(30))).whenever(svc).objectLastModified(eq("users/u/kept.jpg"))
        doReturn(now.minus(Duration.ofHours(1))).whenever(svc).objectLastModified(eq("users/u/fresh.jpg"))
        doReturn(null).whenever(svc).objectLastModified(eq("users/u/unknown.jpg"))

        val deleted = svc.deleteOrphans(
            setOf("users/u/kept.jpg"), dryRun = false, gracePeriod = Duration.ofDays(7)
        )
        // القديم اليتيم فقط؛ الجديد (سماح) والمجهول العمر (fail-closed) محميّان
        assertEquals(listOf("users/u/old-orphan.jpg"), deleted)
        verify(minio, times(1)).removeObject(any<RemoveObjectArgs>())
    }

    // ── 4) الجردة الشاملة بأسماء الحقول الصحيحة ────────────────────────
    private fun fullMocks(): Quad {
        val mongo = mock<MongoTemplate>()
        val jdbc = mock<JdbcTemplate>()
        val grants = mock<MediaGrantService>()

        val post = PostDocument(
            id = "p1", authorId = "a", authorRedId = "R1", authorUsername = "u",
            authorDisplayName = "d", text = "t", visibility = PostVisibility.PUBLIC,
            media = listOf(PostMedia("users/u/post1.jpg", "image/jpeg")),
            poll = Poll(listOf(PollOption("o1", "x", 0, "/api/media/users/u/poll1.png")), expiresAt = null)
        )
        whenever(mongo.find(any<Query>(), eq(PostDocument::class.java))).thenReturn(listOf(post))

        val story = StoryDocument(
            id = "s1", ownerId = "o", ownerRedId = "R", ownerUsername = "u",
            ownerDisplayName = "d", mediaKey = "users/u/story1.mp4", mediaType = "VIDEO",
            caption = null, expiresAt = Instant.now().plusSeconds(3600)
        )
        whenever(mongo.find(any<Query>(), eq(StoryDocument::class.java))).thenReturn(listOf(story))

        val group = GroupDocument(id = "g1", name = "n", description = null, ownerRedId = "R", avatarMediaKey = "users/u/group1.png")
        whenever(mongo.find(any<Query>(), eq(GroupDocument::class.java))).thenReturn(listOf(group))

        val channel = ChannelDocument(id = "c1", name = "n", ownerId = "o", avatarMediaKey = "users/u/channel1.png")
        whenever(mongo.find(any<Query>(), eq(ChannelDocument::class.java))).thenReturn(listOf(channel))

        listOf(
            "messages" to "users/u/msg1.jpg",
            "group_messages" to "users/u/gmsg1.jpg",
            "channel_messages" to "users/u/cmsg1.jpg"
        ).forEach { (collection, key) ->
            val coll = mock<MongoCollection<Document>>()
            whenever(coll.distinct(eq("attachments.mediaKey"), eq(String::class.java)))
                .thenReturn(distinctOf(key))
            whenever(mongo.getCollection(collection)).thenReturn(coll)
        }

        whenever(jdbc.queryForList(any<String>(), eq(String::class.java)))
            .thenReturn(listOf("users/u/avatar1.png"))
        whenever(grants.listActiveGrantedKeys()).thenReturn(setOf("users/u/granted1.jpg"))

        return Quad(mongo, jdbc, grants)
    }

    @Test
    fun `inventory covers every live reference source`() {
        val (mongo, jdbc, grants) = fullMocks()
        val scheduler = OrphanCleanupScheduler(
            mock<StorageMonitorService>(), mock<MediaService>(), mongo, jdbc, grants,
            dryRunDefault = true, graceDays = 7
        )
        val inventory = scheduler.collectReferencedMediaKeys()
        assertTrue(inventory.complete, "failures: ${inventory.failures}")
        listOf(
            "users/u/post1.jpg", "users/u/poll1.png", // تطبيع /api/media/ بادئة
            "users/u/story1.mp4",
            "users/u/group1.png", // avatarMediaKey لا avatarKey
            "users/u/channel1.png",
            "users/u/msg1.jpg", "users/u/gmsg1.jpg", "users/u/cmsg1.jpg", // attachments.mediaKey
            "users/u/avatar1.png", // users.avatar_url (JDBC)
            "users/u/granted1.jpg" // media_grants (JDBC لا Mongo)
        ).forEach { key ->
            assertTrue(inventory.keys.contains(key), "missing $key")
            assertTrue(inventory.keys.contains("thumbs/$key"), "missing thumbs/$key")
        }
    }

    // ── 5) اختبار الاستعادة: المراجع لا تُحذف أبدًا ────────────────────
    @Test
    fun `restore pipeline never deletes referenced keys`() {
        val (mongo, jdbc, grants) = fullMocks()
        val scheduler = OrphanCleanupScheduler(
            mock<StorageMonitorService>(), mock<MediaService>(), mongo, jdbc, grants,
            dryRunDefault = false, graceDays = 0
        )
        val inventory = scheduler.collectReferencedMediaKeys()
        assertTrue(inventory.complete)

        val orphan = "users/u/true-orphan.jpg"
        val minio = minioWith(*(inventory.keys.filter { !it.startsWith("thumbs/") }.toTypedArray()), orphan)
        val svc = oldSpy(minio) // كل الأعمار قديمة مؤكدة — لا حماية زمنية
        val deleted = svc.deleteOrphans(inventory.keys, dryRun = false, gracePeriod = Duration.ZERO)

        assertEquals(listOf(orphan), deleted)
        verify(minio, times(1)).removeObject(any<RemoveObjectArgs>())
    }

    // ── 6) الجردة الناقصة تمنع أي حذف (fail-closed) ────────────────────
    @Test
    fun `incomplete inventory blocks all deletion`() {
        val mongo = mock<MongoTemplate>()
        whenever(mongo.find(any<Query>(), eq(PostDocument::class.java)))
            .thenThrow(RuntimeException("mongo down"))
        val jdbc = mock<JdbcTemplate>()
        val grants = mock<MediaGrantService>()
        val storage = mock<StorageMonitorService>()
        whenever(storage.getLocalUsageStats()).thenReturn(mapOf("media_files" to 1L, "database_records" to 1L))
        val minio = minioWith("users/u/orphan.jpg")

        val scheduler = OrphanCleanupScheduler(
            storage, MediaService(minio, "test-bucket"), mongo, jdbc, grants,
            dryRunDefault = false, graceDays = 7 // حذف مفعّل نظريًا — البوابة يجب أن تمنعه
        )
        scheduler.dailyOrphanScan()
        verify(minio, never()).removeObject(any<RemoveObjectArgs>())
        verify(minio, never()).listObjects(any<ListObjectsArgs>())
    }

    // ── 7) منح MediaGrantService: السارية فقط ──────────────────────────
    @Test
    fun `grant service lists active granted keys`() {
        val jdbc = mock<JdbcTemplate>()
        whenever(jdbc.queryForList(any<String>(), eq(String::class.java)))
            .thenReturn(listOf("users/u/g1.jpg", "", "users/u/g2.jpg"))
        val svc = MediaGrantService(mock<MediaService>(), mock<UserAccountRepository>(), jdbc)
        assertEquals(setOf("users/u/g1.jpg", "users/u/g2.jpg"), svc.listActiveGrantedKeys())
    }

    // ── 8) المهلة: انتهاؤها يوقف كل شيء بلا حذف ─────────────────────────
    @Test
    fun `expired deadline stops deletion without removing anything`() {
        val minio = minioWith("users/u/orphan.jpg")
        val svc = oldSpy(minio)
        val past = Instant.now().minusSeconds(60)
        val result = svc.deleteOrphans(emptySet(), dryRun = false, gracePeriod = Duration.ZERO, deadline = past)
        assertTrue(result.isEmpty(), "expired deadline must yield nothing")
        verify(minio, never()).removeObject(any<RemoveObjectArgs>())
    }

    @Test
    fun `inventory with expired deadline is incomplete and blocks scan`() {
        val (mongo, jdbc, grants) = fullMocks()
        val scheduler = OrphanCleanupScheduler(
            mock<StorageMonitorService>(), mock<MediaService>(), mongo, jdbc, grants,
            dryRunDefault = false, graceDays = 7
        )
        val inventory = scheduler.collectReferencedMediaKeys(Instant.now().minusSeconds(60))
        assertFalse(inventory.complete, "expired inventory must be incomplete")
        assertTrue(inventory.failures.any { it.contains("timeout") }, "failures: ${inventory.failures}")
    }

    private data class Quad(
        val mongo: MongoTemplate,
        val jdbc: JdbcTemplate,
        val grants: MediaGrantService
    )
}
