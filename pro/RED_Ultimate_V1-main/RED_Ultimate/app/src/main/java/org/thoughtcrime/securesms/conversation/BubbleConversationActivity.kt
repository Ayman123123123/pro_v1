package com.red.sovereign.conversation

import com.red.sovereign.R
import com.red.sovereign.conversation.v2.ConversationActivity
import com.red.sovereign.util.ViewUtil

/**
 * Activity which encapsulates a conversation for a Bubble window.
 *8
 * This activity exists so that we can override some of its manifest parameters
 * without clashing with [ConversationActivity] and provide an API-level
 * independent "is in bubble?" check.
 */
class BubbleConversationActivity : ConversationActivity() {

  override fun onPause() {
    super.onPause()
    ViewUtil.hideKeyboard(this, findViewById(R.id.fragment_container))
  }
}
