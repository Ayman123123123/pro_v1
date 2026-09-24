package com.red.sovereign.components.settings.app.internal

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.core.Observable
import org.signal.ringrtc.CallManager
import com.red.sovereign.components.settings.DividerPreference
import com.red.sovereign.components.settings.PreferenceModel
import com.red.sovereign.components.settings.SectionHeaderPreference
import com.red.sovereign.database.model.RemoteMegaphoneRecord
import com.red.sovereign.jobs.StoryOnboardingDownloadJob
import com.red.sovereign.keyvalue.InternalValues
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.stories.Stories
import com.red.sovereign.util.RemoteConfig
import com.red.sovereign.util.adapter.mapping.MappingModel
import com.red.sovereign.util.adapter.mapping.MappingModelList
import com.red.sovereign.util.livedata.Store
import java.util.Locale

class InternalSettingsViewModel(private val repository: InternalSettingsRepository) : ViewModel() {
  private val preferenceDataStore = REDStore.getPreferenceDataStore()

  private val store = Store(getState())

  init {
    repository.getEmojiVersionInfo { version ->
      store.update { it.copy(emojiVersion = version) }
    }

    val pendingOneTimeDonation: Observable<Boolean> = REDStore.inAppPayments.observablePendingOneTimeDonation
      .distinctUntilChanged()
      .map { it.isPresent }

    store.update(pendingOneTimeDonation) { pending, state ->
      state.copy(hasPendingOneTimeDonation = pending)
    }
  }

  val state: LiveData<InternalSettingsState> = store.stateLiveData

  fun setSeeMoreUserDetails(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.RECIPIENT_DETAILS, enabled)
    refresh()
  }

  fun setShakeToReport(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.SHAKE_TO_REPORT, enabled)
    refresh()
  }

  fun setShowMediaArchiveStateHint(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.SHOW_ARCHIVE_STATE_HINT, enabled)
    refresh()
  }

  fun setDisableStorageService(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.DISABLE_STORAGE_SERVICE, enabled)
    refresh()
  }

  fun setGv2ForceInvites(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.GV2_FORCE_INVITES, enabled)
    refresh()
  }

  fun setGv2IgnoreP2PChanges(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.GV2_IGNORE_P2P_CHANGES, enabled)
    refresh()
  }

  fun setAllowCensorshipSetting(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.ALLOW_CENSORSHIP_SETTING, enabled)
    refresh()
  }

  fun resetPnpInitializedState() {
    REDStore.misc.hasPniInitializedDevices = false
    refresh()
  }

  fun setUseBuiltInEmoji(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.FORCE_BUILT_IN_EMOJI, enabled)
    refresh()
  }

  fun setRemoveSenderKeyMinimum(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.REMOVE_SENDER_KEY_MINIMUM, enabled)
    refresh()
  }

  fun setDelayResends(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.DELAY_RESENDS, enabled)
    refresh()
  }

  fun setInternalGroupCallingServer(server: String?) {
    preferenceDataStore.putString(InternalValues.CALLING_SERVER, server)
    refresh()
  }

  fun setInternalCallingDataMode(dataMode: CallManager.DataMode) {
    preferenceDataStore.putInt(InternalValues.CALLING_DATA_MODE, dataMode.ordinal)
    refresh()
  }

  fun setInternalCallingDisableTelecom(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.CALLING_DISABLE_TELECOM, enabled)
    refresh()
  }

  fun setInternalCallingSetAudioConfig(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.CALLING_SET_AUDIO_CONFIG, enabled)
    refresh()
  }

  fun setInternalCallingUseOboeAdm(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.CALLING_USE_OBOE_ADM, enabled)
    refresh()
  }

  fun setInternalCallingUseSoftwareAec(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.CALLING_USE_SOFTWARE_AEC, enabled)
    refresh()
  }

  fun setInternalCallingUseSoftwareNs(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.CALLING_USE_SOFTWARE_NS, enabled)
    refresh()
  }

  fun setInternalCallingUseInputLowLatency(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.CALLING_USE_INPUT_LOW_LATENCY, enabled)
    refresh()
  }

  fun setInternalCallingUseInputVoiceComm(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.CALLING_USE_INPUT_VOICE_COMM, enabled)
    refresh()
  }

  fun setInternalCallingSetVideoConfig(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.CALLING_SET_VIDEO_CONFIG, enabled)
    refresh()
  }

  fun setInternalCallingUseHardwareVp9Encode(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.CALLING_USE_HARDWARE_VP9_ENCODE, enabled)
    refresh()
  }

  fun setInternalCallingUseHardwareVp9Decode(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.CALLING_USE_HARDWARE_VP9_DECODE, enabled)
    refresh()
  }

  fun setInternalCallingUseSoftwareVp9Encode(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.CALLING_USE_SOFTWARE_VP9_ENCODE, enabled)
    refresh()
  }

  fun setInternalCallingUseSoftwareVp9Decode(enabled: Boolean) {
    preferenceDataStore.putBoolean(InternalValues.CALLING_USE_SOFTWARE_VP9_DECODE, enabled)
    refresh()
  }

  fun setUseConversationItemV2Media(enabled: Boolean) {
    REDStore.internal.useConversationItemV2Media = enabled
    refresh()
  }

  fun setUseNewMediaActivity(enabled: Boolean) {
    REDStore.internal.useNewMediaActivity = enabled
    refresh()
  }

  fun addSampleReleaseNote(callToAction: String = "action") {
    repository.addSampleReleaseNote(callToAction)
  }

  fun addRemoteDonateMegaphone() {
    repository.addRemoteMegaphone(RemoteMegaphoneRecord.ActionId.DONATE)
  }

  fun addRemoteDonateFriendMegaphone() {
    repository.addRemoteMegaphone(RemoteMegaphoneRecord.ActionId.DONATE_FOR_FRIEND)
  }

  fun enqueueSubscriptionRedemption() {
    repository.enqueueSubscriptionRedemption()
  }

  fun refresh() {
    store.update { getState().copy(emojiVersion = it.emojiVersion, searchQuery = it.searchQuery) }
  }

  fun setSearchQuery(query: String) {
    store.update {
      if (it.searchQuery == query) {
        it
      } else {
        it.copy(searchQuery = query)
      }
    }
  }

  fun filterPreferences(context: Context, items: MappingModelList, query: String): MappingModelList {
    val normalizedQuery = query.trim().lowercase(Locale.getDefault())
    if (normalizedQuery.isBlank()) {
      return items
    }

    val groups = buildSearchGroups(items)
    val filtered = MappingModelList()

    groups.forEach { group ->
      val headerMatches = group.header?.searchableText(context)?.contains(normalizedQuery) == true
      val matchingItems = if (headerMatches) {
        group.items
      } else {
        group.items.filter { it.searchableText(context)?.contains(normalizedQuery) == true }
      }

      if (headerMatches || matchingItems.isNotEmpty()) {
        if (filtered.isNotEmpty() && group.divider != null) {
          filtered.add(group.divider)
        }

        group.header?.let { filtered.add(it) }
        filtered.addAll(matchingItems)
      }
    }

    return filtered
  }

  private fun getState() = InternalSettingsState(
    seeMoreUserDetails = REDStore.internal.recipientDetails,
    shakeToReport = REDStore.internal.shakeToReport,
    showArchiveStateHint = REDStore.internal.showArchiveStateHint,
    gv2forceInvites = REDStore.internal.gv2ForceInvites,
    gv2ignoreP2PChanges = REDStore.internal.gv2IgnoreP2PChanges,
    allowCensorshipSetting = REDStore.internal.allowChangingCensorshipSetting,
    callingServer = REDStore.internal.groupCallingServer,
    callingDataMode = REDStore.internal.callingDataMode,
    callingDisableTelecom = REDStore.internal.callingDisableTelecom,
    callingSetAudioConfig = REDStore.internal.callingSetAudioConfig,
    callingUseOboeAdm = REDStore.internal.callingUseOboeAdm,
    callingUseSoftwareAec = REDStore.internal.callingUseSoftwareAec,
    callingUseSoftwareNs = REDStore.internal.callingUseSoftwareNs,
    callingUseInputLowLatency = REDStore.internal.callingUseInputLowLatency,
    callingUseInputVoiceComm = REDStore.internal.callingUseInputVoiceComm,
    callingSetVideoConfig = REDStore.internal.callingSetVideoConfig,
    callingUseHardwareVp9Encode = REDStore.internal.callingUseHardwareVp9Encode,
    callingUseHardwareVp9Decode = REDStore.internal.callingUseHardwareVp9Decode,
    callingUseSoftwareVp9Encode = REDStore.internal.callingUseSoftwareVp9Encode,
    callingUseSoftwareVp9Decode = REDStore.internal.callingUseSoftwareVp9Decode,
    useBuiltInEmojiSet = REDStore.internal.forceBuiltInEmoji,
    emojiVersion = null,
    removeSenderKeyMinimium = REDStore.internal.removeSenderKeyMinimum,
    delayResends = REDStore.internal.delayResends,
    disableStorageService = REDStore.internal.storageServiceDisabled,
    canClearOnboardingState = REDStore.story.hasDownloadedOnboardingStory && Stories.isFeatureEnabled(),
    pnpInitialized = REDStore.misc.hasPniInitializedDevices,
    useConversationItemV2ForMedia = REDStore.internal.useConversationItemV2Media,
    hasPendingOneTimeDonation = REDStore.inAppPayments.getPendingOneTimeDonation() != null,
    forceSplitPane = REDStore.internal.forceSplitPane,
    forceSinglePane = REDStore.internal.forceSinglePane,
    useNewMediaActivity = REDStore.internal.useNewMediaActivity,
    disableInternalUser = RemoteConfig.internalUserDisabled
  )

  fun onClearOnboardingState() {
    REDStore.story.hasDownloadedOnboardingStory = false
    REDStore.story.userHasViewedOnboardingStory = false
    Stories.onStorySettingsChanged(Recipient.self().id)
    refresh()
    StoryOnboardingDownloadJob.enqueueIfNeeded()
  }

  fun setDisableInternalUser(disabled: Boolean) {
    RemoteConfig.internalUserDisabled = disabled
    refresh()
  }

  fun setForceSplitPane(forceSplitPane: Boolean) {
    REDStore.internal.forceSplitPane = forceSplitPane
    refresh()
  }

  fun setForceSinglePane(forceSinglePane: Boolean) {
    REDStore.internal.forceSinglePane = forceSinglePane
    refresh()
  }

  private fun buildSearchGroups(items: MappingModelList): List<SearchGroup> {
    val groups = mutableListOf<SearchGroup>()
    var divider: DividerPreference? = null
    var header: SectionHeaderPreference? = null
    var groupItems = mutableListOf<MappingModel<*>>()

    fun flush() {
      if (header != null || groupItems.isNotEmpty()) {
        groups.add(SearchGroup(divider, header, groupItems))
      }

      divider = null
      header = null
      groupItems = mutableListOf()
    }

    items.forEach { item ->
      when (item) {
        is DividerPreference -> {
          flush()
          divider = item
        }
        is SectionHeaderPreference -> {
          flush()
          header = item
        }
        else -> groupItems.add(item)
      }
    }

    flush()

    return groups
  }

  private fun MappingModel<*>.searchableText(context: Context): String? {
    return if (this is PreferenceModel<*>) {
      listOfNotNull(title, summary)
        .joinToString(separator = " ") { it.resolve(context).toString() }
        .lowercase(Locale.getDefault())
    } else {
      null
    }
  }

  private data class SearchGroup(
    val divider: DividerPreference?,
    val header: SectionHeaderPreference?,
    val items: List<MappingModel<*>>
  )

  class Factory(private val repository: InternalSettingsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(InternalSettingsViewModel(repository)))
    }
  }
}
