/*
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.database.model

import com.red.sovereign.attachments.DatabaseAttachment
import com.red.sovereign.database.CallTable
import com.red.sovereign.payments.Payment
import com.red.sovereign.polls.PollRecord

fun MessageRecord.withReactions(reactions: List<ReactionRecord>): MessageRecord {
  return if (this is MmsMessageRecord) {
    this.withReactions(reactions)
  } else {
    this
  }
}

fun MessageRecord.withAttachments(attachments: List<DatabaseAttachment>): MessageRecord {
  return if (this is MmsMessageRecord) {
    this.withAttachments(attachments)
  } else {
    this
  }
}
fun MessageRecord.withPayment(payment: Payment): MessageRecord {
  return if (this is MmsMessageRecord) {
    this.withPayment(payment)
  } else {
    this
  }
}

fun MessageRecord.withCall(call: CallTable.Call): MessageRecord {
  return if (this is MmsMessageRecord) {
    this.withCall(call)
  } else {
    this
  }
}

fun MessageRecord.withPoll(poll: PollRecord): MessageRecord {
  return if (this is MmsMessageRecord) {
    this.withPoll(poll)
  } else {
    this
  }
}
