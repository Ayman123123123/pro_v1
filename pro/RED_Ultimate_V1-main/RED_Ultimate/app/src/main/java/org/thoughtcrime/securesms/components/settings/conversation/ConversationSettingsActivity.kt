package com.red.sovereign.components.settings.conversation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.ActivityCompat
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback
import com.red.sovereign.R
import com.red.sovereign.components.settings.DSLSettingsActivity
import com.red.sovereign.groups.GroupId
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.util.DynamicConversationSettingsTheme
import com.red.sovereign.util.DynamicTheme

open class ConversationSettingsActivity : DSLSettingsActivity(), ConversationSettingsFragment.TransitionCallback {

  override val dynamicTheme: DynamicTheme = DynamicConversationSettingsTheme()

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    ActivityCompat.postponeEnterTransition(this)
    setExitSharedElementCallback(MaterialContainerTransformSharedElementCallback())
    super.onCreate(savedInstanceState, ready)
  }

  override fun onReadyForEnterTransition() {
    ActivityCompat.startPostponedEnterTransition(this)
  }

  override fun finish() {
    super.finish()
    overridePendingTransition(0, R.anim.slide_fade_to_bottom)
  }

  companion object {
    @JvmStatic
    fun forGroup(context: Context, groupId: GroupId): Intent {
      val startBundle = ConversationSettingsFragmentArgs.Builder(null, groupId, null)
        .build()
        .toBundle()

      return getIntent(context)
        .putExtra(ARG_START_BUNDLE, startBundle)
    }

    @JvmStatic
    fun forRecipient(context: Context, recipientId: RecipientId): Intent {
      val startBundle = ConversationSettingsFragmentArgs.Builder(recipientId, null, null)
        .build()
        .toBundle()

      return getIntent(context)
        .putExtra(ARG_START_BUNDLE, startBundle)
    }

    @JvmStatic
    fun forCall(context: Context, callPeer: Recipient, callMessageIds: LongArray): Intent {
      val startBundleBuilder = if (callPeer.isGroup) {
        ConversationSettingsFragmentArgs.Builder(null, callPeer.requireGroupId(), callMessageIds)
      } else {
        ConversationSettingsFragmentArgs.Builder(callPeer.id, null, callMessageIds)
      }

      val startBundle = startBundleBuilder.build().toBundle()

      return getIntent(context)
        .setClass(context, CallInfoActivity::class.java)
        .putExtra(ARG_START_BUNDLE, startBundle)
    }

    private fun getIntent(context: Context): Intent {
      return Intent(context, ConversationSettingsActivity::class.java)
        .putExtra(ARG_NAV_GRAPH, R.navigation.conversation_settings)
    }
  }
}
