package com.red.server.calls

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

@Document("call_forward_settings")
data class CallForwardDocument(
    @Id val userId: String,
    val always: ForwardRule = ForwardRule(),
    val busy: ForwardRule = ForwardRule(),
    val noAnswer: ForwardRule = ForwardRule(),
    val unreachable: ForwardRule = ForwardRule(),
    val updatedAt: Instant = Instant.now()
)

data class ForwardRule(val enabled: Boolean = false, val target: String = "")
data class ForwardSettings(
    val always: ForwardRule = ForwardRule(),
    val busy: ForwardRule = ForwardRule(),
    val noAnswer: ForwardRule = ForwardRule(),
    val unreachable: ForwardRule = ForwardRule()
)
data class ForwardStatusResponse(val settings: ForwardSettings, val updatedAt: Long)

@RestController
@RequestMapping("/api/calls/forward")
class CallForwardController(private val mongo: MongoTemplate) {

    @GetMapping("/status")
    fun getStatus(auth: Authentication): ResponseEntity<ForwardStatusResponse> {
        val userId = auth.name
        val doc = mongo.findById(userId, CallForwardDocument::class.java)
            ?: CallForwardDocument(userId = userId)
        return ResponseEntity.ok(
            ForwardStatusResponse(
                settings = ForwardSettings(doc.always, doc.busy, doc.noAnswer, doc.unreachable),
                updatedAt = doc.updatedAt.toEpochMilli()
            )
        )
    }

    @PutMapping
    fun update(@RequestBody settings: ForwardSettings, auth: Authentication): ResponseEntity<ForwardStatusResponse> {
        val userId = auth.name
        validate(settings)
        val now = Instant.now()
        val query = Query(Criteria.where("_id").`is`(userId))
        val update = Update()
            .set("always", settings.always)
            .set("busy", settings.busy)
            .set("noAnswer", settings.noAnswer)
            .set("unreachable", settings.unreachable)
            .set("updatedAt", now)
        mongo.upsert(query, update, CallForwardDocument::class.java)
        return ResponseEntity.ok(ForwardStatusResponse(settings, now.toEpochMilli()))
    }

    @DeleteMapping
    fun disableAll(auth: Authentication): ResponseEntity<Map<String, Any>> {
        val userId = auth.name
        val query = Query(Criteria.where("_id").`is`(userId))
        val update = Update()
            .set("always", ForwardRule(false, ""))
            .set("busy", ForwardRule(false, ""))
            .set("noAnswer", ForwardRule(false, ""))
            .set("unreachable", ForwardRule(false, ""))
            .set("updatedAt", Instant.now())
        mongo.upsert(query, update, CallForwardDocument::class.java)
        return ResponseEntity.ok(mapOf("status" to "disabled"))
    }

    private fun validate(s: ForwardSettings) {
        listOf(s.always, s.busy, s.noAnswer, s.unreachable).forEach { rule ->
            if (rule.enabled) {
                require(rule.target.matches(Regex("^[0-9+]{6,20}$"))) { "Invalid forward target: ${rule.target}" }
            }
        }
    }
}
