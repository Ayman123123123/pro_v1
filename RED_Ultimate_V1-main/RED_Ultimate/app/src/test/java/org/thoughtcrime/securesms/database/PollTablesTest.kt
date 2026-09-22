package com.red.sovereign.database

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.deleteAll
import com.red.sovereign.database.model.MessageId
import com.red.sovereign.mms.IncomingMessage
import com.red.sovereign.polls.PollOption
import com.red.sovereign.polls.PollRecord
import com.red.sovereign.polls.Voter
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.testutil.RecipientTestRule

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class PollTablesTest {

  @get:Rule
  val recipients = RecipientTestRule()

  private lateinit var poll1: PollRecord
  private lateinit var other0: RecipientId

  @Before
  fun setUp() {
    poll1 = PollRecord(
      id = 1,
      question = "how do you feel about unit testing?",
      pollOptions = listOf(
        PollOption(1, "yay", listOf(Voter(1, 1))),
        PollOption(2, "ok", emptyList()),
        PollOption(3, "nay", emptyList())
      ),
      allowMultipleVotes = false,
      hasEnded = false,
      authorId = 1,
      messageId = 1
    )

    REDDatabase.polls.writableDatabase.deleteAll(PollTables.PollTable.TABLE_NAME)
    REDDatabase.polls.writableDatabase.deleteAll(PollTables.PollOptionTable.TABLE_NAME)
    REDDatabase.polls.writableDatabase.deleteAll(PollTables.PollVoteTable.TABLE_NAME)

    other0 = recipients.createRecipient("Buddy #0")
    val message = IncomingMessage(type = MessageType.NORMAL, from = other0, sentTimeMillis = 100, serverTimeMillis = 100, receivedTimeMillis = 100)
    REDDatabase.messages.insertMessageInbox(message, REDDatabase.threads.getOrCreateThreadIdFor(other0, isGroup = false))
  }

  @Test
  fun givenAPollWithVoting_whenIGetPoll_thenIExpectThatPoll() {
    REDDatabase.polls.insertPoll("how do you feel about unit testing?", false, listOf("yay", "ok", "nay"), 1, 1)
    REDDatabase.polls.insertVotes(pollId = 1, pollOptionIds = listOf(1), voterId = 1, voteCount = 1, messageId = MessageId(1))

    assertEquals(poll1, REDDatabase.polls.getPoll(1))
  }

  @Test
  fun givenAPoll_whenIGetItsOptionIds_thenIExpectAllOptionsIds() {
    REDDatabase.polls.insertPoll("how do you feel about unit testing?", false, listOf("yay", "ok", "nay"), 1, 1)
    assertEquals(poll1.pollOptions.map { it.id }, REDDatabase.polls.getPollOptionIds(1))
  }

  @Test
  fun givenAPollAndVoter_whenIGetItsVoteCount_thenIExpectTheCorrectVoterCount() {
    REDDatabase.polls.insertPoll("how do you feel about unit testing?", false, listOf("yay", "ok", "nay"), 1, 1)
    REDDatabase.polls.insertVotes(pollId = 1, pollOptionIds = listOf(1), voterId = 1, voteCount = 1, messageId = MessageId(1))
    REDDatabase.polls.insertVotes(pollId = 1, pollOptionIds = listOf(2), voterId = 2, voteCount = 2, messageId = MessageId(1))
    REDDatabase.polls.insertVotes(pollId = 1, pollOptionIds = listOf(3), voterId = 3, voteCount = 3, messageId = MessageId(1))

    assertEquals(1, REDDatabase.polls.getCurrentPollVoteCount(1, 1))
    assertEquals(2, REDDatabase.polls.getCurrentPollVoteCount(1, 2))
    assertEquals(3, REDDatabase.polls.getCurrentPollVoteCount(1, 3))
  }

  @Test
  fun givenMultipleRoundsOfVoting_whenIGetItsCount_thenIExpectTheMostRecentResults() {
    REDDatabase.polls.insertPoll("how do you feel about unit testing?", false, listOf("yay", "ok", "nay"), 1, 1)
    REDDatabase.polls.insertVotes(pollId = 1, pollOptionIds = listOf(2), voterId = 1, voteCount = 1, messageId = MessageId(1))
    REDDatabase.polls.insertVotes(pollId = 1, pollOptionIds = listOf(3), voterId = 1, voteCount = 2, messageId = MessageId(1))
    REDDatabase.polls.insertVotes(pollId = 1, pollOptionIds = listOf(1), voterId = 1, voteCount = 3, messageId = MessageId(1))

    assertEquals(listOf(Voter(1, 3)), REDDatabase.polls.getPoll(1)!!.pollOptions[0].voters)
  }

  @Test
  fun givenAPoll_whenITerminateIt_thenIExpectItToEnd() {
    REDDatabase.polls.insertPoll("how do you feel about unit testing?", false, listOf("yay", "ok", "nay"), 1, 1)
    REDDatabase.polls.endPoll(1, System.currentTimeMillis())

    assertEquals(true, REDDatabase.polls.getPoll(1)!!.hasEnded)
  }

  @Test
  fun givenAPoll_whenIIVote_thenIExpectThatVote() {
    REDDatabase.polls.insertPoll("how do you feel about unit testing?", false, listOf("yay", "ok", "nay"), 1, 1)
    val poll = REDDatabase.polls.getPoll(1)!!
    val pollOption = poll.pollOptions.first()

    val voteCount = REDDatabase.polls.insertVote(poll, pollOption)

    assertEquals(1, voteCount)
    assertEquals(listOf(0), REDDatabase.polls.getVotes(poll.id, false, voteCount))
  }

  @Test
  fun givenAPoll_whenIRemoveVote_thenVoteIsCleared() {
    REDDatabase.polls.insertPoll("how do you feel about unit testing?", false, listOf("yay", "ok", "nay"), 1, 1)
    val poll = REDDatabase.polls.getPoll(1)!!
    val pollOption = poll.pollOptions.first()

    val voteCount = REDDatabase.polls.removeVote(poll, pollOption)
    REDDatabase.polls.markPendingAsRemoved(poll.id, Recipient.self().id.toLong(), voteCount, 1, pollOption.id)

    assertEquals(1, voteCount)
    val votes = REDDatabase.polls.getVotes(poll.id, false, voteCount)
    assertTrue(votes.isEmpty())
  }

  @Test
  fun givenAPendingVote_whenIRevertThatVote_thenItGoesToMostRecentResolvedState() {
    REDDatabase.polls.insertPoll("how do you feel about unit testing?", true, listOf("yay", "ok", "nay"), 1, 1)
    val poll = REDDatabase.polls.getPoll(1)!!
    val option = poll.pollOptions.first()

    REDDatabase.polls.insertVotes(poll.id, listOf(option.id), Recipient.self().id.toLong(), 5, MessageId(1))
    REDDatabase.polls.markPendingAsAdded(poll.id, Recipient.self().id.toLong(), 5, 1, option.id)
    REDDatabase.polls.removeVote(poll, option)

    REDDatabase.polls.removePendingVote(poll.id, option.id, 6, 1)
    val votes = REDDatabase.polls.getVotes(1, true, 6)
    assertEquals(listOf(0), votes)
  }
}
