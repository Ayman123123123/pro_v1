/*
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.recipients.ui.about

import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.rx3.rxSingle
import com.red.sovereign.database.IdentityTable
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.groups.GroupId
import com.red.sovereign.groups.GroupsInCommonRepository
import com.red.sovereign.groups.memberlabel.MemberLabel
import com.red.sovereign.groups.memberlabel.MemberLabelRepository
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId
import java.util.Optional

class AboutSheetRepository {

  fun getGroupsInCommonCount(recipientId: RecipientId): Single<Int> {
    return rxSingle { GroupsInCommonRepository.getGroupsInCommonCount(recipientId) }
  }

  fun getVerified(recipientId: RecipientId): Single<Boolean> {
    return Single.fromCallable {
      val identityRecord = AppDependencies.protocolStore.aci().identities().getIdentityRecord(recipientId)
      identityRecord.isPresent && identityRecord.get().verifiedStatus == IdentityTable.VerifiedStatus.VERIFIED
    }.subscribeOn(Schedulers.io())
  }

  fun getMemberLabel(groupId: GroupId.V2): Single<Optional<MemberLabel>> = rxSingle {
    Optional.ofNullable(MemberLabelRepository.instance.getLabel(groupId, Recipient.self()))
  }

  fun canEditMemberLabel(groupId: GroupId.V2): Single<Boolean> = rxSingle {
    MemberLabelRepository.instance.canSetLabel(groupId, Recipient.self())
  }
}
