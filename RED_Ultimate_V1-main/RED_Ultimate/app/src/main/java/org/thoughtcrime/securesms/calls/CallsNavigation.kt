/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.calls

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import org.signal.core.ui.navigation.TransitionSpecs
import com.red.sovereign.MainNavigator
import com.red.sovereign.calls.links.EditCallLinkNameScreen
import com.red.sovereign.calls.links.details.CallLinkDetailsScreen
import com.red.sovereign.main.EmptyDetailScreen
import com.red.sovereign.main.MainNavigationDetailLocation

fun EntryProviderScope<NavKey>.callsNavEntries(isSplitPane: Boolean) {
  entry<MainNavigationDetailLocation.Empty> {
    NoCallSelectedEntry()
  }

  entry<MainNavigationDetailLocation.CallLinkDetails>(
    metadata = if (isSplitPane) TransitionSpecs.None.metadata else emptyMap()
  ) { route ->
    CallLinkDetailsEntry(route)
  }

  entry<MainNavigationDetailLocation.Calls.CallLinks.EditCallLinkName> { route ->
    EditCallLinkNameEntry(route)
  }
}

@Composable
private fun NoCallSelectedEntry() {
  EmptyDetailScreen()
}

@Composable
private fun CallLinkDetailsEntry(route: MainNavigationDetailLocation.CallLinkDetails) {
  informNavigatorWeAreReady()

  CallLinkDetailsScreen(roomId = route.callLinkRoomId)
}

@Composable
private fun EditCallLinkNameEntry(route: MainNavigationDetailLocation.Calls.CallLinks.EditCallLinkName) {
  informNavigatorWeAreReady()

  EditCallLinkNameScreen(
    roomId = route.callLinkRoomId,
    initialName = route.currentName
  )
}

@Composable
private fun informNavigatorWeAreReady() {
  val navigator = LocalActivity.current as? MainNavigator.NavigatorProvider
  LaunchedEffect(navigator) {
    navigator?.onFirstRender()
  }
}
