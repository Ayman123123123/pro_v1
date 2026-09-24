package com.red.sovereign.components.settings.app.subscription.manage

import android.text.method.LinkMovementMethod
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.signal.core.util.money.FiatMoney
import com.red.sovereign.R
import com.red.sovereign.badges.BadgeImageView
import com.red.sovereign.components.settings.PreferenceModel
import com.red.sovereign.databinding.MySupportPreferenceBinding
import com.red.sovereign.payments.FiatMoneyUtil
import com.red.sovereign.subscription.Subscription
import com.red.sovereign.util.DateUtils
import com.red.sovereign.util.SpanUtil
import com.red.sovereign.util.adapter.mapping.BindingFactory
import com.red.sovereign.util.adapter.mapping.BindingViewHolder
import com.red.sovereign.util.adapter.mapping.MappingAdapter
import com.red.sovereign.util.visible
import java.util.Locale

/**
 * DSL renderable item that displays active subscription information on the user's
 * manage donations page.
 */
object ActiveSubscriptionPreference {

  class Model(
    val price: FiatMoney,
    val subscription: Subscription,
    val renewalTimestamp: Long = -1L,
    val redemptionState: ManageDonationsState.RedemptionState,
    val isPaymentFailure: Boolean = false,
    val subscriberRequiresCancel: Boolean,
    val onContactSupport: () -> Unit,
    val onRowClick: (ManageDonationsState.RedemptionState) -> Unit
  ) : PreferenceModel<Model>() {
    override fun areItemsTheSame(newItem: Model): Boolean {
      return subscription.id == newItem.subscription.id
    }

    override fun areContentsTheSame(newItem: Model): Boolean {
      return super.areContentsTheSame(newItem) &&
        subscription == newItem.subscription &&
        renewalTimestamp == newItem.renewalTimestamp &&
        redemptionState == newItem.redemptionState &&
        FiatMoney.equals(price, newItem.price) &&
        isPaymentFailure == newItem.isPaymentFailure
    }
  }

  class ViewHolder(binding: MySupportPreferenceBinding) : BindingViewHolder<Model, MySupportPreferenceBinding>(binding) {

    val badge: BadgeImageView = binding.mySupportBadge
    val title: TextView = binding.mySupportTitle
    val expiry: TextView = binding.mySupportExpiry
    val progress: ProgressBar = binding.mySupportProgress

    override fun bind(model: Model) {
      itemView.setOnClickListener(null)

      badge.setBadge(model.subscription.badge)

      title.text = context.getString(
        R.string.MySupportPreference__s_per_month,
        FiatMoneyUtil.format(
          context.resources,
          model.price,
          FiatMoneyUtil.formatOptions()
        )
      )

      expiry.movementMethod = LinkMovementMethod.getInstance()

      itemView.setOnClickListener { model.onRowClick(model.redemptionState) }
      itemView.isClickable = model.redemptionState != ManageDonationsState.RedemptionState.IN_PROGRESS

      when (model.redemptionState) {
        ManageDonationsState.RedemptionState.NONE -> presentRenewalState(model)
        ManageDonationsState.RedemptionState.IS_PENDING_BANK_TRANSFER -> presentPendingBankTransferState()
        ManageDonationsState.RedemptionState.IN_PROGRESS -> presentInProgressState()
        ManageDonationsState.RedemptionState.FAILED -> presentFailureState(model)
        ManageDonationsState.RedemptionState.SUBSCRIPTION_REFRESH -> presentRefreshState()
      }
    }

    private fun presentRenewalState(model: Model) {
      expiry.text = context.getString(
        R.string.MySupportPreference__renews_s,
        DateUtils.formatDateWithYear(
          Locale.getDefault(),
          model.renewalTimestamp
        )
      )
      progress.visible = false
    }

    private fun presentRefreshState() {
      expiry.text = context.getString(R.string.MySupportPreference__checking_subscription)
      progress.visible = true
    }

    private fun presentPendingBankTransferState() {
      expiry.text = context.getString(R.string.MySupportPreference__payment_pending)
      progress.visible = true
    }

    private fun presentInProgressState() {
      expiry.text = context.getString(R.string.MySupportPreference__processing_transaction)
      progress.visible = true
    }

    private fun presentFailureState(model: Model) {
      if (model.isPaymentFailure || model.subscriberRequiresCancel) {
        presentPaymentFailureState(model)
      } else {
        presentRedemptionFailureState(model)
      }
    }

    private fun presentPaymentFailureState(model: Model) {
      val contactString = context.getString(R.string.MySupportPreference__please_contact_support)

      expiry.text = SpanUtil.clickSubstring(
        context.getString(R.string.DonationsErrors__error_processing_payment_s, contactString),
        contactString,
        {
          model.onContactSupport()
        },
        ContextCompat.getColor(context, R.color.signal_accent_primary)
      )
      progress.visible = false
    }

    private fun presentRedemptionFailureState(model: Model) {
      val contactString = context.getString(R.string.MySupportPreference__please_contact_support)

      expiry.text = SpanUtil.clickSubstring(
        context.getString(R.string.MySupportPreference__couldnt_add_badge_s, contactString),
        contactString,
        {
          model.onContactSupport()
        },
        ContextCompat.getColor(context, R.color.signal_accent_primary)
      )
      progress.visible = false
    }
  }

  fun register(adapter: MappingAdapter) {
    adapter.registerFactory(Model::class.java, BindingFactory(::ViewHolder, MySupportPreferenceBinding::inflate))
  }
}
