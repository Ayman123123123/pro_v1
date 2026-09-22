package com.red.server.services

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

enum class SimVerificationState { UNKNOWN, PENDING, VERIFIED, MISMATCH, NOT_PRESENT }
enum class SimVerificationMethod { MANUAL, USSD, SMS, CALL }

data class GatewaySimInventoryPort(
    val gatewayId: UUID,
    val gatewayName: String,
    val gatewayModel: String,
    val gatewayHost: String,
    val portIndex: Int,
    val radioType: String?,
    val registrationState: String?,
    val callState: String?,
    val signalPercent: Int?,
    val operatorLabel: String?,
    val simLabel: String?,
    val verificationState: SimVerificationState,
    val verificationMethod: SimVerificationMethod?,
    val msisdnMasked: String?,
    val verifiedAt: Instant?
)

data class GatewaySimInventoryUpdate(
    val operatorLabel: String? = null,
    val simLabel: String? = null,
    val verificationState: SimVerificationState = SimVerificationState.UNKNOWN,
    val verificationMethod: SimVerificationMethod? = null,
    /** Four digits only; the server adds the permanent mask and never accepts a full telephone number. */
    val lastFourDigits: String? = null
)

/** Privacy-preserving inventory for physically installed SIMs and their port assignments. */
@Service
class GatewaySimInventoryService(private val jdbc: JdbcTemplate) {
    fun list(): List<GatewaySimInventoryPort> = jdbc.query(
        """SELECT g.id gateway_id,g.name gateway_name,g.model gateway_model,g.host gateway_host,
                  p.port_index,p.radio_type,p.registration_state,p.call_state,p.signal_percent,
                  i.operator_label,i.sim_label,i.verification_state,i.verification_method,i.msisdn_masked,i.verified_at
           FROM telecom_gateways g
           JOIN gateway_port_snapshots p ON p.gateway_id=g.id
           LEFT JOIN gateway_sim_inventory i ON i.gateway_id=p.gateway_id AND i.port_index=p.port_index
           ORDER BY g.name,p.port_index""",
        { rs, _ ->
            GatewaySimInventoryPort(
                gatewayId = rs.getObject("gateway_id", UUID::class.java),
                gatewayName = rs.getString("gateway_name"),
                gatewayModel = rs.getString("gateway_model"),
                gatewayHost = rs.getString("gateway_host"),
                portIndex = rs.getInt("port_index"),
                radioType = rs.getString("radio_type"),
                registrationState = rs.getString("registration_state"),
                callState = rs.getString("call_state"),
                signalPercent = rs.getObject("signal_percent", Int::class.java),
                operatorLabel = rs.getString("operator_label"),
                simLabel = rs.getString("sim_label"),
                verificationState = rs.getString("verification_state")?.let(SimVerificationState::valueOf) ?: SimVerificationState.UNKNOWN,
                verificationMethod = rs.getString("verification_method")?.let(SimVerificationMethod::valueOf),
                msisdnMasked = rs.getString("msisdn_masked"),
                verifiedAt = rs.getTimestamp("verified_at")?.toInstant()
            )
        }
    )

    @Transactional
    fun update(gatewayId: UUID, portIndex: Int, update: GatewaySimInventoryUpdate, actorId: UUID): GatewaySimInventoryPort {
        require(portIndex in 0..31) { "Port index must be 0..31" }
        val operator = update.operatorLabel?.trim()?.takeIf(String::isNotEmpty)
        val label = update.simLabel?.trim()?.takeIf(String::isNotEmpty)
        require(operator == null || operator.length <= 50 && operator.none(Char::isISOControl)) { "Invalid operator label" }
        require(label == null || label.length <= 80 && label.none(Char::isISOControl)) { "Invalid SIM label" }
        val masked = update.lastFourDigits?.takeIf(String::isNotEmpty)?.let {
            require(it.matches(Regex("^[0-9]{4}$"))) { "Only the last four MSISDN digits may be stored" }
            "••••$it"
        }
        require(jdbc.queryForObject("SELECT COUNT(*) FROM telecom_gateways WHERE id=?", Int::class.java, gatewayId) == 1) { "Gateway not found" }

        jdbc.update(
            """INSERT INTO gateway_sim_inventory(gateway_id,port_index,operator_label,sim_label,verification_state,verification_method,msisdn_masked,verified_at,verified_by,updated_at)
               VALUES (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)
               ON CONFLICT (gateway_id,port_index) DO UPDATE SET
                 operator_label=EXCLUDED.operator_label,sim_label=EXCLUDED.sim_label,
                 verification_state=EXCLUDED.verification_state,verification_method=EXCLUDED.verification_method,
                 msisdn_masked=EXCLUDED.msisdn_masked,verified_at=EXCLUDED.verified_at,
                 verified_by=EXCLUDED.verified_by,updated_at=CURRENT_TIMESTAMP""",
            gatewayId, portIndex, operator, label, update.verificationState.name,
            update.verificationMethod?.name, masked,
            if (update.verificationState == SimVerificationState.VERIFIED) java.sql.Timestamp.from(Instant.now()) else null,
            actorId
        )
        return list().firstOrNull { it.gatewayId == gatewayId && it.portIndex == portIndex }
            ?: throw IllegalStateException("Gateway inventory port is unavailable until a hardware snapshot exists")
    }
}
