package fastsoftware3d.util;

/**
 * Utility for SSAA downsampling with box filtering.
 * Reduces high-resolution SSAA buffer to target resolution by averaging pixels.
 */
public class TerminalDownsampler {

    /**
     * Downsample src buffer to dest buffer using box filter averaging.
     * Each destination pixel is the average of (factor × factor) source pixels.
     */
    public static void downsample(int[] src, int srcW, int srcH,
                                   int[] dest, int destW, int destH, int factor) {
        for (int y = 0; y < destH; y++) {
            for (int x = 0; x < destW; x++) {
                int rSum = 0, gSum = 0, bSum = 0;
                int count = 0;

                for (int dy = 0; dy < factor; dy++) {
                    for (int dx = 0; dx < factor; dx++) {
                        int srcIdx = (y * factor + dy) * srcW + (x * factor + dx);
                        if (srcIdx >= 0 && srcIdx < src.length) {
                            int pixel = src[srcIdx];
                            rSum += (pixel >> 16) & 0xFF;
                            gSum += (pixel >> 8) & 0xFF;
                            bSum += pixel & 0xFF;
                            count++;
                        }
                    }
                }

                int r = rSum / count;
                int g = gSum / count;
                int b = bSum / count;
                dest[y * destW + x] = (r << 16) | (g << 8) | b;
            }
        }
    }
}
