/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.groups.ui.incommon

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import com.red.sovereign.groups.GroupsInCommonRepository
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId

class GroupsInCommonViewModel(
  context: Context,
  recipientId: RecipientId
) : ViewModel() {

  val groups: StateFlow<List<Recipient>> = GroupsInCommonRepository.getGroupsInCommon(context, recipientId)
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(),
      initialValue = emptyList()
    )
}
