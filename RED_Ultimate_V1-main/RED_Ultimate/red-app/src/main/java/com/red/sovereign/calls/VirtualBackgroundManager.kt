package com.red.sovereign.calls

import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * مدير الخلفية الافتراضية — Liquid Glass 2026 (ذكاء اصطناعي خفيف + Blur)
 *
 * يوفّر تأثيرات حديثة دون الاعتماد على مكتبات ثقيلة:
 * - BLUR: ضبابية زجاجية عبر RenderEffect (أندرويد 12+)
 * - SOLID: لون ثابت مع شفافية
 * - IMAGE: صورة مخصصة (يُمرر uri للـ WebRTC إن توفر)
 *
 * التصميم أسطوري: واجهة بسيطة، حالة مراقبة بـ State، استهلاك بطارية منخفض.
 */
enum class VirtualBgEffect { NONE, BLUR, BLUR_HEAVY, SOLID, IMAGE }

data class VirtualBgConfig(
    val effect: VirtualBgEffect = VirtualBgEffect.NONE,
    val solidColor: Color = Color(0xFF0A0F18),
    val imageUri: String? = null,
    val blurRadius: Float = 24f
)

object VirtualBackgroundManager {
    var config by mutableStateOf(VirtualBgConfig())
        private set

    var isSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        private set

    val availableEffects: List<VirtualBgEffect> = listOf(
        VirtualBgEffect.NONE,
        VirtualBgEffect.BLUR,
        VirtualBgEffect.BLUR_HEAVY,
        VirtualBgEffect.SOLID,
        VirtualBgEffect.IMAGE
    )

    fun setEffect(effect: VirtualBgEffect) {
        config = config.copy(effect = effect)
    }

    fun setSolidColor(color: Color) {
        config = config.copy(effect = VirtualBgEffect.SOLID, solidColor = color)
    }

    fun setImage(uri: String) {
        config = config.copy(effect = VirtualBgEffect.IMAGE, imageUri = uri)
    }

    fun clear() {
        config = VirtualBgConfig()
    }

    /** يحدد هل يجب تطبيق blur عبر RenderEffect في Compose */
    fun shouldApplyComposeBlur(): Boolean =
        isSupported && (config.effect == VirtualBgEffect.BLUR || config.effect == VirtualBgEffect.BLUR_HEAVY)

    fun blurRadiusForCompose(): Float = when (config.effect) {
        VirtualBgEffect.BLUR_HEAVY -> 32f
        VirtualBgEffect.BLUR -> 22f
        else -> 0f
    }
}
