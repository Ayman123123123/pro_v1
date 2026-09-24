package com.red.sovereign.stories.settings.connections

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import com.red.sovereign.R
import com.red.sovereign.components.ViewBinderDelegate
import com.red.sovereign.components.WrapperDialogFragment
import com.red.sovereign.contacts.paged.ContactSearchAdapter
import com.red.sovereign.contacts.paged.ContactSearchConfiguration
import com.red.sovereign.contacts.paged.ContactSearchPagedDataSourceRepository
import com.red.sovereign.contacts.paged.ContactSearchRepository
import com.red.sovereign.contacts.paged.ContactSearchViewModel
import com.red.sovereign.database.RecipientTable
import com.red.sovereign.databinding.ViewAllREDConnectionsFragmentBinding
import com.red.sovereign.groups.SelectionLimits
import com.red.sovereign.search.SearchRepository
import com.red.sovereign.util.SystemWindowInsetsSetter

class ViewAllREDConnectionsFragment : Fragment(R.layout.view_all_signal_connections_fragment) {

  private val binding by ViewBinderDelegate(ViewAllREDConnectionsFragmentBinding::bind)

  private val contactSearchViewModel: ContactSearchViewModel by viewModels {
    ContactSearchViewModel.Factory(
      selectionLimits = SelectionLimits(0, 0),
      isMultiSelect = false,
      repository = ContactSearchRepository(),
      performSafetyNumberChecks = false,
      arbitraryRepository = null,
      searchRepository = SearchRepository(requireContext().getString(R.string.note_to_self)),
      contactSearchPagedDataSourceRepository = ContactSearchPagedDataSourceRepository(requireContext())
    )
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.toolbar.setNavigationOnClickListener {
      requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    binding.toolbar.updateLayoutParams { height = ViewGroup.LayoutParams.WRAP_CONTENT }
    SystemWindowInsetsSetter.attach(binding.toolbar, viewLifecycleOwner, WindowInsetsCompat.Type.statusBars())
    SystemWindowInsetsSetter.attach(binding.recycler, viewLifecycleOwner, WindowInsetsCompat.Type.navigationBars())

    binding.recycler.bind(
      viewModel = contactSearchViewModel,
      fragmentManager = childFragmentManager,
      displayOptions = ContactSearchAdapter.DisplayOptions(
        displayCheckBox = false,
        displaySecondaryInformation = ContactSearchAdapter.DisplaySecondaryInformation.NEVER
      ),
      mapStateToConfiguration = { getConfiguration() }
    )
  }

  private fun getConfiguration(): ContactSearchConfiguration {
    return ContactSearchConfiguration.build {
      addSection(
        ContactSearchConfiguration.Section.Individuals(
          includeHeader = false,
          includeSelfMode = RecipientTable.IncludeSelfMode.Exclude,
          includeLetterHeaders = true,
          transportType = ContactSearchConfiguration.TransportType.PUSH
        )
      )
    }
  }

  class Dialog : WrapperDialogFragment() {
    override fun getWrappedFragment(): Fragment {
      return ViewAllREDConnectionsFragment()
    }

    companion object {
      fun show(fragmentManager: FragmentManager) {
        Dialog().show(fragmentManager, null)
      }
    }
  }
}
