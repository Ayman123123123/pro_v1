package com.red.sovereign.keyboard.emoji

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.red.sovereign.R
import com.red.sovereign.components.emoji.EmojiPageModel
import com.red.sovereign.components.emoji.EmojiPageViewGridAdapter.EmojiHeader
import com.red.sovereign.components.emoji.RecentEmojiPageModel
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.emoji.EmojiCategory
import com.red.sovereign.util.DefaultValueLiveData
import com.red.sovereign.util.TextSecurePreferences
import com.red.sovereign.util.adapter.mapping.MappingModelList
import com.red.sovereign.util.livedata.LiveDataUtil

class EmojiKeyboardPageViewModel(private val repository: EmojiKeyboardPageRepository) : ViewModel() {

  private val internalSelectedKey = DefaultValueLiveData<String>(getStartingTab())

  val selectedKey: LiveData<String>
    get() = internalSelectedKey

  val allEmojiModels: MutableLiveData<List<EmojiPageModel>> = MutableLiveData()
  val pages: LiveData<MappingModelList>
  val categories: LiveData<MappingModelList>

  init {
    pages = LiveDataUtil.mapAsync(allEmojiModels) { models ->
      val list = MappingModelList()
      models.forEach { pageModel ->
        if (RecentEmojiPageModel.KEY != pageModel.key) {
          val category = EmojiCategory.forKey(pageModel.key)
          list += EmojiHeader(pageModel.key, category.getCategoryLabel())
          list += pageModel.toMappingModels()
        } else if (pageModel.displayEmoji.isNotEmpty()) {
          list += EmojiHeader(pageModel.key, R.string.ReactWithAnyEmojiBottomSheetDialogFragment__recently_used)
          list += pageModel.toMappingModels()
        }
      }

      list
    }

    categories = LiveDataUtil.combineLatest(allEmojiModels, internalSelectedKey) { models, selectedKey ->
      val list = MappingModelList()
      list += models.map { m ->
        if (RecentEmojiPageModel.KEY == m.key) {
          RecentsMappingModel(m.key == selectedKey)
        } else {
          val category = EmojiCategory.forKey(m.key)
          EmojiCategoryMappingModel(category, category.key == selectedKey)
        }
      }
      list
    }
  }

  fun onKeySelected(key: String) {
    internalSelectedKey.value = key
  }

  fun refreshRecentEmoji() {
    repository.getEmoji(allEmojiModels::postValue)
  }

  companion object {
    fun getStartingTab(): String {
      return if (RecentEmojiPageModel.hasRecents(AppDependencies.application, TextSecurePreferences.RECENT_STORAGE_KEY)) {
        RecentEmojiPageModel.KEY
      } else {
        EmojiCategory.PEOPLE.key
      }
    }
  }

  class Factory(context: Context) : ViewModelProvider.Factory {

    private val repository = EmojiKeyboardPageRepository(context)

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(EmojiKeyboardPageViewModel(repository)))
    }
  }
}
