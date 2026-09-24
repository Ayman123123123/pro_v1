/*
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.avatar

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import com.red.sovereign.R
import com.red.sovereign.components.AvatarImageView
import com.red.sovereign.conversation.colors.AvatarColor
import com.red.sovereign.database.model.ProfileAvatarFileDetails
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.recipients.rememberRecipientField

@Composable
fun AvatarImage(
  recipient: Recipient,
  modifier: Modifier = Modifier,
  useProfile: Boolean = true,
  contentDescription: String? = null
) {
  AvatarImage(
    recipientId = recipient.id,
    modifier = modifier,
    useProfile = useProfile,
    contentDescription = contentDescription
  )
}

@Composable
fun AvatarImage(
  recipientId: RecipientId,
  modifier: Modifier = Modifier,
  useProfile: Boolean = true,
  contentDescription: String? = null
) {
  if (LocalInspectionMode.current) {
    Image(
      painter = painterResource(R.drawable.ic_avatar_abstract_02),
      contentDescription = null,
      modifier = modifier
        .background(color = Color(AvatarColor.random().colorInt()), CircleShape)
    )
  } else {
    val context = LocalContext.current
    val avatarImageState by rememberRecipientField(recipientId) {
      AvatarImageState(
        getDisplayName(context),
        this,
        profileAvatarFileDetails
      )
    }

    AndroidView(
      factory = {
        AvatarImageView(context).apply {
          initialize(context, null)
          this.contentDescription = contentDescription
        }
      },
      modifier = modifier.background(color = Color.Transparent, shape = CircleShape)
    ) {
      if (useProfile) {
        it.setAvatarUsingProfile(avatarImageState.self)
      } else {
        it.setAvatar(avatarImageState.self)
      }
    }
  }
}

@DayNightPreviews
@Composable
private fun AvatarImagePreview() {
  Previews.Preview {
    AvatarImage(
      recipientId = RecipientId.from(1)
    )
  }
}

private data class AvatarImageState(
  val displayName: String?,
  val self: Recipient,
  val avatarFileDetails: ProfileAvatarFileDetails
)
