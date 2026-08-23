package com.red.server.calls

import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface LiveStreamRepository : MongoRepository<LiveStreamRecord, String> {
    fun findByBroadcasterId(broadcasterId: String): List<LiveStreamRecord>
}
