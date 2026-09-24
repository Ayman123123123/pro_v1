/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.registration.ui.restore

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import com.red.sovereign.R
import com.red.sovereign.registration.ui.shared.RegistrationScreen

/**
 * Screen showing various restore methods available during quick and manual re-registration.
 */
@Composable
fun SelectRestoreMethodScreen(
  restoreMethods: List<RestoreMethod>,
  onRestoreMethodClicked: (RestoreMethod) -> Unit = {},
  onSkip: () -> Unit = {},
  extraContent: @Composable ColumnScope.() -> Unit = {}
) {
  RegistrationScreen(
    title = stringResource(id = R.string.SelectRestoreMethodFragment__restore_or_transfer_account),
    subtitle = stringResource(id = R.string.SelectRestoreMethodFragment__get_your_signal_account),
    bottomContent = {
      TextButton(
        onClick = onSkip,
        modifier = Modifier.align(Alignment.Center)
      ) {
        Text(text = stringResource(R.string.registration_activity__skip_restore))
      }
    }
  ) {
    for (method in restoreMethods) {
      RestoreRow(
        icon = painterResource(method.iconRes),
        title = stringResource(method.titleRes),
        subtitle = stringResource(method.subtitleRes),
        onRowClick = { onRestoreMethodClicked(method) }
      )
    }

    extraContent()
  }
}

@DayNightPreviews
@Composable
private fun SelectRestoreMethodScreenPreview() {
  Previews.Preview {
    SelectRestoreMethodScreen(listOf(RestoreMethod.FROM_SIGNAL_BACKUPS, RestoreMethod.FROM_OLD_DEVICE, RestoreMethod.FROM_LOCAL_BACKUP_V1))
  }
}
