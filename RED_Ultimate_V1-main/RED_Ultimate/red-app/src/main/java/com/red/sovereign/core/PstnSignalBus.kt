package com.red.sovereign.core

import com.red.sovereign.calls.CallSignal
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object PstnSignalBus {
    private val _events = MutableSharedFlow<CallSignal>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events = _events.asSharedFlow()
    fun publish(signal: CallSignal) { _events.tryEmit(signal) }
}
