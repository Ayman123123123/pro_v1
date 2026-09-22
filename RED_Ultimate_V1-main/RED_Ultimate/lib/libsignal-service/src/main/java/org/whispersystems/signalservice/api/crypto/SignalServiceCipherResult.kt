package org.whispersystems.signalservice.api.crypto

import org.whispersystems.signalservice.internal.push.Content

/**
 * Represents the output of decrypting a [REDServiceProtos.Envelope] via [REDServiceCipher.decrypt]
 *
 * @param content The [REDServiceProtos.Content] that was decrypted from the envelope.
 * @param metadata The decrypted metadata of the envelope. Represents sender information that may have
 *                 been encrypted with sealed sender.
 */
data class REDServiceCipherResult(
  val content: Content,
  val metadata: EnvelopeMetadata
)
