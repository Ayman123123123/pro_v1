package com.red.sovereign.keyboard.emoji.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.red.sovereign.components.emoji.EmojiPageModel
import com.red.sovereign.components.emoji.RecentEmojiPageModel
import com.red.sovereign.keyboard.emoji.toMappingModels
import com.red.sovereign.util.adapter.mapping.MappingModel
import com.red.sovereign.util.livedata.LiveDataUtil

class EmojiSearchViewModel(private val repository: EmojiSearchRepository) : ViewModel() {

  private val pageModel = MutableLiveData<EmojiPageModel>()

  val emojiList: LiveData<EmojiSearchResults> = LiveDataUtil.mapAsync(pageModel) { page ->
    EmojiSearchResults(page.toMappingModels(), page.key == RecentEmojiPageModel.KEY)
  }

  init {
    onQueryChanged("")
  }

  fun onQueryChanged(query: String) {
    repository.submitQuery(query = query, includeRecents = true, consumer = pageModel::postValue)
  }

  data class EmojiSearchResults(val emojiList: List<MappingModel<*>>, val isRecents: Boolean)

  class Factory(private val repository: EmojiSearchRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(EmojiSearchViewModel(repository)))
    }
  }
}
