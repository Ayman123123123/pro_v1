package com.red.server.pstn

import com.red.server.auth.model.AccountStatus
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.calls.CallHistoryService
import com.red.server.calls.CallRoute
import com.red.server.calls.CallType
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@Service
class PstnCallService(
    private val users: UserAccountRepository,
    private val redis: StringRedisTemplate,
    private val pstn: PstnManager,
    private val loadBalancer: DinstarLoadBalancer,
    private val history: CallHistoryService
) {
    companion object {
        private val log = LoggerFactory.getLogger(PstnCallService::class.java)

        /**
         * الطول الوطني لرقم المحمول اليمني: بادئة من رقمين + 7 أرقام.
         *
         * الهاتف الثابت أقصر (رمز محافظة من رقم واحد + 6–7 أرقام) ولا
         * تتصل به البوابة عبر شريحة GSM، فيُرفض هنا.
         */
        private const val MOBILE_NSN_LENGTH = 9
    }

    fun dial(userId: UUID, suppliedNumber: String): PstnCallResponse {
        val user = users.findById(userId).orElseThrow { NoSuchElementException("User not found") }
        require(user.status == AccountStatus.APPROVED) { "Account is not approved" }
        require(user.pstnEnabled && user.pstnDailyLimit > 0) { "PSTN access is not enabled for this account" }
        
        val number = normalizeYemeniNumber(suppliedNumber)
        
        // Rate limiting with Redis atomic counter
        val day = LocalDate.now(ZoneId.of("Asia/Aden"))
        val key = "red:pstn:daily:${user.id}:$day"
        val used = redis.opsForValue().increment(key) ?: 1L
        if (used == 1L) redis.expire(key, Duration.ofDays(2))
        if (used > user.pstnDailyLimit) {
            redis.opsForValue().decrement(key)
            log.warn("PSTN daily limit reached for user {} ({}/{})", user.redId, used - 1, user.pstnDailyLimit)
            throw IllegalArgumentException("Daily PSTN call limit reached ($used/${user.pstnDailyLimit})")
        }

        return runCatching {
            // الاختيار يشمل الأسطول كله ويستبعد المنافذ بلا إشارة صالحة.
            // `null` تعني عدم توفر أي مسار — نُبلّغ بذلك بدل محاولة اتصال
            // فاشلة على منفذ ميت.
            val selection = loadBalancer.selectPort(number)
                ?: throw IllegalStateException("No DINSTAR port with a usable signal is available")
            log.info("PSTN dial: user={} number={} gateway={} port={}",
                user.redId, number, selection.gatewayHost, selection.portIndex)
            val actionId = pstn.dialGsm(number, selection.pjsipEndpoint)
            history.start(user.redId, number, number, CallType.VOICE, CallRoute.DINSTAR, actionId)
            PstnCallResponse(actionId, "DIALING", number, used.toInt(), user.pstnDailyLimit, selection.portIndex)
        }.getOrElse {
            redis.opsForValue().decrement(key)
            throw IllegalStateException("Asterisk rejected the PSTN call", it)
        }
    }

    /**
     * Normalize and validate Yemeni phone numbers.
     * Supports: +967XXXXXXXXX, 967XXXXXXXXX, 0XXXXXXXXX, XXXXXXXXX
     * Output: local format without leading 0 (e.g., 777123456)
     */
    private fun normalizeYemeniNumber(value: String): String {
        val compact = value.filter { it.isDigit() || it == '+' }
        val local = when {
            compact.startsWith("+967") -> compact.removePrefix("+967")
            compact.startsWith("00967") -> compact.removePrefix("00967")
            compact.startsWith("967") -> compact.removePrefix("967")
            compact.startsWith("0") -> compact.removePrefix("0")
            else -> compact
        }
        require(local.matches(Regex("^[0-9]{6,12}$"))) { "Only valid Yemeni numbers are allowed" }

        // التصنيف يفوَّض إلى DinstarLoadBalancer — المصدر الوحيد لخريطة
        // بادئات المشغّلين، وهو ما يختار المنفذ فعليًا بعد قليل. جدول
        // محلي ثانٍ كان يفتح باب التفرّع: النسخة السابقة هنا أغفلت 78
        // و70 تمامًا فكانت ترفض أرقام يمن موبايل الجديدة وواي.
        val operator = DinstarLoadBalancer.classifyNumber(local)
        require(operator != null && operator.isMobile) { "Unrecognized Yemeni mobile prefix" }

        // الشرط السابق كان `... || local.length >= 9`، وكل محمول يمني
        // تسعة أرقام — فكان الطرف الثاني يُصدّق أي رقم ويُبطل التحقق
        // من البادئة كليًا.
        require(local.length == MOBILE_NSN_LENGTH) {
            "Yemeni mobile numbers are $MOBILE_NSN_LENGTH digits"
        }
        return local
    }
}

data class PstnCallResponse(
    val callId: String,
    val status: String,
    val number: String,
    val usedToday: Int,
    val dailyLimit: Int,
    val slot: Int = -1
)
