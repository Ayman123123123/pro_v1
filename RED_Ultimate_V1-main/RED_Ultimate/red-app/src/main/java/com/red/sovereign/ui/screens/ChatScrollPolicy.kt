package com.red.sovereign.ui.screens

import androidx.compose.foundation.lazy.LazyListState

/**
 * سياسة التثبيت التلقائي لأسفل قائمة الرسائل أثناء تحميل التاريخ.
 *
 * الهدف: لا قفز إلى النهاية عند فتح محادثة قديمة، ومع ذلك يبقى القارئ
 * الملتصق بالأسفل متابعاً لأحدث رسالة.
 *
 * التوحيد (2026-09-10): كل شاشات الدردشة كانت تكرر
 * `runCatching { listState.scrollToItem(lastIndex) }` مع مفتاح `size`
 * (عاصفة LaunchedEffect عند الحذف الجماعي/التحديث). الآن التمرير موحد
 * في [LazyListState.scrollOnce] والمفتاح هو معرف آخر رسالة (lastId)
 * لا الحجم — لا منطق محذوف، فقط المفتاح والدالة.
 */
object ChatScrollPolicy {

    /**
     * @param lastVisibleIndex فهرس آخر عنصر مرئي حالياً (-1 قبل أول تخطيط).
     * @param itemCount عدد عناصر القائمة الحالي.
     * @param lastVisibleMessageId معرف آخر رسالة مرئية (تشخيص إضافي).
     * @param latestMessageId معرف أحدث رسالة في المحادثة.
     *
     * يُثبَّت التمرير للأسفل عندما: لم يتم التخطيط بعد (عرض أول)، أو كان
     * المستخدم قريباً من النهاية (ضمن عنصرين من الآخر).
     */
    fun shouldKeepPinned(
        lastVisibleIndex: Int,
        itemCount: Int,
        lastVisibleMessageId: String?,
        latestMessageId: String?
    ): Boolean {
        // قبل أول قياس تخطيط — ثبّت على أحدث رسالة (فتح محادثة جديدة).
        if (lastVisibleIndex < 0 || itemCount <= 0) return true
        // قريب من النهاية → تابع الأحدث؛ بعيد (يقرأ تاريخاً) → لا تقفز.
        return lastVisibleIndex >= itemCount - NEAR_END_WINDOW
    }

    /** كم عنصراً من النهاية نعتبر «القارئ ملتصقاً بالأسفل». */
    const val NEAR_END_WINDOW = 2

    /**
     * مهلة منع العاصفة لحضور جهات الاتصال (حضور جماعي لا يستحق
     * طلب شبكة لكل تغيّر حجم أثناء المزامنة الأولى).
     */
    const val PRESENCE_DEBOUNCE_MS = 2000L
}

/**
 * تمرير آمن موحد لأسفل القائمة — scrollToItem + runCatching.
 *
 * كان `animateScrollToItem` يرمي IndexOutOfBounds عند تقلص القائمة
 * أثناء الأنيميشن (حذف/تحديث سريع)، فكل الشاشات تستخدم هذه الدالة الآن.
 * لا تعيد رمي أي استثناء — الفشل الصامت مقصود هنا (إطار لم يُخطط بعد).
 */
suspend fun LazyListState.scrollOnce(index: Int) {
    if (index < 0) return
    runCatching { scrollToItem(index) }
}
