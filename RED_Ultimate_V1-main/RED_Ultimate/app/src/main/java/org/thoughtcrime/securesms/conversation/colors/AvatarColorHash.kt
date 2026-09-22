/*
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.conversation.colors

import org.signal.core.models.ServiceId
import org.signal.core.util.CryptoUtil
import com.red.sovereign.groups.GroupId

/**
 * Stolen from iOS. Utilizes a simple hash to map different characteristics to an avatar color index.
 */
object AvatarColorHash {

  /**
   * Utilize Uppercase UUID of ServiceId.
   *
   * Uppercase is necessary here because iOS utilizes uppercase UUIDs by default.
   */
  fun forAddress(serviceId: ServiceId?, e164: String?): AvatarColor {
    if (serviceId != null) {
      return forData(serviceId.toByteArray())
    }

    if (!e164.isNullOrEmpty()) {
      return forData(e164.toByteArray(Charsets.UTF_8))
    }

    return AvatarColor.A100
  }

  fun forGroupId(group: GroupId): AvatarColor {
    return forData(group.decodedId)
  }

  @JvmStatic
  fun forCallLink(rootKey: ByteArray): AvatarColor {
    return forIndex(rootKey.first().toInt())
  }

  private fun forData(data: ByteArray): AvatarColor {
    val hash = CryptoUtil.sha256(data)
    val firstByte: Byte = hash[0]
    return forIndex(firstByte.toInt())
  }

  private fun forIndex(index: Int): AvatarColor {
    return AvatarColor.RANDOM_OPTIONS[(index.toUInt() % AvatarColor.RANDOM_OPTIONS.size.toUInt()).toInt()]
  }
}
