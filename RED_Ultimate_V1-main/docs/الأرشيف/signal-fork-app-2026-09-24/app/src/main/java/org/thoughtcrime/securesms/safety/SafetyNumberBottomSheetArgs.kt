package com.red.sovereign.safety

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import com.red.sovereign.contacts.paged.ContactSearchKey
import com.red.sovereign.database.model.MessageId
import com.red.sovereign.recipients.RecipientId

/**
 * Fragment argument for `SafetyNumberBottomSheetFragment`
 */
@Parcelize
data class SafetyNumberBottomSheetArgs(
  val untrustedRecipients: List<RecipientId>,
  val destinations: List<ContactSearchKey.RecipientSearchKey>,
  val messageId: MessageId? = null
) : Parcelable
