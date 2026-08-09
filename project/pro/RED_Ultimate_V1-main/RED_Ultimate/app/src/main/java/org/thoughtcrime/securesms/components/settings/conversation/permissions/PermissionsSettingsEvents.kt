package com.red.sovereign.components.settings.conversation.permissions

import com.red.sovereign.groups.ui.GroupChangeFailureReason

sealed class PermissionsSettingsEvents {
  class GroupChangeError(val reason: GroupChangeFailureReason) : PermissionsSettingsEvents()
  object ShowMemberLabelsWillBeRemovedWarning : PermissionsSettingsEvents()
}
