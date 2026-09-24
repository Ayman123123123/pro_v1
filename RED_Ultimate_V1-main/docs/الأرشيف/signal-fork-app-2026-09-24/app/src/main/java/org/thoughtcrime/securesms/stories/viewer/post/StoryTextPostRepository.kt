package com.red.sovereign.stories.viewer.post

import android.graphics.Typeface
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.Base64
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.MmsMessageRecord
import com.red.sovereign.database.model.databaseprotos.StoryTextPost
import com.red.sovereign.database.withAttachments
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.fonts.TextFont
import com.red.sovereign.fonts.TextToScript
import com.red.sovereign.fonts.TypefaceCache

class StoryTextPostRepository {
  fun getRecord(recordId: Long): Single<MmsMessageRecord> {
    return Single.fromCallable {
      REDDatabase.messages.getMessageRecord(recordId).withAttachments() as MmsMessageRecord
    }.subscribeOn(Schedulers.io())
  }

  fun getTypeface(recordId: Long): Single<Typeface> {
    return getRecord(recordId).flatMap {
      val model = StoryTextPost.ADAPTER.decode(Base64.decode(it.body))
      val textFont = TextFont.fromStyle(model.style)
      val script = TextToScript.guessScript(model.body)

      TypefaceCache.get(AppDependencies.application, textFont, script)
    }
  }
}
