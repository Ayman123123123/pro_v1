/*
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.conversation.v2.items

import com.red.sovereign.util.adapter.mapping.MappingModel
import com.red.sovereign.util.adapter.mapping.MappingViewHolder

/**
 * Base ViewHolder to share some common properties shared among conversation items.
 */
abstract class V2ConversationItemViewHolder<Model : MappingModel<Model>>(
  root: V2ConversationItemLayout,
  appearanceInfoProvider: V2ConversationContext
) : MappingViewHolder<Model>(root) {
  protected val shapeDelegate = V2ConversationItemShape(appearanceInfoProvider)
  protected val themeDelegate = V2ConversationItemTheme(context, appearanceInfoProvider)
}
