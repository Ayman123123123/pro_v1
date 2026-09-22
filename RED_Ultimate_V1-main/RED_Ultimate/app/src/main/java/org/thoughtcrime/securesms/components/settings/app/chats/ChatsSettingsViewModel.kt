package com.red.sovereign.components.settings.app.chats

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.util.ThrottledDebouncer
import com.red.sovereign.backup.LocalExportProgress
import com.red.sovereign.components.settings.app.chats.folders.ChatFoldersRepository
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobs.LocalBackupJob
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.util.BackupUtil
import com.red.sovereign.util.ConversationUtil
import com.red.sovereign.util.RemoteConfig
import com.red.sovereign.util.TextSecurePreferences

class ChatsSettingsViewModel @JvmOverloads constructor(
  private val repository: ChatsSettingsRepository = ChatsSettingsRepository()
) : ViewModel() {

  private val refreshDebouncer = ThrottledDebouncer(500L)

  private val store = MutableStateFlow(
    ChatsSettingsState(
      generateLinkPreviews = REDStore.settings.isLinkPreviewsEnabled,
      useAddressBook = REDStore.settings.isPreferSystemContactPhotos,
      keepMutedChatsArchived = REDStore.settings.shouldKeepMutedChatsArchived(),
      useSystemEmoji = REDStore.settings.isPreferSystemEmoji,
      enterKeySends = REDStore.settings.isEnterKeySends,
      localBackupsEnabled = REDStore.settings.isBackupEnabled && BackupUtil.canUserAccessBackupDirectory(AppDependencies.application),
      folderCount = 0,
      userUnregistered = TextSecurePreferences.isUnauthorizedReceived(AppDependencies.application) || !REDStore.account.isRegistered,
      clientDeprecated = REDStore.misc.isClientDeprecated,
      isPlaintextExportEnabled = RemoteConfig.localPlaintextExport,
      chatExportState = ChatExportState.None
    )
  )

  val state: StateFlow<ChatsSettingsState> = store

  init {
    viewModelScope.launch {
      LocalExportProgress.plaintextProgress.collect { progress ->
        store.update {
          it.copy(
            plaintextExportProgress = progress,
            chatExportState = when {
              progress.succeeded != null && it.plaintextExportProgress.succeeded == null -> ChatExportState.Success
              progress.canceled != null -> ChatExportState.None
              else -> it.chatExportState
            }
          )
        }
      }
    }
  }

  fun requestChatExportType() {
    store.update { it.copy(chatExportState = ChatExportState.ConfirmExport) }
  }

  fun setExportTypeAndGoToSelectFolder(includeMediaInExport: Boolean) {
    store.update { it.copy(chatExportState = ChatExportState.ChooseAFolder, includeMediaInExport = includeMediaInExport) }
  }

  fun startChatExportToFolder(uri: Uri) {
    store.update { it.copy(chatExportState = ChatExportState.None) }
    LocalBackupJob.enqueuePlaintextArchive(uri.toString(), store.value.includeMediaInExport)
  }

  fun clearChatExportFlow() {
    store.update { it.copy(chatExportState = ChatExportState.None, includeMediaInExport = false) }
  }

  fun cancelChatExport() {
    store.update { it.copy(chatExportState = ChatExportState.Canceling) }
    AppDependencies.jobManager.cancelAllInQueue(LocalBackupJob.PLAINTEXT_ARCHIVE_QUEUE)
  }

  fun setGenerateLinkPreviewsEnabled(enabled: Boolean) {
    store.update { it.copy(generateLinkPreviews = enabled) }
    REDStore.settings.isLinkPreviewsEnabled = enabled
    repository.syncLinkPreviewsState()
  }

  fun setUseAddressBook(enabled: Boolean) {
    store.update { it.copy(useAddressBook = enabled) }
    refreshDebouncer.publish { ConversationUtil.refreshRecipientShortcuts() }
    REDStore.settings.isPreferSystemContactPhotos = enabled
    repository.syncPreferSystemContactPhotos()
  }

  fun setKeepMutedChatsArchived(enabled: Boolean) {
    store.update { it.copy(keepMutedChatsArchived = enabled) }
    REDStore.settings.setKeepMutedChatsArchived(enabled)
    repository.syncKeepMutedChatsArchivedState()
  }

  fun setUseSystemEmoji(enabled: Boolean) {
    store.update { it.copy(useSystemEmoji = enabled) }
    REDStore.settings.isPreferSystemEmoji = enabled
  }

  fun setEnterKeySends(enabled: Boolean) {
    store.update { it.copy(enterKeySends = enabled) }
    REDStore.settings.isEnterKeySends = enabled
  }

  fun refresh() {
    viewModelScope.launch(Dispatchers.IO) {
      val count = ChatFoldersRepository.getFolderCount()
      val backupsEnabled = REDStore.settings.isBackupEnabled && BackupUtil.canUserAccessBackupDirectory(AppDependencies.application)

      if (store.value.localBackupsEnabled != backupsEnabled) {
        store.update {
          it.copy(
            folderCount = count,
            localBackupsEnabled = backupsEnabled
          )
        }
      } else {
        store.update {
          it.copy(
            folderCount = count
          )
        }
      }
    }
  }
}
