/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.recipients.ui.findby

import androidx.annotation.WorkerThread
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import com.red.sovereign.profiles.manage.UsernameRepository
import com.red.sovereign.recipients.PhoneNumber
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientRepository
import com.red.sovereign.registration.ui.countrycode.Country
import com.red.sovereign.util.UsernameUtil

class FindByViewModel(
  mode: FindByMode
) : ViewModel() {

  private val internalState = mutableStateOf(
    FindByState.startingState(self = Recipient.self(), mode = mode)
  )

  val state: State<FindByState> = internalState

  fun onUserEntryChanged(userEntry: String) {
    val cleansed = if (state.value.mode == FindByMode.PHONE_NUMBER) {
      userEntry.filter { it.isDigit() }
    } else {
      userEntry
    }

    internalState.value = state.value.copy(userEntry = cleansed)
  }

  fun onCountrySelected(country: Country) {
    internalState.value = state.value.copy(selectedCountry = country)
  }

  suspend fun onNextClicked(): FindByResult {
    internalState.value = state.value.copy(isLookupInProgress = true)
    val findByResult = viewModelScope.async(context = Dispatchers.IO) {
      if (state.value.mode == FindByMode.USERNAME) {
        performUsernameLookup()
      } else {
        performPhoneLookup()
      }
    }.await()

    internalState.value = state.value.copy(isLookupInProgress = false)
    return findByResult
  }

  @WorkerThread
  private fun performUsernameLookup(): FindByResult {
    val username = state.value.userEntry.trim()

    if (!UsernameUtil.isValidUsernameForSearch(username)) {
      return FindByResult.InvalidEntry
    }

    return when (val result = UsernameRepository.fetchAciForUsername(usernameString = username.removePrefix("@"))) {
      UsernameRepository.UsernameAciFetchResult.NetworkError -> FindByResult.NetworkError
      UsernameRepository.UsernameAciFetchResult.NotFound -> FindByResult.NotFound()
      is UsernameRepository.UsernameAciFetchResult.Success -> FindByResult.Success(Recipient.externalUsername(result.aci, username).id)
    }
  }

  private suspend fun performPhoneLookup(): FindByResult {
    val stateSnapshot = state.value
    val countryCode = stateSnapshot.selectedCountry.countryCode
    val nationalNumber = stateSnapshot.userEntry.removePrefix(countryCode.toString())

    val e164 = "+$countryCode$nationalNumber"

    return when (val result = RecipientRepository.lookup(PhoneNumber(e164))) {
      is RecipientRepository.PhoneLookupResult.InvalidPhone -> FindByResult.InvalidEntry
      is RecipientRepository.PhoneLookupResult.NotFound -> FindByResult.NotFound()
      is RecipientRepository.PhoneLookupResult.Found -> FindByResult.Success(result.recipient.id)
      is RecipientRepository.LookupResult.NetworkError -> FindByResult.NetworkError
    }
  }

  fun filterCountries(filterBy: String) {
    if (filterBy.isEmpty()) {
      internalState.value = state.value.copy(
        query = filterBy,
        filteredCountries = emptyList()
      )
    } else {
      internalState.value = state.value.copy(
        query = filterBy,
        filteredCountries = state.value.supportedCountries.filter { country: Country ->
          country.name.contains(filterBy, ignoreCase = true) ||
            country.countryCode.toString().contains(filterBy.removePrefix("+")) ||
            (filterBy.equals("usa", ignoreCase = true) && country.name.equals("United States", ignoreCase = true))
        }
      )
    }
  }
}
