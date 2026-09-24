package com.red.sovereign.profiles.manage

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.core.app.ShareCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.findNavController
import org.signal.core.util.Base64
import org.signal.core.util.DimensionUnit
import org.signal.core.util.Util
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.libsignal.usernames.Username
import com.red.sovereign.R
import com.red.sovereign.components.settings.DSLConfiguration
import com.red.sovereign.components.settings.DSLSettingsAdapter
import com.red.sovereign.components.settings.DSLSettingsBottomSheetFragment
import com.red.sovereign.components.settings.DSLSettingsText
import com.red.sovereign.components.settings.configure
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.util.FragmentResultContract
import org.signal.core.ui.R as CoreUiR

/**
 * Allows the user to either share their username directly or to copy it to their clipboard.
 */
class UsernameShareBottomSheet : DSLSettingsBottomSheetFragment() {

  companion object {
    private const val REQUEST_KEY = "copy_username"
  }

  private val lifecycleDisposable = LifecycleDisposable()

  override fun bindAdapter(adapter: DSLSettingsAdapter) {
    CopyButton.register(adapter)
    ShareButton.register(adapter)

    lifecycleDisposable += Recipient.observable(Recipient.self().id).subscribe {
      if (context == null) {
        return@subscribe
      }

      adapter.submitList(getConfiguration(it).toMappingModelList())
    }
  }

  private fun getConfiguration(recipient: Recipient): DSLConfiguration {
    return configure {
      noPadTextPref(
        title = DSLSettingsText.from(
          R.string.UsernameShareBottomSheet__copy_or_share_a_username_link,
          DSLSettingsText.TextAppearanceModifier(CoreUiR.style.RED_Text_BodyMedium),
          DSLSettingsText.CenterModifier,
          DSLSettingsText.ColorModifier(
            ContextCompat.getColor(requireContext(), CoreUiR.color.signal_colorOnSurfaceVariant)
          )
        )
      )

      space(DimensionUnit.DP.toPixels(32f).toInt())

      val username = recipient.username.get()
      customPref(
        CopyButton.Model(
          text = username,
          onClick = {
            copyToClipboard(it)
          }
        )
      )

      space(DimensionUnit.DP.toPixels(20f).toInt())

      customPref(
        CopyButton.Model(
          text = getString(R.string.signal_me_username_url, Base64.encodeUrlSafeWithoutPadding(Username(username).hash)),
          onClick = {
            copyToClipboard(it)
          }
        )
      )

      space(DimensionUnit.DP.toPixels(24f).toInt())

      customPref(
        ShareButton.Model(
          text = getString(R.string.signal_me_username_url, Base64.encodeUrlSafeWithoutPadding(Username(username).hash)),
          onClick = {
            openShareSheet(it.text)
          }
        )
      )

      space(DimensionUnit.DP.toPixels(18f).toInt())
    }
  }

  private fun copyToClipboard(model: CopyButton.Model) {
    Util.copyToClipboard(requireContext(), model.text)
    setFragmentResult(REQUEST_KEY, Bundle().apply { putBoolean(REQUEST_KEY, true) })
    findNavController().popBackStack()
  }

  private fun openShareSheet(charSequence: CharSequence) {
    val mimeType = Intent.normalizeMimeType("text/plain")
    val shareIntent = ShareCompat.IntentBuilder(requireContext())
      .setText(charSequence)
      .setType(mimeType)
      .createChooserIntent()
      .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    try {
      startActivity(shareIntent)
    } catch (e: ActivityNotFoundException) {
      Toast.makeText(requireContext(), R.string.MediaPreviewActivity_cant_find_an_app_able_to_share_this_media, Toast.LENGTH_LONG).show()
    }
  }

  object ResultContract : FragmentResultContract<Boolean>(REQUEST_KEY) {
    override fun getResult(bundle: Bundle): Boolean {
      return bundle.getBoolean(REQUEST_KEY, false)
    }
  }
}
