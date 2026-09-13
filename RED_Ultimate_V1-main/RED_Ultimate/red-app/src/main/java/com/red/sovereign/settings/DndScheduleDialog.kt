package com.red.sovereign.settings

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Calendar

/**
 * سياسة عدم الإزعاج — المصدر الوحيد لحساب النشاط والقمع.
 * يستهلكها [com.red.sovereign.calls.CallNotificationManager] قبل عرض رنين المكالمات،
 * وحوار [DndScheduleDialog] للضبط اليدوي وطلب صلاحية النظام.
 *
 * الوقت بدقائق منذ منتصف الليل (0..1439). النطاق الليلي (start > end) يعني
 * العبور لمنتصف الليل: نشط إذا now >= start أو now < end.
 */
object DndPolicy {
    fun minutesNow(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    fun isActiveNow(s: YounesSettings, nowMinutes: Int = minutesNow()): Boolean {
        if (!s.dndEnabled) return false
        val start = s.dndStartMinutes.coerceIn(0, 1439)
        val end = s.dndEndMinutes.coerceIn(0, 1439)
        if (start == end) return true
        return if (start < end) nowMinutes in start until end else (nowMinutes >= start || nowMinutes < end)
    }

    /**
     * هل تُقمع مكالمة واردة الآن؟ الاستثناءات تسمح بالمرور:
     * المفضلة (جهات مميزة) والمتكررة (نفس المتصل خلال دقائق) حسب مفاتيح الحوار.
     */
    fun shouldSuppress(s: YounesSettings, isFavorite: Boolean = false, isRepeatCaller: Boolean = false): Boolean {
        if (!isActiveNow(s)) return false
        if (isFavorite && s.dndAllowFavorites) return false
        if (isRepeatCaller && s.dndAllowRepeat) return false
        return true
    }

    fun hasPolicyAccess(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) nm.isNotificationPolicyAccessGranted else true
    }

    fun policyAccessIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    /**
     * يضبط INTERRUPTION_FILTER الفعلي للنظام — يتطلب ACCESS_NOTIFICATION_POLICY.
     * نشط: PRIORITY إن سُمح للمفضلة (تمر جهات الأولوية) وإلا NONE. خامد: ALL.
     * @return true إذا طُبق، false إذا بلا صلاحية أو فشل النظام.
     */
    fun applySystemFilter(context: Context): Boolean {
        if (!hasPolicyAccess(context)) return false
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (nm == null) {
            android.util.Log.w("DndPolicy", "NotificationManager unavailable — cannot apply filter")
            return false
        }
        return runCatching {
            val s = SettingsRuntime.current
            val active = isActiveNow(s)
            val filter = when {
                !active -> NotificationManager.INTERRUPTION_FILTER_ALL
                s.dndAllowFavorites -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
                else -> NotificationManager.INTERRUPTION_FILTER_NONE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) nm.setInterruptionFilter(filter)
            true
        }.getOrDefault(false)
    }

    fun formatMinutes(v: Int): String = "%02d:%02d".format(v / 60, v % 60)
}

/**
 * حوار جدولة عدم الإزعاج: تفعيل + وقت البدء/النهاية + استثناءات
 * + طلب صلاحية ACCESS_NOTIFICATION_POLICY + زر ضبط INTERRUPTION_FILTER.
 * كل تغيير يُحفظ فوراً في [SettingsViewModel] ويُطبق فلتر النظام إن أمكن.
 */
@Composable
fun DndScheduleDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val s = viewModel.state
    val active = DndPolicy.isActiveNow(s)
    val hasAccess = DndPolicy.hasPolicyAccess(context)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("عدم الإزعاج", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("تفعيل الجدولة", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (active) "نشط الآن — تُقمع المكالمات والتنبيهات" else "خامد الآن",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = s.dndEnabled,
                        onCheckedChange = {
                            viewModel.setDndEnabled(it)
                            if (it) {
                                val applied = DndPolicy.applySystemFilter(context)
                                if (!applied) {
                                    android.widget.Toast.makeText(context, "تعذر ضبط فلتر النظام", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                                if (nm == null) {
                                    android.widget.Toast.makeText(context, "تعذر الوصول لخدمة الإشعارات", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    runCatching {
                                        if (DndPolicy.hasPolicyAccess(context)) {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
                Text("من ${DndPolicy.formatMinutes(s.dndStartMinutes)}", fontWeight = FontWeight.SemiBold)
                Slider(
                    value = s.dndStartMinutes.toFloat(),
                    onValueChange = { viewModel.setDndSchedule(it.toInt(), s.dndEndMinutes) },
                    valueRange = 0f..1439f
                )
                Text("إلى ${DndPolicy.formatMinutes(s.dndEndMinutes)}", fontWeight = FontWeight.SemiBold)
                Slider(
                    value = s.dndEndMinutes.toFloat(),
                    onValueChange = { viewModel.setDndSchedule(s.dndStartMinutes, it.toInt()) },
                    valueRange = 0f..1439f
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("استثناء المفضلة", fontWeight = FontWeight.SemiBold)
                        Text("مكالمات الجهات المميزة تمر دائماً", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = s.dndAllowFavorites, onCheckedChange = viewModel::setDndAllowFavorites)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("استثناء المتكررة", fontWeight = FontWeight.SemiBold)
                        Text("نفس المتصل مرتين خلال دقائق يمر", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = s.dndAllowRepeat, onCheckedChange = viewModel::setDndAllowRepeat)
                }
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (hasAccess) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (hasAccess) "صلاحية سياسة الإشعارات ممنوحة — يمكن ضبط فلتر النظام"
                            else "يلزم إذن الوصول لسياسة الإشعارات لضبط فلتر النظام",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!hasAccess) {
                                OutlinedButton(onClick = {
                                    runCatching { context.startActivity(DndPolicy.policyAccessIntent()) }
                                }) { Text("منح الصلاحية") }
                            }
                            OutlinedButton(onClick = {
                                val ok = DndPolicy.applySystemFilter(context)
                                if (!ok) {
                                    android.widget.Toast.makeText(context, "تعذر ضبط فلتر النظام", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }) { Text("ضبط الفلتر الآن") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("تم") } }
    )
}

/** صف دخول يُستخدم داخل NotificationSettings لفتح حوار الجدولة. */
@Composable
fun DndEntry(onOpen: () -> Unit) {
    val s = SettingsRuntime.current
    val label = if (s.dndEnabled) "مفعّل ${DndPolicy.formatMinutes(s.dndStartMinutes)}–${DndPolicy.formatMinutes(s.dndEndMinutes)}" else "متوقف"
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("عدم الإزعاج المجدول", fontWeight = FontWeight.SemiBold)
                Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Text("ضبط", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}
