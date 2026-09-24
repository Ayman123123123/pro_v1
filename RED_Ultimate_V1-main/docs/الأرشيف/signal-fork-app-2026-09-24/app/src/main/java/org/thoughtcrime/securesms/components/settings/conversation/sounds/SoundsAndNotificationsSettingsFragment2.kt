/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.components.settings.conversation.sounds

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.Navigation
import org.signal.core.ui.compose.ComposeFragment
import com.red.sovereign.MuteDialog
import com.red.sovereign.components.settings.conversation.preferences.Utils.formatMutedUntil
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.util.navigation.safeNavigate

class SoundsAndNotificationsSettingsFragment2 : ComposeFragment() {

  private val viewModel: SoundsAndNotificationsSettingsViewModel2 by viewModels(
    factoryProducer = {
      val recipientId = SoundsAndNotificationsSettingsFragment2Args.fromBundle(requireArguments()).recipientId
      SoundsAndNotificationsSettingsViewModel2.Factory(recipientId)
    }
  )

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (!state.channelConsistencyCheckComplete || state.recipientId == Recipient.UNKNOWN.id) {
      return
    }

    SoundsAndNotificationsSettingsScreen(
      state = state,
      formatMuteUntil = { it.formatMutedUntil(requireContext()) },
      onEvent = { event ->
        when (event) {
          is SoundsAndNotificationsEvent.NavigateToCustomNotifications -> {
            val action = SoundsAndNotificationsSettingsFragment2Directions
              .actionSoundsAndNotificationsSettingsFragment2ToCustomNotificationsSettingsFragment(state.recipientId)
            Navigation.findNavController(requireView()).safeNavigate(action)
          }
          else -> viewModel.onEvent(event)
        }
      },
      onNavigationClick = {
        requireActivity().onBackPressedDispatcher.onBackPressed()
      },
      onMuteClick = {
        MuteDialog.show(requireContext(), childFragmentManager, viewLifecycleOwner) { muteUntil ->
          viewModel.onEvent(SoundsAndNotificationsEvent.SetMuteUntil(muteUntil))
        }
      }
    )
  }
}
