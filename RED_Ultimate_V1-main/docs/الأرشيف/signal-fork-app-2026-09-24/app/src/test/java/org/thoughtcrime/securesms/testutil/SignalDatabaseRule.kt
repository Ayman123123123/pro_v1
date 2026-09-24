/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.testutil

import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.rules.ExternalResource
import com.red.sovereign.database.DatabaseTable
import com.red.sovereign.database.RemappedRecordsTestHelper
import com.red.sovereign.database.SQLiteDatabase
import com.red.sovereign.database.SearchTable
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.testing.JdbcSqliteDatabase
import com.red.sovereign.testing.TestREDDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabase as SQLCipherSQLiteDatabase

class REDDatabaseRule : ExternalResource() {

  lateinit var signalDatabase: TestREDDatabase

  val readableDatabase: SQLiteDatabase
    get() = signalDatabase.signalReadableDatabase

  val writeableDatabase: SQLiteDatabase
    get() = signalDatabase.signalWritableDatabase

  override fun before() {
    RecipientId.clearCache()
    RemappedRecordsTestHelper.resetInstance()
    DatabaseTable.clearTableReferencesForTests()

    signalDatabase = inMemoryREDDatabase()

    mockkObject(REDDatabase)
    every { REDDatabase.instance } returns signalDatabase
    every { REDDatabase.inTransaction } answers { signalDatabase.signalWritableDatabase.inTransaction() }
    every { REDDatabase.rawDatabase } returns rawDatabaseDelegatingTransactionsToTestDatabase()
  }

  /**
   * The test database is backed by sqlite-jdbc rather than SQLCipher, so there's no real
   * [SQLCipherSQLiteDatabase] to hand out. Callers that grab [REDDatabase.rawDatabase] do so to control
   * transactions, so that's what we wire up here. Everything else is relaxed and does nothing.
   */
  private fun rawDatabaseDelegatingTransactionsToTestDatabase(): SQLCipherSQLiteDatabase {
    return mockk(relaxed = true) {
      every { beginTransaction() } answers { writeableDatabase.beginTransaction() }
      every { beginTransactionNonExclusive() } answers { writeableDatabase.beginTransactionNonExclusive() }
      every { setTransactionSuccessful() } answers { writeableDatabase.setTransactionSuccessful() }
      every { endTransaction() } answers { writeableDatabase.endTransaction() }
      every { inTransaction() } answers { writeableDatabase.inTransaction() }
    }
  }

  override fun after() {
    unmockkObject(REDDatabase)
    signalDatabase.close()
    RecipientId.clearCache()
    RemappedRecordsTestHelper.resetInstance()
    DatabaseTable.clearTableReferencesForTests()
  }

  companion object {
    /**
     * Create an in-memory only database mimicking one created fresh for RED. Uses sqlite-jdbc
     * (org.xerial) to provide a modern SQLite with FTS5 and JSON1 support, bypassing Robolectric's
     * limited native SQLite.
     */
    private fun inMemoryREDDatabase(): TestREDDatabase {
      val db = JdbcSqliteDatabase.createInMemory()
      val signalDatabase = TestREDDatabase(ApplicationProvider.getApplicationContext(), db, db)
      signalDatabase.onCreateTablesIndexesAndTriggers(signalDatabase.signalWritableDatabase)
      SearchTable.CREATE_TABLE.forEach { signalDatabase.signalWritableDatabase.execSQL(it) }
      SearchTable.CREATE_TRIGGERS.forEach { signalDatabase.signalWritableDatabase.execSQL(it) }

      return signalDatabase
    }
  }
}
