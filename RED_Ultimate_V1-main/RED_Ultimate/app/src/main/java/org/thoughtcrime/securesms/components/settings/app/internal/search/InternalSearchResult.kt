/*
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.components.settings.app.internal.search

import com.red.sovereign.groups.GroupId
import com.red.sovereign.recipients.RecipientId

data class InternalSearchResult(
  val name: String,
  val id: RecipientId,
  val aci: String? = null,
  val pni: String? = null,
  val groupId: GroupId? = null
)
