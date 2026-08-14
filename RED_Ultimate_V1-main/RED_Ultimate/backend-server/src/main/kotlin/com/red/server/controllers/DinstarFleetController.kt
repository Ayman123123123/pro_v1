package com.red.server.controllers

import com.red.server.audit.AuditService
import com.red.server.pstn.DinstarLoadBalancer
import com.red.server.services.DinstarFleetService
import com.red.server.services.DinstarHardwareService
import com.red.server.services.DinstarModelProfile
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

/**
 * إدارة أسطول بوابات DINSTAR — عدة أجهزة UC2000-VE-8G / UC2000-VE-8T.
 *
 * كانت اللوحة تخاطب بوابة واحدة يحددها ملف الإعدادات. هذه المسارات
 * تسمح بتسجيل عدة أجهزة، والتعرّف عليها تلقائيًا بفحص نطاق الإدارة،
 * ومتابعة صحّة كل جهاز على حدة.
 *
 * كل المسارات تحت بادئة «api/admin» أي محصورة بدور ADMIN في SecurityConfig.
 * (لا يجوز كتابة النجمة بعد الشرطة المائلة حرفيًا داخل تعليق كتلي —
 *  تعليقات Kotlin الكتلية تتداخل، فتبتلع بقية الملف كاملًا.)
 */
@RestController
@RequestMapping("/api/admin/dinstar/fleet")
class DinstarFleetController(
    private val fleet: DinstarFleetService,
    private val hardware: DinstarHardwareService,
    private val loadBalancer: DinstarLoadBalancer,
    private val audit: AuditService
) {

    /** قائمة الأجهزة المسجّلة مع حالتها. */
    @GetMapping
    fun list(): List<Map<String, Any?>> = fleet.listGateways().map(::present)

    /** الطرازات المدعومة — تستخدمها اللوحة لملء قائمة الإضافة. */
    @GetMapping("/models")
    fun models(): List<Map<String, Any>> = DinstarModelProfile.entries.map { it.metadata() }

    /**
     * التعرّف التلقائي: فحص نطاقات الإدارة بحثًا عن بوابات.
     *
     * الفحص عملية ثقيلة نسبيًا ومقصورة على شبكات خاصة، ولا يُفعّل إلا
     * إذا كان `red.dinstar.discovery.enabled=true`.
     *
     * @param adopt عند `true` تُسجَّل النتائج مباشرة في الأسطول.
     */
    @PostMapping("/discover")
    fun discover(
        @RequestBody(required = false) body: Map<String, Any?>?,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any?>> {
        val actor = UUID.fromString(authentication.name)
        @Suppress("UNCHECKED_CAST")
        val subnets = (body?.get("subnets") as? List<String>).orEmpty()
        val adopt = body?.get("adopt") as? Boolean ?: false
        val site = body?.get("siteLabel") as? String

        val found = fleet.discoverFleet(subnets)
        val adopted = if (adopt) fleet.adoptDiscovered(found, site) else emptyList()

        audit.record(actor, "DINSTAR_FLEET_DISCOVER", subnets.joinToString(","),
            mapOf("found" to found.size, "adopted" to adopted.size))

        return ResponseEntity.ok(mapOf(
            "scannedSubnets" to subnets,
            "found" to found.map {
                mapOf(
                    "host" to it.host, "apiPort" to it.apiPort, "scheme" to it.scheme,
                    "model" to it.model, "portCount" to it.portCount,
                    "serialNumber" to it.serialNumber, "firmwareVersion" to it.firmwareVersion,
                    "registeredPorts" to it.registeredPorts
                )
            },
            "adoptedIds" to adopted
        ))
    }

    /** فحص عنوان واحد دون تسجيله — للتأكد قبل الإضافة. */
    @PostMapping("/probe")
    fun probe(@RequestBody body: Map<String, Any?>): ResponseEntity<Map<String, Any?>> {
        val host = (body["host"] as? String)?.trim().orEmpty()
        require(host.isNotBlank()) { "host is required" }
        val port = (body["apiPort"] as? Number)?.toInt() ?: 443
        val scheme = (body["scheme"] as? String) ?: "https"

        val result = fleet.probeHost(host, port, scheme)
            ?: return ResponseEntity.ok(mapOf(
                "reachable" to false,
                "message" to "لا توجد استجابة get_port_info مصادَقة على $scheme://$host:$port"
            ))

        return ResponseEntity.ok(mapOf(
            "reachable" to true, "host" to result.host, "model" to result.model,
            "portCount" to result.portCount, "serialNumber" to result.serialNumber,
            "firmwareVersion" to result.firmwareVersion, "registeredPorts" to result.registeredPorts
        ))
    }

    /** تسجيل بوابة يدويًا. */
    @PostMapping
    fun register(
        @RequestBody body: Map<String, Any?>,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any?>> {
        val actor = UUID.fromString(authentication.name)
        val host = (body["host"] as? String)?.trim().orEmpty()
        require(host.isNotBlank()) { "host is required" }
        val model = (body["model"] as? String) ?: "UC2000-VE-8G"
        val profile = DinstarModelProfile.parse(model)

        val id = fleet.upsertGateway(
            host = host,
            apiPort = (body["apiPort"] as? Number)?.toInt() ?: 443,
            scheme = (body["scheme"] as? String) ?: "https",
            model = profile.modelId,
            portCount = (body["portCount"] as? Number)?.toInt() ?: profile.portCount,
            name = (body["name"] as? String) ?: "DINSTAR ${profile.modelId} @ $host",
            pjsipEndpoint = body["pjsipEndpoint"] as? String,
            siteLabel = body["siteLabel"] as? String,
            routingPriority = (body["routingPriority"] as? Number)?.toInt() ?: 100,
            discoveryMethod = "MANUAL"
        )
        audit.record(actor, "DINSTAR_GATEWAY_REGISTERED", id.toString(), mapOf("host" to host, "model" to profile.modelId))
        return ResponseEntity.status(201).body(mapOf("id" to id, "host" to host, "model" to profile.modelId))
    }

    /** تفعيل أو تعطيل بوابة دون حذفها. */
    @PostMapping("/{id}/enabled")
    fun setEnabled(
        @PathVariable id: UUID,
        @RequestBody body: Map<String, Any?>,
        authentication: Authentication
    ): Map<String, Any?> {
        val actor = UUID.fromString(authentication.name)
        val enabled = body["enabled"] as? Boolean ?: true
        requireNotNull(fleet.findGateway(id)) { "Gateway not found" }
        fleet.setEnabled(id, enabled)
        audit.record(actor, if (enabled) "DINSTAR_GATEWAY_ENABLED" else "DINSTAR_GATEWAY_DISABLED", id.toString())
        return mapOf("id" to id, "enabled" to enabled)
    }

    @DeleteMapping("/{id}")
    fun remove(@PathVariable id: UUID, authentication: Authentication): Map<String, Any?> {
        val actor = UUID.fromString(authentication.name)
        requireNotNull(fleet.findGateway(id)) { "Gateway not found" }
        fleet.removeGateway(id)
        audit.record(actor, "DINSTAR_GATEWAY_REMOVED", id.toString())
        return mapOf("id" to id, "removed" to true)
    }

    /** حالة منافذ بوابة بعينها. */
    @GetMapping("/{id}/ports")
    fun ports(@PathVariable id: UUID): List<Map<String, Any?>> {
        val gateway = requireNotNull(fleet.findGateway(id)) { "Gateway not found" }
        return runCatching { hardware.getHardwareStatus(gateway) }
            .onSuccess { fleet.markHealthy(id) }
            .onFailure { fleet.markFailure(id, it.message ?: "port query failed") }
            .getOrThrow()
    }

    /**
     * نظرة موحّدة على كل منافذ الأسطول — ما تعرضه صفحة DINSTAR.
     * لا تُسقط بوابة ساقطة العملية كلها: تُدرَج بخطئها ويستمر الباقي.
     */
    @GetMapping("/ports")
    fun allPorts(): Map<String, Any?> {
        val gateways = fleet.listGateways(onlyEnabled = true)
        val perGateway = gateways.map { gw ->
            val result = runCatching { hardware.getHardwareStatus(gw) }
            if (result.isSuccess) fleet.markHealthy(gw.id)
            else fleet.markFailure(gw.id, result.exceptionOrNull()?.message ?: "unreachable")
            mapOf(
                "gateway" to present(gw),
                "ports" to result.getOrDefault(emptyList()),
                "error" to result.exceptionOrNull()?.message
            )
        }
        val allPorts = perGateway.flatMap {
            @Suppress("UNCHECKED_CAST")
            (it["ports"] as List<Map<String, Any?>>)
        }
        return mapOf(
            "gateways" to perGateway,
            "totals" to mapOf(
                "gateways" to gateways.size,
                "online" to gateways.count { it.healthState == "ONLINE" },
                "ports" to allPorts.size,
                "registered" to allPorts.count { (it["status"]?.toString() ?: "").equals("REGISTERED", true) },
                // الفارق بين «مسجّلة» و«جاهزة» هو ما كان مخفيًا: شريحة
                // مسجّلة بإشارة غير قابلة للقياس لا تصلح لحمل مكالمة.
                "usable" to allPorts.count { it["signalUsable"] == true }
            )
        )
    }

    /**
     * محاكاة اختيار المنفذ لرقم وجهة — **دون إجراء أي مكالمة**.
     *
     * تُظهر أي بوابة ومنفذ سيحملان الاتصال ولماذا استُبعد الباقي. كانت
     * أعطال التوجيه تظهر سابقًا على شكل «مكالمة فشلت» بلا تفسير؛ هذا
     * المسار يجعل القرار قابلًا للفحص قبل الاتصال.
     */
    @PostMapping("/routing/select")
    fun previewRouting(@RequestBody body: Map<String, Any?>): ResponseEntity<Map<String, Any?>> {
        val number = (body["number"] as? String)?.trim().orEmpty()
        require(number.isNotBlank()) { "number is required" }

        val selection = loadBalancer.selectPort(number)
            ?: return ResponseEntity.status(503).body(mapOf(
                "error" to "NO_USABLE_PORT",
                "message" to "لا يوجد منفذ مسجّل وغير مشغول وبإشارة كافية في الأسطول"
            ))

        return ResponseEntity.ok(mapOf(
            "selected" to mapOf(
                "gatewayId" to selection.gatewayId,
                "gatewayHost" to selection.gatewayHost,
                "pjsipEndpoint" to selection.pjsipEndpoint,
                "portIndex" to selection.portIndex,
                "operator" to selection.operator,
                "signalDbm" to selection.signalDbm,
                "score" to selection.score,
                "onNet" to selection.reason.contains("on-net")
            ),
            "targetOperator" to loadBalancer.classifyOperator(number)?.apiName,
            "reason" to selection.reason
        ))
    }

    /** آخر قرارات التوجيه — للتدقيق ومعرفة سبب اختيار جهاز بعينه. */
    @GetMapping("/routing/decisions")
    fun routingDecisions(): List<Map<String, Any?>> = fleet.recentRouteDecisions()

    private fun present(g: DinstarFleetService.Gateway): Map<String, Any?> = mapOf(
        "id" to g.id, "name" to g.name, "model" to g.model, "host" to g.host,
        "scheme" to g.scheme, "apiPort" to g.apiPort, "portCount" to g.portCount,
        "enabled" to g.enabled, "healthState" to g.healthState,
        "routingPriority" to g.routingPriority, "pjsipEndpoint" to g.pjsipEndpoint,
        "serialNumber" to g.serialNumber, "firmwareVersion" to g.firmwareVersion,
        "siteLabel" to g.siteLabel, "consecutiveFailures" to g.consecutiveFailures
    )
}
