package com.red.sovereign.avatar.picker

import com.red.sovereign.avatar.Avatar

data class AvatarPickerState(
  val currentAvatar: Avatar? = null,
  val selectableAvatars: List<Avatar> = listOf(),
  val canSave: Boolean = false,
  val canClear: Boolean = false,
  val isCleared: Boolean = false
)
