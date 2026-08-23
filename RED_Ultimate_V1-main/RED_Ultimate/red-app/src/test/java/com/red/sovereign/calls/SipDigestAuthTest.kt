package com.red.sovereign.calls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SipDigestAuthTest {

    @Test
    fun `builds RFC compatible MD5 qop auth authorization`() {
        val challenge = SipDigestAuth.parseChallenge(
            "Digest realm=\"testrealm@host.com\", qop=\"auth\", nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\", opaque=\"5ccc069c403ebaf9f0171e9517f40e41\""
        )
        assertNotNull(challenge)

        val authorization = SipDigestAuth.buildAuthorization(
            method = "GET",
            digestUri = "/dir/index.html",
            username = "Mufasa",
            password = "Circle Of Life",
            challenge = challenge!!,
            nonceCount = 1,
            cnonce = "0a4f113b",
        )

        assertNotNull(authorization)
        assertEquals("00000001", authorization!!.nonceCount)
        assertTrue(authorization.value.contains("response=\"6629fae49393a05397450978507c4ef1\""))
        assertTrue(authorization.value.contains("qop=auth"))
        assertTrue(authorization.value.contains("nc=00000001"))
        assertTrue(authorization.value.contains("cnonce=\"0a4f113b\""))
        assertTrue(authorization.value.contains("opaque=\"5ccc069c403ebaf9f0171e9517f40e41\""))
    }

    @Test
    fun `keeps compatibility with an MD5 challenge without qop`() {
        val challenge = SipDigestAuth.parseChallenge(
            "Digest realm=\"asterisk\", nonce=\"nonce-1\", algorithm=MD5"
        )
        assertNotNull(challenge)

        val authorization = SipDigestAuth.buildAuthorization(
            method = "REGISTER",
            digestUri = "sip:pbx.local",
            username = "red-webrtc-client",
            password = "secret",
            challenge = challenge!!,
            cnonce = "ignored-for-legacy",
        )

        assertNotNull(authorization)
        assertNull(authorization!!.nonceCount)
        assertFalse(authorization.value.contains("qop="))
        assertFalse(authorization.value.contains("cnonce="))
    }

    @Test
    fun `rejects a challenge that only advertises unsupported qop`() {
        val challenge = SipDigestAuth.parseChallenge(
            "Digest realm=\"asterisk\", nonce=\"nonce-1\", qop=\"auth-int\""
        )
        assertNotNull(challenge)

        val authorization = SipDigestAuth.buildAuthorization(
            method = "REGISTER",
            digestUri = "sip:pbx.local",
            username = "red-webrtc-client",
            password = "secret",
            challenge = challenge!!,
            cnonce = "unused",
        )

        assertNull(authorization)
        assertEquals("auth-int", challenge.qop)
    }
}
