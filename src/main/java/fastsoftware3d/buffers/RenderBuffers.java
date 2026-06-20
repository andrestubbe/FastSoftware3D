package fastsoftware3d.buffers;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;

/**
 * Manages all render buffers: screen, render target, downsampling buffers.
 * Handles allocation, reallocation, and buffer state for SSAA + low-res modes.
 */
public class RenderBuffers {

    // Screen target buffer (displayed on canvas)
    public BufferedImage screenBuffer;
    public int[] screenPixels;

    // High-resolution rendering target (for SSAA)
    public BufferedImage renderBuffer;
    public int[] renderPixels;
    public int[] renderBackground;

    // Downsampled buffer (SSAA intermediate)
    public BufferedImage downsampleBuffer;
    public int[] downsamplePixels;

    private final int screenWidth;
    private final int screenHeight;
    private final int lowResWidth;
    private final int lowResHeight;

    public RenderBuffers(int screenWidth, int screenHeight, int lowResWidth, int lowResHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.lowResWidth = lowResWidth;
        this.lowResHeight = lowResHeight;

        // Initialize screen buffer
        screenBuffer = new BufferedImage(screenWidth, screenHeight, BufferedImage.TYPE_INT_RGB);
        screenPixels = ((DataBufferInt) screenBuffer.getRaster().getDataBuffer()).getData();
    }

    /**
     * Reallocate render and downsample buffers based on current resolution and SSAA factor.
     *
     * @param lowResMode true to use low resolution, false for full resolution
     * @param ssaaFactor SSAA multiplier (1, 2, 4, 8, 16)
     */
    public synchronized void reallocate(boolean lowResMode, int ssaaFactor) {
        int baseW = lowResMode ? lowResWidth : screenWidth;
        int baseH = lowResMode ? lowResHeight : screenHeight;
        reallocateForSize(baseW, baseH, ssaaFactor);
    }

    /**
     * Reallocate render and downsample buffers for arbitrary dimensions (e.g., terminal).
     *
     * @param baseW      Base width (logical pixels)
     * @param baseH      Base height (logical pixels)
     * @param ssaaFactor SSAA multiplier (1, 2, 4, 8, 16)
     */
    public synchronized void reallocateForSize(int baseW, int baseH, int ssaaFactor) {
        int renderW = baseW * ssaaFactor;
        int renderH = baseH * ssaaFactor;

        // Allocate render buffer
        renderBuffer = new BufferedImage(renderW, renderH, BufferedImage.TYPE_INT_RGB);
        renderPixels = ((DataBufferInt) renderBuffer.getRaster().getDataBuffer()).getData();
        renderBackground = new int[renderW * renderH];
        Arrays.fill(renderBackground, 0x000000);

        // Allocate downsample buffer if SSAA is enabled
        if (ssaaFactor > 1) {
            downsampleBuffer = new BufferedImage(baseW, baseH, BufferedImage.TYPE_INT_RGB);
            downsamplePixels = ((DataBufferInt) downsampleBuffer.getRaster().getDataBuffer()).getData();
        } else {
            downsampleBuffer = null;
            downsamplePixels = null;
        }
    }

    /**
     * Get the base (logical) dimensions for the current mode.
     */
    public int[] getBaseDimensions(boolean lowResMode) {
        return new int[]{
                lowResMode ? lowResWidth : screenWidth,
                lowResMode ? lowResHeight : screenHeight
        };
    }
}
