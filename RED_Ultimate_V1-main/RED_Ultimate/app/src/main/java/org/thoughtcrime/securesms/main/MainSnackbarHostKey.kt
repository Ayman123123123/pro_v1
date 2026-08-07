/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.main

import com.red.sovereign.components.snackbars.SnackbarHostKey

sealed interface MainSnackbarHostKey : SnackbarHostKey {
  data object Chat : MainSnackbarHostKey
  data object MainChrome : MainSnackbarHostKey
}
