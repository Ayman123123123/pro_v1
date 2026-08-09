package com.red.sovereign.testing

import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.signal.core.models.ServiceId.ACI
import org.signal.core.models.ServiceId.PNI
import org.signal.core.util.deleteAll
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.ThreadTable
import com.red.sovereign.keyvalue.REDStore
import java.util.UUID

/**
 * Sets up bare-minimum to allow writing unit tests against the database,
 * including setting up the local ACI and PNI pair.
 *
 * @param deleteAllThreadsOnEachRun Run deleteAllThreads between each unit test
 */
class REDDatabaseRule(
  private val deleteAllThreadsOnEachRun: Boolean = true
) : TestWatcher() {

  val localAci: ACI = ACI.from(UUID.randomUUID())
  val localPni: PNI = PNI.from(UUID.randomUUID())

  override fun starting(description: Description?) {
    deleteAllThreads()

    REDStore.account.setAci(localAci)
    REDStore.account.setPni(localPni)
  }

  override fun finished(description: Description?) {
    deleteAllThreads()
  }

  private fun deleteAllThreads() {
    if (deleteAllThreadsOnEachRun) {
      REDDatabase.threads.deleteAllConversations()
      REDDatabase.rawDatabase.deleteAll(ThreadTable.TABLE_NAME)
    }
  }
}
