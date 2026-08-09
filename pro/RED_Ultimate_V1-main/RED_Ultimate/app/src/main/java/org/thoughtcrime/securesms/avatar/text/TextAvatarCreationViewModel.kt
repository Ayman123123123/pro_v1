package com.red.sovereign.avatar.text

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.distinctUntilChanged
import com.red.sovereign.avatar.Avatar
import com.red.sovereign.avatar.Avatars
import com.red.sovereign.util.livedata.Store

class TextAvatarCreationViewModel(initialText: Avatar.Text) : ViewModel() {

  private val store = Store(TextAvatarCreationState(initialText))

  val state: LiveData<TextAvatarCreationState> = store.stateLiveData.distinctUntilChanged()

  fun setColor(colorPair: Avatars.ColorPair) {
    store.update { it.copy(currentAvatar = it.currentAvatar.copy(color = colorPair)) }
  }

  fun setText(text: String) {
    store.update {
      if (it.currentAvatar.text == text) {
        it
      } else {
        it.copy(currentAvatar = it.currentAvatar.copy(text = text))
      }
    }
  }

  fun getCurrentAvatar(): Avatar.Text {
    return store.state.currentAvatar
  }

  class Factory(private val initialText: Avatar.Text) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(TextAvatarCreationViewModel(initialText)))
    }
  }
}
