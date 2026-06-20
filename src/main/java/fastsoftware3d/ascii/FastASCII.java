package fastsoftware3d.ascii;

import fastcore.FastCore;

/**
 * High-performance, zero-allocation ASCII and UTF-8 byte processing.
 * Pure Java by default, with optional SIMD-accelerated JNI backend for large buffers.
 */
public final class FastASCII {

    private FastASCII() {} // Utility class

    /**
     * Optional SIMD-accelerated Native backend.
     */
    public static final class Native {
        private static boolean available = false;

        static {
            try {
                FastCore.loadLibrary("fastASCII");
                available = true;
            } catch (Throwable t) {
                // Native backend not available, will gracefully fallback to Pure Java.
                available = false;
            }
        }

        public static boolean isAvailable() {
            return available;
        }

        public static native int findByte(byte[] buf, int offset, int length, int value);
        public static native int findSubstring(byte[] buf, int offset, int length, byte[] needle);
        public static native int parseInt(byte[] buf, int offset, int length);
        public static native long parseLong(byte[] buf, int offset, int length);
        public static native int validateUtf8(byte[] buf, int offset, int length);
        public static native int decodeUtf8(byte[] buf, int offset, int length, int[] outCodePoint);
    }
}
