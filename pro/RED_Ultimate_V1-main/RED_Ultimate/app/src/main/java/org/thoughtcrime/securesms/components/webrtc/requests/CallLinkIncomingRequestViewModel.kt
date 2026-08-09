/*
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.components.webrtc.requests

import android.content.Context
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.rx3.asObservable
import com.red.sovereign.groups.GroupsInCommonRepository
import com.red.sovereign.groups.GroupsInCommonSummary
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.util.rx.RxStore

class CallLinkIncomingRequestViewModel(
  private val context: Context,
  private val recipientId: RecipientId
) : ViewModel() {

  private val store = RxStore(CallLinkIncomingRequestState())
  private val disposables = CompositeDisposable().apply {
    add(store)
  }

  override fun onCleared() {
    disposables.dispose()
  }

  fun observeState(context: Context): Flowable<CallLinkIncomingRequestState> {
    disposables += store.update(Recipient.observable(recipientId).toFlowable(BackpressureStrategy.LATEST)) { r, s ->
      s.copy(
        recipient = r,
        name = r.getShortDisplayName(context),
        subtitle = r.e164.orElse(""),
        isSystemContact = r.isSystemContact
      )
    }

    disposables += store.update(getGroupsInCommon()) { groupsInCommon, state ->
      state.copy(groupsInCommon = groupsInCommon.toDisplayText(context))
    }

    return store.stateFlowable
  }

  private fun getGroupsInCommon(): Flowable<GroupsInCommonSummary> {
    return GroupsInCommonRepository.getGroupsInCommonSummary(context, recipientId)
      .asObservable()
      .toFlowable(BackpressureStrategy.LATEST)
  }
}
