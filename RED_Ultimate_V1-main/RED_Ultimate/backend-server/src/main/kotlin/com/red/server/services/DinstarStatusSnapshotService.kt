package com.red.server.services

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * مصدر الحقيقة المركزي لحالة منافذ DINSTAR المعروضة لحظيًا.
 *
 * لا يجوز أن يؤدي كل اتصال WebSocket إلى استعلام عتادي خاص به. تستعلم هذه
 * الخدمة كل بوابة مفعلة مرة واحدة لكل فترة، وتحفظ لقطة مشتركة وآمنة يمكن
 * للواجهة بثها إلى جميع المديرين. كما يمنع [refreshing] تداخل الدورات عندما
 * يتأخر جهاز أو شبكة إدارة عن الفترة المجدولة.
 *
 * قبل هذه الخدمة كان [com.red.server.websocket.DinstarWebSocketHandler] يجدول
 * دورة استعلام عتادي مستقلة لكل جلسة، فـ N مديرين متصلين = N استعلامًا لكل
 * بوابة كل 5 ثوانٍ — عبء يتضاعف خطيًا على العتاد. الآن دورة واحدة مشتركة.
 */
@Service
class DinstarStatusSnapshotService(
    private val hardware: DinstarHardwareService,
    private val fleet: DinstarFleetService,
    private val scheduler: TaskScheduler,
    @Value("\${red.dinstar.status-snapshot-interval:5s}") private val refreshInterval: Duration
) {
    private val log = LoggerFactory.getLogger(DinstarStatusSnapshotService::class.java)
    private val refreshing = AtomicBoolean(false)
    private val successfulRefreshes = AtomicLong(0)
    private val failedRefreshes = AtomicLong(0)
    @Volatile private var task: ScheduledFuture<*>? = null
    @Volatile private var latest: Map<String, Any?> = emptyPayload(System.currentTimeMillis())

    @PostConstruct
    fun start() {
        refreshNow()
        task = scheduler.scheduleAtFixedRate({ refreshNow() }, refreshInterval)
    }

    @PreDestroy
    fun stop() {
        task?.cancel(false)
    }

    fun payload(): Map<String, Any?> = latest

    fun refreshNow() {
        if (!refreshing.compareAndSet(false, true)) {
            log.debug("Skipped overlapping DINSTAR status refresh")
            return
        }
        try {
            val gateways = fleet.listGateways(onlyEnabled = true)
            val ports = mutableListOf<Map<String, Any?>>()
            val unavailableGateways = mutableListOf<String>()

            gateways.forEach { gateway ->
                try {
                    hardware.getHardwareStatus(gateway).forEach { port ->
                        ports += port + mapOf(
                            "gatewayId" to gateway.id.toString(),
                            "gatewayHost" to gateway.host,
                            "gatewayName" to gateway.name,
                            "gatewayModel" to gateway.model
                        )
                    }
                    fleet.markHealthy(gateway.id)
                } catch (error: Exception) {
                    // لا تمرر نص رد جهاز أو عنوان داخلي إلى WebSocket؛ تسجّل داخليًا فقط.
                    fleet.markFailure(gateway.id, error.message ?: "Status snapshot failed")
                    unavailableGateways += gateway.id.toString()
                    log.warn("DINSTAR snapshot failed for gateway {}: {}", gateway.id, error.message, error)
                }
            }

            latest = payloadFor(gateways.size, ports, unavailableGateways)
            successfulRefreshes.incrementAndGet()
        } catch (error: Exception) {
            failedRefreshes.incrementAndGet()
            log.error("DINSTAR status snapshot cycle failed: {}", error.message, error)
            // تبقى آخر لقطة سليمة بدل استبدالها بصفر أو كشف سبب تشغيلي للعميل.
        } finally {
            refreshing.set(false)
        }
    }

    private fun payloadFor(
        totalGateways: Int,
        ports: List<Map<String, Any?>>,
        unavailableGateways: List<String>
    ): Map<String, Any?> = mapOf(
        "type" to "DINSTAR_PORT_STATUS",
        "timestamp" to System.currentTimeMillis(),
        "data" to mapOf(
            "ports" to ports,
            "totalGateways" to totalGateways,
            "totalPorts" to ports.size,
            "registered" to ports.count { (it["status"]?.toString() ?: "").equals("REGISTERED", ignoreCase = true) },
            "usable" to ports.count { it["signalUsable"] == true },
            "unavailableGatewayIds" to unavailableGateways,
            "refreshes" to mapOf(
                "successful" to successfulRefreshes.get(),
                "failed" to failedRefreshes.get(),
                "inProgress" to false
            )
        )
    )

    private fun emptyPayload(timestamp: Long): Map<String, Any?> = mapOf(
        "type" to "DINSTAR_PORT_STATUS",
        "timestamp" to timestamp,
        "data" to mapOf(
            "ports" to emptyList<Map<String, Any?>>(),
            "totalGateways" to 0,
            "totalPorts" to 0,
            "registered" to 0,
            "usable" to 0,
            "unavailableGatewayIds" to emptyList<String>(),
            "refreshes" to mapOf("successful" to 0L, "failed" to 0L, "inProgress" to false)
        )
    )
}
