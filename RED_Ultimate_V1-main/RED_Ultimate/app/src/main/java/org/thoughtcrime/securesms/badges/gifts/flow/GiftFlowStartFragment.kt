package com.red.sovereign.badges.gifts.flow

import android.view.View
import androidx.navigation.fragment.findNavController
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import org.signal.core.util.DimensionUnit
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.donations.InAppPaymentType
import com.red.sovereign.R
import com.red.sovereign.components.settings.DSLConfiguration
import com.red.sovereign.components.settings.DSLSettingsFragment
import com.red.sovereign.components.settings.DSLSettingsText
import com.red.sovereign.components.settings.app.subscription.ui.CurrencySelection
import com.red.sovereign.components.settings.app.subscription.ui.NetworkFailure
import com.red.sovereign.components.settings.configure
import com.red.sovereign.components.settings.models.IndeterminateLoadingCircle
import com.red.sovereign.components.settings.models.SplashImage
import com.red.sovereign.util.ViewUtil
import com.red.sovereign.util.activityViewModel
import com.red.sovereign.util.adapter.mapping.MappingAdapter
import com.red.sovereign.util.navigation.safeNavigate
import java.util.concurrent.TimeUnit
import org.signal.core.ui.R as CoreUiR

/**
 * Landing fragment for sending gifts.
 */
class GiftFlowStartFragment : DSLSettingsFragment(
  layoutId = R.layout.gift_flow_start_fragment
) {

  private val viewModel: GiftFlowViewModel by activityViewModel {
    GiftFlowViewModel()
  }

  private val lifecycleDisposable = LifecycleDisposable()

  override fun bindAdapter(adapter: MappingAdapter) {
    CurrencySelection.register(adapter)
    GiftRowItem.register(adapter)
    NetworkFailure.register(adapter)
    IndeterminateLoadingCircle.register(adapter)
    SplashImage.register(adapter)

    val next = requireView().findViewById<View>(R.id.next)
    next.setOnClickListener {
      findNavController().safeNavigate(R.id.action_giftFlowStartFragment_to_giftFlowRecipientSelectionFragment)
    }

    lifecycleDisposable.bindTo(viewLifecycleOwner)
    lifecycleDisposable += viewModel.state.observeOn(AndroidSchedulers.mainThread()).subscribe { state ->
      next.isEnabled = state.stage == GiftFlowState.Stage.READY

      adapter.submitList(getConfiguration(state).toMappingModelList())
    }
  }

  override fun onResume() {
    super.onResume()
    ViewUtil.hideKeyboard(requireContext(), requireView())
  }

  private fun getConfiguration(state: GiftFlowState): DSLConfiguration {
    return configure {
      customPref(
        SplashImage.Model(
          R.drawable.ic_gift_chat
        )
      )

      noPadTextPref(
        title = DSLSettingsText.from(
          R.string.GiftFlowStartFragment__donate_for_a_friend,
          DSLSettingsText.CenterModifier,
          DSLSettingsText.TextAppearanceModifier(CoreUiR.style.RED_Text_Headline)
        )
      )

      space(DimensionUnit.DP.toPixels(16f).toInt())

      val days = state.giftBadge?.duration?.let { TimeUnit.MILLISECONDS.toDays(it) } ?: 60L
      noPadTextPref(
        title = DSLSettingsText.from(resources.getQuantityString(R.plurals.GiftFlowStartFragment__support_signal_by, days.toInt(), days), DSLSettingsText.CenterModifier)
      )

      space(DimensionUnit.DP.toPixels(16f).toInt())

      customPref(
        CurrencySelection.Model(
          selectedCurrency = state.currency,
          isEnabled = state.stage == GiftFlowState.Stage.READY,
          onClick = {
            val action = GiftFlowStartFragmentDirections.actionGiftFlowStartFragmentToSetCurrencyFragment(InAppPaymentType.ONE_TIME_GIFT, viewModel.getSupportedCurrencyCodes().toTypedArray())
            findNavController().safeNavigate(action)
          }
        )
      )

      @Suppress("CascadeIf")
      if (state.stage == GiftFlowState.Stage.FAILURE) {
        customPref(
          NetworkFailure.Model(
            onRetryClick = {
              viewModel.retry()
            }
          )
        )
      } else if (state.stage == GiftFlowState.Stage.INIT) {
        customPref(IndeterminateLoadingCircle)
      } else if (state.giftBadge != null) {
        state.giftPrices[state.currency]?.let {
          customPref(
            GiftRowItem.Model(
              giftBadge = state.giftBadge,
              price = it
            )
          )
        }
      }
    }
  }
}
