package fastsoftware3d.core;

import fastsoftware3d.material.Material;

/**
 * Bridges projected vertices + UVs to the TriangleRasterizer.
 */
public final class RasterStage {

    public void rasterizeTriangle(
            float[] v0, float[] v1, float[] v2,
            float[] uv0, float[] uv1, float[] uv2,
            Material material,
            Framebuffer fb,
            TriangleRasterizer rasterizer
    ) {
        rasterizer.drawTriangle(
                v0[0], v0[1], v0[2], uv0[0], uv0[1],
                v1[0], v1[1], v1[2], uv1[0], uv1[1],
                v2[0], v2[1], v2[2], uv2[0], uv2[1],
                material,
                fb
        );
    }
}
