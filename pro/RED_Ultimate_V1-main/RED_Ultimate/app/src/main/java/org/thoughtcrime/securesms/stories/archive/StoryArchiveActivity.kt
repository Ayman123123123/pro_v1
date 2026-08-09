package com.red.sovereign.stories.archive

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import org.signal.core.ui.compose.theme.REDTheme
import com.red.sovereign.PassphraseRequiredActivity

class StoryArchiveActivity : PassphraseRequiredActivity() {

  companion object {
    fun createIntent(context: Context): Intent {
      return Intent(context, StoryArchiveActivity::class.java)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    super.onCreate(savedInstanceState, ready)

    setContent {
      REDTheme {
        StoryArchiveScreen(
          onNavigationClick = { onBackPressedDispatcher.onBackPressed() }
        )
      }
    }
  }
}
