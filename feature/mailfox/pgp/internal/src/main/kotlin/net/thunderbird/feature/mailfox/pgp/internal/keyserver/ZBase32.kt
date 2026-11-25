package net.thunderbird.feature.mailfox.pgp.internal.keyserver

/**
 * z-base-32 encoding as used by the OpenPGP Web Key Directory (WKD).
 */
internal object ZBase32 {
    private const val ALPHABET = "ybndrfg8ejkmcpqxot1uwisza345h769"
    private const val BITS_PER_CHAR = 5
    private const val BYTE_MASK = 0xFF
    private const val CHAR_MASK = 0x1F
    private const val BITS_PER_BYTE = 8

    fun encode(data: ByteArray): String {
        val result = StringBuilder()
        var buffer = 0
        var bitCount = 0
        for (byte in data) {
            buffer = (buffer shl BITS_PER_BYTE) or (byte.toInt() and BYTE_MASK)
            bitCount += BITS_PER_BYTE
            while (bitCount >= BITS_PER_CHAR) {
                result.append(ALPHABET[(buffer shr (bitCount - BITS_PER_CHAR)) and CHAR_MASK])
                bitCount -= BITS_PER_CHAR
            }
        }
        if (bitCount > 0) {
            result.append(ALPHABET[(buffer shl (BITS_PER_CHAR - bitCount)) and CHAR_MASK])
        }
        return result.toString()
    }
}
