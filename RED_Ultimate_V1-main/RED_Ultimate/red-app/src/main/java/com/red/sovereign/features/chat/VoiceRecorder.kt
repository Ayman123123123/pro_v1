package com.red.sovereign.features.chat

/**
 * Compatibility alias for the old chat package voice recorder.
 *
 * The active implementation lives in `com.red.sovereign.core.utils.VoiceRecorder`
 * with safer error handling. Keeping this alias preserves older imports from
 * branch `arena/019fe4dd-pro-v1` without duplicating recorder logic.
 */
typealias VoiceRecorder = com.red.sovereign.core.utils.VoiceRecorder
