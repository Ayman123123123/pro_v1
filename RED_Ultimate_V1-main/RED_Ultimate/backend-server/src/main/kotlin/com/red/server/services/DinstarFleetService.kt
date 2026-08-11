package com.red.server.services

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.net.InetAddress
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * أسطول بوابات DINSTAR — إدارة عدة أجهزة UC2000-VE-8G / UC2000-VE-8T معًا.
 *
 * ## لماذا هذه الطبقة
 * كانت الشيفرة تفترض بوابة واحدة يحددها `red.dinstar.ip`. عند وصل جهاز
 * ثانٍ لم يكن هناك أي وسيلة لمخاطبته، ولا لمعرفة أي جهاز يحمل الشريحة
 * المناسبة لرقم الوجهة. هذه الخدمة تحتفظ بسجل الأجهزة وتُعرّف عليها.
 *
 * ## التعرّف التلقائي — كيف يعمل فعلًا
 * لا تدعم UC2000 أي بروتوكول اكتشاف بثّي (لا mDNS ولا SSDP)، فالادعاء
 * بغير ذلك سيكون تلفيقًا. الطريقة الصحيحة والوحيدة الموثقة هي فحص نطاق
 * الإدارة الخاص باستدعاء `/api/get_port_info` المُوثَّق مع مصادقة
 * Digest: الجهاز الذي يرد باستجابة JSON صحيحة هو بوابة DINSTAR.
 *
 * الفحص يجري بالتوازي على مجموعة محدودة من العناوين، بمهلة قصيرة،
 * وعلى عناوين خاصة حصرًا (RFC 1918) حتى لا يتحول الخادم إلى ماسح
 * شبكات على الإنترنت العام.
 *
 * ## هوية الجهاز
 * العنوان الشبكي ليس هوية: DHCP قد يبدّله فتتضاعف السجلات. لذلك يُشتق
 * المفتاح من الرقم التسلسلي حين تُفصح عنه البوابة عبر `get_status`،
 * ويُستخدم `host:port` فقط كبديل عند غيابه.
 */
@Service
class DinstarFleetService(
    @Value("\${red.dinstar.ip:192.168.11.1}") private val seedIp: String,
    @Value("\${red.dinstar.port:443}") private val seedPort: Int,
    @Value("\${red.dinstar.scheme:https}") private val seedScheme: String,
    @Value("\${red.dinstar.discovery.subnets:}") private val configuredSubnets: String,
    @Value("\${red.dinstar.discovery.enabled:false}") private val discoveryEnabled: Boolean,
    private val connections: DinstarConnectionFactory,
    private val mapper: ObjectMapper,
    private val jdbc: JdbcTemplate
) {
    companion object {
        private val log = LoggerFactory.getLogger(DinstarFleetService::class.java)

        /** حد أقصى صارم لعدد العناوين المفحوصة في عملية واحدة. */
        private const val MAX_SCAN_HOSTS = 254

        /** عدد الإخفاقات المتتالية قبل اعتبار البوابة ساقطة. */
        private const val FAILURE_THRESHOLD = 3
    }

    data class Gateway(
        val id: UUID,
        val name: String,
        val model: String,
        val host: String,
        val scheme: String,
        val apiPort: Int,
        val portCount: Int,
        val enabled: Boolean,
        val healthState: String,
        val routingPriority: Int,
        val pjsipEndpoint: String?,
        val serialNumber: String?,
        val firmwareVersion: String?,
        val siteLabel: String?,
        val consecutiveFailures: Int
    ) {
        val portRange: IntRange get() = 0 until portCount
    }

    // ═══════════════════════════════════════════════════════════
    // السجل
    // ═══════════════════════════════════════════════════════════

    fun listGateways(onlyEnabled: Boolean = false): List<Gateway> {
        val sql = buildString {
            append("SELECT * FROM telecom_gateways WHERE vendor = 'DINSTAR' ")
            if (onlyEnabled) append("AND enabled = TRUE ")
            append("ORDER BY routing_priority ASC, name ASC")
        }
        return jdbc.query(sql) { rs, _ ->
            Gateway(
                id = rs.getObject("id", UUID::class.java),
                name = rs.getString("name"),
                model = rs.getString("model"),
                host = rs.getString("host"),
                scheme = rs.getString("scheme"),
                apiPort = rs.getInt("api_port"),
                portCount = rs.getInt("port_count"),
                enabled = rs.getBoolean("enabled"),
                healthState = rs.getString("health_state"),
                routingPriority = rs.getInt("routing_priority"),
                pjsipEndpoint = rs.getString("pjsip_endpoint"),
                serialNumber = rs.getString("serial_number"),
                firmwareVersion = rs.getString("firmware_version"),
                siteLabel = rs.getString("site_label"),
                consecutiveFailures = rs.getInt("consecutive_failures")
            )
        }
    }

    fun findGateway(id: UUID): Gateway? = listGateways().firstOrNull { it.id == id }

    /** البوابات الصالحة للتوجيه: مفعّلة وليست ساقطة، مرتّبة بالأولوية. */
    fun routableGateways(): List<Gateway> =
        listGateways(onlyEnabled = true).filter { it.healthState != "OFFLINE" }

    /**
     * تسجيل بوابة أو تحديثها. المعرّف مشتق من الرقم التسلسلي إن وُجد،
     * وإلا من `host:port` — حتى لا يُنشئ تغيّر عنوان DHCP سجلًا مكررًا.
     */
    fun upsertGateway(
        host: String,
        apiPort: Int,
        scheme: String,
        model: String,
        portCount: Int,
        name: String,
        serialNumber: String? = null,
        firmwareVersion: String? = null,
        macAddress: String? = null,
        pjsipEndpoint: String? = null,
        siteLabel: String? = null,
        routingPriority: Int = 100,
        discoveryMethod: String = "MANUAL",
        capabilities: Map<String, Any?> = emptyMap()
    ): UUID {
        require(scheme in setOf("http", "https")) { "Gateway scheme must be http or https" }
        require(apiPort in 1..65535) { "Gateway API port out of range" }
        require(isPrivateAddress(host)) { "DINSTAR gateways must live on a private management address" }
        DinstarModelProfile.parse(model) // يرفض الطرازات غير المدعومة

        val id = gatewayIdFor(serialNumber, host, apiPort)
        jdbc.update(
            """INSERT INTO telecom_gateways
               (id,name,vendor,model,host,scheme,api_port,port_count,capabilities_json,
                pjsip_endpoint,site_label,routing_priority,serial_number,firmware_version,
                mac_address,discovery_method,discovered_at,last_seen_at)
               VALUES (?,?,'DINSTAR',?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
               ON CONFLICT (id) DO UPDATE SET
                 name=EXCLUDED.name, model=EXCLUDED.model, host=EXCLUDED.host,
                 scheme=EXCLUDED.scheme, api_port=EXCLUDED.api_port, port_count=EXCLUDED.port_count,
                 capabilities_json=EXCLUDED.capabilities_json, pjsip_endpoint=EXCLUDED.pjsip_endpoint,
                 site_label=EXCLUDED.site_label, routing_priority=EXCLUDED.routing_priority,
                 serial_number=COALESCE(EXCLUDED.serial_number, telecom_gateways.serial_number),
                 firmware_version=COALESCE(EXCLUDED.firmware_version, telecom_gateways.firmware_version),
                 mac_address=COALESCE(EXCLUDED.mac_address, telecom_gateways.mac_address),
                 last_seen_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP""",
            id, name, model, host, scheme, apiPort, portCount,
            mapper.writeValueAsString(capabilities), pjsipEndpoint, siteLabel,
            routingPriority, serialNumber, firmwareVersion, macAddress, discoveryMethod
        )
        return id
    }

    fun setEnabled(id: UUID, enabled: Boolean) {
        jdbc.update(
            "UPDATE telecom_gateways SET enabled=?, updated_at=CURRENT_TIMESTAMP WHERE id=?",
            enabled, id
        )
    }

    fun removeGateway(id: UUID) {
        jdbc.update("DELETE FROM telecom_gateways WHERE id=?", id)
    }

    // ═══════════════════════════════════════════════════════════
    // الصحّة
    // ═══════════════════════════════════════════════════════════

    fun markHealthy(id: UUID) {
        jdbc.update(
            """UPDATE telecom_gateways SET health_state='ONLINE', consecutive_failures=0,
               last_error=NULL, last_seen_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP WHERE id=?""",
            id
        )
    }

    /**
     * تسجيل إخفاق. لا تُعلَن البوابة ساقطة من أول خطأ: انقطاع شبكي عابر
     * أو مهلة واحدة لا يجوز أن تُخرج جهازًا سليمًا من التوجيه. تمر
     * بحالة DEGRADED أولًا، ولا تصبح OFFLINE إلا بعد [FAILURE_THRESHOLD].
     */
    fun markFailure(id: UUID, error: String) {
        jdbc.update(
            """UPDATE telecom_gateways SET
                 consecutive_failures = consecutive_failures + 1,
                 health_state = CASE WHEN consecutive_failures + 1 >= ? THEN 'OFFLINE' ELSE 'DEGRADED' END,
                 last_error = ?, updated_at = CURRENT_TIMESTAMP
               WHERE id = ?""",
            FAILURE_THRESHOLD, error.take(500), id
        )
    }

    // ═══════════════════════════════════════════════════════════
    // التعرّف التلقائي
    // ═══════════════════════════════════════════════════════════

    data class DiscoveredGateway(
        val host: String,
        val apiPort: Int,
        val scheme: String,
        val model: String,
        val portCount: Int,
        val serialNumber: String?,
        val firmwareVersion: String?,
        val registeredPorts: Int
    )

    /**
     * فحص نطاقات الإدارة بحثًا عن بوابات DINSTAR.
     *
     * لا يعتمد على بثّ شبكي — UC2000 لا تدعمه. يتحقق من كل عنوان
     * باستدعاء `get_port_info` المُوثَّق: الرد بـ JSON يحمل
     * `error_code=200` وقائمة `info` هو التوقيع القاطع للجهاز.
     *
     * @param subnets نطاقات بصيغة `192.168.11.0/24`. عند الإهمال تُستخدم
     *                `red.dinstar.discovery.subnets` ثم شبكة عنوان البذرة.
     */
    fun discoverFleet(subnets: List<String> = emptyList()): List<DiscoveredGateway> {
        check(discoveryEnabled) {
            "Subnet discovery is disabled. Set red.dinstar.discovery.enabled=true to allow it."
        }
        val targets = (subnets.takeIf { it.isNotEmpty() }
            ?: configuredSubnets.split(',').map(String::trim).filter(String::isNotEmpty).takeIf { it.isNotEmpty() }
            ?: listOf(defaultSubnetOf(seedIp)))
            .flatMap(::expandSubnet)
            .distinct()
            .take(MAX_SCAN_HOSTS)

        require(targets.isNotEmpty()) { "No scannable hosts resolved from the given subnets" }
        log.info("DINSTAR discovery scanning {} candidate hosts", targets.size)

        // تفرّع محدود: فحص متوازٍ سريع دون إغراق الشبكة الإدارية.
        val pool = Executors.newFixedThreadPool(minOf(32, targets.size))
        return try {
            val futures = targets.map { host ->
                pool.submit<DiscoveredGateway?> { probeHost(host) }
            }
            futures.mapNotNull { runCatching { it.get(20, TimeUnit.SECONDS) }.getOrNull() }
        } finally {
            pool.shutdownNow()
        }
    }

    /** فحص عنوان واحد. يعيد `null` إن لم يكن بوابة DINSTAR مصادقة. */
    fun probeHost(host: String, apiPort: Int = seedPort, scheme: String = seedScheme): DiscoveredGateway? {
        if (!isPrivateAddress(host)) return null
        val client = connections.clientFor(host, apiPort, scheme)

        val portInfo = runCatching { client.getPortInfo() }.getOrNull() ?: return null
        if (portInfo.isEmpty()) return null

        // get_status اختياري: بعض الإصدارات لا تُفصح عن التسلسلي.
        val status = runCatching { client.getDeviceStatus() }.getOrNull().orEmpty()

        val portCount = portInfo.size
        val model = inferModel(portInfo, status, portCount)
        val registered = portInfo.count { (it["reg"]?.toString() ?: "").equals("REGISTERED", true) }

        return DiscoveredGateway(
            host = host,
            apiPort = apiPort,
            scheme = scheme,
            model = model,
            portCount = portCount,
            serialNumber = (status["sn"] ?: status["serial_number"])?.toString(),
            firmwareVersion = (status["version"] ?: status["firmware_version"])?.toString(),
            registeredPorts = registered
        )
    }

    /**
     * استنتاج الطراز من قدرات المنافذ المُبلَّغة.
     *
     * `get_port_info` يُعيد `type` لكل منفذ (GSM / WCDMA / LTE). الطراز
     * ‎-8T يحمل وحدات LTE والـ ‎-8G وحدات GSM فقط. إذا أفصحت البوابة عن
     * الطراز نصًا فهو أوثق ويُقدَّم.
     */
    private fun inferModel(
        portInfo: List<Map<String, Any?>>,
        status: Map<String, Any?>,
        portCount: Int
    ): String {
        val declared = (status["model"] ?: status["device_model"])?.toString()?.trim()
        if (!declared.isNullOrBlank()) {
            runCatching { return DinstarModelProfile.parse(declared).modelId }
        }
        val radios = portInfo.mapNotNull { it["type"]?.toString()?.uppercase() }
        val hasLte = radios.any { it.contains("LTE") || it.contains("4G") }
        return if (hasLte) "UC2000-VE-8T" else "UC2000-VE-8G"
    }

    /** تسجيل نتائج الفحص في السجل دفعةً واحدة. */
    fun adoptDiscovered(found: List<DiscoveredGateway>, siteLabel: String? = null): List<UUID> =
        found.mapIndexed { i, g ->
            upsertGateway(
                host = g.host, apiPort = g.apiPort, scheme = g.scheme,
                model = g.model, portCount = g.portCount,
                name = "DINSTAR ${g.model} @ ${g.host}",
                serialNumber = g.serialNumber, firmwareVersion = g.firmwareVersion,
                siteLabel = siteLabel, routingPriority = 100 + i,
                discoveryMethod = "SUBNET_SCAN",
                capabilities = mapOf("portsDetected" to g.portCount, "registeredAtDiscovery" to g.registeredPorts)
            )
        }

    /**
     * ضمان وجود بوابة البذرة في السجل. تُستدعى عند الإقلاع حتى تعمل
     * عمليات النشر أحادية الجهاز دون أي إعداد يدوي.
     */
    fun ensureSeedGateway(): UUID? {
        if (!isPrivateAddress(seedIp)) {
            log.warn("Configured DINSTAR IP {} is not a private address; skipping seed registration", seedIp)
            return null
        }
        if (listGateways().any { it.host == seedIp && it.apiPort == seedPort }) return null
        val probe = runCatching { probeHost(seedIp, seedPort, seedScheme) }.getOrNull()
        return upsertGateway(
            host = seedIp, apiPort = seedPort, scheme = seedScheme,
            model = probe?.model ?: "UC2000-VE-8G",
            portCount = probe?.portCount ?: 8,
            name = probe?.let { "DINSTAR ${it.model} @ $seedIp" } ?: "DINSTAR @ $seedIp",
            serialNumber = probe?.serialNumber, firmwareVersion = probe?.firmwareVersion,
            pjsipEndpoint = "dinstar-gateway", routingPriority = 0,
            discoveryMethod = "CONFIG_SEED"
        )
    }

    /** آخر قرارات التوجيه المسجّلة — مصدرها `gateway_route_decisions`. */
    fun recentRouteDecisions(limit: Int = 100): List<Map<String, Any?>> =
        jdbc.query(
            """SELECT d.*, g.host AS gateway_host FROM gateway_route_decisions d
               LEFT JOIN telecom_gateways g ON g.id = d.gateway_id
               ORDER BY d.created_at DESC LIMIT ?""",
            { rs, _ ->
                mapOf(
                    "id" to rs.getString("id"),
                    "gatewayId" to rs.getString("gateway_id"),
                    "gatewayHost" to rs.getString("gateway_host"),
                    "portIndex" to rs.getObject("port_index"),
                    "destinationPrefix" to rs.getString("destination_prefix"),
                    "matchedOperator" to rs.getString("matched_operator"),
                    "score" to rs.getObject("score"),
                    "reason" to rs.getString("reason"),
                    "outcome" to rs.getString("outcome"),
                    "createdAt" to rs.getTimestamp("created_at")?.toInstant()?.toString()
                )
            },
            limit.coerceIn(1, 500)
        )

    // ═══════════════════════════════════════════════════════════
    // أدوات
    // ═══════════════════════════════════════════════════════════

    private fun gatewayIdFor(serial: String?, host: String, port: Int): UUID =
        if (!serial.isNullOrBlank()) UUID.nameUUIDFromBytes("DINSTAR:SN:$serial".toByteArray())
        else UUID.nameUUIDFromBytes("DINSTAR:$host:$port".toByteArray())

    private fun defaultSubnetOf(ip: String): String {
        val parts = ip.split('.')
        require(parts.size == 4) { "Cannot derive a subnet from $ip" }
        return "${parts[0]}.${parts[1]}.${parts[2]}.0/24"
    }

    /**
     * توسيع `a.b.c.0/24` إلى عناوين المضيفين. تُقبل الأقنعة ‎/24..‎/30
     * فقط: ما هو أوسع يعني آلاف الطلبات وهو ما لا يجوز إطلاقه من خادم.
     */
    private fun expandSubnet(cidr: String): List<String> {
        val (base, maskPart) = cidr.split('/').let {
            require(it.size == 2) { "Subnet must be in CIDR form, e.g. 192.168.11.0/24" }
            it[0] to it[1].toIntOrNull()
        }
        val mask = requireNotNull(maskPart) { "Invalid CIDR mask in $cidr" }
        require(mask in 24..30) { "Only /24../30 subnets may be scanned (got /$mask)" }
        require(isPrivateAddress(base)) { "Refusing to scan a non-private subnet: $cidr" }

        val octets = base.split('.').map { it.toIntOrNull() ?: -1 }
        require(octets.size == 4 && octets.all { it in 0..255 }) { "Invalid IPv4 base in $cidr" }

        val hostBits = 32 - mask
        val size = 1 shl hostBits
        val baseInt = (octets[0] shl 24) or (octets[1] shl 16) or (octets[2] shl 8) or octets[3]
        val network = baseInt and (-1 shl hostBits)

        // استبعاد عنواني الشبكة والبث
        return (1 until size - 1).map { offset ->
            val v = network + offset
            "${(v shr 24) and 0xFF}.${(v shr 16) and 0xFF}.${(v shr 8) and 0xFF}.${v and 0xFF}"
        }
    }

    private fun isPrivateAddress(host: String): Boolean =
        runCatching {
            val addr = InetAddress.getByName(host)
            addr.isSiteLocalAddress || addr.isLoopbackAddress
        }.getOrDefault(false)
}
