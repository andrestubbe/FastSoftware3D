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

        // Initialize edge step derivatives
        float de0_dx = y1 - y2;
        float de0_dy = x2 - x1;
        float de1_dx = y2 - y0;
        float de1_dy = x0 - x2;

        // Compute step increments for parameters (1/z, u/z, v/z)
        float dw_dx = invDenom * (de0_dx * (w0 - w2) + de1_dx * (w1 - w2));
        float dw_dy = invDenom * (de0_dy * (w0 - w2) + de1_dy * (w1 - w2));

        float du_dx = invDenom * (de0_dx * (uOverZ0 - uOverZ2) + de1_dx * (uOverZ1 - uOverZ2));
        float du_dy = invDenom * (de0_dy * (uOverZ0 - uOverZ2) + de1_dy * (uOverZ1 - uOverZ2));

        float dv_dx = invDenom * (de0_dx * (vOverZ0 - vOverZ2) + de1_dx * (vOverZ1 - vOverZ2));
        float dv_dy = invDenom * (de0_dy * (vOverZ0 - vOverZ2) + de1_dy * (vOverZ1 - vOverZ2));

        // Evaluate starting values at the top-left of the bounding box (at pixel centers)
        float startX = minX + 0.5f;
        float startY = minY + 0.5f;

        float e0_row = (y1 - y2) * (startX - x2) + (x2 - x1) * (startY - y2);
        float e1_row = (y2 - y0) * (startX - x2) + (x0 - x2) * (startY - y2);

        float alpha_start = e0_row * invDenom;
        float beta_start = e1_row * invDenom;
        float gamma_start = 1.0f - alpha_start - beta_start;

        float w_row = alpha_start * w0 + beta_start * w1 + gamma_start * w2;
        float u_row = alpha_start * uOverZ0 + beta_start * uOverZ1 + gamma_start * uOverZ2;
        float v_row = alpha_start * vOverZ0 + beta_start * vOverZ1 + gamma_start * vOverZ2;

        for (int y = minY; y <= maxY; y++) {
            float e0 = e0_row;
            float e1 = e1_row;
            float wVal = w_row;
            float uVal = u_row;
            float vVal = v_row;

            for (int x = minX; x <= maxX; x++) {
                float alpha = e0 * invDenom;
                float beta = e1 * invDenom;
                float gamma = 1.0f - alpha - beta;

                if (alpha >= 0.0f && beta >= 0.0f && gamma >= 0.0f) {
                    float depth = 1.0f / wVal;
                    int pixelIdx = y * width + x;

                    if (depth < zBuf[pixelIdx]) {
                        zBuf[pixelIdx] = depth;

                        float u = uVal * depth;
                        float v = vVal * depth;

                        u = u - (float) Math.floor(u);
                        v = v - (float) Math.floor(v);

                        int texX = (int) (u * (texWidth - 1));
                        int texY = (int) (v * (texHeight - 1));
                        int texColor = texels[texY * texWidth + texX];

                        // Linear depth fog calculation
                        float fogNear = 750.0f;
                        float fogFar = 2100.0f;
                        int fogColor = 0x000000; // Black fog color

                        float fogFactor = (fogFar - depth) / (fogFar - fogNear);
                        if (fogFactor < 0.50f) fogFactor = 0.50f; // Max 50% fog at far distance
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

                e0 += de0_dx;
                e1 += de1_dx;
                wVal += dw_dx;
                uVal += du_dx;
                vVal += dv_dx;
            }

            e0_row += de0_dy;
            e1_row += de1_dy;
            w_row += dw_dy;
            u_row += du_dy;
            v_row += dv_dy;
        }
    }
}
