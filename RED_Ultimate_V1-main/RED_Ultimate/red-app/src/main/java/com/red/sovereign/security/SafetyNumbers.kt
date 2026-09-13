package com.red.sovereign.security

import java.security.MessageDigest

/**
 * 🔑 SafetyNumbers — مطابقة الأرقام الأمنية وأكواد QR بين الأطراف
 *
 * يتيح التأكد من عدم وجود طرف ثالث معترض (Man-in-the-Middle Attack)
 * عبر توليد بصمة رقمية مشتقة من مفاتيح الهوية للطرفين (Identity Keys).
 */
object SafetyNumbers {

    /**
     * توليد متسلسلة أرقام الأمان الصريحة (6 مجموعات من 5 أرقام)
     */
    fun computeFingerprintDigits(myIdentityKeyBytes: ByteArray, contactIdentityKeyBytes: ByteArray): String {
        val sortedKeys = if (compareByteArrays(myIdentityKeyBytes, contactIdentityKeyBytes) <= 0) {
            myIdentityKeyBytes + contactIdentityKeyBytes
        } else {
            contactIdentityKeyBytes + myIdentityKeyBytes
        }

        val digest = MessageDigest.getInstance("SHA-256")
        var hash = digest.digest(sortedKeys)

        // تكرار التجزئة لرفع الأمان
        repeat(5200) {
            hash = digest.digest(hash)
        }

        val sb = StringBuilder()
        for (i in 0 until 30 step 5) {
            val chunk = ((hash[i].toInt() and 0xFF) shl 24) or
                        ((hash[i + 1].toInt() and 0xFF) shl 16) or
                        ((hash[i + 2].toInt() and 0xFF) shl 8) or
                        (hash[i + 3].toInt() and 0xFF)
            val number = (Math.abs(chunk.toLong()) % 100000).toString().padStart(5, '0')
            sb.append(number)
            if (i < 25) sb.append(" ")
        }
        return sb.toString()
    }

    /**
     * نص مشفر صالح للطباعة أو التضمين داخل كود QR لمطابقته
     */
    fun computeQrCodeData(myRedId: String, contactRedId: String, digits: String): String {
        return "sovereign://safety-verify?users=$myRedId,$contactRedId&fp=${digits.replace(" ", "")}"
    }

    private fun compareByteArrays(a: ByteArray, b: ByteArray): Int {
        val minLen = minOf(a.size, b.size)
        for (i in 0 until minLen) {
            val b1 = a[i].toInt() and 0xFF
            val b2 = b[i].toInt() and 0xFF
            if (b1 != b2) return b1 - b2
        }
        return a.size - b.size
    }
}
