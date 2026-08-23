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
        // فهرس المالك للاستعلام السريع عن مجموعات المالك
        runCatching {
            mongo.indexOps(GroupDocument::class.java)
                .createIndex(Index().on("ownerRedId", Sort.Direction.ASC))
        }
    }
}
