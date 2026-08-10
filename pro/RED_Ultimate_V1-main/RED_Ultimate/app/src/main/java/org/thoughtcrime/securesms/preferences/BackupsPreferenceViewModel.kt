package com.red.sovereign.preferences

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.util.logging.Log
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.util.BackupUtil

class BackupsPreferenceViewModel : ViewModel() {

  private val internalBackupsEnabled = MutableLiveData<Boolean>()
  val backupsEnabled: LiveData<Boolean> = internalBackupsEnabled

  fun refreshBackupStatus() {
    viewModelScope.launch {
      val enabled = withContext(Dispatchers.IO) {
        val context = AppDependencies.application

        if (REDStore.settings.isBackupEnabled) {
          if (BackupUtil.canUserAccessBackupDirectory(context)) {
            true
          } else {
            Log.w(TAG, "Cannot access backup directory. Disabling backups.")
            BackupUtil.disableBackups(context)
            false
          }
        } else {
          false
        }
      }

      internalBackupsEnabled.value = enabled
    }
  }

  companion object {
    private val TAG = Log.tag(BackupsPreferenceViewModel::class.java)
  }
}
