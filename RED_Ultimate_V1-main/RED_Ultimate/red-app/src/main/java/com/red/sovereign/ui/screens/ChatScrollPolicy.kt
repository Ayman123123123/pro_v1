package com.red.sovereign.ui.screens

/**
 * سياسة التثبيت التلقائي لأسفل قائمة الرسائل أثناء تحميل التاريخ.
 *
 * الهدف: لا قفز إلى النهاية عند فتح محادثة قديمة، ومع ذلك يبقى القارئ
 * الملتصق بالأسفل متابعاً لأحدث رسالة.
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
}
