package org.signal.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API

class Registry : IssueRegistry() {
  override val vendor = Vendor(
    vendorName = "RED",
    identifier = "RED",
    feedbackUrl = "RED",
    contact = "RED"
  )

  override val issues = listOf(
    REDLogDetector.LOG_NOT_SIGNAL,
    REDLogDetector.LOG_NOT_APP,
    REDLogDetector.INLINE_TAG,
    VersionCodeDetector.VERSION_CODE_USAGE,
    AlertDialogBuilderDetector.ALERT_DIALOG_BUILDER_USAGE,
    BlockingGetDetector.UNSAFE_BLOCKING_GET,
    RecipientIdDatabaseDetector.RECIPIENT_ID_DATABASE_REFERENCE_ISSUE,
    ThreadIdDatabaseDetector.THREAD_ID_DATABASE_REFERENCE_ISSUE,
    StartForegroundServiceDetector.START_FOREGROUND_SERVICE_ISSUE,
    CardViewDetector.CARD_VIEW_USAGE,
    SystemOutPrintLnDetector.SYSTEM_OUT_PRINTLN_USAGE,
    SystemOutPrintLnDetector.KOTLIN_IO_PRINTLN_USAGE
  )

  override val api = CURRENT_API
}
