package com.red.sovereign.conversation.mutiselect.forward

import androidx.compose.runtime.Composable
import androidx.fragment.app.FragmentManager
import kotlinx.collections.immutable.persistentHashMapOf
import com.red.sovereign.contacts.paged.ArbitraryRepository
import com.red.sovereign.contacts.paged.ContactSearchConfiguration
import com.red.sovereign.contacts.paged.ContactSearchState
import com.red.sovereign.util.adapter.mapping.compose.MappingEntryProvider

/**
 * Allows a parent of MultiselectForwardFragment to provide a custom search page configuration.
 */
interface SearchConfigurationProvider {
  /**
   * @param fragmentManager    The child fragment manager of the MultiselectForwardFragment, to launch actions in to.
   * @param contactSearchState The search state, to build the configuration from.
   *
   * @return A configuration or null. Returning null will result in MultiselectForwardFragment using it's default configuration.
   */
  fun getSearchConfiguration(fragmentManager: FragmentManager, contactSearchState: ContactSearchState): ContactSearchConfiguration? = null

  /**
   * @return An ArbitraryRepository or null. Returning null will result in not being able to use the Arbitrary section, keys, or data.
   */
  fun getArbitraryRepository(): ArbitraryRepository? = null

  /**
   * @return a mapping of additional entries. Recommended to put your arbitrary stuff here.
   */
  @Composable
  fun getAdditionalEntries(): MappingEntryProvider<Any> = persistentHashMapOf()
}
