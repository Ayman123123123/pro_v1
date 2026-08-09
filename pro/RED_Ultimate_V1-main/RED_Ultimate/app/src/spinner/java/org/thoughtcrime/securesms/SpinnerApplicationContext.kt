package com.red.sovereign

import android.content.ContentValues
import android.os.Build
import org.signal.core.util.logging.AndroidLogger
import org.signal.core.util.logging.Log
import org.signal.spinner.Spinner
import org.signal.spinner.Spinner.DatabaseConfig
import org.signal.spinner.SpinnerLogger
import com.red.sovereign.database.AttachmentTransformer
import com.red.sovereign.database.CollapsedStateTransformer
import com.red.sovereign.database.DatabaseMonitor
import com.red.sovereign.database.GV2Transformer
import com.red.sovereign.database.GV2UpdateTransformer
import com.red.sovereign.database.IdPopupTransformer
import com.red.sovereign.database.IsStoryTransformer
import com.red.sovereign.database.JobDatabase
import com.red.sovereign.database.KeyValueDatabase
import com.red.sovereign.database.KyberKeyTransformer
import com.red.sovereign.database.LocalMetricsDatabase
import com.red.sovereign.database.LogDatabase
import com.red.sovereign.database.MegaphoneDatabase
import com.red.sovereign.database.MessageBitmaskColumnTransformer
import com.red.sovereign.database.MessageRangesTransformer
import com.red.sovereign.database.PollTransformer
import com.red.sovereign.database.ProfileKeyCredentialTransformer
import com.red.sovereign.database.QueryMonitor
import com.red.sovereign.database.RecipientTransformer
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.REDStoreTransformer
import com.red.sovereign.database.TimestampTransformer
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.logging.PersistentLogger
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.util.AppSignatureUtil
import com.red.sovereign.util.RemoteConfig
import java.util.Locale

class SpinnerApplicationContext : ApplicationContext() {
  override fun onCreate() {
    super.onCreate()

    try {
      Class.forName("dalvik.system.CloseGuard")
        .getMethod("setEnabled", Boolean::class.javaPrimitiveType)
        .invoke(null, true)
    } catch (e: ReflectiveOperationException) {
      throw RuntimeException(e)
    }

    Spinner.init(
      this,
      mapOf(
        "Device" to { "${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})" },
        "Package" to { "$packageName (${AppSignatureUtil.getAppSignature(this)})" },
        "App Version" to { "${BuildConfig.VERSION_NAME} (${BuildConfig.CANONICAL_VERSION_CODE}, ${BuildConfig.GIT_HASH})" },
        "Profile Name" to { (if (REDStore.account.isRegistered) Recipient.self().profileName.toString() else "none") },
        "E164" to { REDStore.account.e164 ?: "none" },
        "ACI" to { REDStore.account.aci?.toString() ?: "none" },
        "PNI" to { REDStore.account.pni?.toString() ?: "none" },
        Spinner.KEY_ENVIRONMENT to { BuildConfig.FLAVOR_environment.uppercase(Locale.US) }
      ),
      linkedMapOf(
        "signal" to DatabaseConfig(
          db = { REDDatabase.rawDatabase },
          columnTransformers = listOf(
            MessageBitmaskColumnTransformer,
            GV2Transformer,
            GV2UpdateTransformer,
            IsStoryTransformer,
            TimestampTransformer,
            ProfileKeyCredentialTransformer,
            MessageRangesTransformer,
            KyberKeyTransformer,
            RecipientTransformer,
            AttachmentTransformer,
            PollTransformer,
            IdPopupTransformer,
            CollapsedStateTransformer
          )
        ),
        "jobmanager" to DatabaseConfig(db = { JobDatabase.getInstance(this).sqlCipherDatabase }, columnTransformers = listOf(TimestampTransformer)),
        "keyvalue" to DatabaseConfig(db = { KeyValueDatabase.getInstance(this).sqlCipherDatabase }, columnTransformers = listOf(REDStoreTransformer)),
        "megaphones" to DatabaseConfig(db = { MegaphoneDatabase.getInstance(this).sqlCipherDatabase }),
        "localmetrics" to DatabaseConfig(db = { LocalMetricsDatabase.getInstance(this).sqlCipherDatabase }),
        "logs" to DatabaseConfig(
          db = { LogDatabase.getInstance(this).sqlCipherDatabase },
          columnTransformers = listOf(TimestampTransformer)
        )
      ),
      linkedMapOf(
        StorageServicePlugin.PATH to StorageServicePlugin(),
        AttachmentPlugin.PATH to AttachmentPlugin(),
        BackupPlugin.PATH to BackupPlugin(),
        ApiPlugin.PATH to ApiPlugin()
      )
    )

    Log.initialize({ RemoteConfig.internalUser }, AndroidLogger, PersistentLogger.getInstance(this), SpinnerLogger)

    DatabaseMonitor.initialize(object : QueryMonitor {
      override fun onSql(sql: String, args: Array<Any>?) {
        Spinner.onSql("signal", sql, args)
      }

      override fun onQuery(distinct: Boolean, table: String, projection: Array<String>?, selection: String?, args: Array<Any>?, groupBy: String?, having: String?, orderBy: String?, limit: String?) {
        Spinner.onQuery("signal", distinct, table, projection, selection, args, groupBy, having, orderBy, limit)
      }

      override fun onDelete(table: String, selection: String?, args: Array<Any>?) {
        Spinner.onDelete("signal", table, selection, args)
      }

      override fun onUpdate(table: String, values: ContentValues, selection: String?, args: Array<Any>?) {
        Spinner.onUpdate("signal", table, values, selection, args)
      }
    })
  }
}
