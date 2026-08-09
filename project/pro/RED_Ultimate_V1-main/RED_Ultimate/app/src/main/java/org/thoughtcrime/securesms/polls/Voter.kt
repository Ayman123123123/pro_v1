/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.polls

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Class to track someone who has voted in an option within a poll.
 */
@Parcelize
data class Voter(
  val id: Long,
  val voteCount: Int
) : Parcelable
