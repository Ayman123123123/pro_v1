/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.libsignal.protocol

import java.io.Serializable

/**
 * RED Protocol Address - a simple wrapper around a service ID string and device ID.
 * This is a local implementation to replace the missing class from libsignal 0.86.5.
 */
data class REDProtocolAddress(
    val serviceId: String,
    val deviceId: Int
) : Serializable {

    override fun toString(): String {
        return "$serviceId:$deviceId"
    }

    companion object {
        fun parse(address: String): REDProtocolAddress? {
            val parts = address.split(':')
            if (parts.size != 2) return null
            val name = parts[0]
            val deviceId = parts[1].toIntOrNull() ?: return null
            return REDProtocolAddress(name, deviceId)
        }
    }
}