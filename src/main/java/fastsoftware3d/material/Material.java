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

    public Material(int[] texels, int texWidth, int texHeight) {
        this.texels = texels;
        this.texWidth = texWidth;
        this.texHeight = texHeight;
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
