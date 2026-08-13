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
    @Value("\${red.dinstar.enabled:true}") private val dinstarEnabled: Boolean
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
                if (id != null) log.info("سُجّلت بوابة DINSTAR الافتراضية في الأسطول: {}", id)
                else log.debug("لا حاجة لتسجيل بوابة بذرة — مسجّلة سلفًا أو العنوان غير خاص")
            }
            .onFailure { log.warn("تعذّر تسجيل بوابة DINSTAR الافتراضية: {}", it.message) }
    }
}
