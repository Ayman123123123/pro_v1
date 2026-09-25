package com.red.sovereign.features.profile

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.system.Os
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.core.ServerEndpoint
import com.red.sovereign.core.database.RedDatabase
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * 🔒 مدير النسخ الاحتياطي السيادي الحقيقي
 * - ينشئ نسخة احتياطية مشفرة من قاعدة البيانات المحلية (SQLCipher) + metadata
 * - يحسب SHA-256 checksum
 * - يخزن في filesDir/backups بشكل مشفر عبر EncryptedFile (AES256-GCM)
 * - الاستعادة تتحقق من checksum وتستبدل DB (يتطلب إعادة تشغيل التطبيق)
 *
 * ليس وهميًا: كل زر ينشئ ملف حقيقي ويتحقق من سلامته.
 */
class BackupManager(private val context: Context) {

    companion object {
        private const val TAG = "BackupManager"
    }

    private val backupsDir: File by lazy {
        File(context.filesDir, "sovereign_backups").apply { mkdirs() }
    }

    data class BackupInfo(
        val fileName: String,
        val absolutePath: String,
        val sizeBytes: Long,
        val checksum: String,
        val createdAt: Long,
        val type: String = "FULL"
    )

    /**
     * إنشاء نسخة احتياطية مشفرة حقيقية
     */
    suspend fun createBackup(): Result<BackupInfo> = withContext(Dispatchers.IO) {
        try {
            // 1) مصدر قاعدة البيانات
            val dbFile = context.getDatabasePath("red_sovereign.db")
            if (!dbFile.exists()) {
                return@withContext Result.failure(IllegalStateException("DB file not found: ${dbFile.absolutePath}"))
            }

            // 2) إعداد المفتاح الرئيسي للتشفير (Android Keystore)
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            // 3) اسم ملف النسخة
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val plainFileName = "red_backup_${timestamp}.db"
            val encFileName = "${plainFileName}.enc"
            val encFile = File(backupsDir, encFileName)

            // 4) تشفير ونسخ
            val encryptedFile = EncryptedFile.Builder(
                context,
                encFile,
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()

            // نسخ DB عبر EncryptedFile output stream
            dbFile.inputStream().use { input ->
                encryptedFile.openFileOutput().use { output ->
                    input.copyTo(output)
                }
            }

            // 5) كتابة metadata جانبية (JSON) غير مشفرة للتحقق السريع
            // تتضمن dbSha256 (بصمة قاعدة البيانات الأصلية) ليقارنها restoreBackup
            // مع الملف المستعاد قبل الاستبدال — مع checksum الملف المشفر للتحقق السريع.
            val dbSha = sha256(dbFile)
            val meta = File(backupsDir, "${plainFileName}.meta.json")
            val metaContent = """
                {
                    "fileName": "$encFileName",
                    "plainName": "$plainFileName",
                    "createdAt": ${System.currentTimeMillis()},
                    "dbSize": ${dbFile.length()},
                    "sha256": "$dbSha",
                    "appVersion": "${try { context.packageManager.getPackageInfo(context.packageName, 0).versionName } catch (_: Exception) { "unknown" }}"
                }
            """.trimIndent()
            meta.writeText(metaContent)

            // 6) حساب الحجم والـ checksum للملف المشفر
            val size = encFile.length()
            val checksum = sha256(encFile)

            val info = BackupInfo(
                fileName = encFileName,
                absolutePath = encFile.absolutePath,
                sizeBytes = size,
                checksum = checksum,
                createdAt = System.currentTimeMillis()
            )

            // 7) حفظ آخر نسخة في SharedPrefs لعرضها في UI
            val prefs = context.getSharedPreferences("sovereign_backup", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("last_backup_path", info.absolutePath)
                .putString("last_backup_checksum", info.checksum)
                .putLong("last_backup_size", info.sizeBytes)
                .putLong("last_backup_time", info.createdAt)
                .apply()

            Result.success(info)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * استعادة نسخة احتياطية: تفك تشفير وتستبدل DB الحالية
     * يتطلب إغلاق RedDatabase instance وإعادة تشغيل التطبيق بعد الاستعادة
     * (انظر [restartAppAfterRestore]).
     *
     * قبل renameTo: يتحقق من tempRestore عبر PRAGMA integrity_check ويقارن
     * sha256/الحجم مع meta.json المجاور (إن وُجد) — أي فشل يلغي الاستعادة
     * ويُبقي DB الحالية سليمة.
     *
     * @param autoRestart إن true يعيد تشغيل التطبيق تلقائياً بعد النجاح
     * (ProcessPhoenix عبر reflection إن وُجد، وإلا AlarmManager + exitProcess).
     */
    suspend fun restoreBackup(file: File, autoRestart: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) return@withContext Result.failure(IllegalStateException("Backup file not found"))

            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val encryptedFile = EncryptedFile.Builder(
                context,
                file,
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()

            // إغلاق DB الحالية - محاولة
            try {
                RedDatabase.getInstance(context).close()
            } catch (e: Exception) {
                Log.w(TAG, "تعذّر إغلاق قاعدة البيانات قبل الاستعادة (غير قاتل) — سيُعاد المحاولة عند الاستبدال", e)
            }

            val dbFile = context.getDatabasePath("red_sovereign.db")
            val tempRestore = File(context.filesDir, "red_sovereign_restore.db")
            if (tempRestore.exists()) tempRestore.delete()

            encryptedFile.openFileInput().use { input ->
                tempRestore.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // تحقق أساسي: الملف المستعاد قابل للفتح (حجمه > 0)
            if (tempRestore.length() == 0L) {
                tempRestore.delete()
                return@withContext Result.failure(IllegalStateException("Restored file is empty"))
            }

            // C2-1: تحقق السلامة عبر PRAGMA integrity_check قبل اللمس الأصلي
            if (!verifySqliteIntegrity(tempRestore)) {
                tempRestore.delete()
                return@withContext Result.failure(IllegalStateException("النسخة تالفة — integrity_check فشل، أُلغيت الاستعادة"))
            }

            // C2-2: مقارنة sha256/الحجم مع meta.json المجاور (إن وُجد)
            val metaCheck = verifyAgainstMeta(file, tempRestore)
            if (metaCheck.isFailure) {
                tempRestore.delete()
                return@withContext metaCheck
            }

            // استبدال DB الأصلية
            // حذف wal/shm إن وجدت
            File("${dbFile.absolutePath}-wal").delete()
            File("${dbFile.absolutePath}-shm").delete()
            if (dbFile.exists()) dbFile.delete()
            val renamed = tempRestore.renameTo(dbFile)
            if (!renamed) {
                // fallback نسخ ثم حذف — renameTo يفشل عبر filesystems مختلفة
                tempRestore.copyTo(dbFile, overwrite = true)
                tempRestore.delete()
            }

            if (autoRestart) restartAppAfterRestore()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * فحص جاف (dry-run): يفك التشفير إلى ملف مؤقت ويتحقق من integrity + meta
     * دون لمس DB الحالية. يُستخدم من زر "فحص النسخة" قبل الاستعادة الفعلية.
     */
    suspend fun restoreDryRun(file: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) return@withContext Result.failure(IllegalStateException("Backup file not found"))
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val encryptedFile = EncryptedFile.Builder(
                context, file, masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()
            val tmp = File.createTempFile("restore_dryrun", ".db", context.cacheDir)
            try {
                encryptedFile.openFileInput().use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
                if (tmp.length() == 0L) return@withContext Result.failure(IllegalStateException("Restored file is empty"))
                if (!verifySqliteIntegrity(tmp)) {
                    return@withContext Result.failure(IllegalStateException("النسخة تالفة — integrity_check فشل"))
                }
                val metaCheck = verifyAgainstMeta(file, tmp)
                if (metaCheck.isFailure) return@withContext metaCheck
                Result.success(Unit)
            } finally {
                tmp.delete()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * إعادة تشغيل تلقائية بعد الاستعادة الناجحة — للربط بزر "إعادة التشغيل الآن".
     * يستخدم ProcessPhoenix (عبر reflection — لا dependency صلبة) إن وُجد،
     * وإلا يعيد الجدولة عبر AlarmManager ثم exitProcess.
     */
    fun restartAppAfterRestore() {
        // ProcessPhoenix إن وُجد في classpath — reflection كي لا نكسر البناء بدونه
        val phoenix = runCatching { Class.forName("com.jakewharton.processphoenix.ProcessPhoenix") }.getOrNull()
        if (phoenix != null) {
            runCatching {
                val m = phoenix.getMethod("triggerRebirth", Context::class.java)
                m.invoke(null, context.applicationContext)
                return
            }
        }
        // fallback: إعادة إطلاق عبر AlarmManager ثم إنهاء العملية
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return
        val flags = PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getActivity(context.applicationContext, 0, launch, flags)
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        am?.set(AlarmManager.RTC, System.currentTimeMillis() + 500, pi)
        exitProcess(0)
    }

    /** يفتح tempRestore عبر SQLCipher بالمفتاح الحقيقي ويتحقق من PRAGMA integrity_check = ok. */
    private fun verifySqliteIntegrity(dbFile: File): Boolean {
        // مفتاح SQLCipher الحقيقي — نفس مخزن RedDatabase (red_database_security/passphrase).
        // الفتح بلا مفتاح عبر android.database.sqlite يعطي "ملف مشفر" أو ok كاذب — ممنوع.
        val passphrase = try {
            com.red.sovereign.core.SecureStore(
                context.applicationContext, "red_database_security"
            ).get("passphrase")
        } catch (_: Exception) { null }
        if (passphrase.isNullOrBlank()) {
            Log.w(TAG, "verifySqliteIntegrity: no passphrase — refuse restore (never open w/o key)")
            return false
        }
        var db: net.zetetic.database.sqlcipher.SQLiteDatabase? = null
        return try {
            db = net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(
                dbFile.absolutePath, passphrase, null,
                net.zetetic.database.sqlcipher.SQLiteDatabase.OPEN_READONLY
            )
            val ok = db.rawQuery("PRAGMA integrity_check", null).use { c ->
                c.moveToFirst() && c.getString(0).equals("ok", ignoreCase = true)
            }
            if (!ok) return false
            // integrity_check وحدها تمر لملف فارغ/خاطئ — تحقق أن جداول Room موجودة.
            db.rawQuery(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN ('local_history','messages','conversations')",
                null
            ).use { c -> c.moveToFirst() && c.getInt(0) >= 3 }
        } catch (e: Exception) {
            Log.w(TAG, "verifySqliteIntegrity failed", e)
            false
        } finally {
            runCatching { db?.close() }
        }
    }

    /**
     * يقارن الملف المستعاد مع meta.json المجاور لملف النسخة المشفرة.
     * - meta المتوقع: <plain>.meta.json بجانب <plain>.db.enc
     * - يقارن dbSize (إن وُجد) مع حجم tempRestore، و sha256/checksum (إن وُجد)
     *   مع sha256(tempRestore). غياب meta أو الحقول = تخطٍ متوافق رجعياً.
     */
    private fun verifyAgainstMeta(encFile: File, tempRestore: File): Result<Unit> {
        return try {
            val metaName = encFile.name.removeSuffix(".enc") + ".meta.json"
            val metaFile = File(encFile.parent ?: backupsDir.absolutePath, metaName)
            if (!metaFile.exists()) return Result.success(Unit) // نسخ قديمة بلا meta
            val meta = JSONObject(metaFile.readText())
            val expectedSize = if (meta.has("dbSize")) meta.optLong("dbSize", -1L) else -1L
            if (expectedSize > 0 && tempRestore.length() != expectedSize) {
                return Result.failure(
                    IllegalStateException("حجم النسخة لا يطابق meta.json (متوقع $expectedSize، فعلي ${tempRestore.length()})")
                )
            }
            val expectedSha = when {
                meta.has("sha256") -> meta.optString("sha256")
                meta.has("checksum") -> meta.optString("checksum")
                else -> ""
            }
            if (expectedSha.isNotBlank()) {
                val actual = sha256(tempRestore)
                if (!actual.equals(expectedSha, ignoreCase = true)) {
                    return Result.failure(IllegalStateException("بصمة sha256 لا تطابق meta.json — ملف مزور أو تالف؟"))
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun listBackups(): List<BackupInfo> {
        return backupsDir.listFiles()?.filter { it.name.endsWith(".enc") }?.mapNotNull { file ->
            try {
                // البصمة من meta.json المجاور أولاً (فوري) — تجنّب sha256 ملف كامل
                // على خيط الواجهة لكل عنصر عند كل recomposition. الفارغ = غير محسوبة
                // بعد (تُحسب عند الفحص/الاستعادة عبر verifyAgainstMeta) لا قيمة وهمية.
                val metaChecksum = runCatching {
                    val metaFile = java.io.File(file.parent ?: backupsDir.absolutePath, file.name.removeSuffix(".enc") + ".meta.json")
                    if (metaFile.exists()) JSONObject(metaFile.readText()).optString("sha256").takeIf { it.isNotBlank() } else null
                }.getOrNull()
                BackupInfo(
                    fileName = file.name,
                    absolutePath = file.absolutePath,
                    sizeBytes = file.length(),
                    checksum = metaChecksum.orEmpty(),
                    createdAt = file.lastModified()
                )
            } catch (_: Exception) { null }
        }?.sortedByDescending { it.createdAt } ?: emptyList()
    }

    fun getLastBackupInfo(): BackupInfo? {
        val prefs = context.getSharedPreferences("sovereign_backup", Context.MODE_PRIVATE)
        val path = prefs.getString("last_backup_path", null) ?: return null
        val file = File(path)
        if (!file.exists()) return null
        return BackupInfo(
            fileName = file.name,
            absolutePath = file.absolutePath,
            sizeBytes = prefs.getLong("last_backup_size", file.length()),
            checksum = prefs.getString("last_backup_checksum", "") ?: sha256(file),
            createdAt = prefs.getLong("last_backup_time", file.lastModified())
        )
    }

    /**
     * تصدير نسخة إلى خارج التطبيق عبر FileProvider — للنسخ إلى Drive أو مشاركة
     * يعيد Uri صالح للمشاركة عبر Intent.
     */
    fun getShareUri(file: File): Uri {
        return FileProvider.getUriForFile(context, "com.red.sovereign.fileprovider", file)
    }

    /**
     * رفع سحابي اختياري — يرفع الملف المشفر إلى خادم MinIO عبر backend
     * المسار: POST ${ServerEndpoint}/api/sovereign/backup/upload
     * يتطلب Bearer صالح. يعيد رابط التحميل أو معرف النسخة.
     * إن لم يكن الخادم يدعم المسار، يعيد failure مع رسالة واضحة.
     */
    suspend fun uploadToCloud(file: File, onProgress: ((Long, Long) -> Unit)? = null): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) return@withContext Result.failure(IllegalStateException("Backup file not found"))
            val token = TokenStore(context).accessToken ?: return@withContext Result.failure(IllegalStateException("غير مصادق — سجل الدخول أولاً"))
            val base = ServerEndpoint.url().trimEnd('/')
            val url = "$base/api/sovereign/backup/upload"
            val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).build()
            val mediaType = "application/octet-stream".toMediaType()
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", file.name, okhttp3.RequestBody.create(mediaType, file))
                .addFormDataPart("checksum", sha256(file))
                .addFormDataPart("createdAt", file.lastModified().toString())
                .build()
            val req = Request.Builder().url(url).header("Authorization", "Bearer $token").post(body).build()
            val resp = client.newCall(req).execute()
            val respBody = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return@withContext Result.failure(IllegalStateException("رفع سحابي فشل HTTP ${resp.code}: ${respBody.take(200)} — تأكد أن الخادم يدعم /api/sovereign/backup/upload"))
            }
            // نتوقع JSON { url, id } — نعيد النص كما هو للعرض
            Result.success(respBody.ifBlank { "تم الرفع بنجاح (${file.name})" })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * استيراد من Uri خارجي (SAF) — ينسخ الملف إلى backupsDir ثم يمكن استعادته
     */
    suspend fun importFromUri(uri: Uri): Result<File> = withContext(Dispatchers.IO) {
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext Result.failure(IllegalStateException("تعذر فتح الملف"))
            val outName = "red_backup_import_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.enc"
            val outFile = File(backupsDir, outName)
            input.use { ins -> outFile.outputStream().use { outs -> ins.copyTo(outs) } }
            Result.success(outFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            var n: Int
            while (input.read(buf).also { n = it } != -1) {
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
