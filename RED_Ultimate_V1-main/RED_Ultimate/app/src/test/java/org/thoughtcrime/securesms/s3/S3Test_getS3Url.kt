/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.s3

import assertk.assertThat
import assertk.assertions.isEqualTo
import okio.IOException
import org.junit.Test

@Suppress("ClassName")
class S3Test_getS3Url {
  @Test
  fun validS3Urls() {
    assertThat(S3.s3Url("/static/heart.png").toString()).isEqualTo("https://updates2.red.local/static/heart.png")
    assertThat(S3.s3Url("/static/heart.png?weee=1").toString()).isEqualTo("https://updates2.red.local/static/heart.png%3Fweee=1")
    assertThat(S3.s3Url("/@red.local").toString()).isEqualTo("https://updates2.red.local/@red.local")
  }

  @Test(expected = IOException::class)
  fun invalid() {
    S3.s3Url("@red.local")
  }

  @Test(expected = IOException::class)
  fun invalidRelative() {
    S3.s3Url("static/heart.png")
  }
}
