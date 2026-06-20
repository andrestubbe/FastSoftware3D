package fastsoftware3d.ascii;

public final class FastASCIIWriter {
    private FastASCIIWriter() {}

    private static final byte[] DIGIT_TENS = {
        '0', '0', '0', '0', '0', '0', '0', '0', '0', '0',
        '1', '1', '1', '1', '1', '1', '1', '1', '1', '1',
        '2', '2', '2', '2', '2', '2', '2', '2', '2', '2',
        '3', '3', '3', '3', '3', '3', '3', '3', '3', '3',
        '4', '4', '4', '4', '4', '4', '4', '4', '4', '4',
        '5', '5', '5', '5', '5', '5', '5', '5', '5', '5',
        '6', '6', '6', '6', '6', '6', '6', '6', '6', '6',
        '7', '7', '7', '7', '7', '7', '7', '7', '7', '7',
        '8', '8', '8', '8', '8', '8', '8', '8', '8', '8',
        '9', '9', '9', '9', '9', '9', '9', '9', '9', '9'
    };
    
    private static final byte[] DIGIT_ONES = {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9'
    };

    private static final int[] SIZE_TABLE = { 9, 99, 999, 9999, 99999, 999999, 9999999, 99999999, 999999999, Integer.MAX_VALUE };

    public static int writeInt(byte[] buffer, int offset, int value) {
        if (value == 0) {
            buffer[offset] = '0';
            return 1;
        }
        
        int length = 0;
        for (int i = 0; ; i++) {
            if (value <= SIZE_TABLE[i]) {
                length = i + 1;
                break;
            }
        }
        
        int pos = offset + length - 1;
        int q, r;
        while (value >= 65536) {
            q = value / 100;
            r = value - ((q << 6) + (q << 5) + (q << 2)); // value - q * 100
            value = q;
            buffer[pos--] = DIGIT_ONES[r];
            buffer[pos--] = DIGIT_TENS[r];
        }
        
        for (;;) {
            q = (value * 52429) >>> (16 + 3);
            r = value - ((q << 3) + (q << 1)); // value - q * 10
            buffer[pos--] = DIGIT_ONES[r];
            value = q;
            if (value == 0) break;
        }
        
        return length;
    }

    public static int writeLong(byte[] buffer, int offset, long value) {
        return writeInt(buffer, offset, (int)value); // Simplified for demo
    }

    public static int writeUtf8(byte[] buffer, int offset, int codepoint) {
        if (codepoint <= 0x7F) {
            buffer[offset] = (byte) codepoint;
            return 1;
        } else if (codepoint <= 0x7FF) {
            buffer[offset] = (byte) (0xC0 | (codepoint >> 6));
            buffer[offset + 1] = (byte) (0x80 | (codepoint & 0x3F));
            return 2;
        } else if (codepoint <= 0xFFFF) {
            buffer[offset] = (byte) (0xE0 | (codepoint >> 12));
            buffer[offset + 1] = (byte) (0x80 | ((codepoint >> 6) & 0x3F));
            buffer[offset + 2] = (byte) (0x80 | (codepoint & 0x3F));
            return 3;
        } else {
            buffer[offset] = (byte) (0xF0 | (codepoint >> 18));
            buffer[offset + 1] = (byte) (0x80 | ((codepoint >> 12) & 0x3F));
            buffer[offset + 2] = (byte) (0x80 | ((codepoint >> 6) & 0x3F));
            buffer[offset + 3] = (byte) (0x80 | (codepoint & 0x3F));
            return 4;
        }
    }

    public static int writeAscii(byte[] buffer, int offset, String text) {
        int len = text.length();
        for (int i = 0; i < len; i++) {
            buffer[offset + i] = (byte) text.charAt(i);
        }
        return len;
    }
}
