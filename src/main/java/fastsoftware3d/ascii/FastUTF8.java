package fastsoftware3d.ascii;

/**
 * High-performance, zero-allocation UTF-8 decoding and encoding.
 */
public final class FastUTF8 {

    private FastUTF8() {}

    /**
     * Decodes a UTF-8 code point from the buffer.
     * @param outCodePoint A single-element array to receive the decoded code point.
     * @return The number of bytes consumed (1-4), or -1 if invalid.
     */
    public static int decodeCodePoint(byte[] buffer, int offset, int length, int[] outCodePoint) {
        if (FastASCII.Native.isAvailable() && length >= 1024) { // Only use JNI for bulk, for single char it's too slow
            // Normally JNI would decode a whole string, but for API completeness:
            // FastASCII.Native.decodeUtf8(buffer, offset, length, outCodePoint);
        }

        if (length <= 0) return -1;
        int b1 = buffer[offset] & 0xFF;

        if (b1 <= 0x7F) {
            outCodePoint[0] = b1;
            return 1;
        }

        if ((b1 >> 5) == 0x06) {
            if (length < 2) return -1;
            int b2 = buffer[offset + 1] & 0xFF;
            if ((b2 >> 6) != 0x02) return -1;
            outCodePoint[0] = ((b1 & 0x1F) << 6) | (b2 & 0x3F);
            return 2;
        }

        if ((b1 >> 4) == 0x0E) {
            if (length < 3) return -1;
            int b2 = buffer[offset + 1] & 0xFF;
            int b3 = buffer[offset + 2] & 0xFF;
            if ((b2 >> 6) != 0x02 || (b3 >> 6) != 0x02) return -1;
            outCodePoint[0] = ((b1 & 0x0F) << 12) | ((b2 & 0x3F) << 6) | (b3 & 0x3F);
            return 3;
        }

        if ((b1 >> 3) == 0x1E) {
            if (length < 4) return -1;
            int b2 = buffer[offset + 1] & 0xFF;
            int b3 = buffer[offset + 2] & 0xFF;
            int b4 = buffer[offset + 3] & 0xFF;
            if ((b2 >> 6) != 0x02 || (b3 >> 6) != 0x02 || (b4 >> 6) != 0x02) return -1;
            outCodePoint[0] = ((b1 & 0x07) << 18) | ((b2 & 0x3F) << 12) | ((b3 & 0x3F) << 6) | (b4 & 0x3F);
            return 4;
        }

        return -1;
    }

    public static int encodeCodePoint(byte[] buffer, int offset, int codePoint) {
        return FastASCIIWriter.writeUtf8(buffer, offset, codePoint);
    }

    public static boolean validate(byte[] buffer, int offset, int length) {
        if (FastASCII.Native.isAvailable() && length >= 1024) {
            return FastASCII.Native.validateUtf8(buffer, offset, length) == 1;
        }

        int end = offset + length;
        int i = offset;
        int[] cp = new int[1];
        while (i < end) {
            int len = decodeCodePoint(buffer, i, end - i, cp);
            if (len == -1) return false;
            i += len;
        }
        return true;
    }
}
