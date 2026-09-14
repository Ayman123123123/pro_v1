package com.red.sovereign.calls

import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Virtual Background Manager — Liquid Glass Sovereign 2026
 *
 * Provides lightweight background effects without heavy libraries:
 * - BLUR: Glassy blur via RenderEffect (Android 12+)
 * - BLUR_HEAVY: High intensity blur
 * - SOLID: Solid color with transparency
 * - IMAGE: Custom background image URI
 */
enum class VirtualBgEffect { NONE, BLUR, BLUR_HEAVY, SOLID, IMAGE }

data class VirtualBgConfig(
    val effect: VirtualBgEffect = VirtualBgEffect.NONE,
    val solidColor: Color = Color(0xFF0A0F18),
    val imageUri: String? = null,
    val blurRadius: Float = 24f
)

object VirtualBackgroundManager {
    private val lock = Any()

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
        synchronized(lock) {
            config = config.copy(effect = effect)
        }
    }

    fun setSolidColor(color: Color) {
        synchronized(lock) {
            config = config.copy(effect = VirtualBgEffect.SOLID, solidColor = color)
        }
    }

    fun setImage(uri: String) {
        synchronized(lock) {
            config = config.copy(effect = VirtualBgEffect.IMAGE, imageUri = uri)
        }
    }

    fun clear() {
        synchronized(lock) {
            config = VirtualBgConfig()
        }
    }

    fun shouldApplyComposeBlur(): Boolean =
        isSupported && (config.effect == VirtualBgEffect.BLUR || config.effect == VirtualBgEffect.BLUR_HEAVY)

    fun blurRadiusForCompose(): Float = when (config.effect) {
        VirtualBgEffect.BLUR_HEAVY -> 36f
        VirtualBgEffect.BLUR -> 24f
        else -> 0f
    }
}
