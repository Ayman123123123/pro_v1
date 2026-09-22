/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.components.compose

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import org.signal.core.ui.compose.IconButtons
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.REDIcons
import com.red.sovereign.R

/**
 * A consistent ActionMode top-bar for dealing with multiselect scenarios.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionModeTopBar(
  title: String,
  onCloseClick: () -> Unit,
  toolbarColor: Color? = null,
  windowInsets: WindowInsets = TopAppBarDefaults.windowInsets
) {
  TopAppBar(
    colors = TopAppBarDefaults.topAppBarColors(
      containerColor = toolbarColor ?: MaterialTheme.colorScheme.surface
    ),
    navigationIcon = {
      IconButtons.IconButton(onClick = onCloseClick) {
        Icon(
          imageVector = REDIcons.X.imageVector,
          contentDescription = stringResource(R.string.CallScreenTopBar__go_back)
        )
      }
    },
    title = {
      Text(text = title)
    },
    windowInsets = windowInsets
  )
}

@PreviewLightDark
@Composable
fun ActionModeTopBarPreview() {
  Previews.Preview {
    ActionModeTopBar(
      title = "1 selected",
      onCloseClick = {}
    )
  }
}
