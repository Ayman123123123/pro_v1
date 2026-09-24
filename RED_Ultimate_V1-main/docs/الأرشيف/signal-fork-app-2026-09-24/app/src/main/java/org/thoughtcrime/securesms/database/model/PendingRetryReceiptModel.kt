package com.red.sovereign.database.model

import com.red.sovereign.recipients.RecipientId

/** A model for [com.red.sovereign.database.PendingRetryReceiptTable] */
data class PendingRetryReceiptModel(
  val id: Long,
  val author: RecipientId,
  val authorDevice: Int,
  val sentTimestamp: Long,
  val receivedTimestamp: Long,
  val threadId: Long
)
