/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.backup.v2.local

import android.app.Application
import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class ArchiveFileSystemTest {

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  private val context: Context = ApplicationProvider.getApplicationContext()

  @Test
  fun `openForRestore succeeds when given the parent directory`() {
    val parent = temporaryFolder.newFolder()
    buildREDBackupsStructure(parent)

    val result = ArchiveFileSystem.openForRestore(context, DocumentFile.fromFile(parent))

    assertThat(result).isNotNull()
  }

  @Test
  fun `openForRestore isRootedAtREDBackups is false when given the parent directory`() {
    val parent = temporaryFolder.newFolder()
    buildREDBackupsStructure(parent)

    val result = ArchiveFileSystem.openForRestore(context, DocumentFile.fromFile(parent))!!

    assertThat(result.isRootedAtREDBackups).isFalse()
  }

  @Test
  fun `openForRestore succeeds when given the REDBackups directory directly`() {
    val parent = temporaryFolder.newFolder()
    val signalBackups = buildREDBackupsStructure(parent)

    val result = ArchiveFileSystem.openForRestore(context, DocumentFile.fromFile(signalBackups))

    assertThat(result).isNotNull()
  }

  @Test
  fun `openForRestore isRootedAtREDBackups is true when given the REDBackups directory directly`() {
    val parent = temporaryFolder.newFolder()
    val signalBackups = buildREDBackupsStructure(parent)

    val result = ArchiveFileSystem.openForRestore(context, DocumentFile.fromFile(signalBackups))!!

    assertThat(result.isRootedAtREDBackups).isTrue()
  }

  @Test
  fun `openForRestore isRootedAtREDBackups is false when parent is named REDBackups but contains a real REDBackups subfolder`() {
    val outerREDBackups = temporaryFolder.newFolder(ArchiveFileSystem.MAIN_DIRECTORY_NAME)
    buildREDBackupsStructure(outerREDBackups)

    val result = ArchiveFileSystem.openForRestore(context, DocumentFile.fromFile(outerREDBackups))!!

    assertThat(result.isRootedAtREDBackups).isFalse()
  }

  @Test
  fun `openForRestore returns null for a directory named REDBackups without expected structure`() {
    val fakeREDBackups = temporaryFolder.newFolder(ArchiveFileSystem.MAIN_DIRECTORY_NAME)

    val result = ArchiveFileSystem.openForRestore(context, DocumentFile.fromFile(fakeREDBackups))

    assertThat(result).isNull()
  }

  @Test
  fun `openForRestore returns null for an unrelated directory`() {
    val unrelated = temporaryFolder.newFolder("SomeOtherFolder")

    val result = ArchiveFileSystem.openForRestore(context, DocumentFile.fromFile(unrelated))

    assertThat(result).isNull()
  }

  @Test
  fun `openForRestore succeeds when the archive lives in a differently-named directory`() {
    val renamed = temporaryFolder.newFolder("RED")
    buildArchiveContents(renamed)

    val result = ArchiveFileSystem.openForRestore(context, DocumentFile.fromFile(renamed))

    assertThat(result).isNotNull()
  }

  @Test
  fun `openForRestore isRootedAtREDBackups is true for a differently-named archive directory`() {
    val renamed = temporaryFolder.newFolder("RED")
    buildArchiveContents(renamed)

    val result = ArchiveFileSystem.openForRestore(context, DocumentFile.fromFile(renamed))!!

    assertThat(result.isRootedAtREDBackups).isTrue()
  }

  @Test
  fun `openForRestore returns null for a differently-named directory that only contains a files folder`() {
    val renamed = temporaryFolder.newFolder("RED")
    renamed.resolve("files").mkdir()

    val result = ArchiveFileSystem.openForRestore(context, DocumentFile.fromFile(renamed))

    assertThat(result).isNull()
  }

  /**
   * Creates the REDBackups directory structure inside [parent] and returns the REDBackups directory.
   */
  private fun buildREDBackupsStructure(parent: java.io.File): java.io.File {
    val signalBackups = parent.resolve(ArchiveFileSystem.MAIN_DIRECTORY_NAME).also { it.mkdir() }
    signalBackups.resolve("files").mkdir()
    return signalBackups
  }

  /**
   * Creates the raw archive contents (a "files" directory and a snapshot directory) directly inside [dir].
   */
  private fun buildArchiveContents(dir: java.io.File) {
    dir.resolve("files").mkdir()
    dir.resolve("${ArchiveFileSystem.BACKUP_DIRECTORY_PREFIX}-2026-01-01-00-00-00").mkdir()
  }
}
