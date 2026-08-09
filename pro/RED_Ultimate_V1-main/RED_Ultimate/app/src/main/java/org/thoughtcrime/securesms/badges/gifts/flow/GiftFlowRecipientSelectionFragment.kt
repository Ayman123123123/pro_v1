package com.red.sovereign.badges.gifts.flow

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.navigation.fragment.findNavController
import org.signal.core.util.getParcelableArrayListCompat
import com.red.sovereign.R
import com.red.sovereign.contacts.paged.ContactSearchConfiguration
import com.red.sovereign.contacts.paged.ContactSearchKey
import com.red.sovereign.contacts.paged.ContactSearchState
import com.red.sovereign.conversation.mutiselect.forward.MultiselectForwardFragment
import com.red.sovereign.conversation.mutiselect.forward.MultiselectForwardFragmentArgs
import com.red.sovereign.conversation.mutiselect.forward.SearchConfigurationProvider
import com.red.sovereign.database.RecipientTable
import com.red.sovereign.util.activityViewModel
import com.red.sovereign.util.navigation.safeNavigate

/**
 * Allows the user to select a recipient to send a gift to.
 */
class GiftFlowRecipientSelectionFragment : Fragment(R.layout.multiselect_forward_activity), MultiselectForwardFragment.Callback, SearchConfigurationProvider {

  private val viewModel: GiftFlowViewModel by activityViewModel {
    GiftFlowViewModel()
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    if (savedInstanceState == null) {
      childFragmentManager.beginTransaction()
        .replace(
          R.id.fragment_container,
          MultiselectForwardFragment.create(
            MultiselectForwardFragmentArgs(
              multiShareArgs = emptyList(),
              title = R.string.GiftFlowRecipientSelectionFragment__choose_recipient,
              forceDisableAddMessage = true,
              selectSingleRecipient = true
            )
          )
        )
        .commit()
    }
  }

  override fun getSearchConfiguration(fragmentManager: FragmentManager, contactSearchState: ContactSearchState): ContactSearchConfiguration {
    return ContactSearchConfiguration.build {
      query = contactSearchState.query

      if (query.isNullOrEmpty()) {
        addSection(
          ContactSearchConfiguration.Section.Recents(
            includeSelf = false,
            includeHeader = true,
            mode = ContactSearchConfiguration.Section.Recents.Mode.INDIVIDUALS
          )
        )
      }

      addSection(
        ContactSearchConfiguration.Section.Individuals(
          includeSelfMode = RecipientTable.IncludeSelfMode.Exclude,
          transportType = ContactSearchConfiguration.TransportType.PUSH,
          includeHeader = true
        )
      )
    }
  }

  override fun onFinishForwardAction() = Unit

  override fun exitFlow() = Unit

  override fun navigateUp() {
    requireActivity().onBackPressedDispatcher.onBackPressed()
  }

  override fun onSearchInputFocused() = Unit

  override fun setResult(bundle: Bundle) {
    val contacts: List<ContactSearchKey.RecipientSearchKey> = bundle.getParcelableArrayListCompat(MultiselectForwardFragment.RESULT_SELECTION, ContactSearchKey.RecipientSearchKey::class.java)!!

    if (contacts.isNotEmpty()) {
      viewModel.setSelectedContact(contacts.first())
      findNavController().safeNavigate(R.id.action_giftFlowRecipientSelectionFragment_to_giftFlowConfirmationFragment)
    }
  }

  override fun getContainer(): ViewGroup = requireView() as ViewGroup

  override fun getDialogBackgroundColor(): Int = Color.TRANSPARENT
}
