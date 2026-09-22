package com.red.sovereign.conversation.colors.ui

import android.content.Context
import org.signal.core.util.concurrent.REDExecutors
import com.red.sovereign.conversation.colors.ChatColors
import com.red.sovereign.conversation.colors.ChatColorsPalette
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.wallpaper.ChatWallpaper

sealed class ChatColorSelectionRepository(context: Context) {

  protected val context: Context = context.applicationContext

  abstract fun getWallpaper(consumer: (ChatWallpaper?) -> Unit)
  abstract fun getChatColors(consumer: (ChatColors) -> Unit)
  abstract fun save(chatColors: ChatColors, onSaved: () -> Unit)

  fun duplicate(chatColors: ChatColors) {
    REDExecutors.BOUNDED.execute {
      val duplicate = chatColors.withId(ChatColors.Id.NotSet)
      REDDatabase.chatColors.saveChatColors(duplicate)
    }
  }

  fun getUsageCount(chatColorsId: ChatColors.Id, consumer: (Int) -> Unit) {
    REDExecutors.BOUNDED.execute {
      consumer(REDDatabase.recipients.getColorUsageCount(chatColorsId))
    }
  }

  fun delete(chatColors: ChatColors, onDeleted: () -> Unit) {
    REDExecutors.BOUNDED.execute {
      REDDatabase.chatColors.deleteChatColors(chatColors)
      onDeleted()
    }
  }

  private class Global(context: Context) : ChatColorSelectionRepository(context) {
    override fun getWallpaper(consumer: (ChatWallpaper?) -> Unit) {
      consumer(REDStore.wallpaper.wallpaper)
    }

    override fun getChatColors(consumer: (ChatColors) -> Unit) {
      if (REDStore.chatColors.hasChatColors) {
        consumer(requireNotNull(REDStore.chatColors.chatColors))
      } else {
        getWallpaper { wallpaper ->
          if (wallpaper != null) {
            consumer(wallpaper.autoChatColors)
          } else {
            consumer(ChatColorsPalette.Bubbles.default.withId(ChatColors.Id.Auto))
          }
        }
      }
    }

    override fun save(chatColors: ChatColors, onSaved: () -> Unit) {
      if (chatColors.id == ChatColors.Id.Auto) {
        REDStore.chatColors.chatColors = null
      } else {
        REDStore.chatColors.chatColors = chatColors
      }
      onSaved()
    }
  }

  private class Single(context: Context, private val recipientId: RecipientId) : ChatColorSelectionRepository(context) {
    override fun getWallpaper(consumer: (ChatWallpaper?) -> Unit) {
      REDExecutors.BOUNDED.execute {
        val recipient = Recipient.resolved(recipientId)
        consumer(recipient.wallpaper)
      }
    }

    override fun getChatColors(consumer: (ChatColors) -> Unit) {
      REDExecutors.BOUNDED.execute {
        val recipient = Recipient.resolved(recipientId)
        consumer(recipient.chatColors)
      }
    }

    override fun save(chatColors: ChatColors, onSaved: () -> Unit) {
      REDExecutors.BOUNDED.execute {
        val recipientDatabase = REDDatabase.recipients
        recipientDatabase.setColor(recipientId, chatColors)
        onSaved()
      }
    }
  }

  companion object {
    fun create(context: Context, recipientId: RecipientId?): ChatColorSelectionRepository {
      return if (recipientId != null) {
        Single(context, recipientId)
      } else {
        Global(context)
      }
    }
  }
}
