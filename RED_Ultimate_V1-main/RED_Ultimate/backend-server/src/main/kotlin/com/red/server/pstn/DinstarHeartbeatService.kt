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
 * - Releases ports stuck ACTIVE > 10 minutes.
 */
@Service
@EnableScheduling
class DinstarHeartbeatService(
    private val fleet: DinstarFleetService,
    private val hardware: DinstarHardwareService,
    private val loadBalancer: DinstarLoadBalancer,
    @Value("\${red.dinstar.enabled:false}") private val dinstarEnabled: Boolean,
    private val wsBroadcaster: org.springframework.beans.factory.ObjectProvider<com.red.server.websocket.DinstarWebSocketHandler>
) {
    companion object { private val log = LoggerFactory.getLogger(DinstarHeartbeatService::class.java) }

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
                    // حالة المكالمة الحالية في العتاد هي مصدر التحرير المباشر؛
                    // أما انتهاء صلاحية الحجز في PostgreSQL فيعالجه cleanupStaleCalls.
                    if (callState.equals("IDLE", true) || callState.equals("REGISTERED", true)) {
                        loadBalancer.releasePort(gw.id, idx)
                    }
                }
                val recovered = lastReachable.put(gw.host, true) == false
                if (recovered) log.info("DINSTAR gateway {} is reachable again", gw.host)
                else log.debug("DINSTAR heartbeat ok: gateway={} ports={}", gw.host, ports.size)
                // بثّ التحديثات لكل عملاء WebSocket المتصلين
                wsBroadcaster.ifAvailable { it.broadcastPortStatus() }
            } catch (e: Exception) {
                fleet.markFailure(gw.id, e.message ?: "heartbeat failed")
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
        val released = loadBalancer.releaseExpiredReservations()
        if (released > 0) log.warn("DINSTAR cleanup released {} expired port reservation(s)", released)
    }
}
