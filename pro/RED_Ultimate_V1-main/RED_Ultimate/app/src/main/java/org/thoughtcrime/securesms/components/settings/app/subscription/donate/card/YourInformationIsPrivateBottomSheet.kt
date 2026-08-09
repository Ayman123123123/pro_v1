package com.red.sovereign.components.settings.app.subscription.donate.card

import org.signal.core.util.dp
import com.red.sovereign.R
import com.red.sovereign.components.settings.DSLConfiguration
import com.red.sovereign.components.settings.DSLSettingsAdapter
import com.red.sovereign.components.settings.DSLSettingsBottomSheetFragment
import com.red.sovereign.components.settings.DSLSettingsText
import com.red.sovereign.components.settings.configure
import org.signal.core.ui.R as CoreUiR

/**
 * Displays information about how RED keeps card details private and how
 * RED does not link donation information to your RED account.
 */
class YourInformationIsPrivateBottomSheet : DSLSettingsBottomSheetFragment() {
  override fun bindAdapter(adapter: DSLSettingsAdapter) {
    adapter.submitList(getConfiguration().toMappingModelList())
  }

  private fun getConfiguration(): DSLConfiguration {
    return configure {
      space(10.dp)

      noPadTextPref(
        title = DSLSettingsText.from(
          R.string.YourInformationIsPrivateBottomSheet__your_information_is_private,
          DSLSettingsText.CenterModifier,
          DSLSettingsText.TextAppearanceModifier(CoreUiR.style.RED_Text_HeadlineMedium)
        )
      )

      space(24.dp)

      noPadTextPref(
        title = DSLSettingsText.from(
          R.string.YourInformationIsPrivateBottomSheet__signal_does_not_collect,
          DSLSettingsText.BodyLargeModifier
        )
      )

      space(24.dp)

      noPadTextPref(
        title = DSLSettingsText.from(
          R.string.YourInformationIsPrivateBottomSheet__we_use_stripe,
          DSLSettingsText.BodyLargeModifier
        )
      )

      space(24.dp)

      noPadTextPref(
        title = DSLSettingsText.from(
          R.string.YourInformationIsPrivateBottomSheet__signal_does_not_and_cannot,
          DSLSettingsText.BodyLargeModifier
        )
      )

      space(24.dp)

      noPadTextPref(
        title = DSLSettingsText.from(
          R.string.YourInformationIsPrivateBottomSheet__thank_you,
          DSLSettingsText.BodyLargeModifier
        )
      )

      space(56.dp)
    }
  }
}
