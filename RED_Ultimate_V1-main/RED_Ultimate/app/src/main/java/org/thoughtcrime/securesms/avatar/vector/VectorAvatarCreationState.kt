package com.red.sovereign.avatar.vector

import com.red.sovereign.avatar.Avatar
import com.red.sovereign.avatar.AvatarColorItem
import com.red.sovereign.avatar.Avatars

data class VectorAvatarCreationState(
  val currentAvatar: Avatar.Vector
) {
  fun colors(): List<AvatarColorItem> = Avatars.colors.map { AvatarColorItem(it, currentAvatar.color == it) }
}
