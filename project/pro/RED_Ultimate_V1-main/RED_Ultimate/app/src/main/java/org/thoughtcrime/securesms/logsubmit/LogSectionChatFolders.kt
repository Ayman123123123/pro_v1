package com.red.sovereign.logsubmit

import android.content.Context
import com.red.sovereign.components.settings.app.chats.folders.ChatFolderRecord
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.recipients.Recipient

/**
 * Prints out chat folders settings
 */
class LogSectionChatFolders : LogSection {
  override fun getTitle(): String = "CHAT FOLDERS"

  override fun getContent(context: Context): CharSequence {
    val output = StringBuilder()

    if (Recipient.isSelfSet) {
      val count = REDDatabase.chatFolders.getFolderCount()
      val hasDefault = REDDatabase.chatFolders.getCurrentChatFolders().any { folder -> folder.folderType == ChatFolderRecord.FolderType.ALL }
      output.append("Has default all chats         : ${hasDefault}\n")
      output.append("Number of folders (undeleted) : ${count}\n")
    } else {
      output.append("< Self is not set yet >\n")
    }

    return output
  }
}
