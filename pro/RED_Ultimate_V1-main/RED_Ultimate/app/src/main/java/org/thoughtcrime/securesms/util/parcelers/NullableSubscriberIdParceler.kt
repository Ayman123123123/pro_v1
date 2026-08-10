/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.util.parcelers

import android.os.Parcel
import kotlinx.parcelize.Parceler
import org.whispersystems.signalservice.api.subscriptions.SubscriberId

/**
 * Parceler for nullable SubscriberIds
 */
object NullableSubscriberIdParceler : Parceler<SubscriberId?> {
  override fun create(parcel: Parcel): SubscriberId? {
    return parcel.readString()?.let { SubscriberId.deserialize(it) }
  }

  override fun SubscriberId?.write(parcel: Parcel, flags: Int) {
    parcel.writeString(this?.serialize())
  }
}
