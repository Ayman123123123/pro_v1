package com.red.server.pstn

import com.red.server.services.DinstarFleetService
import com.red.server.services.DinstarHardwareService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Heartbeat between the backend and Dinstar gateways.
 * - Skips entirely when DINSTAR_ENABLED=false (no hardware / Docker cannot see LAN).
 * - Logs WARN only on state change or every 5 minutes (no 30s spam).
 * - Reconciles backend reservations against the DEVICE's own port state:
 *   the device is the final authority on whether a port carries a call.
 *   Orphaned reservations (device IDLE, reservation older than the grace
 *   window) are released; ports the device reports stuck ACTIVE past the
 *   limit are hung up on the device and released.
 */
@Service
@EnableScheduling
class DinstarHeartbeatService(
    private val fleet: DinstarFleetService,
    private val hardware: DinstarHardwareService,
    private val loadBalancer: DinstarLoadBalancer,
    @Value("\${red.dinstar.enabled:true}") private val dinstarEnabled: Boolean,
    private val wsBroadcaster: org.springframework.beans.factory.ObjectProvider<com.red.server.websocket.DinstarWebSocketHandler>,
    private val eventBridge: org.springframework.beans.factory.ObjectProvider<com.red.server.websocket.DinstarEventBridge>,
    private val reservations: PersistentReservationService? = null,
    private val pstnManager: org.springframework.beans.factory.ObjectProvider<EnhancedPstnManager>? = null
) {
    companion object {
        private val log = LoggerFactory.getLogger(DinstarHeartbeatService::class.java)

        /** مهلة السماح قبل اعتبار حجزٍ على منفذ خالٍ حجزًا يتيمًا. */
        private val IDLE_GRACE: Duration = Duration.ofMinutes(2)

        /** حدّ اعتبار منفذ ACTIVE على الجهاز منفذًا عالقًا. */
        private const val STUCK_ACTIVE_MINUTES = 10L
    }

    private val activeCalls = ConcurrentHashMap<String, Instant>()
    private val lastWarnAt = ConcurrentHashMap<String, Instant>()
    private val lastReachable = ConcurrentHashMap<String, Boolean>()

    @Scheduled(fixedDelayString = "\${red.dinstar.heartbeat-ms:30000}", initialDelay = 15_000)
    fun heartbeat() {
        if (!dinstarEnabled) return
        val gateways = try { fleet.listGateways(onlyEnabled = true) } catch (e: Exception) {
            log.warn("DINSTAR heartbeat: failed to list gateways: {}", e.message)
            return
        }
        if (gateways.isEmpty()) {
            log.debug("DINSTAR heartbeat: no enabled gateways")
            return
        }

        for (gw in gateways) {
            try {
                val ports = hardware.getHardwareStatus(gw)
                fleet.markHealthy(gw.id)
                for (port in ports) {
                    val idx = (port["index"] as? Number)?.toInt() ?: continue
                    val callState = port["callState"]?.toString()
                    val key = "${gw.id}#$idx"
                    if (callState.equals("ACTIVE", true)) {
                        activeCalls.putIfAbsent(key, Instant.now())
                        val started = activeCalls[key] ?: Instant.now()
                        if (Duration.between(started, Instant.now()).toMinutes() > STUCK_ACTIVE_MINUTES) {
                            // المنفذ عالق ACTIVE على الجهاز نفسه: لا مكالمة
                            // لدينا تفسّره. أسقط ساق SIP إن وُجدت، ثم حرّر.
                            log.warn("DINSTAR port {} on gateway {} stuck ACTIVE >{}min — force releasing", idx, gw.host, STUCK_ACTIVE_MINUTES)
                            forceRelease(gw.id, idx)
                            activeCalls.remove(key)
                        }
                    } else {
                        activeCalls.remove(key)
                        // ⚠️ كان هنا `releasePort` غير مشروط لكل منفذ يقول
                        // الجهاز إنه IDLE. وذلك يمسح حجز مكالمة في طور
                        // التهيئة (بين الحجز وإعلان ACTIVE تمرّ ثوانٍ يكون
                        // فيها المنفذ IDLE بحق) ⇒ منفذ واحد يُمنَح لمكالمتين.
                        //
                        // الصحيح: تصفية الحجوزات اليتيمة فقط — أي التي
                        // مضى على حجزها أكثر من IDLE_GRACE والجهاز يقول
                        // إن المنفذ خالٍ. هذا يُصلح «البورت المعلّق» بلا
                        // كسر المكالمات الجديدة.
                        if (callState.equals("IDLE", true) || callState.equals("REGISTERED", true)) {
                            runCatching {
                                reservations?.releaseStaleIdlePort(gw.id, idx, IDLE_GRACE)
                            }.onFailure { log.debug("stale idle reconcile failed for {}#{}: {}", gw.host, idx, it.message) }
                        }
                    }
                    // جسر حالة المنفذ لمراقبي `/ws/dinstar` (كان الجسر بلا منتِج).
                    eventBridge.ifAvailable { bridge ->
                        runCatching { bridge.onPortStatusChanged(gw.host, idx, port) }
                    }
                }
                val recovered = lastReachable.put(gw.host, true) == false
                if (recovered) log.info("DINSTAR gateway {} is reachable again", gw.host)
                else log.debug("DINSTAR heartbeat ok: gateway={} ports={}", gw.host, ports.size)
                // بثّ التحديثات لكل عملاء WebSocket المتصلين
                wsBroadcaster.ifAvailable { it.broadcastPortStatus() }
            } catch (e: Exception) {
                fleet.markFailure(gw.id, e.message ?: "heartbeat failed")
                // أبلغ مراقبي `/ws/dinstar` بالانقطاع فورًا بدل انتظار قراءة اللوق.
                eventBridge.ifAvailable { bridge ->
                    runCatching {
                        bridge.onException(
                            "GATEWAY_UNREACHABLE",
                            mapOf("gatewayHost" to gw.host, "gatewayId" to gw.id.toString(), "error" to (e.message ?: "heartbeat failed"))
                        )
                    }
                }
                val firstFailure = lastReachable.put(gw.host, false) != false
                val last = lastWarnAt[gw.host]
                val quiet = last != null && Duration.between(last, Instant.now()).toMinutes() < 5
                if (firstFailure || !quiet) {
                    log.warn(
                        "DINSTAR heartbeat failed for {} — power, LAN IP, or Docker cannot reach the box. Set DINSTAR_ENABLED=false if no hardware. ({})",
                        gw.host,
                        e.message
                    )
                    lastWarnAt[gw.host] = Instant.now()
                } else {
                    log.debug("DINSTAR still unreachable {}: {}", gw.host, e.message)
                }
            }
        }
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    fun cleanupStaleCalls() {
        if (!dinstarEnabled) return
        val now = Instant.now()
        val stale = activeCalls.filter { Duration.between(it.value, now).toMinutes() > 15 }
        for ((key, _) in stale) {
            log.warn("DINSTAR cleanup: releasing stale call {}", key)
            val parts = key.split("#")
            if (parts.size == 2) {
                val port = parts[1].toIntOrNull()
                val gwId = runCatching { java.util.UUID.fromString(parts[0]) }.getOrNull()
                if (port != null) forceRelease(gwId, port)
            }
            activeCalls.remove(key)
        }
    }

    /**
     * تحرير قسري لمنفذ عالق: يُسقط ساق SIP الحيّة على أستيريكس أولًا ثم
     * يمسح الحجز. بلا إسقاط الساق كان الخادم «يحرّر» المنفذ منطقيًا فقط
     * ويبقى الجهاز يراه مشغولًا، فتُوجَّه إليه مكالمة تفشل حتمًا.
     */
    private fun forceRelease(gatewayId: java.util.UUID?, port: Int) {
        // ساق الجهاز: أي قناة PJSIP نحو نظير هذا المنفذ.
        runCatching {
            pstnManager?.ifAvailable { mgr -> mgr.hangupPortChannels(gatewayId, port) }
        }.onFailure { log.debug("force release: AMI hangup for port {} failed: {}", port, it.message) }
        loadBalancer.releasePort(gatewayId, port)
    }
}
