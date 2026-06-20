package fastsoftware3d.ascii;

/**
 * High-performance, zero-allocation scanner for ASCII bytes.
 */
public final class FastASCIIScanner {

    private FastASCIIScanner() {}

    public static int find(byte[] haystack, int offset, int length, byte needle) {
        if (FastASCII.Native.isAvailable() && length >= 1024) {
            return FastASCII.Native.findByte(haystack, offset, length, needle);
        }

        int end = offset + length;
        for (int i = offset; i < end; i++) {
            if (haystack[i] == needle) return i;
        }
        return -1;
    }

    public static int find(byte[] haystack, int offset, int length, byte[] needle) {
        if (FastASCII.Native.isAvailable() && length >= 1024) {
            return FastASCII.Native.findSubstring(haystack, offset, length, needle);
        }

        if (needle.length == 0) return offset;
        if (needle.length > length) return -1;

        int end = offset + length - needle.length;
        byte first = needle[0];
        for (int i = offset; i <= end; i++) {
            if (haystack[i] == first) {
                boolean match = true;
                for (int j = 1; j < needle.length; j++) {
                    if (haystack[i + j] != needle[j]) {
                        match = false;
                        break;
                    }
                }
                if (match) return i;
            }
        }
        return -1;
    }

    public static int skipWhitespace(byte[] buffer, int offset, int limit) {
        while (offset < limit && FastASCIIChar.isWhitespace(buffer[offset])) {
            offset++;
        }
        return offset;
    }

    public static int skipDigits(byte[] buffer, int offset, int limit) {
        while (offset < limit && FastASCIIChar.isDigit(buffer[offset])) {
            offset++;
        }
        return offset;
    }
}
