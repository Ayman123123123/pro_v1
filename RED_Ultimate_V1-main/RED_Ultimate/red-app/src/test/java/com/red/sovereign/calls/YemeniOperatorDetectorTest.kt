package com.red.sovereign.calls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class YemeniOperatorDetectorTest {
    @Test fun `mobile 77 يمن موبايل with +967 prefix`() {
        val info = YemeniOperatorDetector.getOperatorInfo("+967773456789")
        assertNotNull(info)
        assertEquals("يمن موبايل", info!!.name)
    }

    @Test fun `mobile 73 سبأفون without prefix`() {
        assertEquals("يو (YOU)", YemeniOperatorDetector.getOperatorInfo("733456789")!!.name)
    }

    @Test fun `mobile 71 Y(واي) with 00967 prefix`() {
        assertEquals("سبأفون", YemeniOperatorDetector.getOperatorInfo("00967712345678")!!.name)
    }

    @Test fun `mobile 70 يو YOU after stripping leading zero`() {
        assertEquals("واي (Y)", YemeniOperatorDetector.getOperatorInfo("0701234567")!!.name)
    }

    @Test fun `landline 1 هاتف ثابت صنعاء`() {
        assertEquals("هاتف ثابت (صنعاء)", YemeniOperatorDetector.getOperatorInfo("1234567")!!.name)
    }

    @Test fun `landline 2 عدن`() {
        assertEquals("هاتف ثابت (عدن)", YemeniOperatorDetector.getOperatorInfo("2345678")!!.name)
    }

    @Test fun `landline 3 الحديدة`() {
        assertEquals("هاتف ثابت (الحديدة)", YemeniOperatorDetector.getOperatorInfo("3567890")!!.name)
    }

    @Test fun `unknown prefix returns null`() {
        assertNull(YemeniOperatorDetector.getOperatorInfo("9000000"))
    }

    @Test fun `empty returns null`() {
        assertNull(YemeniOperatorDetector.getOperatorInfo(""))
    }

    @Test fun `non-digit returns null after strip`() {
        assertNull(YemeniOperatorDetector.getOperatorInfo("abc"))
    }

    @Test fun `prefix 967 stripped correctly`() {
        assertEquals("يو (YOU)", YemeniOperatorDetector.getOperatorInfo("967733456789")!!.name)
    }

    @Test fun `mixed-format number with spaces and dashes`() {
        val info = YemeniOperatorDetector.getOperatorInfo("+967 77-345-6789")
        assertNotNull(info)
        assertEquals("يمن موبايل", info!!.name)
    }
}
