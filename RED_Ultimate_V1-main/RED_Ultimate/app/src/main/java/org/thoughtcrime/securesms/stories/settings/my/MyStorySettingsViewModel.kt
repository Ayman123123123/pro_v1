package com.red.sovereign.stories.settings.my

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.schedulers.Schedulers
import com.red.sovereign.database.model.DistributionListPrivacyMode
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.stories.Stories
import com.red.sovereign.util.livedata.Store

class MyStorySettingsViewModel @JvmOverloads constructor(private val repository: MyStorySettingsRepository = MyStorySettingsRepository()) : ViewModel() {
  private val store = Store(MyStorySettingsState(hasUserPerformedManualSelection = REDStore.story.userHasBeenNotifiedAboutStories))
  private val disposables = CompositeDisposable()

  val state: LiveData<MyStorySettingsState> = store.stateLiveData

  override fun onCleared() {
    disposables.clear()
  }

  fun refresh() {
    disposables.clear()
    disposables += repository.getPrivacyState()
      .subscribe { myStoryPrivacyState -> store.update { it.copy(myStoryPrivacyState = myStoryPrivacyState) } }
    disposables += repository.getRepliesAndReactionsEnabled()
      .subscribe { repliesAndReactionsEnabled -> store.update { it.copy(areRepliesAndReactionsEnabled = repliesAndReactionsEnabled) } }
    disposables += repository.getAllREDConnectionsCount()
      .subscribe { allREDConnectionsCount -> store.update { it.copy(allREDConnectionsCount = allREDConnectionsCount) } }
  }

  fun setRepliesAndReactionsEnabled(repliesAndReactionsEnabled: Boolean) {
    disposables += repository.setRepliesAndReactionsEnabled(repliesAndReactionsEnabled)
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { refresh() }
  }

  fun setMyStoryPrivacyMode(privacyMode: DistributionListPrivacyMode): Completable {
    store.update { state ->
      state.copy(hasUserPerformedManualSelection = true)
    }

    REDStore.story.userHasBeenNotifiedAboutStories = true

    return if (privacyMode == state.value!!.myStoryPrivacyState.privacyMode) {
      Completable.fromAction {
        Stories.onStorySettingsChanged(Recipient.self().id)
      }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
    } else {
      repository.setPrivacyMode(privacyMode)
        .observeOn(AndroidSchedulers.mainThread())
        .doOnComplete { refresh() }
    }
  }
}
