package com.red.server.groups

import jakarta.annotation.PostConstruct
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.stereotype.Component

@Component
class GroupIndexInitializer(private val mongo: MongoTemplate) {
    @PostConstruct
    fun init() {
        // فهرس انتهاء الدعوات — تنظيف تلقائي للدعوات المنتهية (TTL 0 يعني حذف عند expiresAt)
        runCatching {
            mongo.indexOps(GroupInviteDocument::class.java)
                .createIndex(Index().on("expiresAt", Sort.Direction.ASC).expire(0))
        }
        // مركب لطلبات الانضمام — بحث سريع عن المعلقة حسب المجموعة
        runCatching {
            mongo.indexOps(GroupJoinRequestDocument::class.java)
                .createIndex(Index().on("groupId", Sort.Direction.ASC).on("status", Sort.Direction.ASC).on("createdAt", Sort.Direction.ASC))
        }
        // فهرس الأعضاء حسب redId للبحث السريع
        runCatching {
            mongo.indexOps(GroupMember::class.java)
                .createIndex(Index().on("redId", Sort.Direction.ASC))
        }
        // فهرس الأعضاء حسب المجموعة — response() وقوائم الأعضاء تُستعلم بـ groupId دائمًا
        runCatching {
            mongo.indexOps(GroupMember::class.java)
                .createIndex(Index().on("groupId", Sort.Direction.ASC).on("joinedAt", Sort.Direction.ASC))
        }
        // فهرس المالك للاستعلام السريع عن مجموعات المالك
        runCatching {
            mongo.indexOps(GroupDocument::class.java)
                .createIndex(Index().on("ownerRedId", Sort.Direction.ASC))
        }
        // سجل التدقيق حسب المجموعة والزمن — يُكتب في كل تغيير عضوية/دور/حظر
        runCatching {
            mongo.indexOps(GroupAudit::class.java)
                .createIndex(Index().on("groupId", Sort.Direction.ASC).on("at", Sort.Direction.DESC))
        }
        // الحظر حسب المجموعة + TTL للحظر المؤقت (null/غائب = لا حذف — آمن)
        runCatching {
            mongo.indexOps(GroupBan::class.java)
                .createIndex(Index().on("groupId", Sort.Direction.ASC).on("bannedAt", Sort.Direction.DESC))
        }
        runCatching {
            mongo.indexOps(GroupBan::class.java)
                .createIndex(Index().on("expiresAt", Sort.Direction.ASC).expire(0))
        }
        // استطلاعات المجموعة حسب المجموعة والزمن
        runCatching {
            mongo.indexOps(GroupPollDocument::class.java)
                .createIndex(Index().on("groupId", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC))
        }
        // علامات القراءة حسب المجموعة والتسلسل — استعلام readers() الدائم
        runCatching {
            mongo.indexOps(GroupReadMark::class.java)
                .createIndex(Index().on("groupId", Sort.Direction.ASC).on("lastReadSequence", Sort.Direction.DESC))
        }
    }
}
