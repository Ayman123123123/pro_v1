/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.banner.banners

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.rx3.asFlow
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import com.red.sovereign.R
import com.red.sovereign.banner.Banner
import com.red.sovereign.banner.ui.compose.Action
import com.red.sovereign.banner.ui.compose.DefaultBanner
import com.red.sovereign.banner.ui.compose.Importance
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.net.DeviceTransferBlockingInterceptor
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.registration.ui.RegistrationActivity
import com.red.sovereign.util.TextSecurePreferences

/**
 * A banner displayed when the client is unauthorized (deregistered).
 */
class UnauthorizedBanner(val context: Context) : Banner<Unit>() {

  override val enabled: Boolean
    get() = TextSecurePreferences.isUnauthorizedReceived(context) || !REDStore.account.isRegistered

  override val dataFlow: Flow<Unit>
    get() = flowOf(Unit)

  override val stateUpdates: Flow<Unit>
    get() = Recipient.self()
      .live()
      .observable()
      .asFlow()
      .map { enabled }
      .distinctUntilChanged()
      .map { }

  @Composable
  override fun DisplayBanner(model: Unit, contentPadding: PaddingValues) {
    Banner(contentPadding, REDStore.account.isLinkedDevice)
  }
}

@Composable
private fun Banner(contentPadding: PaddingValues, isLinkedDevice: Boolean) {
  val context = LocalContext.current

  DefaultBanner(
    title = null,
    body = stringResource(
      id = if (isLinkedDevice) {
        R.string.UnauthorizedReminder_this_device_is_no_longer_linked_relink_to_continue_messaging
      } else {
        R.string.UnauthorizedReminder_this_is_likely_because_you_registered_your_phone_number_with_RED_on_a_different_device
      }
    ),
    importance = Importance.ERROR,
    actions = listOf(
      Action(if (isLinkedDevice) R.string.UnauthorizedReminder_relink_action else R.string.UnauthorizedReminder_reregister_action) {
        if (REDStore.misc.isOldDeviceTransferLocked) {
          REDStore.misc.isOldDeviceTransferLocked = false
          DeviceTransferBlockingInterceptor.getInstance().unblockNetwork()
        }

        val registrationIntent = if (isLinkedDevice) {
          RegistrationActivity.newIntentForReLinkDevice(context)
        } else {
          RegistrationActivity.newIntentForReRegistration(context)
        }
        context.startActivity(registrationIntent)
      }
    ),
    paddingValues = contentPadding
  )
}

@DayNightPreviews
@Composable
private fun BannerPreview() {
  Previews.Preview {
    Banner(PaddingValues(0.dp), isLinkedDevice = false)
  }
}
