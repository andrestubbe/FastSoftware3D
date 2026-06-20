package fastsoftware3d.core;

import fastsoftware3d.camera.Camera;
import fastsoftware3d.material.Material;
import fastsoftware3d.model.ObjLoader;

import java.util.Arrays;

/**
 * Core rendering pipeline (refactored):
 * - holds camera, framebuffer, rasterizer
 * - orchestrates transform → project → rasterize stages
 *
 * This version maintains 100% behavioral compatibility with the previous
 * implementation while using the new modular stages internally.
 */
public final class RenderPipeline {

    private final Camera camera;
    private Framebuffer fb;
    private final TriangleRasterizer rasterizer;

    private final TransformStage transformStage = new TransformStage();
    private final ProjectionStage projectionStage = new ProjectionStage();
    private final RasterStage rasterStage = new RasterStage();

    public RenderPipeline(Camera camera, Framebuffer fb, TriangleRasterizer rasterizer) {
        this.camera = camera;
        this.fb = fb;
        this.rasterizer = rasterizer;
    }

    /**
     * Swap to a newly allocated framebuffer (called on resize / SSAA change).
     */
    public void setFramebuffer(Framebuffer newFb) {
        this.fb = newFb;
    }

    public Framebuffer getFramebuffer() {
        return fb;
    }

    public Camera getCamera() {
        return camera;
    }

    /**
     * Clear depth buffer for a new frame.
     */
    public void clear() {
        fb.clearDepth();
    }

    // Coordinate helpers (used by SceneUtilities / GridNode via Renderer3D)

    public float[] transformToCamera(float wx, float wy, float wz) {
        return transformStage.worldToCamera(wx, wy, wz, camera);
    }

    public float[] project(float[] cameraPoint) {
        return projectionStage.project(cameraPoint, camera, fb.width, fb.height);
    }

    /**
     * Render a model at the given world position/rotation with the provided material.
     * Maintains 100% compatibility with previous implementation.
     */
    public void renderModel(ObjLoader.ModelData model,
                            float modelX, float modelY, float modelZ,
                            float rotationY,
                            Material material) {
        // Model-level frustum culling
        float[] camCenter = transformToCamera(modelX, modelY, modelZ);
        float cx = camCenter[0], cy = camCenter[1], cz = camCenter[2];
        float R = model.boundingRadius;

        // System.out.println("renderModel: pos=(" + modelX + "," + modelY + "," + modelZ + ") camPos=(" + cx + "," + cy + "," + cz + ") R=" + R + " faces=" + model.faces.size());

        // CULLING DISABLED — comment back in when geometry is stable
        // if (cz + R <= 1.0f) return;


        float cosRY = (float) Math.cos(rotationY);
        float sinRY = (float) Math.sin(rotationY);

        int vertexCount = model.vertices.size();
        float[][] cachedCamVerts = new float[vertexCount][];
        float[][] cachedProjVerts = new float[vertexCount][];
        boolean[] vertexValid = new boolean[vertexCount];

        for (int i = 0; i < vertexCount; i++) {
            float[] lv = model.vertices.get(i);

            // Local rotation (yaw only, around Y axis)
            float rx = lv[0] * cosRY - lv[2] * sinRY;
            float rz = lv[0] * sinRY + lv[2] * cosRY;

            // World-space position
            float wx = rx + modelX;
            float wy = lv[1] + modelY;
            float wz = rz + modelZ;

            // Stage 2: Transform to camera-space
            float[] cam = transformStage.worldToCamera(wx, wy, wz, camera);
            cachedCamVerts[i] = cam;

            // Stage 3: Project to screen-space
            float[] proj = projectionStage.project(cam, camera, fb.width, fb.height);
            if (proj != null) {
                cachedProjVerts[i] = proj;
                vertexValid[i] = true;
            }
        }

        for (ObjLoader.Face face : model.faces) {
            int i0 = face.vIndices[0];
            int i1 = face.vIndices[1];
            int i2 = face.vIndices[2];

            if (!vertexValid[i0] || !vertexValid[i1] || !vertexValid[i2]) {
                continue;
            }

            float[] p0 = cachedProjVerts[i0];
            float[] p1 = cachedProjVerts[i1];
            float[] p2 = cachedProjVerts[i2];

            // Backface culling (screen-space cross product)
            float x0 = p0[0], y0 = p0[1];
            float x1 = p1[0], y1 = p1[1];
            float x2 = p2[0], y2 = p2[1];
            float cross = (x1 - x0) * (y2 - y0) - (y1 - y0) * (x2 - x0);
            // BACKFACE CULLING DISABLED
            // if (cross <= 0) continue;

            // Fetch UVs
            float[] uv0 = face.uvIndices[0] >= 0 ? model.uvs.get(face.uvIndices[0]) : new float[]{0, 0};
            float[] uv1 = face.uvIndices[1] >= 0 ? model.uvs.get(face.uvIndices[1]) : new float[]{1, 0};
            float[] uv2 = face.uvIndices[2] >= 0 ? model.uvs.get(face.uvIndices[2]) : new float[]{0, 1};

            // Stage 4: Rasterize
            rasterStage.rasterizeTriangle(
                    p0, p1, p2,
                    new float[]{uv0[0], 1.0f - uv0[1]},
                    new float[]{uv1[0], 1.0f - uv1[1]},
                    new float[]{uv2[0], 1.0f - uv2[1]},
                    material, fb, rasterizer
            );
        }

    }

    public void postProcess() {
        if (camera.fisheyeEnabled && camera.fisheyeStrength != 0.0f) {
            int w = fb.width;
            int h = fb.height;
            int[] pixels = fb.pixels;
            int[] temp = new int[pixels.length];
            float halfW = w / 2.0f;
            float halfH = h / 2.0f;
            float strength = camera.fisheyeStrength;
            final float zoom = (strength > 0.0f) ? (1.0f + strength * 2.0f) : 1.0f; // Zoom factor to eliminate black border in corners for barrel distortion

            java.util.stream.IntStream.range(0, h).parallel().forEach(y -> {
                float dy = (y - halfH) / halfH;
                int rowOffset = y * w;
                for (int x = 0; x < w; x++) {
                    float dx = (x - halfW) / halfW;
                    float r2 = dx * dx + dy * dy;
                    float factor = 1.0f + strength * r2;
                    float srcDx = (dx * factor) / zoom;
                    float srcDy = (dy * factor) / zoom;

                    int srcX = (int) (halfW + srcDx * halfW);
                    int srcY = (int) (halfH + srcDy * halfH);

                    if (srcX >= 0 && srcX < w && srcY >= 0 && srcY < h) {
                        temp[rowOffset + x] = pixels[srcY * w + srcX];
                    } else {
                        temp[rowOffset + x] = 0x000000;
                    }
                }
            });
            System.arraycopy(temp, 0, pixels, 0, pixels.length);
        }
    }
}
