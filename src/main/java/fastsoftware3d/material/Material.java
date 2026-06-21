package fastsoftware3d.material;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

/**
 * A material is a texture (int[] texels) plus its dimensions.
 * Static factory methods provide procedural and image-based materials.
 */
public final class Material {

    public final int[] texels;
    public final int texWidth;
    public final int texHeight;

    public final int[] mipmapData;
    public final int[] mipmapOffsets;
    public final int[] mipmapWidths;
    public final int[] mipmapHeights;
    public final int mipmapLevels;

    public Material(int[] texels, int texWidth, int texHeight) {
        this.texels = texels;
        this.texWidth = texWidth;
        this.texHeight = texHeight;

        // Generate mipmaps
        int levels = 1;
        int w = texWidth;
        int h = texHeight;
        while (w > 1 || h > 1) {
            levels++;
            w = Math.max(1, w / 2);
            h = Math.max(1, h / 2);
        }
        this.mipmapLevels = levels;
        this.mipmapOffsets = new int[levels];
        this.mipmapWidths = new int[levels];
        this.mipmapHeights = new int[levels];

        int totalSize = 0;
        w = texWidth;
        h = texHeight;
        for (int i = 0; i < levels; i++) {
            mipmapWidths[i] = w;
            mipmapHeights[i] = h;
            mipmapOffsets[i] = totalSize;
            totalSize += w * h;
            w = Math.max(1, w / 2);
            h = Math.max(1, h / 2);
        }

        this.mipmapData = new int[totalSize];
        // Copy level 0 (base)
        System.arraycopy(texels, 0, mipmapData, 0, texels.length);

        // Generate downscaled levels using box filtering
        for (int level = 1; level < levels; level++) {
            int srcWidth = mipmapWidths[level - 1];
            int srcHeight = mipmapHeights[level - 1];
            int srcOffset = mipmapOffsets[level - 1];

            int dstWidth = mipmapWidths[level];
            int dstHeight = mipmapHeights[level];
            int dstOffset = mipmapOffsets[level];

            for (int dy = 0; dy < dstHeight; dy++) {
                int sy0 = dy * 2;
                int sy1 = Math.min(sy0 + 1, srcHeight - 1);
                for (int dx = 0; dx < dstWidth; dx++) {
                    int sx0 = dx * 2;
                    int sx1 = Math.min(sx0 + 1, srcWidth - 1);

                    int p00 = mipmapData[srcOffset + sy0 * srcWidth + sx0];
                    int p01 = mipmapData[srcOffset + sy0 * srcWidth + sx1];
                    int p10 = mipmapData[srcOffset + sy1 * srcWidth + sx0];
                    int p11 = mipmapData[srcOffset + sy1 * srcWidth + sx1];

                    // Average colors
                    int r = (((p00 >> 16) & 0xFF) + ((p01 >> 16) & 0xFF) + ((p10 >> 16) & 0xFF) + ((p11 >> 16) & 0xFF)) / 4;
                    int g = (((p00 >> 8) & 0xFF) + ((p01 >> 8) & 0xFF) + ((p10 >> 8) & 0xFF) + ((p11 >> 8) & 0xFF)) / 4;
                    int b = ((p00 & 0xFF) + (p01 & 0xFF) + (p10 & 0xFF) + (p11 & 0xFF)) / 4;

                    mipmapData[dstOffset + dy * dstWidth + dx] = (r << 16) | (g << 8) | b;
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Factory methods
    // ------------------------------------------------------------------

    public static Material fromPng(String path) {
        try {
            BufferedImage img = ImageIO.read(new java.io.File(path));
            int w = img.getWidth();
            int h = img.getHeight();
            int[] tex = new int[w * h];
            img.getRGB(0, 0, w, h, tex, 0, w);
            return new Material(tex, w, h);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load texture: " + path, e);
        }
    }


    /**
     * Procedural wooden-crate texture (256x256).
     */
    public static Material woodCrate() {
        final int SIZE = 256;
        int[] tex = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int r = 139, g = 90, b = 43;

                if (y % 64 < 3 || y == SIZE - 1) {
                    r = 60;
                    g = 38;
                    b = 18;
                } else {
                    double noise = Math.sin(x * 0.05 + Math.cos(y * 0.1) * 2.0) * 8.0;
                    double grain = Math.sin(y * 0.8) * 4.0;
                    r += (int) (noise + grain);
                    g += (int) (noise * 0.8 + grain);
                    b += (int) (noise * 0.6 + grain);
                    if (Math.abs(Math.sin(x * 0.02) * Math.cos(y * 0.02)) > 0.85) {
                        r -= 25;
                        g -= 20;
                        b -= 15;
                    }
                }

                int border = 16;
                if (x < border || x >= SIZE - border || y < border || y >= SIZE - border) {
                    r = (int) (r * 0.7);
                    g = (int) (g * 0.7);
                    b = (int) (b * 0.7);
                    if (x == border || x == SIZE - border - 1 || y == border || y == SIZE - border - 1) {
                        r = 40;
                        g = 25;
                        b = 12;
                    }
                }

                int diff = Math.abs(x - y);
                if (diff < 14 && x >= border && x < SIZE - border && y >= border && y < SIZE - border) {
                    r = (int) (r * 0.85);
                    g = (int) (g * 0.85);
                    b = (int) (b * 0.85);
                }

                if ((x == 24 && y == 24) || (x == SIZE - 25 && y == 24) ||
                        (x == 24 && y == SIZE - 25) || (x == SIZE - 25 && y == SIZE - 25)) {
                    r = 180;
                    g = 180;
                    b = 180;
                } else if ((Math.pow(x - 24, 2) + Math.pow(y - 24, 2) < 16) ||
                        (Math.pow(x - (SIZE - 25), 2) + Math.pow(y - 24, 2) < 16) ||
                        (Math.pow(x - 24, 2) + Math.pow(y - (SIZE - 25), 2) < 16) ||
                        (Math.pow(x - (SIZE - 25), 2) + Math.pow(y - (SIZE - 25), 2) < 16)) {
                    r = 100;
                    g = 100;
                    b = 100;
                }

                r = Math.max(0, Math.min(255, r));
                g = Math.max(0, Math.min(255, g));
                b = Math.max(0, Math.min(255, b));
                tex[y * SIZE + x] = (r << 16) | (g << 8) | b;
            }
        }
        return new Material(tex, SIZE, SIZE);
    }

    /**
     * Solid flat color.
     */
    public static Material solidColor(int rgb) {
        return new Material(new int[]{rgb}, 1, 1);
    }

    /**
     * Wrap an existing texel array (legacy int[] textures).
     */
    public static Material fromTexels(int[] texels, int size) {
        return new Material(texels, size, size);
    }

    /**
     * Load from a BufferedImage.
     */
    public static Material fromImage(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        BufferedImage converted = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        converted.getGraphics().drawImage(img, 0, 0, null);
        int[] data = new int[w * h];
        converted.getRaster().getDataElements(0, 0, w, h, data);
        return new Material(data, w, h);
    }
}
