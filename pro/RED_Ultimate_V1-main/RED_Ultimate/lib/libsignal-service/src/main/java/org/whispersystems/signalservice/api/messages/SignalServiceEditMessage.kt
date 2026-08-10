package org.whispersystems.signalservice.api.messages

data class REDServiceEditMessage(
  val targetSentTimestamp: Long,
  val dataMessage: REDServiceDataMessage
)
