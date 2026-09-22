package com.red.sovereign.components.settings.conversation.permissions

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.red.sovereign.groups.GroupAccessControl
import com.red.sovereign.groups.GroupId
import com.red.sovereign.groups.LiveGroup
import com.red.sovereign.util.SingleLiveEvent
import com.red.sovereign.util.livedata.LiveDataUtil
import com.red.sovereign.util.livedata.Store

class PermissionsSettingsViewModel(
  private val groupId: GroupId,
  private val repository: PermissionsSettingsRepository,
  liveGroup: LiveGroup = LiveGroup(groupId)
) : ViewModel() {

  private val store = Store(PermissionsSettingsState())
  private val internalEvents = SingleLiveEvent<PermissionsSettingsEvents>()

  val state: LiveData<PermissionsSettingsState> = store.stateLiveData
  val events: LiveData<PermissionsSettingsEvents> = internalEvents

  init {
    store.update(LiveDataUtil.combineLatest(liveGroup.isSelfAdmin, liveGroup.isActive) { admin, active -> admin && active }) { canEdit, state ->
      state.copy(selfCanEditSettings = canEdit)
    }

    store.update(liveGroup.membershipAdditionAccessControl) { membershipAdditionAccessControl, state ->
      state.copy(nonAdminCanAddMembers = membershipAdditionAccessControl == GroupAccessControl.ALL_MEMBERS)
    }

    store.update(liveGroup.attributesAccessControl) { attributesAccessControl, state ->
      state.copy(nonAdminCanEditGroupInfo = attributesAccessControl == GroupAccessControl.ALL_MEMBERS)
    }

    store.update(liveGroup.isAnnouncementGroup) { isAnnouncementGroup, state ->
      state.copy(announcementGroup = isAnnouncementGroup)
    }

    store.update(liveGroup.memberLabelAccessControl) { memberLabelAccessControl, state ->
      state.copy(nonAdminCanSetMemberLabel = memberLabelAccessControl == GroupAccessControl.ALL_MEMBERS)
    }
  }

  fun setNonAdminCanAddMembers(nonAdminCanAddMembers: Boolean) {
    repository.applyMembershipRightsChange(groupId, nonAdminCanAddMembers.asGroupAccessControl()) { reason ->
      internalEvents.postValue(PermissionsSettingsEvents.GroupChangeError(reason))
    }
  }

  fun setNonAdminCanEditGroupInfo(nonAdminCanEditGroupInfo: Boolean) {
    repository.applyAttributesRightsChange(groupId, nonAdminCanEditGroupInfo.asGroupAccessControl()) { reason ->
      internalEvents.postValue(PermissionsSettingsEvents.GroupChangeError(reason))
    }
  }

  fun setAnnouncementGroup(announcementGroup: Boolean) {
    repository.applyAnnouncementGroupChange(groupId, announcementGroup) { reason ->
      internalEvents.postValue(PermissionsSettingsEvents.GroupChangeError(reason))
    }
  }

  fun onMemberLabelPermissionChangeRequested(nonAdminCanSetMemberLabel: Boolean) {
    if (!nonAdminCanSetMemberLabel && repository.hasNonAdminMembersWithLabels(groupId)) {
      internalEvents.postValue(PermissionsSettingsEvents.ShowMemberLabelsWillBeRemovedWarning)
    } else {
      setNonAdminCanSetMemberLabel(nonAdminCanSetMemberLabel)
    }
  }

  fun onRestrictMemberLabelsToAdminsConfirmed() = setNonAdminCanSetMemberLabel(false)

  private fun setNonAdminCanSetMemberLabel(nonAdminCanSetMemberLabel: Boolean) {
    repository.applyMemberLabelRightsChange(
      groupId = groupId,
      newRights = nonAdminCanSetMemberLabel.asGroupAccessControl()
    ) { failureReason ->
      internalEvents.postValue(PermissionsSettingsEvents.GroupChangeError(failureReason))
    }
  }

  private fun Boolean.asGroupAccessControl(): GroupAccessControl {
    return if (this) {
      GroupAccessControl.ALL_MEMBERS
    } else {
      GroupAccessControl.ONLY_ADMINS
    }
  }

  class Factory(
    private val groupId: GroupId,
    private val repository: PermissionsSettingsRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(PermissionsSettingsViewModel(groupId, repository)))
    }
  }
}
