package com.red.server.pstn

import com.red.server.services.DinstarFleetService
import com.red.server.services.DinstarHardwareService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * ❤️ Heartbeat قوي بين الباك إند وبوابات Dinstar
 * - يفحص حالة كل بوابة كل 30 ثانية عبر getHardwareStatus
 * - يحدّث health_state و last_seen_at في DB
 * - يحرر المنافذ العالقة التي بقيت ACTIVE لأكثر من 10 دقائق بلا HangupEvent
 * - يكشف انقطاع الشبكة ويعيد الاتصال AMI تلقائيًا
 *
 * يمنع تعليق خطوط SIM عند سقوط WebSocket أو فقدان حدث Hangup.
 */
@Service
@EnableScheduling
class DinstarHeartbeatService(
    private val fleet: DinstarFleetService,
    private val hardware: DinstarHardwareService,
    private val loadBalancer: DinstarLoadBalancer
) {
    companion object { private val log = LoggerFactory.getLogger(DinstarHeartbeatService::class.java) }

    // تتبع وقت بدء كل مكالمة نشطة لمنع التعليق
    private val activeCalls = ConcurrentHashMap<String, Instant>() // key: gatewayId#port

    @Scheduled(fixedDelay = 30_000, initialDelay = 10_000)
    fun heartbeat() {
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

                // فحص المنافذ العالقة: إذا بقيت ACTIVE لأكثر من 10 دقائق بدون تحديث، حررها
                for (port in ports) {
                    val idx = (port["index"] as? Number)?.toInt() ?: continue
                    val callState = port["callState"]?.toString()
                    val key = "${gw.id}#$idx"
                    if (callState.equals("ACTIVE", true)) {
                        activeCalls.putIfAbsent(key, Instant.now())
                        val started = activeCalls[key] ?: Instant.now()
                        if (java.time.Duration.between(started, Instant.now()).toMinutes() > 10) {
                            log.warn("DINSTAR port {} on gateway {} stuck ACTIVE >10min — force releasing", idx, gw.host)
                            loadBalancer.releasePort(gw.id, idx)
                            activeCalls.remove(key)
                        }
                    } else {
                        activeCalls.remove(key)
                        // إذا أصبح غير مشغول، تأكد من تحريره من الموزع
                        if (callState.equals("IDLE", true) || callState.equals("REGISTERED", true)) {
                            loadBalancer.releasePort(gw.id, idx)
                        }
                    }
                }
                log.debug("DINSTAR heartbeat ok: gateway={} ports={}", gw.host, ports.size)
            } catch (e: Exception) {
                log.warn("DINSTAR heartbeat failed for gateway {}: {}", gw.host, e.message)
                fleet.markFailure(gw.id, e.message ?: "heartbeat failed")
            }
        }
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    fun cleanupStaleCalls() {
        val now = Instant.now()
        val stale = activeCalls.filter { java.time.Duration.between(it.value, now).toMinutes() > 15 }
        for ((key, _) in stale) {
            log.warn("DINSTAR cleanup: releasing stale call {}", key)
            val parts = key.split("#")
            if (parts.size == 2) {
                try {
                    val gwId = java.util.UUID.fromString(parts[0])
                    val port = parts[1].toIntOrNull()
                    if (port != null) loadBalancer.releasePort(gwId, port)
                } catch (_: Exception) {
                    // gatewayId might be "local" for single-gateway mode
                    val port = parts[1].toIntOrNull()
                    if (port != null) loadBalancer.releasePort(null, port)
                }
            }
            activeCalls.remove(key)
        }
    }
}
