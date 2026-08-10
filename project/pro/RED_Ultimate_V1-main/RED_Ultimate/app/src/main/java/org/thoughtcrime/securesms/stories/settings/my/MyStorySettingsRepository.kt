package com.red.sovereign.stories.settings.my

import androidx.annotation.WorkerThread
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import com.red.sovereign.database.RecipientTable
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.DistributionListId
import com.red.sovereign.database.model.DistributionListPrivacyData
import com.red.sovereign.database.model.DistributionListPrivacyMode
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.stories.Stories
import com.red.sovereign.stories.settings.privacy.ChooseInitialMyStoryMembershipState

class MyStorySettingsRepository {

  fun getPrivacyState(): Single<MyStoryPrivacyState> {
    return Single.fromCallable {
      getStoryPrivacyState()
    }.subscribeOn(Schedulers.io())
  }

  fun observeChooseInitialPrivacy(): Observable<ChooseInitialMyStoryMembershipState> {
    return Single
      .fromCallable { REDDatabase.distributionLists.getRecipientId(DistributionListId.MY_STORY)!! }
      .subscribeOn(Schedulers.io())
      .flatMapObservable { recipientId ->
        val allREDConnectionsCount = getAllREDConnectionsCount().toObservable()
        val stateWithoutCount = Recipient.observable(recipientId)
          .flatMap { Observable.just(ChooseInitialMyStoryMembershipState(recipientId = recipientId, privacyState = getStoryPrivacyState())) }

        Observable.combineLatest(allREDConnectionsCount, stateWithoutCount) { count, state -> state.copy(allREDConnectionsCount = count) }
      }
  }

  fun setPrivacyMode(privacyMode: DistributionListPrivacyMode): Completable {
    return Completable.fromAction {
      REDDatabase.distributionLists.setPrivacyMode(DistributionListId.MY_STORY, privacyMode)
      Stories.onStorySettingsChanged(DistributionListId.MY_STORY)
    }.subscribeOn(Schedulers.io())
  }

  fun getRepliesAndReactionsEnabled(): Single<Boolean> {
    return Single.fromCallable {
      REDDatabase.distributionLists.getStoryType(DistributionListId.MY_STORY).isStoryWithReplies
    }.subscribeOn(Schedulers.io())
  }

  fun setRepliesAndReactionsEnabled(repliesAndReactionsEnabled: Boolean): Completable {
    return Completable.fromAction {
      REDDatabase.distributionLists.setAllowsReplies(DistributionListId.MY_STORY, repliesAndReactionsEnabled)
      Stories.onStorySettingsChanged(DistributionListId.MY_STORY)
    }.subscribeOn(Schedulers.io())
  }

  fun getAllREDConnectionsCount(): Single<Int> {
    return Single.fromCallable {
      REDDatabase.recipients.getREDContactsCount(RecipientTable.IncludeSelfMode.Exclude)
    }.subscribeOn(Schedulers.io())
  }

  @WorkerThread
  private fun getStoryPrivacyState(): MyStoryPrivacyState {
    val privacyData: DistributionListPrivacyData = REDDatabase.distributionLists.getPrivacyData(DistributionListId.MY_STORY)

    return MyStoryPrivacyState(
      privacyMode = privacyData.privacyMode,
      connectionCount = privacyData.memberCount
    )
  }
}
