package com.red.sovereign.calls

/**
 * Perfect-negotiation helpers for the conference/space mesh.
 *
 * Newcomer offers to every existing peer. Existing peers only answer.
 * If two newcomers glare (both offer), the lexicographically smaller
 * RED ID is polite and rolls back to the remote offer.
 */
object MeshNegotiation {
    const val MAX_PEERS = 8

    fun shouldOfferTo(remoteUserId: String, localUserId: String, isNewcomer: Boolean): Boolean {
        if (remoteUserId.isBlank() || remoteUserId == localUserId) return false
        return isNewcomer
    }

    fun shouldAcceptRemoteOffer(localUserId: String, remoteUserId: String, haveLocalOffer: Boolean): Boolean {
        if (!haveLocalOffer) return true
        return localUserId < remoteUserId
    }

    fun canAttach(existingPeerCount: Int, alreadyAttached: Boolean): Boolean =
        alreadyAttached || existingPeerCount < MAX_PEERS

    fun targetOf(payload: Map<String, String>, fallback: String = ""): String =
        payload["targetUserId"].orEmpty().ifBlank { fallback }
}
