package com.red.sovereign.conversationlist.chatfilter

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import com.red.sovereign.conversationlist.model.ConversationFilter

@Parcelize
data class ConversationFilterRequest(
  val filter: ConversationFilter,
  val source: ConversationFilterSource
) : Parcelable
