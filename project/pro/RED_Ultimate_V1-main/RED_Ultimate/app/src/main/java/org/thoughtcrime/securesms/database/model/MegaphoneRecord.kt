package com.red.sovereign.database.model

import com.red.sovereign.megaphone.Megaphones

data class MegaphoneRecord(
  val event: Megaphones.Event,
  val interactionCount: Int,
  val lastInteractionTime: Long,
  val firstVisible: Long,
  val lastVisible: Long,
  val finished: Boolean
)
