package com.red.sovereign.components.settings.app.appearance

import android.app.Activity
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.signal.core.util.AppUtil
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobs.EmojiSearchIndexDownloadJob
import com.red.sovereign.keyvalue.SettingsValues.Theme
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.util.SplashScreenUtil

class AppearanceSettingsViewModel : ViewModel() {
  private val store = MutableStateFlow(getState())
  val state: StateFlow<AppearanceSettingsState> = store

  fun refreshState() {
    store.update { getState() }
  }

  fun setTheme(activity: Activity?, theme: Theme) {
    store.update { it.copy(theme = theme) }
    REDStore.settings.theme = theme
    SplashScreenUtil.setSplashScreenThemeIfNecessary(activity, theme)
  }

  fun setLanguage(language: String) {
    store.update { it.copy(language = language) }
    REDStore.settings.language = language
    EmojiSearchIndexDownloadJob.scheduleImmediately()
    AppUtil.restart(AppDependencies.application)
  }

  fun setMessageFontSize(size: Int) {
    store.update { it.copy(messageFontSize = size) }
    REDStore.settings.messageFontSize = size
  }

  private fun getState(): AppearanceSettingsState {
    return AppearanceSettingsState(
      REDStore.settings.theme,
      REDStore.settings.messageFontSize,
      REDStore.settings.language,
      REDStore.settings.useCompactNavigationBar
    )
  }
}
