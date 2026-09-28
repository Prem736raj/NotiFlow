package com.premraj.notiflow.util

/**
 * Compatible RFC 4648 Base64 encoder and decoder that runs across all supported Android
 * versions (minSdk 24+) without requiring Android framework mocks in JVM unit tests or
 * calling API 26+ java.util.Base64.
 */
object Base64Compat {
    private const val ENCODE_TABLE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private val DECODE_TABLE = IntArray(128) { -1 }.apply {
        for (i in ENCODE_TABLE.indices) {
            this[ENCODE_TABLE[i].code] = i
        }
    }

    fun encodeToString(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val sb = java.lang.StringBuilder(((bytes.size + 2) / 3) * 4)
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i++].toInt() and 0xFF
            val b1 = if (i < bytes.size) bytes[i++].toInt() and 0xFF else -1
            val b2 = if (i < bytes.size) bytes[i++].toInt() and 0xFF else -1

            val c0 = b0 ushr 2
            val c1 = ((b0 and 3) shl 4) or (if (b1 != -1) b1 ushr 4 else 0)
            val c2 = if (b1 != -1) ((b1 and 0x0F) shl 2) or (if (b2 != -1) b2 ushr 6 else 0) else -1
            val c3 = if (b2 != -1) b2 and 0x3F else -1

            sb.append(ENCODE_TABLE[c0])
            sb.append(ENCODE_TABLE[c1])
            sb.append(if (c2 != -1) ENCODE_TABLE[c2] else '=')
            sb.append(if (c3 != -1) ENCODE_TABLE[c3] else '=')
        }
        return sb.toString()
    }

    fun decode(str: String): ByteArray {
        val clean = str.filter { !it.isWhitespace() }
        if (clean.isEmpty()) return ByteArray(0)
        require(clean.length % 4 == 0) { "Invalid Base64 length: ${clean.length}" }

        var pad = 0
        if (clean.endsWith("==")) pad = 2
        else if (clean.endsWith("=")) pad = 1

        val outLen = (clean.length * 3 / 4) - pad
        val out = ByteArray(outLen)
        var inIdx = 0
        var outIdx = 0
        while (inIdx < clean.length) {
            val c0 = clean[inIdx++].code
            val c1 = clean[inIdx++].code
            val c2 = clean[inIdx++].code
            val c3 = clean[inIdx++].code

            val v0 = if (c0 in 0..127) DECODE_TABLE[c0] else -1
            val v1 = if (c1 in 0..127) DECODE_TABLE[c1] else -1
            val v2 = if (c2 == '='.code) 0 else if (c2 in 0..127) DECODE_TABLE[c2] else -1
            val v3 = if (c3 == '='.code) 0 else if (c3 in 0..127) DECODE_TABLE[c3] else -1

            require(v0 != -1 && v1 != -1 && (v2 != -1 || c2 == '='.code) && (v3 != -1 || c3 == '='.code)) {
                "Invalid Base64 character"
            }

            val b24 = (v0 shl 18) or (v1 shl 12) or (v2 shl 6) or v3
            if (outIdx < outLen) out[outIdx++] = ((b24 ushr 16) and 0xFF).toByte()
            if (outIdx < outLen) out[outIdx++] = ((b24 ushr 8) and 0xFF).toByte()
            if (outIdx < outLen) out[outIdx++] = (b24 and 0xFF).toByte()
        }
        return out
    }
}
