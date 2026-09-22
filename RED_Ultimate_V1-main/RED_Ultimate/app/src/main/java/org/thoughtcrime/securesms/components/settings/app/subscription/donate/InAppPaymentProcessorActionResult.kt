package com.red.sovereign.components.settings.app.subscription.donate

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import com.red.sovereign.database.InAppPaymentTable

@Parcelize
class InAppPaymentProcessorActionResult(
  val action: InAppPaymentProcessorAction,
  val inAppPaymentId: InAppPaymentTable.InAppPaymentId?,
  val status: Status
) : Parcelable {
  enum class Status {
    SUCCESS,
    FAILURE
  }
}
