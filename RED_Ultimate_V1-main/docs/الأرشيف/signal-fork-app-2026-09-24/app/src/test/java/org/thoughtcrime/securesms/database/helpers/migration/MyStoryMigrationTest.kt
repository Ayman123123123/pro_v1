package com.red.sovereign.database.helpers.migration

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.count
import org.signal.core.util.delete
import org.signal.core.util.readToSingleInt
import org.signal.core.util.update
import com.red.sovereign.database.DistributionListTables
import com.red.sovereign.database.model.DistributionListId
import com.red.sovereign.testutil.REDDatabaseRule
import org.whispersystems.signalservice.api.push.DistributionId
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class MyStoryMigrationTest {

  @get:Rule val signalDatabaseRule = REDDatabaseRule()

  @Test
  fun givenAValidMyStory_whenIMigrate_thenIExpectMyStoryToBeValid() {
    assertValidMyStoryExists()

    runMigration()

    assertValidMyStoryExists()
  }

  @Test
  fun givenNoMyStory_whenIMigrate_thenIExpectMyStoryToBeCreated() {
    deleteMyStory()

    runMigration()

    assertValidMyStoryExists()
  }

  @Test
  fun givenA00000000DistributionIdForMyStory_whenIMigrate_thenIExpectMyStoryToBeCreated() {
    setMyStoryDistributionId("0000-0000")

    runMigration()

    assertValidMyStoryExists()
  }

  @Test
  fun givenARandomDistributionIdForMyStory_whenIMigrate_thenIExpectMyStoryToBeCreated() {
    setMyStoryDistributionId(UUID.randomUUID().toString())

    runMigration()

    assertValidMyStoryExists()
  }

  private fun setMyStoryDistributionId(serializedId: String) {
    signalDatabaseRule.writeableDatabase
      .update(DistributionListTables.LIST_TABLE_NAME)
      .values(DistributionListTables.DISTRIBUTION_ID to serializedId)
      .where("_id = ?", DistributionListId.MY_STORY_ID)
      .run()
  }

  private fun deleteMyStory() {
    signalDatabaseRule.writeableDatabase
      .delete(DistributionListTables.LIST_TABLE_NAME)
      .where("_id = ?", DistributionListId.MY_STORY_ID)
      .run()
  }

  private fun assertValidMyStoryExists() {
    val count = signalDatabaseRule.writeableDatabase
      .count()
      .from(DistributionListTables.LIST_TABLE_NAME)
      .where("_id = ? AND ${DistributionListTables.DISTRIBUTION_ID} = ?", DistributionListId.MY_STORY_ID, DistributionId.MY_STORY.toString())
      .run()
      .readToSingleInt()

    assertEquals("assertValidMyStoryExists: Query produced an unexpected count.", 1, count)
  }

  private fun runMigration() {
    V151_MyStoryMigration.migrate(
      ApplicationProvider.getApplicationContext(),
      signalDatabaseRule.writeableDatabase,
      0,
      1
    )
  }
}
