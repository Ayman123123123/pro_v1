package com.red.sovereign.stories.settings.create

import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.stories.Stories

class CreateStoryWithViewersRepository {
  fun createList(name: CharSequence, members: Set<RecipientId>): Single<RecipientId> {
    return Single.create<RecipientId> {
      val result = REDDatabase.distributionLists.createList(name.toString(), members.toList())
      if (result == null) {
        it.onError(Exception("Null result, due to a duplicated name."))
      } else {
        Stories.onStorySettingsChanged(result)
        it.onSuccess(REDDatabase.recipients.getOrInsertFromDistributionListId(result))
      }
    }.subscribeOn(Schedulers.io())
  }
}
