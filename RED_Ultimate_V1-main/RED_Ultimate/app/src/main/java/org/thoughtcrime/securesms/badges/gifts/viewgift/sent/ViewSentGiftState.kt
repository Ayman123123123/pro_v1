package com.red.sovereign.badges.gifts.viewgift.sent

import com.red.sovereign.badges.models.Badge
import com.red.sovereign.recipients.Recipient

data class ViewSentGiftState(
  val recipient: Recipient? = null,
  val badge: Badge? = null
)
