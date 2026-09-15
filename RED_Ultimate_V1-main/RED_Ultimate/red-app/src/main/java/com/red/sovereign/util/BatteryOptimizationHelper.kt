package com.red.sovereign.util

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * ════════════════════════════════════════════════════════════════════════
 *  Sovereign Battery Optimization & Background Reliability Helper
 * ════════════════════════════════════════════════════════════════════════
 *
 * بدون خدمات Google/Firebase، يعتمد التطبيق السيادي على UnifiedPush أو
 * الاتصال المباشر لإيقاظ الهاتف ورنين المكالمات الواردة.
 *
 * توفر هذه الفئة فحصاً وتوجيهاً ذكياً لحماية التطبيق من الإيقاف العدواني
 * بواسطة أنظمة إدارة البطارية (Xiaomi, Samsung, Huawei, Oppo, Vivo).
 */
object BatteryOptimizationHelper {
    private const val TAG = "BatteryOptHelper"

    /**
     * يتحقق مما إذا كان التطبيق مستثنى من قيود تحسين البطارية.
     */
    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            return pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        }
        return true
    }

    /**
     * يطلب من النظام استثناء التطبيق من تحسين البطارية عبر نافذة الحوار الرسمية.
     */
    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimization(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                if (!isBatteryOptimizationIgnored(context)) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to launch ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", e)
                try {
                    val genericIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(genericIntent)
                    return true
                } catch (e2: Exception) {
                    Log.e(TAG, "Failed generic battery settings fallback", e2)
                }
            }
        }
        return false
    }

    /**
     * يفتح صفحة التشغيل التلقائي (Auto-Start) بحسب الشركة المصنعة للجهاز.
     */
    fun openAutostartSettings(context: Context): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val intents = when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> listOf(
                Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
                Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings"))
            )
            manufacturer.contains("samsung") -> listOf(
                Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")),
                Intent().setComponent(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")),
                Intent().setComponent(ComponentName("com.samsung.android.sm_cn", "com.samsung.android.sm.ui.battery.BatteryActivity"))
            )
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> listOf(
                Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
                Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
                Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"))
            )
            manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus") -> listOf(
                Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
                Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")),
                Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
                Intent().setComponent(ComponentName("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity"))
            )
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> listOf(
                Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
                Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
                Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.MainGuideActivity"))
            )
            else -> emptyList()
        }

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                Log.d(TAG, "Intent failed: ${intent.component}", e)
            }
        }

        // Fallback: Open general app details settings
        return try {
            val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(appDetails)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed fallback to app details settings", e)
            false
        }
    }

    /**
     * إرشادات عربية مخصصة بحسب نوع هاتف المستخدم.
     */
    fun getManufacturerInstructions(): String {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") ->
                "لأجهزة شاومي (MIUI / HyperOS):\n" +
                "1. فعّل خيار «التشغيل التلقائي» (Auto-start).\n" +
                "2. اضبط موفر البطارية على «لا توجد قيود» (No restrictions).\n" +
                "3. اقفل التطبيق في قائمة التطبيقات الأخيرة."

            manufacturer.contains("samsung") ->
                "لأجهزة سامسونج (One UI):\n" +
                "1. أضف تطبيق RED إلى «التطبيقات التي لا يتم وضعها في وضع السكون» (Never sleeping apps).\n" +
                "2. اضبط استخدام البطارية على «غير مقيد» (Unrestricted)."

            manufacturer.contains("huawei") || manufacturer.contains("honor") ->
                "لأجهزة هواوي وهونر (EMUI / MagicOS):\n" +
                "1. ادخل إلى مدير التشغيل واضبط تشغيل RED على «إدارة يدوية» (Manual).\n" +
                "2. فعّل التشغيل التلقائي والتشغيل في الخلفية."

            manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus") ->
                "لأجهزة أوبو وريلمي وون بلس:\n" +
                "1. اسمح بالتشغيل التلقائي وبدء التشغيل في الخلفية.\n" +
                "2. عطّل تحسين استخدام البطارية للتطبيق."

            manufacturer.contains("vivo") ->
                "لأجهزة فيفو:\n" +
                "1. اسمح ببدء التشغيل التلقائي والاستهلاك العالي للطاقة في الخلفية."

            else ->
                "للهواتف القياسية:\n" +
                "اضبط تحسين البطارية للتطبيق على «غير مقيد» (Unrestricted) لضمان استقبال رنين المكالمات فورياً دون تأخير."
        }
    }
}
