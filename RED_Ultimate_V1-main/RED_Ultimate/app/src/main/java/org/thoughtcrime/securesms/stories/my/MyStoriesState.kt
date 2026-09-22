package com.red.sovereign.stories.my

import com.red.sovereign.conversation.ConversationMessage
import com.red.sovereign.database.model.MessageRecord

data class MyStoriesState(
  val distributionSets: List<DistributionSet> = emptyList()
) {

  data class DistributionSet(
    val label: String?,
    val stories: List<DistributionStory>
  )

  data class DistributionStory(
    val message: ConversationMessage,
    val views: Int
  ) {
    val messageRecord: MessageRecord = message.messageRecord
  }
}
