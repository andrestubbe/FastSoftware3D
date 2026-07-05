package fastsoftware3d.util;

public class TerminalDownsampler {
    public static void downsample(int[] src, int srcW, int srcH, int[] dst, int dstW, int dstH, int ssaa) {
        for (int y = 0; y < dstH; y++) {
            int dstIdx = y * dstW;
            for (int x = 0; x < dstW; x++) {
                int r = 0, g = 0, b = 0;
                int baseSrc = (y * ssaa) * srcW + (x * ssaa);
                for (int dy = 0; dy < ssaa; dy++) {
                    int rowOff = baseSrc + dy * srcW;
                    for (int dx = 0; dx < ssaa; dx++) {
                        int px = src[rowOff + dx];
                        r += (px >> 16) & 0xFF;
                        g += (px >> 8) & 0xFF;
                        b += px & 0xFF;
                    }
                }
                int count = ssaa * ssaa;
                int shift = Integer.numberOfTrailingZeros(count);
                dst[dstIdx + x] = ((r >> shift) << 16) | ((g >> shift) << 8) | (b >> shift);
            }
        }
    }
}
