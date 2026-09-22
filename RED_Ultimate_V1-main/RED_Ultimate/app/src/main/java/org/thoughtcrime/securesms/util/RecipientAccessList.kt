package com.red.sovereign.util

import org.signal.core.models.ServiceId
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId
import org.whispersystems.signalservice.api.push.REDServiceAddress

/**
 * A list of Recipients, but with some helpful methods for retrieving them by various properties. Uses lazy properties to ensure that it will be as performant
 * as a regular list if you don't call any of the extra methods.
 */
class RecipientAccessList(private val recipients: List<Recipient>) : List<Recipient> by recipients {

  private val byServiceId: Map<ServiceId, Recipient> by lazy {
    recipients
      .filter { it.hasServiceId }
      .associateBy { it.requireServiceId() }
  }

  private val byE164: Map<String, Recipient> by lazy {
    recipients
      .filter { it.hasE164 }
      .associateBy { it.requireE164() }
  }

  fun requireByAddress(address: REDServiceAddress): Recipient {
    if (byServiceId.containsKey(address.serviceId)) {
      return byServiceId[address.serviceId]!!
    } else if (address.number.isPresent && byE164.containsKey(address.number.get())) {
      return byE164[address.number.get()]!!
    } else {
      throw IllegalArgumentException("Could not find a matching recipient!")
    }
  }

  fun requireIdByAddress(address: REDServiceAddress): RecipientId {
    return requireByAddress(address).id
  }
}
