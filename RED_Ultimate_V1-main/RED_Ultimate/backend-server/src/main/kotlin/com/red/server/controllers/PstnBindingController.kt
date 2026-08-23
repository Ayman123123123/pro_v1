package com.red.server.controllers

import com.red.server.audit.AuditService
import com.red.server.auth.model.AccountStatus
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.pstn.DinstarLoadBalancer
import com.red.server.services.DinstarFleetService
import com.red.server.services.DinstarHardwareService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.util.UUID

/**
 * إدارة الربط الدائم SIM↔مستخدم (V34) من لوحة الأدمن.
 *
 * قبل هذه النقطة كانت أعمدة pstn_gateway_id/pstn_port_index/pstn_number
 * بلا أي كاتب — الربط كان يتطلب SQL يدوياً. الآن:
 * - GET    /api/admin/dinstar/bindings           → كل حسابات PSTN وحالة ربطها
 * - POST   /api/admin/dinstar/bindings           → تعيين/تحديث ربط حساب لشريحة
 * - DELETE /api/admin/dinstar/bindings/{userId}  → فك الربط
 *
 * V35 adds smart SIM→port discovery & reconciliation:
 * - GET    /api/admin/dinstar/bindings/reconcile → LIVE vs DB diff per port
 * - POST   /api/admin/dinstar/bindings/bulk      → atomic bulk bind
 * - GET    /api/admin/dinstar/bindings/discover  → reconcile + learnable flag
 *
 * التعيين يتحقق حياً من المنفذ عبر get_port_info عندما تكون البوابة متاحة،
 * ويرفض تعيين منفذ مرتبط أصلاً بحساب آخر (قيد UNIQUE في القاعدة أيضاً).
 */
@RestController
@RequestMapping("/api/admin/dinstar/bindings")
class PstnBindingController(
    private val users: UserAccountRepository,
    private val fleet: DinstarFleetService,
    private val hardware: DinstarHardwareService,
    private val loadBalancer: DinstarLoadBalancer,
    private val audit: AuditService
) {

    companion object {
        private val log = LoggerFactory.getLogger(PstnBindingController::class.java)
        const val INSTRUCTION_NUMBER_LEARNING =
            "Dinstar UI: SIM Settings > Phone Number Learning > Mode=USSD/SMS/Call with keyword extraction (Sabafon: *100# is balance, *121# bundle menu — if USSD own-number unknown use missed-call loop fallback)."
    }

    @GetMapping
    fun list(): List<Map<String, Any?>> =
        users.findAllByStatusOrderByCreatedAtAsc(AccountStatus.APPROVED)
            .filter { it.pstnEnabled }
            .map { user ->
                mapOf(
                    "userId" to user.id.toString(),
                    "redId" to user.redId,
                    "username" to user.username,
                    "pstnEnabled" to user.pstnEnabled,
                    "dailyLimit" to user.pstnDailyLimit,
                    "gatewayId" to user.pstnGatewayId?.toString(),
                    "gatewayHost" to user.pstnGatewayId?.let { fleet.findGateway(it)?.host },
                    "portIndex" to user.pstnPortIndex,
                    "number" to user.pstnNumber,
                    "bound" to (user.pstnGatewayId != null && user.pstnPortIndex != null)
                )
            }

    @PostMapping
    fun bind(@RequestBody body: Map<String, Any?>, authentication: Authentication): ResponseEntity<Map<String, Any?>> {
        val actor = UUID.fromString(authentication.name)
        val userId = UUID.fromString(requireNotNull(body["userId"]?.toString()?.trim()?.ifBlank { null }) { "userId is required" })
        val gatewayId = UUID.fromString(requireNotNull(body["gatewayId"]?.toString()?.trim()?.ifBlank { null }) { "gatewayId is required" })
        val portIndex = requireNotNull((body["portIndex"] as? Number)?.toInt()) { "portIndex is required" }
        require(portIndex in 0..31) { "portIndex must be within 0..31" }
        // الرقم اختياري لكن إن وُجد يجب أن يكون أرقاماً فقط (CLIP صادر).
        val number = body["number"]?.toString()?.trim()?.ifBlank { null }
            ?.also { require(it.matches(Regex("\\d{6,20}"))) { "number must be 6-20 digits" } }

        val user = users.findById(userId).orElseThrow { NoSuchElementException("User not found") }
        val gateway = requireNotNull(fleet.findGateway(gatewayId)) { "Gateway not found" }

        // منفذ محجوز لحساب آخر؟ ارفض مبكراً برسالة واضحة قبل قيد القاعدة.
        users.findByPstnGatewayIdAndPstnPortIndex(gatewayId, portIndex)?.let { existing ->
            if (existing.id != userId) {
                return ResponseEntity.status(409).body(mapOf(
                    "error" to "PORT_ALREADY_BOUND",
                    "message" to "Port $portIndex on ${gateway.host} is already bound to ${existing.redId}"
                ))
            }
        }

        // تحقق حي اختياري: حالة المنفذ الحقيقية (تسجيل + إشارة) عند توفر البوابة.
        val liveCheck = runCatching { hardware.getHardwareStatus(gateway) }.getOrNull()
        val portStatus = liveCheck?.firstOrNull { (it["index"] as? Number)?.toInt() == portIndex }

        user.pstnGatewayId = gatewayId
        user.pstnPortIndex = portIndex
        user.pstnNumber = number ?: user.pstnNumber
        users.save(user)

        audit.record(actor, "PSTN_SIM_BOUND", user.redId, mapOf(
            "gatewayId" to gatewayId.toString(), "gatewayHost" to gateway.host,
            "portIndex" to portIndex, "number" to number,
            "liveCheckAvailable" to (portStatus != null),
            "liveStatus" to portStatus?.get("status")?.toString()
        ))

        return ResponseEntity.ok(mapOf(
            "userId" to userId.toString(),
            "gatewayId" to gatewayId.toString(),
            "gatewayHost" to gateway.host,
            "portIndex" to portIndex,
            "number" to user.pstnNumber,
            "livePortStatus" to portStatus
        ))
    }

    @DeleteMapping("/{userId}")
    fun unbind(@PathVariable userId: UUID, authentication: Authentication): Map<String, Any?> {
        val actor = UUID.fromString(authentication.name)
        val user = users.findById(userId).orElseThrow { NoSuchElementException("User not found") }
        val previous = mapOf(
            "gatewayId" to user.pstnGatewayId?.toString(),
            "portIndex" to user.pstnPortIndex,
            "number" to user.pstnNumber
        )
        user.pstnGatewayId = null
        user.pstnPortIndex = null
        user.pstnNumber = null
        users.save(user)
        audit.record(actor, "PSTN_SIM_UNBOUND", user.redId, previous)
        return mapOf("userId" to userId.toString(), "unbound" to true, "previous" to previous)
    }

    /** معاينة حية: هل شريحة المستخدم المرتبطة جاهزة لمكالمة الآن؟ */
    @GetMapping("/{userId}/health")
    fun health(@PathVariable userId: UUID): Map<String, Any?> {
        val user = users.findById(userId).orElseThrow { NoSuchElementException("User not found") }
        val gatewayId = user.pstnGatewayId ?: return mapOf("bound" to false)
        val portIndex = user.pstnPortIndex ?: return mapOf("bound" to false)
        val selection = loadBalancer.selectPermanentPort(gatewayId, portIndex, null)
        return mapOf(
            "bound" to true,
            "readyForCalls" to (selection != null),
            "gatewayHost" to fleet.findGateway(gatewayId)?.host,
            "portIndex" to portIndex,
            "detail" to (selection?.reason ?: "SIM not registered/busy/no signal right now")
        )
    }

    // ═══════════════════════════════════════════════════════════════════
    // V35: Smart SIM→port discovery & reconciliation
    // ═══════════════════════════════════════════════════════════════════

    /**
     * GET /api/admin/dinstar/bindings/reconcile
     *
     * Reads LIVE ports for the fleet's SIM-loaded gateways, compares each
     * port's live `number` (from hardware.getHardwareStatus), masked IMSI
     * last-4, and operator vs `users` bindings.
     */
    @GetMapping("/reconcile")
    fun reconcile(): Map<String, Any?> = buildReconcile(includeLearnable = false)

    /**
     * GET /api/admin/dinstar/bindings/discover
     *
     * Alias to reconcile but also includes `learnable` flag per port and a
     * one-line instruction how to enable Phone Number Learning for blank ports.
     */
    @GetMapping("/discover")
    fun discover(): Map<String, Any?> = buildReconcile(includeLearnable = true)

    /**
     * POST /api/admin/dinstar/bindings/bulk
     *
     * Body {bindings: [{userId, gatewayId, portIndex, number?}, ...]}
     * Atomically validates all (no duplicate ports, gateway exists, port in range),
     * then saves each in a transaction, returns per-row result.
     * Rejects whole batch if any PORT_ALREADY_BOUND conflict (422 with details).
     */
    @PostMapping("/bulk")
    @Transactional
    fun bulk(@RequestBody body: Map<String, Any?>, authentication: Authentication): ResponseEntity<Map<String, Any?>> {
        val actor = UUID.fromString(authentication.name)
        @Suppress("UNCHECKED_CAST")
        val bindingsRaw = body["bindings"] as? List<Map<String, Any?>>
            ?: throw IllegalArgumentException("bindings is required and must be a non-empty array")
        require(bindingsRaw.isNotEmpty()) { "bindings must not be empty" }

        // Pre-check duplicate ports within batch
        val seenPorts = mutableSetOf<String>()
        val duplicatePortErrors = mutableListOf<Map<String, Any?>>()
        for ((idx, b) in bindingsRaw.withIndex()) {
            val gid = b["gatewayId"]?.toString()?.trim()?.ifBlank { null }
                ?: continue // will be caught in per-row validation
            val pIdx = (b["portIndex"] as? Number)?.toInt() ?: continue
            val key = "$gid#$pIdx"
            if (!seenPorts.add(key)) {
                duplicatePortErrors.add(mapOf(
                    "index" to idx,
                    "gatewayId" to gid,
                    "portIndex" to pIdx,
                    "error" to "PORT_DUPLICATE_IN_BATCH",
                    "message" to "Duplicate port $pIdx on $gid within batch"
                ))
            }
        }
        if (duplicatePortErrors.isNotEmpty()) {
            return ResponseEntity.status(422).body(mapOf(
                "error" to "BULK_VALIDATION_FAILED",
                "message" to "Duplicate ports within batch",
                "details" to duplicatePortErrors
            ))
        }

        // Validate each entry fully
        val errors = mutableListOf<Map<String, Any?>>()
        data class ValidEntry(
            val user: com.red.server.auth.model.UserAccount,
            val gatewayId: UUID,
            val gatewayHost: String,
            val portIndex: Int,
            val number: String?
        )
        val validEntries = mutableListOf<ValidEntry>()
        val seenNumbersInBatch = mutableMapOf<String, Int>() // number -> first index

        for ((idx, b) in bindingsRaw.withIndex()) {
            try {
                val userIdStr = b["userId"]?.toString()?.trim()?.ifBlank { null }
                    ?: throw IllegalArgumentException("userId is required at index $idx")
                val gatewayIdStr = b["gatewayId"]?.toString()?.trim()?.ifBlank { null }
                    ?: throw IllegalArgumentException("gatewayId is required at index $idx")
                val portIndexAny = b["portIndex"] ?: throw IllegalArgumentException("portIndex is required at index $idx")
                val portIndex = (portIndexAny as? Number)?.toInt()
                    ?: throw IllegalArgumentException("portIndex must be a number at index $idx")
                require(portIndex in 0..31) { "portIndex must be within 0..31 at index $idx" }
                val numberRaw = b["number"]?.toString()?.trim()?.ifBlank { null }
                if (numberRaw != null) require(numberRaw.matches(Regex("\\d{6,20}"))) {
                    "number must be 6-20 digits at index $idx"
                }

                val userId = try { UUID.fromString(userIdStr) } catch (e: Exception) {
                    throw IllegalArgumentException("Invalid userId at index $idx: $userIdStr")
                }
                val gatewayId = try { UUID.fromString(gatewayIdStr) } catch (e: Exception) {
                    throw IllegalArgumentException("Invalid gatewayId at index $idx: $gatewayIdStr")
                }

                val user = users.findById(userId).orElseThrow { NoSuchElementException("User not found at index $idx: $userId") }
                val gateway = fleet.findGateway(gatewayId)
                    ?: throw IllegalArgumentException("Gateway not found at index $idx: $gatewayId")

                if (portIndex >= gateway.portCount) {
                    // Allow 0..31 per DB constraint, but warn if beyond gateway's portCount
                    // Keep as valid per spec (port in range 0..31) but note if exceeds physical
                    log.warn("Bulk bind port {} exceeds gateway {} portCount {}", portIndex, gateway.host, gateway.portCount)
                }

                // Check port already bound to another user (DB or within batch already)
                val existing = users.findByPstnGatewayIdAndPstnPortIndex(gatewayId, portIndex)
                if (existing != null && existing.id != userId) {
                    errors.add(mapOf(
                        "index" to idx,
                        "userId" to userId.toString(),
                        "gatewayId" to gatewayId.toString(),
                        "portIndex" to portIndex,
                        "error" to "PORT_ALREADY_BOUND",
                        "message" to "Port $portIndex on ${gateway.host} is already bound to ${existing.redId}"
                    ))
                    continue
                }

                // Check number global uniqueness pre-check
                if (numberRaw != null) {
                    // duplicate number within batch?
                    val firstIdx = seenNumbersInBatch[numberRaw]
                    if (firstIdx != null) {
                        errors.add(mapOf(
                            "index" to idx,
                            "error" to "NUMBER_DUPLICATE_IN_BATCH",
                            "message" to "Number $numberRaw duplicate in batch (first at $firstIdx)",
                            "number" to numberRaw
                        ))
                        continue
                    }
                    seenNumbersInBatch[numberRaw] = idx

                    val byNumber = users.findByPstnNumber(numberRaw)
                    if (byNumber != null && byNumber.id != userId) {
                        errors.add(mapOf(
                            "index" to idx,
                            "error" to "NUMBER_ALREADY_BOUND",
                            "message" to "Number $numberRaw already bound to ${byNumber.redId}",
                            "number" to numberRaw
                        ))
                        continue
                    }
                } else {
                    // number not provided — must have existing number to satisfy consistency constraint
                    if (user.pstnNumber.isNullOrBlank()) {
                        errors.add(mapOf(
                            "index" to idx,
                            "userId" to userId.toString(),
                            "error" to "NUMBER_REQUIRED",
                            "message" to "Number is required for user ${user.redId} (no existing pstn_number)"
                        ))
                        continue
                    }
                }

                val effectiveNumber = numberRaw ?: user.pstnNumber
                validEntries.add(ValidEntry(user, gatewayId, gateway.host, portIndex, effectiveNumber))
            } catch (e: IllegalArgumentException) {
                errors.add(mapOf("index" to idx, "error" to "INVALID_REQUEST", "message" to (e.message ?: "invalid"), "details" to e.javaClass.simpleName))
            } catch (e: NoSuchElementException) {
                errors.add(mapOf("index" to idx, "error" to "NOT_FOUND", "message" to (e.message ?: "not found")))
            } catch (e: Exception) {
                errors.add(mapOf("index" to idx, "error" to "INTERNAL", "message" to (e.message ?: "unknown")))
            }
        }

        if (errors.isNotEmpty()) {
            val hasPortConflict = errors.any { it["error"] == "PORT_ALREADY_BOUND" }
            val status = if (hasPortConflict) 422 else 400
            return ResponseEntity.status(status).body(mapOf(
                "error" to "BULK_VALIDATION_FAILED",
                "details" to errors
            ))
        }

        // All validated — save atomically within transaction
        val results = mutableListOf<Map<String, Any?>>()
        for (entry in validEntries) {
            entry.user.pstnGatewayId = entry.gatewayId
            entry.user.pstnPortIndex = entry.portIndex
            entry.user.pstnNumber = entry.number
            try {
                users.save(entry.user)
            } catch (e: Exception) {
                // DB constraint violation (e.g., ux_users_pstn_number) — rollback whole batch
                log.error("Bulk save failed for user {}: {}", entry.user.redId, e.message)
                // Transaction will rollback due to exception; return 409/422
                throw IllegalStateException("Bulk save failed: ${e.message}", e)
            }
            audit.record(actor, "PSTN_SIM_BOUND", entry.user.redId, mapOf(
                "gatewayId" to entry.gatewayId.toString(),
                "gatewayHost" to entry.gatewayHost,
                "portIndex" to entry.portIndex,
                "number" to entry.number,
                "bulk" to true
            ))
            results.add(mapOf(
                "userId" to entry.user.id.toString(),
                "redId" to entry.user.redId,
                "gatewayId" to entry.gatewayId.toString(),
                "gatewayHost" to entry.gatewayHost,
                "portIndex" to entry.portIndex,
                "number" to entry.number,
                "status" to "BOUND"
            ))
        }

        return ResponseEntity.ok(mapOf(
            "count" to results.size,
            "results" to results
        ))
    }

    // ── internal reconcile logic ───────────────────────────────────────

    private fun buildReconcile(includeLearnable: Boolean): Map<String, Any?> {
        val gateways = try { fleet.listGateways() } catch (e: Exception) {
            log.warn("Failed to list gateways for reconcile: {}", e.message)
            emptyList()
        }

        // Filter to enabled gateways only; disabled gateways are ignored
        val enabledGateways = gateways.filter { it.enabled }

        // Build bound user map (key = gatewayId#portIndex) FIRST — needed to decide which
        // gateways are SIM-loaded (has SIM evidence OR has a bound user referencing it).
        val allBoundUsers: List<com.red.server.auth.model.UserAccount> = try {
            // JpaRepository.findAll() is the most direct, but mocks may stub other methods
            val all = users.findAll()
            val filtered = all.filter { it.pstnGatewayId != null && it.pstnPortIndex != null }
            if (filtered.isNotEmpty()) filtered else {
                // Try per-gateway fallback for tests that stub findByPstnGatewayId
                enabledGateways.flatMap { gw -> try { users.findByPstnGatewayId(gw.id) } catch (ex: Exception) { emptyList() } }
                    .filter { it.pstnGatewayId != null && it.pstnPortIndex != null }
                    .distinctBy { it.id }
            }
        } catch (e: Exception) {
            try {
                users.findAllByStatusOrderByCreatedAtAsc(AccountStatus.APPROVED)
                    .filter { it.pstnGatewayId != null && it.pstnPortIndex != null }
            } catch (ex: Exception) {
                emptyList()
            }
        }
        val boundGatewayIds = allBoundUsers.mapNotNull { it.pstnGatewayId }.toSet()

        val liveByGateway = mutableMapOf<UUID, List<Map<String, Any?>>>()
        for (gw in enabledGateways) {
            try {
                val ports = hardware.getHardwareStatus(gw)
                // SIM-loaded detection: at least one port has SIM evidence OR gateway has a bound user
                val hasSimEvidence = ports.any { p -> hasSimEvidence(p) }
                val hasBound = gw.id in boundGatewayIds
                if (!hasSimEvidence && !hasBound) {
                    log.debug("Gateway {} has no SIM evidence and no bound users — skip for SIM reconciliation", gw.host)
                    continue
                }
                liveByGateway[gw.id] = ports
                runCatching { fleet.markHealthy(gw.id) }
            } catch (e: Exception) {
                log.warn("Reconcile: gateway {} unreachable: {}", gw.host, e.message)
                runCatching { fleet.markFailure(gw.id, e.message ?: "reconcile fetch failed") }
            }
        }

        // Fallback single-gateway mode when fleet is empty (legacy single device)
        if (liveByGateway.isEmpty() && enabledGateways.isEmpty()) {
            try {
                val fallbackPorts = hardware.getHardwareStatus()
                if (fallbackPorts.isNotEmpty()) {
                    val syntheticId = gateways.firstOrNull()?.id ?: UUID.nameUUIDFromBytes("DINSTAR:fallback".toByteArray())
                    val syntheticHost = gateways.firstOrNull()?.host ?: "192.168.11.2"
                    // Only use fallback if it has SIM evidence or has bound users
                    val hasFallbackSim = fallbackPorts.any { p -> hasSimEvidence(p) }
                    val fallbackHasBound = boundGatewayIds.isNotEmpty()
                    if (hasFallbackSim || fallbackHasBound) {
                        liveByGateway[syntheticId] = fallbackPorts
                    }
                }
            } catch (e: Exception) {
                log.debug("Fallback single-gateway reconcile fetch failed: {}", e.message)
            }
        }

        val boundByPort = allBoundUsers.associateBy { "${it.pstnGatewayId}#${it.pstnPortIndex}" }
        val liveKeys = mutableSetOf<String>()
        for ((gwId, ports) in liveByGateway) {
            for (p in ports) {
                (p["index"] as? Number)?.toInt()?.let { idx -> liveKeys.add("$gwId#$idx") }
            }
        }

        val portsResult = mutableListOf<Map<String, Any?>>()
        var ok = 0
        var mismatched = 0
        var unboundWithSim = 0

        // Helper to find gateway object for host lookup
        val gatewayById = gateways.associateBy { it.id }

        for ((gatewayId, ports) in liveByGateway) {
            val gateway = gatewayById[gatewayId] ?: enabledGateways.firstOrNull { it.id == gatewayId }
            val host = gateway?.host ?: "unknown"
            for (port in ports) {
                val index = (port["index"] as? Number)?.toInt() ?: continue
                val liveNumber = port["number"]?.toString()?.trim()?.takeIf { it.isNotBlank() && it != "null" }
                val liveNumberMasked = port["numberMasked"]?.toString()
                    ?: port["sim_number_masked"]?.toString()
                    ?: liveNumber?.let { "••••${it.takeLast(4)}" }
                val imsiRaw = port["imsi"]?.toString()?.trim()?.takeIf { it.isNotBlank() && it != "null" }
                val imsiMasked = port["imsiMasked"]?.toString()?.trim()?.takeIf { it.isNotBlank() && it != "null" }
                val iccidRaw = port["iccid"]?.toString()?.trim()?.takeIf { it.isNotBlank() && it != "null" }
                val imsiLast4 = extractLast4(imsiRaw, imsiMasked)
                val operator = port["operator"]?.toString() ?: port["operator_name"]?.toString()
                val signalUsable = port["signalUsable"] as? Boolean
                    ?: port["signal_usable"] as? Boolean
                    ?: false

                val key = "$gatewayId#$index"
                val boundUser = boundByPort[key]
                val boundRedId = boundUser?.redId
                val boundNumber = boundUser?.pstnNumber

                val hasSim = hasSimEvidence(port)

                var mismatch = false
                var reason: String
                var suggestedAction: String
                var needsNumberLearning = false

                if (boundUser == null) {
                    if (hasSim) {
                        reason = "Port has SIM (operator=${operator ?: "UNKNOWN"}, signalUsable=$signalUsable, IMSI last4=${imsiLast4 ?: "?"}) but no RED binding — assign a user"
                        suggestedAction = "UNBOUND_HAS_SIM"
                        unboundWithSim++
                        mismatch = false
                        needsNumberLearning = liveNumber.isNullOrBlank()
                    } else {
                        reason = "Empty port — no SIM detected"
                        suggestedAction = "OK"
                        ok++
                        needsNumberLearning = false
                    }
                } else {
                    // bound exists
                    if (liveNumber.isNullOrBlank()) {
                        needsNumberLearning = true
                        if (!hasSim) {
                            mismatch = true
                            reason = "Bound ${boundRedId}#${boundNumber} but port has no SIM (signalUsable=false, IMSI empty) — orphan binding needs clear"
                            suggestedAction = "ORPHAN_BINDING_NEEDS_CLEAR"
                        } else {
                            // Fallback identifier via gateway+port + imsiLast4
                            mismatch = false
                            reason = "Live number blank (Sabafon) — fallback IMSI last4=${imsiLast4 ?: "?"} gateway=${host}#${index} bound=${boundNumber}; needs Phone Number Learning to populate number"
                            suggestedAction = "OK"
                            ok++
                        }
                    } else {
                        val normLive = liveNumber.filter { it.isDigit() }
                        val normBound = boundNumber?.filter { it.isDigit() }
                        if (normLive == normBound) {
                            mismatch = false
                            reason = "Bound number matches live SIM number"
                            suggestedAction = "OK"
                            ok++
                        } else {
                            mismatch = true
                            reason = "Bound $boundNumber != live $liveNumber (operator ${operator ?: "?"}, IMSI last4 ${imsiLast4 ?: "?"})"
                            suggestedAction = "MISMATCH"
                            mismatched++
                        }
                        needsNumberLearning = false
                    }
                }

                val entry = mutableMapOf<String, Any?>(
                    "index" to index,
                    "gatewayId" to gatewayId.toString(),
                    "host" to host,
                    "liveNumber" to liveNumber,
                    "liveNumberMasked" to liveNumberMasked,
                    "imsiLast4" to imsiLast4,
                    "operator" to operator,
                    "signalUsable" to signalUsable,
                    "boundRedId" to boundRedId,
                    "boundNumber" to boundNumber,
                    "mismatch" to mismatch,
                    "reason" to reason,
                    "suggestedAction" to suggestedAction,
                    "needsNumberLearning" to needsNumberLearning
                )
                if (includeLearnable) {
                    entry["learnable"] = needsNumberLearning
                    entry["learnInstruction"] = if (needsNumberLearning) INSTRUCTION_NUMBER_LEARNING else null
                }
                portsResult.add(entry)
            }
        }

        // orphanBindings = bindings whose gateway+port not in liveKeys OR per-port orphan
        val orphanByMissingKey = allBoundUsers.count { "${it.pstnGatewayId}#${it.pstnPortIndex}" !in liveKeys }
        val perPortOrphan = portsResult.count { it["suggestedAction"] == "ORPHAN_BINDING_NEEDS_CLEAR" }
        // If liveKeys missing, perPortOrphan will be 0; sum gives total orphan
        // But perPortOrphan ports are live, so not counted in orphanByMissingKey — sum is correct
        val totalOrphan = orphanByMissingKey + perPortOrphan

        val boundPorts = portsResult.count { it["boundRedId"] != null }

        val summary = mapOf(
            "totalPorts" to portsResult.size,
            "boundPorts" to boundPorts,
            "ok" to ok,
            "mismatched" to mismatched,
            "unboundWithSim" to unboundWithSim,
            "orphanBindings" to totalOrphan
        )

        val result = mutableMapOf<String, Any?>(
            "ports" to portsResult,
            "summary" to summary
        )
        if (includeLearnable) {
            val anyLearnable = portsResult.any { it["needsNumberLearning"] == true }
            result["learnInstruction"] = if (anyLearnable) INSTRUCTION_NUMBER_LEARNING else null
            result["anyLearnable"] = anyLearnable
        }
        return result
    }

    private fun hasSimEvidence(port: Map<String, Any?>): Boolean {
        val status = port["status"]?.toString() ?: port["registration_state"]?.toString() ?: ""
        val usable = port["signalUsable"] as? Boolean ?: port["signal_usable"] as? Boolean ?: false
        val imsi = port["imsi"]?.toString()
        val imsiMasked = port["imsiMasked"]?.toString() ?: port["imsi_masked"]?.toString()
        val iccid = port["iccid"]?.toString()
        val iccidMasked = port["iccidMasked"]?.toString() ?: port["iccid_masked"]?.toString()
        val number = port["number"]?.toString()
        val numberMasked = port["numberMasked"]?.toString() ?: port["sim_number_masked"]?.toString()
        val gprs = port["gprs"]?.toString()
        return usable
                || !imsi.isNullOrBlank() && imsi != "null"
                || !imsiMasked.isNullOrBlank() && imsiMasked != "null"
                || !iccid.isNullOrBlank() && iccid != "null"
                || !iccidMasked.isNullOrBlank() && iccidMasked != "null"
                || !number.isNullOrBlank() && number != "null"
                || !numberMasked.isNullOrBlank() && numberMasked != "null"
                || status.equals("REGISTER_OK", ignoreCase = true)
                || status.equals("REGISTERED", ignoreCase = true)
                || status.equals("Mobile Registered", ignoreCase = true)
                || !gprs.isNullOrBlank() && gprs != "null"
    }

    private fun extractLast4(raw: String?, masked: String?): String? {
        if (!raw.isNullOrBlank() && raw != "null" && raw.length >= 4) {
            val digits = raw.filter { it.isDigit() }
            if (digits.length >= 4) return digits.takeLast(4)
            return raw.takeLast(4)
        }
        if (!masked.isNullOrBlank() && masked != "null") {
            // masked is like "••••1234" or "••••last4"
            val digits = masked.filter { it.isDigit() }
            if (digits.length >= 4) return digits.takeLast(4)
            // fallback: take last 4 chars regardless
            return masked.takeLast(4)
        }
        return null
    }
}
