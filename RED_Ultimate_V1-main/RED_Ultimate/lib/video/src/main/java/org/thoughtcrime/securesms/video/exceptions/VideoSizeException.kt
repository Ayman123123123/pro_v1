/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.red.sovereign.video.exceptions

import java.io.IOException

/**
 * Exception to denote when video processing has been unable to meet its output file size requirements.
 */
class VideoSizeException(message: String?) : IOException(message)
