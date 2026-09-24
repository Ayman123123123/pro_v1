package com.red.sovereign.components.settings.app.subscription.receipts.list

import io.reactivex.rxjava3.core.Single
import com.red.sovereign.badges.Badges
import com.red.sovereign.components.settings.app.subscription.getBoostBadges
import com.red.sovereign.components.settings.app.subscription.getGiftBadges
import com.red.sovereign.components.settings.app.subscription.getSubscriptionLevels
import com.red.sovereign.database.model.InAppPaymentReceiptRecord
import com.red.sovereign.dependencies.AppDependencies
import java.util.Locale

class DonationReceiptListRepository {
  fun getBadges(): Single<List<DonationReceiptBadge>> {
    return Single.fromCallable {
      AppDependencies.donationsService
        .getDonationsConfiguration(Locale.getDefault())
    }.map { response ->
      if (response.result.isPresent) {
        val config = response.result.get()
        val boostBadge = DonationReceiptBadge(InAppPaymentReceiptRecord.Type.ONE_TIME_DONATION, -1, config.getBoostBadges().first())
        val giftBadge = DonationReceiptBadge(InAppPaymentReceiptRecord.Type.ONE_TIME_GIFT, -1, config.getGiftBadges().first())
        val subBadges = config.getSubscriptionLevels().map {
          DonationReceiptBadge(
            level = it.key,
            badge = Badges.fromServiceBadge(it.value.badge),
            type = InAppPaymentReceiptRecord.Type.RECURRING_DONATION
          )
        }
        subBadges + boostBadge + giftBadge
      } else {
        emptyList()
      }
    }
  }
}
