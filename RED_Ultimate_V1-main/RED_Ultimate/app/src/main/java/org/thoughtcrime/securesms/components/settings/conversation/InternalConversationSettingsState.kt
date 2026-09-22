/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.components.settings.conversation

import androidx.annotation.WorkerThread
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import org.signal.core.util.Base64
import org.signal.core.util.Hex
import com.red.sovereign.components.settings.app.subscription.InAppPaymentsRepository
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.InAppPaymentSubscriberRecord
import com.red.sovereign.database.model.RecipientRecord
import com.red.sovereign.groups.GroupId
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId

@Immutable
data class InternalConversationSettingsState(
  val recipientId: RecipientId,
  val isGroup: Boolean,
  val e164: String,
  val aci: String,
  val pni: String,
  val groupId: GroupId?,
  val threadId: Long?,
  val profileName: String,
  val profileKeyBase64: String,
  val profileKeyHex: String,
  val sealedSenderAccessMode: String,
  val phoneNumberSharing: String,
  val phoneNumberDiscoverability: String,
  val profileSharing: String,
  val capabilities: AnnotatedString,
  val hasServiceId: Boolean,
  val isSelf: Boolean,
  val subscriberId: String
) {

  val groupIdString = groupId?.toString()
  val threadIdString = threadId?.toString() ?: "N/A"

  companion object {
    @WorkerThread
    fun create(recipient: Recipient, threadId: Long?, groupId: GroupId?): InternalConversationSettingsState {
      return InternalConversationSettingsState(
        recipientId = recipient.id,
        isGroup = recipient.isGroup,
        e164 = recipient.e164.orElse("null"),
        aci = recipient.aci.map { it.toString() }.orElse("null"),
        pni = recipient.pni.map { it.toString() }.orElse("null"),
        groupId = groupId,
        threadId = threadId,
        profileName = with(recipient) {
          if (isGroup) "" else "[${profileName.givenName}] [${profileName.familyName}]"
        },
        profileKeyBase64 = with(recipient) {
          if (isGroup) "" else profileKey?.let(Base64::encodeWithPadding) ?: "None"
        },
        profileKeyHex = with(recipient) {
          if (isGroup) "" else profileKey?.let(Hex::toStringCondensed) ?: "None"
        },
        sealedSenderAccessMode = recipient.sealedSenderAccessMode.toString(),
        phoneNumberSharing = recipient.phoneNumberSharing.name,
        phoneNumberDiscoverability = REDDatabase.recipients.getPhoneNumberDiscoverability(recipient.id)?.name ?: "null",
        profileSharing = recipient.isProfileSharing.toString(),
        capabilities = buildCapabilities(recipient),
        hasServiceId = recipient.hasServiceId,
        isSelf = recipient.isSelf,
        subscriberId = buildSubscriberId(recipient)
      )
    }

    @WorkerThread
    private fun buildSubscriberId(recipient: Recipient): String {
      return if (recipient.isSelf) {
        val subscriber: InAppPaymentSubscriberRecord? = InAppPaymentsRepository.getSubscriber(InAppPaymentSubscriberRecord.Type.DONATION)
        if (subscriber != null) {
          """currency code: ${subscriber.currency!!.currencyCode}
            |subscriber id: ${subscriber.subscriberId.serialize()}
          """.trimMargin()
        } else {
          "None"
        }
      } else {
        "None"
      }
    }

    @WorkerThread
    private fun buildCapabilities(recipient: Recipient): AnnotatedString {
      return if (recipient.isGroup) {
        AnnotatedString("null")
      } else {
        val capabilities: RecipientRecord.Capabilities? = REDDatabase.recipients.getCapabilities(recipient.id)
        if (capabilities != null) {
          AnnotatedString("No capabilities right now.")
          // Always leave one as an example in case we add one in the future
          val style: SpanStyle = when (capabilities.usernameSyncMessages) {
            Recipient.Capability.SUPPORTED -> SpanStyle(color = Color(0, 150, 0))
            Recipient.Capability.NOT_SUPPORTED -> SpanStyle(color = Color.Red)
            Recipient.Capability.UNKNOWN -> SpanStyle(fontStyle = FontStyle.Italic)
          }

          buildAnnotatedString {
            withStyle(style = style) {
              append("usernameSyncMessages")
            }
          }
        } else {
          AnnotatedString("Recipient not found!")
        }
      }
    }
  }
}
