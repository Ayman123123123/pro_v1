/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.testing

@Retention(AnnotationRetention.RUNTIME)
annotation class REDFlakyTest(val allowedAttempts: Int = 3)
