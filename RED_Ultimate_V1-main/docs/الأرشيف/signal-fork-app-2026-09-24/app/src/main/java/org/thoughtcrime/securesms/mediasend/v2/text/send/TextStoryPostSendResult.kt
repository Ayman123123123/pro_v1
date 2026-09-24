package com.red.sovereign.mediasend.v2.text.send

import com.red.sovereign.database.model.IdentityRecord

sealed class TextStoryPostSendResult {
  object Success : TextStoryPostSendResult()
  object Failure : TextStoryPostSendResult()
  data class UntrustedRecordsError(val untrustedRecords: List<IdentityRecord>) : TextStoryPostSendResult()
}
