package com.red.sovereign.avatar.text

import com.red.sovereign.avatar.Avatar
import com.red.sovereign.avatar.AvatarColorItem
import com.red.sovereign.avatar.Avatars

data class TextAvatarCreationState(
  val currentAvatar: Avatar.Text
) {
  fun colors(): List<AvatarColorItem> = Avatars.colors.map { AvatarColorItem(it, currentAvatar.color == it) }
}
