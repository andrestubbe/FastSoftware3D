package fastsoftware3d.core;

import java.util.Arrays;

/**
 * Owns the raw pixel and depth (z-buffer) arrays for a single render target.
 * Allocated externally and passed into RenderPipeline -- the pipeline never
 * allocates memory itself.
 */
public final class Framebuffer {
    public static final float EMPTY_DEPTH = Float.MAX_VALUE;

    public final int width;
    public final int height;
    public final int[] pixels;
    public final float[] zBuffer;
    public final int[] scratchPixels;

    public Framebuffer(int width, int height, int[] pixels) {
        this.width = width;
        this.height = height;
        this.pixels = pixels;
        this.zBuffer = new float[width * height];
        this.scratchPixels = new int[width * height];
    }

    /**
     * Reset depth buffer; pixel clearing is the caller's responsibility.
     */
    public void clearDepth() {
        Arrays.fill(zBuffer, EMPTY_DEPTH);
    }
}
