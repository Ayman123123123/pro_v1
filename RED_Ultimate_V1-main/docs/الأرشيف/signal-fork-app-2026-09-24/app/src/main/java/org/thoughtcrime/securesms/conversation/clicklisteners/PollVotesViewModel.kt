package com.red.sovereign.conversation.clicklisteners

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.util.concurrent.REDDispatchers
import org.signal.core.util.logging.Log
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.polls.PollOption
import com.red.sovereign.polls.PollRecord
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId

/**
 * View model for [PollVotesFragment] which allows you to see results for a given poll.
 */
class PollVotesViewModel(pollId: Long) : ViewModel() {

  companion object {
    private val TAG = Log.tag(PollVotesViewModel::class)
  }

  private val _state = MutableStateFlow(PollVotesState())
  val state = _state.asStateFlow()

  init {
    loadPollInfo(pollId)
  }

  private fun loadPollInfo(pollId: Long) {
    viewModelScope.launch(REDDispatchers.Default) {
      val poll = REDDatabase.polls.getPollFromId(pollId)!!
      val mostVotes = poll.pollOptions.maxByOrNull { option -> option.voters.size }?.voters?.size
      _state.update {
        it.copy(
          poll = poll,
          pollOptions = poll.pollOptions.map { option ->
            PollOptionModel(
              pollOption = option,
              voters = Recipient.resolvedList(option.voters.map { voter -> RecipientId.from(voter.id) }),
              hasMostVotes = option.voters.size == mostVotes
            )
          },
          isAuthor = poll.authorId == Recipient.self().id.toLong()
        )
      }
    }
  }
}

data class PollVotesState(
  val poll: PollRecord? = null,
  val pollOptions: List<PollOptionModel> = emptyList(),
  val isAuthor: Boolean = false
)

data class PollOptionModel(
  val pollOption: PollOption,
  val voters: List<Recipient> = emptyList(),
  val hasMostVotes: Boolean
)
