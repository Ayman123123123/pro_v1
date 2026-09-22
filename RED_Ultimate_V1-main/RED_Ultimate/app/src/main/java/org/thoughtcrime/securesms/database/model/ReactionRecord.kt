package com.red.sovereign.database.model

import com.red.sovereign.recipients.RecipientId

/**
 * Represents an individual reaction to a message.
 */
data class ReactionRecord(
  val emoji: String,
  val author: RecipientId,
  val dateSent: Long,
  val dateReceived: Long
)
