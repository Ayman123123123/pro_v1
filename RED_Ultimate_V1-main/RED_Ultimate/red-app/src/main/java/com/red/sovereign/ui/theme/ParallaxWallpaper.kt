package com.red.sovereign.ui.theme

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * 🌌 ParallaxWallpaper — خلفية الدردشة التفاعلية مع حركة الهاتف
 *
 * تتفاعل الخلفية بحركة انزلاق زجاجية خفيفة (Parallax Shift)
 * بناءً على حساس التسارع والجاذبية في الهاتف.
 */
@Composable
fun ParallaxWallpaper(
    primaryColor: Color = SovereignColors.Navy,
    secondaryColor: Color = SovereignColors.Obsidian,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    // Throttled + low-passed: SENSOR_DELAY_NORMAL (~200ms) instead of UI/GAME
    // storm, exponential smoothing to kill jitter, and a 1.5dp deadband so
    // recomposition only happens on meaningful tilt. Unregistered on dispose.
    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var smoothX = 0f
        var smoothY = 0f
        var lastEmitMs = 0L

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null || event.values.size < 2) return
                val now = android.os.SystemClock.elapsedRealtime()
                // Throttle: at most ~15Hz even though sensor delivers ~5Hz
                if (now - lastEmitMs < 66L) return
                // Low-pass: raw tilt target (Max 20dp) smoothed with alpha 0.15
                val targetX = (event.values[0] * -2f).coerceIn(-20f, 20f)
                val targetY = (event.values[1] * 2f).coerceIn(-20f, 20f)
                smoothX += 0.15f * (targetX - smoothX)
                smoothY += 0.15f * (targetY - smoothY)
                // Deadband: skip recomposition for sub-pixel jitter
                if (kotlin.math.abs(smoothX - offsetX) > 1.5f || kotlin.math.abs(smoothY - offsetY) > 1.5f) {
                    offsetX = smoothX
                    offsetY = smoothY
                    lastEmitMs = now
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager?.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)

        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(offsetX.toInt(), offsetY.toInt()) }
                .background(
                    Brush.radialGradient(
                        colors = listOf(primaryColor, secondaryColor),
                        radius = 1200f
                    )
                )
        )
        content()
    }
}
