/*
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.conversation.v2.items

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Space
import android.widget.TextView
import com.google.android.material.imageview.ShapeableImageView
import com.red.sovereign.badges.BadgeImageView
import com.red.sovereign.components.AlertView
import com.red.sovereign.components.AvatarImageView
import com.red.sovereign.components.DeliveryStatusView
import com.red.sovereign.components.ExpirationTimerView
import com.red.sovereign.components.emoji.EmojiTextView
import com.red.sovereign.databinding.V2ConversationItemTextOnlyIncomingBinding
import com.red.sovereign.databinding.V2ConversationItemTextOnlyOutgoingBinding
import com.red.sovereign.reactions.ReactionsConversationView

/**
 * Pass-through interface for bridging incoming and outgoing text-only message views.
 *
 * Essentially, just a convenience wrapper since the layouts differ *very slightly* and
 * we want to be able to have each follow the same code-path.
 */
data class V2ConversationItemTextOnlyBindingBridge(
  val root: V2ConversationItemLayout,
  val senderNameWithLabel: SenderNameWithLabelView?,
  val senderPhoto: AvatarImageView?,
  val senderBadge: BadgeImageView?,
  val bodyWrapper: ViewGroup,
  val body: EmojiTextView,
  val reply: ShapeableImageView,
  val reactions: ReactionsConversationView,
  val deliveryStatus: DeliveryStatusView?,
  val footerDate: TextView,
  val footerExpiry: ExpirationTimerView,
  val footerBackground: View,
  val footerSpace: Space?,
  val alert: AlertView?,
  val isIncoming: Boolean,
  val footerPinned: ImageView,
  val footerStarred: ImageView,
  val starredSource: TextView?,
  val starredSourceWrapper: View?,
  val starredSourceAvatar: AvatarImageView?
)

/**
 * Wraps the binding in the bridge.
 */
fun V2ConversationItemTextOnlyIncomingBinding.bridge(): V2ConversationItemTextOnlyBindingBridge {
  return V2ConversationItemTextOnlyBindingBridge(
    root = root,
    senderNameWithLabel = groupSenderNameWithLabel,
    senderPhoto = contactPhoto,
    senderBadge = badge,
    body = conversationItemBody,
    bodyWrapper = conversationItemBodyWrapper,
    reply = conversationItemReply,
    reactions = conversationItemReactions,
    deliveryStatus = null,
    footerDate = conversationItemFooterDate,
    footerExpiry = conversationItemExpirationTimer,
    footerBackground = conversationItemFooterBackground,
    alert = null,
    footerSpace = footerEndPad,
    isIncoming = true,
    footerPinned = conversationItemFooterPinned,
    footerStarred = conversationItemFooterStarred,
    starredSource = conversationItemStarredSource,
    starredSourceWrapper = conversationItemStarredSourceWrapper,
    starredSourceAvatar = conversationItemStarredSourceAvatar
  )
}

/**
 * Wraps the binding in the bridge.
 */
fun V2ConversationItemTextOnlyOutgoingBinding.bridge(): V2ConversationItemTextOnlyBindingBridge {
  return V2ConversationItemTextOnlyBindingBridge(
    root = root,
    senderNameWithLabel = null,
    senderPhoto = null,
    senderBadge = null,
    body = conversationItemBody,
    bodyWrapper = conversationItemBodyWrapper,
    reply = conversationItemReply,
    reactions = conversationItemReactions,
    deliveryStatus = conversationItemDeliveryStatus,
    footerDate = conversationItemFooterDate,
    footerExpiry = conversationItemExpirationTimer,
    footerBackground = conversationItemFooterBackground,
    alert = conversationItemAlert,
    footerSpace = footerEndPad,
    isIncoming = false,
    footerPinned = conversationItemFooterPinned,
    footerStarred = conversationItemFooterStarred,
    starredSource = null,
    starredSourceWrapper = null,
    starredSourceAvatar = null
  )
}
