/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.verify

import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.recipients.Recipient

data class VerifyDisplayScreenState(
  val isSafetyNumberVerified: Boolean,
  val isAutomaticVerificationVisible: Boolean = REDStore.settings.automaticVerificationEnabled,
  val shouldDisplayVerifyAutomaticallyEducationSheet: Boolean = REDStore.settings.automaticVerificationEnabled && !REDStore.uiHints.hasSeenVerifyAutomaticallySheet(),
  val recipient: Recipient? = null,
  val fingerprintHolder: FingerprintHolder = FingerprintHolder.Uninitialised,
  val automaticVerificationStatus: AutomaticVerificationStatus = AutomaticVerificationStatus.NONE,
  val clipComparisonResult: ClipComparisonResult? = null,
  val scanComparisonResult: ScanComparisonResult? = null
) {
  sealed interface ClipComparisonResult {
    val submissionTime: Long

    data class NoDataInClipboard(override val submissionTime: Long = System.currentTimeMillis()) : ClipComparisonResult
    data class NoSafetyNumberInClipboard(override val submissionTime: Long = System.currentTimeMillis()) : ClipComparisonResult
    data class Success(override val submissionTime: Long = System.currentTimeMillis()) : ClipComparisonResult
    data class Failure(override val submissionTime: Long = System.currentTimeMillis()) : ClipComparisonResult
  }

  sealed interface ScanComparisonResult {
    val submissionTime: Long

    data class IncorrectFormat(override val submissionTime: Long = System.currentTimeMillis()) : ScanComparisonResult
    data class Success(override val submissionTime: Long = System.currentTimeMillis()) : ScanComparisonResult
    data class Failure(override val submissionTime: Long = System.currentTimeMillis()) : ScanComparisonResult
  }
}
