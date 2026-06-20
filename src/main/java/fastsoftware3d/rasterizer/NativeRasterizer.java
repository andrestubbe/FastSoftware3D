package fastsoftware3d.rasterizer;

import fastsoftware3d.core.TriangleRasterizer;
import fastsoftware3d.core.Framebuffer;
import fastsoftware3d.material.Material;

/**
 * JNI-backed TriangleRasterizer implementation.
 * Falls back transparently to JavaRasterizer if the native library is unavailable.
 */
public final class NativeRasterizer implements TriangleRasterizer {

    private static boolean nativeAvailable = true;
    private final JavaRasterizer fallback = new JavaRasterizer();

    static {
        try {
            fastcore.FastCore.loadLibrary("fastsoftware3d");
        } catch (Throwable t) {
            nativeAvailable = false;
            System.err.println("NativeRasterizer: native library unavailable, using Java fallback. " + t.getMessage());
        }
    }

    @Override
    public void drawTriangle(
            float x0, float y0, float z0, float u0, float v0,
            float x1, float y1, float z1, float u1, float v1,
            float x2, float y2, float z2, float u2, float v2,
            Material material,
            Framebuffer fb
    ) {
        if (!nativeAvailable) {
            fallback.drawTriangle(
                    x0, y0, z0, u0, v0,
                    x1, y1, z1, u1, v1,
                    x2, y2, z2, u2, v2,
                    material, fb);
            return;
        }
        try {
            drawTexturedTriangleNative(
                    x0, y0, z0, u0, v0,
                    x1, y1, z1, u1, v1,
                    x2, y2, z2, u2, v2,
                    fb.pixels, fb.zBuffer, material.texels, fb.width, fb.height
            );
        } catch (UnsatisfiedLinkError e) {
            nativeAvailable = false;
            fallback.drawTriangle(
                    x0, y0, z0, u0, v0,
                    x1, y1, z1, u1, v1,
                    x2, y2, z2, u2, v2,
                    material, fb);
        }
    }

    private static native void drawTexturedTriangleNative(
            float x0, float y0, float z0, float u0, float v0,
            float x1, float y1, float z1, float u1, float v1,
            float x2, float y2, float z2, float u2, float v2,
            int[] pixels, float[] zBuffer, int[] texels, int width, int height
    );
}
