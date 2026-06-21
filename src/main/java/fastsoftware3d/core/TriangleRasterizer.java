package fastsoftware3d.core;

import fastsoftware3d.material.Material;

/**
 * Strategy interface for perspective-correct triangle rasterization.
 * Implementations may be pure Java (fallback) or native JNI.
 */
public interface TriangleRasterizer {

    /**
     * Rasterize a textured triangle into the given framebuffer.
     *
     * @param x0..z2  projected screen coordinates (x, y) and camera-space depth (z)
     * @param u0..v2  UV texture coordinates [0..1]
     * @param material the material (texture) to sample
     * @param fb       target framebuffer
     */
    void drawTriangle(
            float x0, float y0, float z0, float u0, float v0,
            float x1, float y1, float z1, float u1, float v1,
            float x2, float y2, float z2, float u2, float v2,
            Material material,
            Framebuffer fb
    );

    default void drawTriangles(
            float[] triangleData,
            int triangleCount,
            Material material,
            Framebuffer fb
    ) {
        for (int i = 0; i < triangleCount; i++) {
            int offset = i * 15;
            drawTriangle(
                    triangleData[offset],      triangleData[offset + 1],  triangleData[offset + 2],  triangleData[offset + 3],  triangleData[offset + 4],
                    triangleData[offset + 5],  triangleData[offset + 6],  triangleData[offset + 7],  triangleData[offset + 8],  triangleData[offset + 9],
                    triangleData[offset + 10], triangleData[offset + 11], triangleData[offset + 12], triangleData[offset + 13], triangleData[offset + 14],
                    material,
                    fb
            );
        }
    }
}
