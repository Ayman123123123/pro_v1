package com.red.sovereign.components.settings.app.usernamelinks.colorpicker

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.collections.immutable.toImmutableList
import org.signal.core.util.concurrent.REDExecutors
import com.red.sovereign.components.settings.app.usernamelinks.QrCodeData
import com.red.sovereign.components.settings.app.usernamelinks.QrCodeState
import com.red.sovereign.components.settings.app.usernamelinks.UsernameQrCodeColorScheme
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.profiles.manage.UsernameRepository.toLink
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.storage.StorageSyncHelper

class UsernameLinkQrColorPickerViewModel : ViewModel() {

  private val _state = mutableStateOf(
    UsernameLinkQrColorPickerState(
      username = REDStore.account.username!!,
      qrCodeData = QrCodeState.Loading,
      colorSchemes = UsernameQrCodeColorScheme.entries.toImmutableList(),
      selectedColorScheme = REDStore.misc.usernameQrCodeColorScheme
    )
  )

  val state: State<UsernameLinkQrColorPickerState> = _state

  private val disposable: CompositeDisposable = CompositeDisposable()

  init {
    val usernameLink = REDStore.account.usernameLink

    if (usernameLink != null) {
      disposable += Single
        .fromCallable { QrCodeData.forData(usernameLink.toLink()) }
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe { qrData ->
          _state.value = _state.value.copy(
            qrCodeData = QrCodeState.Present(qrData)
          )
        }
    } else {
      _state.value = _state.value.copy(
        qrCodeData = QrCodeState.NotSet
      )
    }
  }

  override fun onCleared() {
    disposable.clear()
  }

  fun onColorSelected(color: UsernameQrCodeColorScheme) {
    REDStore.misc.usernameQrCodeColorScheme = color
    REDExecutors.BOUNDED.run {
      REDDatabase.recipients.markNeedsSync(Recipient.self().id)
      StorageSyncHelper.scheduleSyncForDataChange()
    }

    _state.value = _state.value.copy(
      selectedColorScheme = color
    )
  }
}
