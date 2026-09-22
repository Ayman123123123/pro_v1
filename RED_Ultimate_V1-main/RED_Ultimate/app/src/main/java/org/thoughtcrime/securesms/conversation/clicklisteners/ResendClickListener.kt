/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.conversation.clicklisteners

import android.view.View
import org.signal.core.util.concurrent.REDExecutors
import org.signal.core.util.logging.Log
import com.red.sovereign.database.model.MessageRecord
import com.red.sovereign.mms.Slide
import com.red.sovereign.mms.SlidesClickedListener
import com.red.sovereign.sms.MessageSender

class ResendClickListener(private val messageRecord: MessageRecord) : SlidesClickedListener {
  override fun onClick(v: View?, slides: MutableList<Slide>?) {
    if (v == null) {
      Log.w(TAG, "Could not resend message, view was null!")
      return
    }

    REDExecutors.BOUNDED.execute {
      MessageSender.resend(v.context, messageRecord)
    }
  }

  companion object {
    private val TAG = Log.tag(ResendClickListener::class.java)
  }
}
