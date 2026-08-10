package com.red.sovereign.features.calls

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CallViewModel @Inject constructor(
    private val voipMaster: RedVoipMaster
) : ViewModel() {
    // محرك المكالمات مربوط الآن بـ Hilt رسمياً
    fun startCall(target: String) = voipMaster.startSecureCall(target)
}
