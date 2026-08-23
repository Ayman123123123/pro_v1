package com.red.sovereign.calls

/**
 * حقبة (Epoch) لمقبس الإشارات — نفس عقد RedSocketGeneration لكن باسم
 * مجال المكالمات: أي callback من مقبس قديم بعد invalidate() يُرفض
 * ولا يمس الاتصال البديل.
 */
typealias SignalingSocketEpoch = com.red.sovereign.core.RedSocketGeneration
