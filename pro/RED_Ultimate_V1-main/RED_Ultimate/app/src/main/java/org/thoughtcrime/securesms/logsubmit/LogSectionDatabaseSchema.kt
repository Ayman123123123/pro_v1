package com.red.sovereign.logsubmit

import android.content.Context
import org.signal.core.util.getAllIndexDefinitions
import org.signal.core.util.getAllTableDefinitions
import org.signal.core.util.getAllTriggerDefinitions
import org.signal.core.util.getForeignKeys
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.helpers.REDDatabaseMigrations

/**
 * Renders data pertaining to sender key. While all private info is obfuscated, this is still only intended to be printed for internal users.
 */
class LogSectionDatabaseSchema : LogSection {
  override fun getTitle(): String {
    return "DATABASE SCHEMA"
  }

  override fun getContent(context: Context): CharSequence {
    val builder = StringBuilder()
    builder.append("--- Metadata").append("\n")
    builder.append("Version: ${REDDatabaseMigrations.DATABASE_VERSION}\n")
    builder.append("\n\n")

    builder.append("--- Tables").append("\n")
    REDDatabase.rawDatabase.getAllTableDefinitions().forEach {
      builder.append(it.statement).append("\n")
    }
    builder.append("\n\n")

    builder.append("--- Indexes").append("\n")
    REDDatabase.rawDatabase.getAllIndexDefinitions().forEach {
      builder.append(it.statement).append("\n")
    }
    builder.append("\n\n")

    builder.append("--- Foreign Keys").append("\n")
    REDDatabase.rawDatabase.getForeignKeys().forEach {
      builder.append("${it.table}.${it.column} DEPENDS ON ${it.dependsOnTable}.${it.dependsOnColumn}, ON DELETE ${it.onDelete}").append("\n")
    }
    builder.append("\n\n")

    builder.append("--- Triggers").append("\n")
    REDDatabase.rawDatabase.getAllTriggerDefinitions().forEach {
      builder.append(it.statement).append("\n")
    }
    builder.append("\n\n")

    return builder
  }
}
