/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.backup.v2.ui.subscription

import androidx.compose.runtime.Stable
import org.signal.core.util.money.FiatMoney
import com.red.sovereign.backup.v2.MessageBackupTier
import kotlin.time.Duration

/**
 * Represents a type of backup a user can select.
 */
@Stable
sealed interface MessageBackupsType {

  val tier: MessageBackupTier

  data class Paid(
    val pricePerMonth: FiatMoney,
    val storageAllowanceBytes: Long,
    val mediaTtl: Duration
  ) : MessageBackupsType {
    override val tier: MessageBackupTier = MessageBackupTier.PAID
  }

  data class Free(
    val mediaRetentionDays: Int
  ) : MessageBackupsType {
    override val tier: MessageBackupTier = MessageBackupTier.FREE
  }
}
