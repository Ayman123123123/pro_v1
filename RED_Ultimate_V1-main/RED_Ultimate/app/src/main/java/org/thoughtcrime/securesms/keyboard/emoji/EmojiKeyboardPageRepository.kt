package com.red.sovereign.keyboard.emoji

import android.content.Context
import org.signal.core.util.concurrent.REDExecutors
import com.red.sovereign.components.emoji.EmojiPageModel
import com.red.sovereign.components.emoji.RecentEmojiPageModel
import com.red.sovereign.emoji.EmojiSource.Companion.latest
import com.red.sovereign.util.TextSecurePreferences
import java.util.function.Consumer

class EmojiKeyboardPageRepository(private val context: Context) {
  fun getEmoji(consumer: Consumer<List<EmojiPageModel>>) {
    REDExecutors.BOUNDED.execute {
      val list = mutableListOf<EmojiPageModel>()
      list += RecentEmojiPageModel(context, TextSecurePreferences.RECENT_STORAGE_KEY)
      list += latest.displayPages
      consumer.accept(list)
    }
  }
}
