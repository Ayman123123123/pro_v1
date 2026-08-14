package com.red.sovereign.calls

/** Keeps a small, ordered outbox while the signaling socket is reconnecting. */
internal class PendingCallSignalQueue(private val capacity: Int = DEFAULT_CAPACITY) {
    private val values = ArrayDeque<String>()

    @Synchronized
    fun enqueue(value: String) {
        if (values.size == capacity) values.removeFirst()
        values.addLast(value)
    }

    @Synchronized
    fun flush(send: (String) -> Boolean) {
        while (values.isNotEmpty()) {
            val next = values.first()
            if (!send(next)) return
            values.removeFirst()
        }
    }

    @Synchronized
    fun clear() = values.clear()

    @Synchronized
    fun size(): Int = values.size

    private companion object {
        const val DEFAULT_CAPACITY = 64
    }
}
