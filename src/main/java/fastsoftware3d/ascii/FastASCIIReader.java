package fastsoftware3d.ascii;

/**
 * High-performance, zero-allocation reader for ASCII and UTF-8 bytes.
 */
public final class FastASCIIReader {

    private FastASCIIReader() {}

    /**
     * Parses an unsigned integer from the byte buffer.
     * Optionally falls back to FastASCII.Native if available and length is large.
     */
    public static int parseUInt(byte[] buffer, int start, int end) {
        if (FastASCII.Native.isAvailable() && (end - start) >= 1024) {
            return FastASCII.Native.parseInt(buffer, start, end - start);
        }

        int result = 0;
        for (int i = start; i < end; i++) {
            byte b = buffer[i];
            if (b >= '0' && b <= '9') {
                result = result * 10 + (b - '0');
            } else {
                break;
            }
        }
        return result;
    }

    public static int readInt(byte[] buffer, int start, int end) {
        if (start >= end) return 0;
        boolean negative = false;
        int i = start;
        if (buffer[i] == '-') {
            negative = true;
            i++;
        }
        int result = parseUInt(buffer, i, end);
        return negative ? -result : result;
    }

    /**
     * Finds the index of a byte, returning -1 if not found.
     */
    public static int readUntil(byte[] buffer, int start, int end, byte target) {
        for (int i = start; i < end; i++) {
            if (buffer[i] == target) return i;
        }
        return -1;
    }

    /**
     * Returns the length of the line starting at offset, up to \n or \r\n.
     */
    public static int readLine(byte[] buffer, int offset, int limit) {
        int end = readUntil(buffer, offset, limit, (byte) '\n');
        if (end == -1) return limit - offset;
        if (end > offset && buffer[end - 1] == '\r') {
            return end - offset - 1;
        }
        return end - offset;
    }
}
