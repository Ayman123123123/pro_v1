package com.red.sovereign.components.settings.app.internal.donor

import org.signal.donations.StripeDeclineCode
import com.red.sovereign.badges.models.Badge
import com.red.sovereign.components.settings.app.subscription.errors.UnexpectedSubscriptionCancellation

data class InternalDonorErrorConfigurationState(
  val badges: List<Badge> = emptyList(),
  val selectedBadge: Badge? = null,
  val selectedUnexpectedSubscriptionCancellation: UnexpectedSubscriptionCancellation? = null,
  val selectedStripeDeclineCode: StripeDeclineCode.Code? = null
)
