/*
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.conversation.v2

import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.processors.BehaviorProcessor
import com.red.sovereign.database.model.StickerRecord
import com.red.sovereign.stickers.StickerSearchRepository

class StickerSuggestionsViewModel(
  private val stickerSearchRepository: StickerSearchRepository = StickerSearchRepository()
) : ViewModel() {

  private val stickerSearchProcessor = BehaviorProcessor.createDefault("")

  val stickers: Flowable<List<StickerRecord>> = stickerSearchProcessor
    .switchMapSingle { stickerSearchRepository.searchByEmoji(it) }

  fun onInputTextUpdated(text: String) {
    stickerSearchProcessor.onNext(text)
  }
}
