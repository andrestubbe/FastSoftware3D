package fastsoftware3d.ascii;

/**
 * High-performance ASCII character classification.
 */
public final class FastASCIIChar {

    private FastASCIIChar() {}

    public static boolean isDigit(byte b) {
        return b >= '0' && b <= '9';
    }

    public static boolean isLetter(byte b) {
        return (b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z');
    }

    public static boolean isWhitespace(byte b) {
        return b == ' ' || b == '\t' || b == '\n' || b == '\r';
    }

    public static boolean isControl(byte b) {
        return b >= 0 && b < 32;
    }

    public static boolean isPrintable(byte b) {
        return b >= 32 && b <= 126;
    }
}
