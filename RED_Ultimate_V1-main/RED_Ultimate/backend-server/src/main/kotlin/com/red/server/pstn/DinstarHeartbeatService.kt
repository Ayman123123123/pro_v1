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
    @Value("\${red.dinstar.enabled:true}") private val dinstarEnabled: Boolean
) {
    companion object { private val log = LoggerFactory.getLogger(DinstarHeartbeatService::class.java) }

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
                        if (Duration.between(started, Instant.now()).toMinutes() > 10) {
                            log.warn("DINSTAR port {} on gateway {} stuck ACTIVE >10min — force releasing", idx, gw.host)
                            loadBalancer.releasePort(gw.id, idx)
                            activeCalls.remove(key)
                        }
                    } else {
                        activeCalls.remove(key)
                        if (callState.equals("IDLE", true) || callState.equals("REGISTERED", true)) {
                            loadBalancer.releasePort(gw.id, idx)
                        }
                    }
                }
                val recovered = lastReachable.put(gw.host, true) == false
                if (recovered) log.info("DINSTAR gateway {} is reachable again", gw.host)
                else log.debug("DINSTAR heartbeat ok: gateway={} ports={}", gw.host, ports.size)
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
        val now = Instant.now()
        val stale = activeCalls.filter { Duration.between(it.value, now).toMinutes() > 15 }
        for ((key, _) in stale) {
            log.warn("DINSTAR cleanup: releasing stale call {}", key)
            val parts = key.split("#")
            if (parts.size == 2) {
                try {
                    val gwId = java.util.UUID.fromString(parts[0])
                    val port = parts[1].toIntOrNull()
                    if (port != null) loadBalancer.releasePort(gwId, port)
                } catch (_: Exception) {
                    val port = parts[1].toIntOrNull()
                    if (port != null) loadBalancer.releasePort(null, port)
                }
            }
            activeCalls.remove(key)
        }
    }
}
