package fastsoftware3d.material;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.stream.IntStream;

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
        int width = texWidth;
        int height = texHeight;
        IntStream.range(1, levels).forEach(level -> {
            int srcWidth = Math.max(1, width >> (level - 1));
            int srcHeight = Math.max(1, height >> (level - 1));
            int dstWidth = Math.max(1, width >> level);
            int dstHeight = Math.max(1, height >> level);

            int srcOffset = mipmapOffsets[level - 1];
            int dstOffset = mipmapOffsets[level];

            for (int y = 0; y < dstHeight; y++) {
                for (int x = 0; x < dstWidth; x++) {
                    int sx = x * 2;
                    int sy = y * 2;

                    int sx1 = Math.min(sx + 1, srcWidth - 1);
                    int sy1 = Math.min(sy + 1, srcHeight - 1);

                    int c00 = mipmapData[srcOffset + sy * srcWidth + sx];
                    int c10 = mipmapData[srcOffset + sy * srcWidth + sx1];
                    int c01 = mipmapData[srcOffset + sy1 * srcWidth + sx];
                    int c11 = mipmapData[srcOffset + sy1 * srcWidth + sx1];

                    int r = (((c00 >> 16) & 0xFF) + ((c10 >> 16) & 0xFF) + ((c01 >> 16) & 0xFF) + ((c11 >> 16) & 0xFF)) >> 2;
                    int g = (((c00 >> 8) & 0xFF) + ((c10 >> 8) & 0xFF) + ((c01 >> 8) & 0xFF) + ((c11 >> 8) & 0xFF)) >> 2;
                    int b = ((c00 & 0xFF) + (c10 & 0xFF) + (c01 & 0xFF) + (c11 & 0xFF)) >> 2;

                    mipmapData[dstOffset + y * dstWidth + x] = (r << 16) | (g << 8) | b;
                }
            }
        });
    }

    // ------------------------------------------------------------------
    // Factory methods
    // ------------------------------------------------------------------

    private static final java.util.Map<String, Material> textureCache = new java.util.HashMap<>();

    public static Material fromPng(String path) {
        if (textureCache.containsKey(path)) {
            return textureCache.get(path);
        }
        try {
            BufferedImage img = ImageIO.read(new File(path));
            int w = img.getWidth();
            int h = img.getHeight();
            int[] tex = new int[w * h];
            img.getRGB(0, 0, w, h, tex, 0, w);
            Material mat = new Material(tex, w, h);
            textureCache.put(path, mat);
            return mat;
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
