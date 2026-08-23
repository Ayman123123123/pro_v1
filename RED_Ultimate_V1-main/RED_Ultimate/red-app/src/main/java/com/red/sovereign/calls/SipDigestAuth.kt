package com.red.sovereign.calls

import java.security.MessageDigest

/**
 * RFC 3261 / RFC 7616 Digest authorization builder used by the small SIP client.
 *
 * The Asterisk endpoint currently challenges with `qop=auth`; the legacy
 * three-field MD5 response is therefore insufficient. This helper deliberately
 * supports only algorithms supplied by the runtime and returns null for an
 * unsupported challenge so the caller can surface a precise SIP error instead
 * of retrying a malformed request.
 */
internal object SipDigestAuth {

    data class Challenge(
        val realm: String,
        val nonce: String,
        val opaque: String? = null,
        val algorithm: String = "MD5",
        val qop: String? = null,
    )

    data class Authorization(
        val value: String,
        val nonceCount: String? = null,
        val cnonce: String? = null,
    )

    fun parseChallenge(headerValue: String?): Challenge? {
        if (headerValue.isNullOrBlank() || !headerValue.trimStart().startsWith("Digest", ignoreCase = true)) {
            return null
        }
        val params = linkedMapOf<String, String>()
        val body = headerValue.trim().removePrefix("Digest").trim()
        val matcher = PARAMETER.findAll(body)
        matcher.forEach { match ->
            val name = match.groupValues[1].lowercase()
            val raw = match.groupValues[2].trim()
            params[name] = if (raw.startsWith('"') && raw.endsWith('"') && raw.length >= 2) {
                raw.substring(1, raw.length - 1)
            } else {
                raw
            }
        }
        val realm = params["realm"] ?: return null
        val nonce = params["nonce"] ?: return null
        return Challenge(
            realm = realm,
            nonce = nonce,
            opaque = params["opaque"],
            algorithm = params["algorithm"]?.takeIf { it.isNotBlank() } ?: "MD5",
            qop = params["qop"],
        )
    }

    fun buildAuthorization(
        method: String,
        digestUri: String,
        username: String,
        password: String,
        challenge: Challenge,
        nonceCount: Int = 1,
        cnonce: String,
    ): Authorization? {
        val algorithm = challenge.algorithm.uppercase()
        val digestAlgorithm = when (algorithm) {
            "MD5", "MD5-SESS" -> "MD5"
            "SHA-256", "SHA-256-SESS" -> "SHA-256"
            "SHA-512-256", "SHA-512-256-SESS" -> "SHA-512/256"
            else -> return null
        }
        val selectedQop = challenge.qop
            ?.split(',')
            ?.map { it.trim().lowercase() }
            ?.firstOrNull { it == "auth" }
        if (!challenge.qop.isNullOrBlank() && selectedQop == null) return null

        val nc = "%08x".format(nonceCount)
        var ha1 = digest(digestAlgorithm, "$username:${challenge.realm}:$password")
        if (algorithm.endsWith("-SESS")) {
            ha1 = digest(digestAlgorithm, "$ha1:${challenge.nonce}:$cnonce")
        }
        val ha2 = digest(digestAlgorithm, "${method.uppercase()}:$digestUri")
        val response = if (selectedQop == "auth") {
            digest(digestAlgorithm, "$ha1:${challenge.nonce}:$nc:$cnonce:auth:$ha2")
        } else {
            digest(digestAlgorithm, "$ha1:${challenge.nonce}:$ha2")
        }

        val fields = mutableListOf(
            "username=\"$username\"",
            "realm=\"${challenge.realm}\"",
            "nonce=\"${challenge.nonce}\"",
            "uri=\"$digestUri\"",
            "response=\"$response\"",
            "algorithm=${challenge.algorithm}",
        )
        challenge.opaque?.takeIf { it.isNotBlank() }?.let { fields += "opaque=\"$it\"" }
        if (selectedQop == "auth") {
            fields += "qop=auth"
            fields += "nc=$nc"
            fields += "cnonce=\"$cnonce\""
        }
        return Authorization(
            value = "Digest ${fields.joinToString(", ")}",
            nonceCount = if (selectedQop == "auth") nc else null,
            cnonce = if (selectedQop == "auth") cnonce else null,
        )
    }

    private fun digest(algorithm: String, value: String): String =
        MessageDigest.getInstance(algorithm)
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private val PARAMETER = Regex("""([A-Za-z][A-Za-z0-9_-]*)\s*=\s*(\"(?:[^\"\\]|\\.)*\"|[^,\s]+)""")
}
