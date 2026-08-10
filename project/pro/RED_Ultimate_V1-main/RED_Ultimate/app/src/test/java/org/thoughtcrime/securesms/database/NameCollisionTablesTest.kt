/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.database

import android.app.Application
import assertk.assertThat
import assertk.assertions.hasSize
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.red.sovereign.profiles.ProfileName
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.testutil.RecipientTestRule

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class NameCollisionTablesTest {

  @get:Rule
  val recipients = RecipientTestRule()

  private lateinit var alice: RecipientId
  private lateinit var bob: RecipientId
  private lateinit var charlie: RecipientId

  @Before
  fun setUp() {
    alice = recipients.createRecipient("Buddy #0", profileSharing = false).also { recipients.insertIncomingMessage(it) }
    bob = recipients.createRecipient("Buddy #1", profileSharing = false).also { recipients.insertIncomingMessage(it) }
    charlie = recipients.createRecipient("Buddy #2", profileSharing = false).also { recipients.insertIncomingMessage(it) }
  }

  @Test
  fun givenAUserWithAThreadIdButNoConflicts_whenIGetCollisionsForThreadRecipient_thenIExpectNoCollisions() {
    REDDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(alice))
    val actual = REDDatabase.nameCollisions.getCollisionsForThreadRecipientId(alice)

    assertThat(actual).hasSize(0)
  }

  @Test
  fun givenTwoUsers_whenOneChangesTheirProfileNameToMatchTheOther_thenIExpectANameCollision() {
    setProfileNameAndCheckCollision(alice, ProfileName.fromParts("Alice", "Android"))
    setProfileNameAndCheckCollision(bob, ProfileName.fromParts("Bob", "Android"))
    setProfileNameAndCheckCollision(alice, ProfileName.fromParts("Bob", "Android"))

    val actualAlice = REDDatabase.nameCollisions.getCollisionsForThreadRecipientId(alice)
    val actualBob = REDDatabase.nameCollisions.getCollisionsForThreadRecipientId(bob)

    assertThat(actualAlice).hasSize(2)
    assertThat(actualBob).hasSize(2)
  }

  @Test
  fun givenTwoUsersWithANameCollisions_whenOneChangesToADifferentName_thenIExpectNoNameCollisions() {
    setProfileNameAndCheckCollision(alice, ProfileName.fromParts("Bob", "Android"))
    setProfileNameAndCheckCollision(bob, ProfileName.fromParts("Bob", "Android"))
    setProfileNameAndCheckCollision(alice, ProfileName.fromParts("Alice", "Android"))

    val actualAlice = REDDatabase.nameCollisions.getCollisionsForThreadRecipientId(alice)
    val actualBob = REDDatabase.nameCollisions.getCollisionsForThreadRecipientId(bob)

    assertThat(actualAlice).hasSize(0)
    assertThat(actualBob).hasSize(0)
  }

  @Test
  fun givenThreeUsersWithANameCollisions_whenOneChangesToADifferentName_thenIExpectTwoNameCollisions() {
    setProfileNameAndCheckCollision(alice, ProfileName.fromParts("Bob", "Android"))
    setProfileNameAndCheckCollision(bob, ProfileName.fromParts("Bob", "Android"))
    setProfileNameAndCheckCollision(charlie, ProfileName.fromParts("Bob", "Android"))
    setProfileNameAndCheckCollision(alice, ProfileName.fromParts("Alice", "Android"))

    val actualAlice = REDDatabase.nameCollisions.getCollisionsForThreadRecipientId(alice)
    val actualBob = REDDatabase.nameCollisions.getCollisionsForThreadRecipientId(bob)
    val actualCharlie = REDDatabase.nameCollisions.getCollisionsForThreadRecipientId(charlie)

    assertThat(actualAlice).hasSize(0)
    assertThat(actualBob).hasSize(2)
    assertThat(actualCharlie).hasSize(2)
  }

  @Test
  fun givenTwoUsersWithADismissedNameCollision_whenOneChangesToADifferentNameAndBack_thenIExpectANameCollision() {
    setProfileNameAndCheckCollision(alice, ProfileName.fromParts("Bob", "Android"))
    setProfileNameAndCheckCollision(bob, ProfileName.fromParts("Bob", "Android"))
    REDDatabase.nameCollisions.markCollisionsForThreadRecipientDismissed(alice)

    setProfileNameAndCheckCollision(alice, ProfileName.fromParts("Alice", "Android"))
    setProfileNameAndCheckCollision(alice, ProfileName.fromParts("Bob", "Android"))

    val actualAlice = REDDatabase.nameCollisions.getCollisionsForThreadRecipientId(alice)

    assertThat(actualAlice).hasSize(2)
  }

  @Test
  fun givenADismissedNameCollisionForAlice_whenIGetNameCollisionsForAlice_thenIExpectNoNameCollisions() {
    setProfileNameAndCheckCollision(alice, ProfileName.fromParts("Bob", "Android"))
    setProfileNameAndCheckCollision(bob, ProfileName.fromParts("Bob", "Android"))
    REDDatabase.nameCollisions.markCollisionsForThreadRecipientDismissed(alice)

    val actualCollisions = REDDatabase.nameCollisions.getCollisionsForThreadRecipientId(alice)

    assertThat(actualCollisions).hasSize(0)
  }

  @Test
  fun givenADismissedNameCollisionForAliceThatIUpdate_whenIGetNameCollisionsForAlice_thenIExpectNoNameCollisions() {
    REDDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(alice))

    setProfileNameAndCheckCollision(alice, ProfileName.fromParts("Bob", "Android"))
    setProfileNameAndCheckCollision(bob, ProfileName.fromParts("Bob", "Android"))
    REDDatabase.nameCollisions.markCollisionsForThreadRecipientDismissed(alice)
    setProfileNameAndCheckCollision(bob, ProfileName.fromParts("Bob", "Android"))

    val actualCollisions = REDDatabase.nameCollisions.getCollisionsForThreadRecipientId(alice)

    assertThat(actualCollisions).hasSize(0)
  }

  @Test
  fun givenADismissedNameCollisionForAlice_whenIGetNameCollisionsForBob_thenIExpectANameCollisionWithTwoEntries() {
    REDDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(alice))

    setProfileNameAndCheckCollision(alice, ProfileName.fromParts("Bob", "Android"))
    setProfileNameAndCheckCollision(bob, ProfileName.fromParts("Bob", "Android"))
    REDDatabase.nameCollisions.markCollisionsForThreadRecipientDismissed(alice)

    val actualCollisions = REDDatabase.nameCollisions.getCollisionsForThreadRecipientId(bob)

    assertThat(actualCollisions).hasSize(2)
  }

  @Test
  fun givenAGroupWithAliceAndBob_whenIInsertNameChangeMessageForAlice_thenIExpectAGroupNameCollision() {
    val info = recipients.createGroup(alice, bob)

    setProfileNameAndCheckCollision(alice, ProfileName.fromParts("Bob", "Android"))
    setProfileNameAndCheckCollision(bob, ProfileName.fromParts("Bob", "Android"))

    REDDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(info.recipientId))
    REDDatabase.messages.insertProfileNameChangeMessages(Recipient.resolved(alice), "Bob Android", "Alice Android")

    val collisions = REDDatabase.nameCollisions.getCollisionsForThreadRecipientId(info.recipientId)

    assertThat(collisions).hasSize(2)
  }

  @Test
  fun givenAGroupWithAliceAndBobWithDismissedCollision_whenIInsertNameChangeMessageForAlice_thenIExpectAGroupNameCollision() {
    val info = recipients.createGroup(alice, bob)

    setProfileNameAndCheckCollision(alice, ProfileName.fromParts("Bob", "Android"))
    setProfileNameAndCheckCollision(bob, ProfileName.fromParts("Bob", "Android"))

    REDDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(info.recipientId))
    REDDatabase.messages.insertProfileNameChangeMessages(Recipient.resolved(alice), "Bob Android", "Alice Android")
    REDDatabase.nameCollisions.markCollisionsForThreadRecipientDismissed(info.recipientId)
    REDDatabase.messages.insertProfileNameChangeMessages(Recipient.resolved(alice), "Bob Android", "Alice Android")

    val collisions = REDDatabase.nameCollisions.getCollisionsForThreadRecipientId(info.recipientId)

    assertThat(collisions).hasSize(0)
  }

  @Test
  fun givenAGroupWithAliceAndBob_whenIInsertNameChangeMessageForAliceWithMismatch_thenIExpectNoGroupNameCollision() {
    val info = recipients.createGroup(alice, bob)

    setProfileNameAndCheckCollision(alice, ProfileName.fromParts("Alice", "Android"))
    setProfileNameAndCheckCollision(bob, ProfileName.fromParts("Bob", "Android"))

    REDDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(info.recipientId))
    REDDatabase.messages.insertProfileNameChangeMessages(Recipient.resolved(alice), "Alice Android", "Bob Android")

    val collisions = REDDatabase.nameCollisions.getCollisionsForThreadRecipientId(info.recipientId)

    assertThat(collisions).hasSize(0)
  }

  @Test
  fun givenTwoUsersInTheSameNameCollision_whenIRemapOneToTheOther_thenIExpectNoConstraintViolation() {
    setProfileNameAndCheckCollision(alice, ProfileName.fromParts("Bob", "Android"))
    setProfileNameAndCheckCollision(bob, ProfileName.fromParts("Bob", "Android"))

    REDDatabase.nameCollisions.remapRecipient(alice, bob)

    assertThat(REDDatabase.nameCollisions.getCollisionsForThreadRecipientId(alice)).hasSize(0)
    assertThat(REDDatabase.nameCollisions.getCollisionsForThreadRecipientId(bob)).hasSize(0)
  }

  private fun setProfileNameAndCheckCollision(recipientId: RecipientId, name: ProfileName) {
    recipients.setProfileName(recipientId, name)
    REDDatabase.nameCollisions.handleIndividualNameCollision(recipientId)
  }
}
