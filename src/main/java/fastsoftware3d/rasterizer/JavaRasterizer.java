package fastsoftware3d.rasterizer;

import fastsoftware3d.core.TriangleRasterizer;
import fastsoftware3d.core.Framebuffer;
import fastsoftware3d.material.Material;

/**
 * Pure-Java fallback implementation of TriangleRasterizer.
 * Performs perspective-correct texture mapping using barycentric coordinates.
 */
public final class JavaRasterizer implements TriangleRasterizer {

    @Override
    public void drawTriangle(
            float x0, float y0, float z0, float u0, float v0,
            float x1, float y1, float z1, float u1, float v1,
            float x2, float y2, float z2, float u2, float v2,
            Material material,
            Framebuffer fb
    ) {
        final int width = fb.width;
        final int height = fb.height;
        final int[] pixels = fb.pixels;
        final float[] zBuf = fb.zBuffer;
        final int[] texels = material.texels;
        final int texWidth = material.texWidth;
        final int texHeight = material.texHeight;

        float w0 = 1.0f / z0;
        float w1 = 1.0f / z1;
        float w2 = 1.0f / z2;

        float uOverZ0 = u0 * w0, vOverZ0 = v0 * w0;
        float uOverZ1 = u1 * w1, vOverZ1 = v1 * w1;
        float uOverZ2 = u2 * w2, vOverZ2 = v2 * w2;

        int minX = (int) Math.max(0, Math.floor(Math.min(x0, Math.min(x1, x2))));
        int maxX = (int) Math.min(width - 1, Math.ceil(Math.max(x0, Math.max(x1, x2))));
        int minY = (int) Math.max(0, Math.floor(Math.min(y0, Math.min(y1, y2))));
        int maxY = (int) Math.min(height - 1, Math.ceil(Math.max(y0, Math.max(y1, y2))));

        float denom = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2);
        if (Math.abs(denom) < 1e-6f) return;
        float invDenom = 1.0f / denom;

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                float px = x + 0.5f;
                float py = y + 0.5f;

                float alpha = ((y1 - y2) * (px - x2) + (x2 - x1) * (py - y2)) * invDenom;
                float beta = ((y2 - y0) * (px - x2) + (x0 - x2) * (py - y2)) * invDenom;
                float gamma = 1.0f - alpha - beta;

                if (alpha < 0 || beta < 0 || gamma < 0) continue;

                float interpW = alpha * w0 + beta * w1 + gamma * w2;
                float depth = 1.0f / interpW;
                int pixelIdx = y * width + x;

                if (depth >= zBuf[pixelIdx]) continue;
                zBuf[pixelIdx] = depth;

                float u = (alpha * uOverZ0 + beta * uOverZ1 + gamma * uOverZ2) / interpW;
                float v = (alpha * vOverZ0 + beta * vOverZ1 + gamma * vOverZ2) / interpW;

                u = u - (float) Math.floor(u);
                v = v - (float) Math.floor(v);

                int texX = (int) (u * (texWidth - 1));
                int texY = (int) (v * (texHeight - 1));
                int texColor = texels[texY * texWidth + texX];

                // Linear depth fog calculation
                float fogNear = 750.0f;
                float fogFar = 2100.0f;
                int fogColor = 0x948D6B; // Darker yellow-brownish fog color

                float fogFactor = (fogFar - depth) / (fogFar - fogNear);
                if (fogFactor < 0.15f) fogFactor = 0.15f; // Max 85% fog at far distance
                if (fogFactor > 1.0f) fogFactor = 1.0f;

                if (fogFactor >= 1.0f) {
                    pixels[pixelIdx] = texColor;
                } else {
                    int rT = (texColor >> 16) & 0xFF;
                    int gT = (texColor >> 8) & 0xFF;
                    int bT = texColor & 0xFF;

                    int rF = (fogColor >> 16) & 0xFF;
                    int gF = (fogColor >> 8) & 0xFF;
                    int bF = fogColor & 0xFF;

                    int rOut = (int) (rT * fogFactor + rF * (1.0f - fogFactor));
                    int gOut = (int) (gT * fogFactor + gF * (1.0f - fogFactor));
                    int bOut = (int) (bT * fogFactor + bF * (1.0f - fogFactor));

                    pixels[pixelIdx] = (rOut << 16) | (gOut << 8) | bOut;
                }
            }
        }
    }
}
