package com.premraj.notiflow.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.security.SecureRandom

class Base64CompatTest {

    @Test
    fun testRfc4648StandardVectors() {
        val testCases = listOf(
            "" to "",
            "f" to "Zg==",
            "fo" to "Zm8=",
            "foo" to "Zm9v",
            "foob" to "Zm9vYg==",
            "fooba" to "Zm9vYmE=",
            "foobar" to "Zm9vYmFy"
        )

        for ((plain, expectedBase64) in testCases) {
            val encoded = Base64Compat.encodeToString(plain.toByteArray(Charsets.UTF_8))
            assertEquals("Encoding failed for '$plain'", expectedBase64, encoded)

            val decoded = Base64Compat.decode(expectedBase64)
            assertEquals("Decoding failed for '$expectedBase64'", plain, String(decoded, Charsets.UTF_8))
        }
    }

    @Test
    fun testDecodeWithWhitespace() {
        val encoded = "  Zm9v\n\r YmFy \n"
        val decoded = Base64Compat.decode(encoded)
        assertEquals("foobar", String(decoded, Charsets.UTF_8))
    }

    @Test
    fun testRandomByteRoundtrip() {
        val random = SecureRandom()
        for (size in 0..128) {
            val original = ByteArray(size)
            random.nextBytes(original)

            val encoded = Base64Compat.encodeToString(original)
            val decoded = Base64Compat.decode(encoded)
            assertArrayEquals("Roundtrip failed for size $size", original, decoded)
        }
    }

    @Test
    fun testInvalidLengthThrows() {
        try {
            Base64Compat.decode("abc")
            fail("Expected IllegalArgumentException for length 3")
        } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun testInvalidCharThrows() {
        try {
            Base64Compat.decode("Zm9!")
            fail("Expected IllegalArgumentException for invalid character '!'")
        } catch (_: IllegalArgumentException) {}
    }
}
