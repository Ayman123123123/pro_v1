package com.red.sovereign.components.settings.conversation

import com.red.sovereign.util.DynamicNoActionBarTheme
import com.red.sovereign.util.DynamicTheme

class CallInfoActivity : ConversationSettingsActivity(), ConversationSettingsFragment.TransitionCallback {

  override val dynamicTheme: DynamicTheme = DynamicNoActionBarTheme()
}
