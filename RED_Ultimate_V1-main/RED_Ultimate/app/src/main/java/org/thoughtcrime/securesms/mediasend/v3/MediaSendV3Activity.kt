/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.mediasend.v3

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.compose.AndroidFragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.mediasend.HudCommand
import org.signal.mediasend.MediaSendActivityContract
import org.signal.mediasend.MediaSendScreen
import org.signal.mediasend.edit.LocalAddAMessageRowTextField
import com.red.sovereign.PassphraseRequiredActivity
import com.red.sovereign.components.emoji.EmojiTextView
import com.red.sovereign.components.settings.app.AppSettingsActivity
import com.red.sovereign.mediasend.v2.review.AddMessageDialogFragment
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.registration.olddevice.QuickTransferOldDeviceActivity
import com.red.sovereign.util.CommunicationActions

/**
 * Encapsulates the media send flow for v3.
 */
class MediaSendV3Activity : PassphraseRequiredActivity() {

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    val contractArgs = MediaSendActivityContract.Args.fromIntent(intent)

    setContent {
      CompositionLocalProvider(
        LocalAddAMessageRowTextField provides { message, modifier ->
          AndroidView(
            factory = { EmojiTextView(it) },
            update = { view ->
              view.text = message
            },
            modifier = modifier
          )
        }
      ) {
        MediaSendScreen(
          contractArgs = contractArgs,
          sendSlot = {
            AndroidFragment(
              clazz = MediaSendV3ForwardFragment::class.java,
              modifier = Modifier.fillMaxSize()
            )
          },
          onExternalHudCommand = {
            when (it) {
              is HudCommand.ShowAddAMessageDialog -> {
                AddMessageDialogFragment.show(
                  fragmentManager = supportFragmentManager,
                  addAMessageDialog = it,
                  destination = contractArgs.recipientId?.let {
                    RecipientId.from(it.id)
                  }
                )
              }

              is HudCommand.GoToConversation -> {
                lifecycleScope.launch(Dispatchers.Default) {
                  val recipient = Recipient.resolved(RecipientId.from(it.recipientId.id))
                  withContext(Dispatchers.Main) {
                    CommunicationActions.startConversation(
                      this@MediaSendV3Activity,
                      recipient,
                      null
                    )
                  }
                }
              }

              HudCommand.GoToLinkedDevices -> {
                startActivity(AppSettingsActivity.linkedDevices(this))
                finish()
              }

              is HudCommand.GoToQuickTransfer -> {
                startActivity(QuickTransferOldDeviceActivity.intent(this, it.qrData))
                finish()
              }

              is HudCommand.CloseScreen -> {
                // SIGNAL_INHERITED: TODO [media-send] warning dialog
                finish()
              }
            }
          }
        )
      }
    }
  }
}
