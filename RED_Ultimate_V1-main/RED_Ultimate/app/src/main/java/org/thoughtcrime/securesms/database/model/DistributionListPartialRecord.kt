package com.red.sovereign.database.model

import com.red.sovereign.recipients.RecipientId

data class DistributionListPartialRecord(
  val id: DistributionListId,
  val name: CharSequence,
  val recipientId: RecipientId,
  val allowsReplies: Boolean,
  val isUnknown: Boolean,
  val privacyMode: DistributionListPrivacyMode
)
