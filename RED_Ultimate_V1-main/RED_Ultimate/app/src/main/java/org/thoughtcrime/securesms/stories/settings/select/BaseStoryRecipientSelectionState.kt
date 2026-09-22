package com.red.sovereign.stories.settings.select

import com.red.sovereign.database.model.DistributionListId
import com.red.sovereign.database.model.DistributionListRecord
import com.red.sovereign.recipients.RecipientId

data class BaseStoryRecipientSelectionState(
  val distributionListId: DistributionListId?,
  val privateStory: DistributionListRecord? = null,
  val selection: Set<RecipientId> = emptySet(),
  val isStartingSelection: Boolean = false
)
