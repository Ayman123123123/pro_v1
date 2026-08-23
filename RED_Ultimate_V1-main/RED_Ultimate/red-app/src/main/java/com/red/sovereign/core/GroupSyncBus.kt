package com.red.sovereign.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 🔄 ناقل مزامنة المجموعات — يُستخدم عندما يحتاج العميل لإعادة تحميل حالة مجموعة:
 * - استلام GROUP_SYNC من الخادم (تغيّر عضوية/أدوار/حذف)
 * - فشل فك تشفير رسالة جماعية (مفاتيح قديمة/عضو جديد) → تحديث ذاتي شفاء
 * القيمة المنشورة = معرف المجموعة، أو "" لتحديث كل المجموعات.
 */
object GroupSyncBus {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val events = _events.asSharedFlow()

    fun needRefresh(groupId: String) {
        _events.tryEmit(groupId)
    }
}
