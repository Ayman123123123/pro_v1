/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.stories

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.fragment.compose.AndroidFragment
import androidx.fragment.compose.rememberFragmentState
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import org.signal.core.ui.navigation.TransitionSpecs
import com.red.sovereign.MainNavigator
import com.red.sovereign.compose.FragmentBackHandler
import com.red.sovereign.compose.FragmentBackPressedState
import com.red.sovereign.main.EmptyDetailScreen
import com.red.sovereign.main.MainNavigationDetailLocation
import com.red.sovereign.stories.archive.StoryArchiveScreen
import com.red.sovereign.stories.my.MyStoriesFragment
import com.red.sovereign.stories.settings.StorySettingsNavHostFragment

fun EntryProviderScope<NavKey>.storiesNavEntries() {
  entry<MainNavigationDetailLocation.Empty> {
    NoStorySelectedEntry()
  }

  entry<MainNavigationDetailLocation.Stories.Archive>(
    metadata = TransitionSpecs.None.metadata
  ) {
    StoryArchiveEntry()
  }

  entry<MainNavigationDetailLocation.Stories.MyStories>(
    metadata = TransitionSpecs.None.metadata
  ) {
    MyStoriesEntry()
  }

  entry<MainNavigationDetailLocation.Stories.PrivacySettings>(
    metadata = TransitionSpecs.None.metadata
  ) {
    StoryPrivacySettingsEntry()
  }
}

@Composable
private fun NoStorySelectedEntry() {
  EmptyDetailScreen()
}

@Composable
private fun StoryArchiveEntry() {
  val backPressedDispatcherOwner = LocalOnBackPressedDispatcherOwner.current
  informNavigatorWeAreReady()

  StoryArchiveScreen(
    onNavigationClick = { backPressedDispatcherOwner?.onBackPressedDispatcher?.onBackPressed() }
  )
}

@Composable
private fun MyStoriesEntry() {
  val fragmentState = key(MainNavigationDetailLocation.Stories.MyStories) { rememberFragmentState() }
  val backPressedState = remember { FragmentBackPressedState() }
  FragmentBackHandler(backPressedState)

  informNavigatorWeAreReady()

  AndroidFragment(
    clazz = MyStoriesFragment::class.java,
    fragmentState = fragmentState,
    modifier = androidx.compose.ui.Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .statusBarsPadding()
      .navigationBarsPadding()
  ) { fragment ->
    backPressedState.attach(fragment)
  }
}

@Composable
private fun StoryPrivacySettingsEntry() {
  val fragmentState = key(MainNavigationDetailLocation.Stories.PrivacySettings) { rememberFragmentState() }
  val backPressedState = remember { FragmentBackPressedState() }
  FragmentBackHandler(backPressedState)

  informNavigatorWeAreReady()

  AndroidFragment(
    clazz = StorySettingsNavHostFragment::class.java,
    fragmentState = fragmentState,
    modifier = androidx.compose.ui.Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .statusBarsPadding()
      .navigationBarsPadding()
  ) { fragment ->
    backPressedState.attach(fragment)
  }
}

@Composable
private fun informNavigatorWeAreReady() {
  val navigator = LocalActivity.current as? MainNavigator.NavigatorProvider
  LaunchedEffect(navigator) {
    navigator?.onFirstRender()
  }
}
