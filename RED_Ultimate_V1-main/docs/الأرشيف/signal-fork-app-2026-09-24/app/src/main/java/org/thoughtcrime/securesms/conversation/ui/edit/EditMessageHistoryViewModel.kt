package com.red.sovereign.conversation.ui.edit

import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import com.red.sovereign.conversation.ConversationMessage
import com.red.sovereign.conversation.colors.GroupAuthorNameColorHelper
import com.red.sovereign.conversation.colors.NameColor
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId

/**
 * View model to show history of edits for a specific message.
 */
class EditMessageHistoryViewModel(private val originalMessageId: Long, private val conversationRecipient: Recipient) : ViewModel() {
  private val groupAuthorNameColorHelper = GroupAuthorNameColorHelper()

  fun getEditHistory(): Observable<List<ConversationMessage>> {
    return EditMessageHistoryRepository
      .getEditHistory(originalMessageId)
      .observeOn(AndroidSchedulers.mainThread())
  }

  fun getNameColorsMap(): Observable<Map<RecipientId, NameColor>> {
    return conversationRecipient
      .live()
      .observable()
      .map { recipient ->
        if (recipient.groupId.isPresent) {
          groupAuthorNameColorHelper.getColorMap(recipient.groupId.get())
        } else {
          emptyMap()
        }
      }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
  }

  fun markRevisionsRead() {
    EditMessageHistoryRepository.markRevisionsRead(originalMessageId)
  }
}
