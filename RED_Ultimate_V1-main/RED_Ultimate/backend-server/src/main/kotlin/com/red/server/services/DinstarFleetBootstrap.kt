package com.red.server.services

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * تسجيل بوابة البذرة عند الإقلاع.
 *
 * كانت `ensureSeedGateway()` معرَّفة ولا تُستدعى من أي مكان، فكان
 * النشر أحادي الجهاز يبدأ بسجل أسطول فارغ: البوابة المضبوطة في
 * `red.dinstar.ip` موجودة فعلًا وتعمل، لكن الموزّع لا يراها لأنه يقرأ
 * من `telecom_gateways` لا من الإعدادات. النتيجة `NO_USABLE_PORT`
 * لكل مكالمة رغم سلامة الجهاز.
 *
 * التسجيل هنا لا في مُنشئ الخدمة: المُنشئ يعمل أثناء بناء السياق، وأي
 * استعلام قاعدة بيانات قبل اكتمال هجرات Flyway يفشل. كما أن
 * `ensureSeedGateway()` تُجري فحصًا شبكيًا للبوابة، وتأخيره إلى ما بعد
 * الجاهزية يمنع إبطاء الإقلاع.
 *
 * العملية **آمنة التكرار**: تتحقق من وجود السجل أولًا فلا تُنشئ نسخًا
 * مكررة عند إعادة التشغيل، وتتخطى العناوين غير الخاصة.
 */
@Component
class DinstarFleetBootstrap(
    private val fleet: DinstarFleetService,
    @Value("\${red.dinstar.enabled:true}") private val dinstarEnabled: Boolean,
    @Value("\${red.dinstar.ips:}") private val dinstarIps: String,
    @Value("\${DINSTAR_IPS:}") private val dinstarIpsEnv: String
) {
    private val log = LoggerFactory.getLogger(DinstarFleetBootstrap::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun registerSeedGateway() {
        if (!dinstarEnabled) {
            log.info("DINSTAR disabled (DINSTAR_ENABLED=false) — seed gateway skipped; internet calls still work")
            return
        }
        // فشل تسجيل البذرة يجب ألا يمنع إقلاع الخادم: بقية المنصة
        // (المحادثات، المكالمات عبر WebRTC، اللوحة) لا تعتمد على PSTN.
        runCatching { fleet.ensureSeedGateway() }
            .onSuccess { id ->
                if (id != null) {
                    fleet.setEnabled(id, true)
                    log.info("سُجّلت بوابة DINSTAR الافتراضية في الأسطول: {}", id)
                }
                else log.debug("لا حاجة لتسجيل بوابة بذرة — مسجّلة سلفًا أو العنوان غير خاص")
            }
            .onFailure { log.warn("تعذّر تسجيل بوابة DINSTAR الافتراضية: {}", it.message) }

        // تسجيل كل بوابات DINSTAR_IPS (دعم 16 منفذ: 8G + 8T) — يضمن ظهور .2 و .3 في Fleet DB حتى لو لم يزرهما المستخدم
        val ipsRaw = (if (dinstarIps.isNotBlank()) dinstarIps else dinstarIpsEnv).trim()
        if (ipsRaw.isNotBlank()) {
            val ips = ipsRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }
            for (ip in ips) {
                if (!isPrivateIp(ip)) {
                    log.warn("تخطي IP غير خاص في DINSTAR_IPS: {}", ip)
                    continue
                }
                runCatching {
                    val discovered = fleet.probeHost(ip)
                    // discoveryMethod يجب أن يطابق قيد DB المسموح
                    // (MANUAL/SUBNET_SCAN/CONFIG_SEED/MDNS) — القيمة السابقة
                    // "DINSTAR_IPS" كانت تكسر UPSERT سجل البوابة في كل إقلاع
                    // فيبقى last_seen قديماً والبوابة تظهر OFFLINE كاذبةً.
                    if (discovered != null) {
                        val id = fleet.upsertGateway(
                            host = ip,
                            apiPort = discovered.apiPort,
                            scheme = discovered.scheme,
                            model = discovered.model,
                            portCount = discovered.portCount,
                            name = "DINSTAR ${discovered.model} @ $ip",
                            serialNumber = discovered.serialNumber,
                            firmwareVersion = discovered.firmwareVersion,
                            macAddress = discovered.macAddress,
                            discoveryMethod = "CONFIG_SEED"
                        )
                        fleet.setEnabled(id, true)
                        log.info("سُجّلت بوابة DINSTAR من DINSTAR_IPS: {} ({})", ip, discovered.model)
                    } else {
                        val id = fleet.upsertGateway(
                            host = ip,
                            apiPort = 443,
                            scheme = "https",
                            model = "UC2000-VE-8G",
                            portCount = 8,
                            name = "DINSTAR @ $ip",
                            discoveryMethod = "CONFIG_SEED"
                        )
                        fleet.setEnabled(id, true)
                        log.info("سُجّلت بوابة DINSTAR (fallback) من DINSTAR_IPS: {}", ip)
                    }
                }.onFailure { log.warn("تعذّر تسجيل بوابة DINSTAR {}: {}", ip, it.message) }
            }
        }
    }

    private fun isPrivateIp(ip: String): Boolean {
        return try {
            val addr = java.net.InetAddress.getByName(ip)
            addr.isSiteLocalAddress || addr.isLoopbackAddress || ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")
        } catch (_: Exception) { false }
    }
}
