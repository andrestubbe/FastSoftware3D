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

    private float[] batchBuffer = new float[1024 * 15]; // start with capacity for 1024 triangles
    private final float[] clipIn = new float[3 * 5];
    private final float[] clipOut = new float[4 * 5];
    private float[] vertexCache = new float[1024 * 3];
    private final float[] projTemp = new float[12];

    private static final float[] UV_FALLBACK_0 = {0.0f, 0.0f};
    private static final float[] UV_FALLBACK_1 = {1.0f, 0.0f};
    private static final float[] UV_FALLBACK_2 = {0.0f, 1.0f};

    private void ensureVertexCacheCapacity(int vertexCount) {
        int reqLen = vertexCount * 3;
        if (reqLen > vertexCache.length) {
            int newLen = Math.max(reqLen, vertexCache.length * 2);
            vertexCache = new float[newLen];
        }
    }

    private void interpolate(float[] out, int outIdx, float[] v1, int v1Idx, float[] v2, int v2Idx, float near) {
        float z1 = v1[v1Idx + 2];
        float z2 = v2[v2Idx + 2];
        float t = (near - z1) / (z2 - z1);
        out[outIdx]     = v1[v1Idx] + t * (v2[v2Idx] - v1[v1Idx]);
        out[outIdx + 1] = v1[v1Idx + 1] + t * (v2[v2Idx + 1] - v1[v1Idx + 1]);
        out[outIdx + 2] = near;
        out[outIdx + 3] = v1[v1Idx + 3] + t * (v2[v2Idx + 3] - v1[v1Idx + 3]);
        out[outIdx + 4] = v1[v1Idx + 4] + t * (v2[v2Idx + 4] - v1[v1Idx + 4]);
    }

    private void ensureBatchCapacity(int triCount) {
        int reqLen = triCount * 15;
        if (reqLen > batchBuffer.length) {
            int newLen = Math.max(reqLen, batchBuffer.length * 2);
            batchBuffer = Arrays.copyOf(batchBuffer, newLen);
        }
    }

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

        // Near plane culling
        if (cz + R <= 0.1f) return;

        // Calculate horizontal and vertical camera frustum planes
        float theta = (float) Math.toRadians(camera.fov / 2.0f);
        float cosTheta = (float) Math.cos(theta);
        float sinTheta = (float) Math.sin(theta);
        float tanTheta = (float) Math.tan(theta);
        float aspect = (float) fb.height / fb.width;
        float phi = (float) Math.atan(tanTheta * aspect);
        float cosPhi = (float) Math.cos(phi);
        float sinPhi = (float) Math.sin(phi);

        // Right/Left plane culling
        if (Math.abs(cx) * cosTheta - cz * sinTheta > R) return;

        // Top/Bottom plane culling
        if (Math.abs(cy) * cosPhi - cz * sinPhi > R) return;


        float cosRY = (float) Math.cos(rotationY);
        float sinRY = (float) Math.sin(rotationY);

        int vertexCount = model.vertices.size();
        ensureVertexCacheCapacity(vertexCount);

        for (int i = 0; i < vertexCount; i++) {
            float[] lv = model.vertices.get(i);

            // Local rotation (yaw only, around Y axis)
            float rx = lv[0] * cosRY - lv[2] * sinRY;
            float rz = lv[0] * sinRY + lv[2] * cosRY;

            // World-space position
            float wx = rx + modelX;
            float wy = lv[1] + modelY;
            float wz = rz + modelZ;

            // Stage 2: Transform to camera-space with zero allocations
            transformStage.worldToCameraZeroAlloc(wx, wy, wz, camera, vertexCache, i * 3);
        }

        int visibleCount = 0;
        float nearPlane = 2.0f; // Matches Camera.project limit of 2.0f

        for (ObjLoader.Face face : model.faces) {
            int i0 = face.vIndices[0];
            int i1 = face.vIndices[1];
            int i2 = face.vIndices[2];

            int vOff0 = i0 * 3;
            int vOff1 = i1 * 3;
            int vOff2 = i2 * 3;

            float c0z = vertexCache[vOff0 + 2];
            float c1z = vertexCache[vOff1 + 2];
            float c2z = vertexCache[vOff2 + 2];

            float[] uv0 = face.uvIndices[0] >= 0 ? model.uvs.get(face.uvIndices[0]) : UV_FALLBACK_0;
            float[] uv1 = face.uvIndices[1] >= 0 ? model.uvs.get(face.uvIndices[1]) : UV_FALLBACK_1;
            float[] uv2 = face.uvIndices[2] >= 0 ? model.uvs.get(face.uvIndices[2]) : UV_FALLBACK_2;

            clipIn[0] = vertexCache[vOff0]; clipIn[1] = vertexCache[vOff0 + 1]; clipIn[2] = c0z; clipIn[3] = uv0[0]; clipIn[4] = uv0[1];
            clipIn[5] = vertexCache[vOff1]; clipIn[6] = vertexCache[vOff1 + 1]; clipIn[7] = c1z; clipIn[8] = uv1[0]; clipIn[9] = uv1[1];
            clipIn[10] = vertexCache[vOff2]; clipIn[11] = vertexCache[vOff2 + 1]; clipIn[12] = c2z; clipIn[13] = uv2[0]; clipIn[14] = uv2[1];

            boolean in0 = c0z >= nearPlane;
            boolean in1 = c1z >= nearPlane;
            boolean in2 = c2z >= nearPlane;

            int count = (in0 ? 1 : 0) + (in1 ? 1 : 0) + (in2 ? 1 : 0);
            if (count == 0) continue;

            int numOutVerts = 0;
            if (count == 3) {
                System.arraycopy(clipIn, 0, clipOut, 0, 15);
                numOutVerts = 3;
            } else if (count == 1) {
                if (in0) {
                    System.arraycopy(clipIn, 0, clipOut, 0, 5);
                    interpolate(clipOut, 5, clipIn, 0, clipIn, 5, nearPlane);
                    interpolate(clipOut, 10, clipIn, 0, clipIn, 10, nearPlane);
                } else if (in1) {
                    System.arraycopy(clipIn, 5, clipOut, 0, 5);
                    interpolate(clipOut, 5, clipIn, 5, clipIn, 10, nearPlane);
                    interpolate(clipOut, 10, clipIn, 5, clipIn, 0, nearPlane);
                } else {
                    System.arraycopy(clipIn, 10, clipOut, 0, 5);
                    interpolate(clipOut, 5, clipIn, 10, clipIn, 0, nearPlane);
                    interpolate(clipOut, 10, clipIn, 10, clipIn, 5, nearPlane);
                }
                numOutVerts = 3;
            } else if (count == 2) {
                if (!in0) {
                    System.arraycopy(clipIn, 5, clipOut, 0, 5);
                    System.arraycopy(clipIn, 10, clipOut, 5, 5);
                    interpolate(clipOut, 10, clipIn, 10, clipIn, 0, nearPlane);
                    interpolate(clipOut, 15, clipIn, 5, clipIn, 0, nearPlane);
                } else if (!in1) {
                    System.arraycopy(clipIn, 10, clipOut, 0, 5);
                    System.arraycopy(clipIn, 0, clipOut, 5, 5);
                    interpolate(clipOut, 10, clipIn, 0, clipIn, 5, nearPlane);
                    interpolate(clipOut, 15, clipIn, 10, clipIn, 5, nearPlane);
                } else {
                    System.arraycopy(clipIn, 0, clipOut, 0, 5);
                    System.arraycopy(clipIn, 5, clipOut, 5, 5);
                    interpolate(clipOut, 10, clipIn, 5, clipIn, 10, nearPlane);
                    interpolate(clipOut, 15, clipIn, 0, clipIn, 10, nearPlane);
                }
                numOutVerts = 4;
            }

            if (numOutVerts == 3) {
                boolean p0Valid = projectionStage.projectZeroAlloc(clipOut[0], clipOut[1], clipOut[2], camera, fb.width, fb.height, projTemp, 0);
                boolean p1Valid = projectionStage.projectZeroAlloc(clipOut[5], clipOut[6], clipOut[7], camera, fb.width, fb.height, projTemp, 3);
                boolean p2Valid = projectionStage.projectZeroAlloc(clipOut[10], clipOut[11], clipOut[12], camera, fb.width, fb.height, projTemp, 6);

                if (p0Valid && p1Valid && p2Valid) {
                    float cross = (projTemp[3] - projTemp[0]) * (projTemp[7] - projTemp[1]) - (projTemp[4] - projTemp[1]) * (projTemp[6] - projTemp[0]);
                    if (cross > 0) {
                        ensureBatchCapacity(visibleCount + 1);
                        int offset = visibleCount * 15;
                        batchBuffer[offset]      = projTemp[0];
                        batchBuffer[offset + 1]  = projTemp[1];
                        batchBuffer[offset + 2]  = projTemp[2];
                        batchBuffer[offset + 3]  = clipOut[3];
                        batchBuffer[offset + 4]  = 1.0f - clipOut[4];

                        batchBuffer[offset + 5]  = projTemp[3];
                        batchBuffer[offset + 6]  = projTemp[4];
                        batchBuffer[offset + 7]  = projTemp[5];
                        batchBuffer[offset + 8]  = clipOut[8];
                        batchBuffer[offset + 9]  = 1.0f - clipOut[9];

                        batchBuffer[offset + 10] = projTemp[6];
                        batchBuffer[offset + 11] = projTemp[7];
                        batchBuffer[offset + 12] = projTemp[8];
                        batchBuffer[offset + 13] = clipOut[13];
                        batchBuffer[offset + 14] = 1.0f - clipOut[14];

                        visibleCount++;
                    }
                }
            } else if (numOutVerts == 4) {
                boolean p0Valid = projectionStage.projectZeroAlloc(clipOut[0], clipOut[1], clipOut[2], camera, fb.width, fb.height, projTemp, 0);
                boolean p1Valid = projectionStage.projectZeroAlloc(clipOut[5], clipOut[6], clipOut[7], camera, fb.width, fb.height, projTemp, 3);
                boolean p2Valid = projectionStage.projectZeroAlloc(clipOut[10], clipOut[11], clipOut[12], camera, fb.width, fb.height, projTemp, 6);
                boolean p3Valid = projectionStage.projectZeroAlloc(clipOut[15], clipOut[16], clipOut[17], camera, fb.width, fb.height, projTemp, 9);

                if (p0Valid && p1Valid && p2Valid && p3Valid) {
                    // Triangle 1: p0, p1, p2
                    float cross1 = (projTemp[3] - projTemp[0]) * (projTemp[7] - projTemp[1]) - (projTemp[4] - projTemp[1]) * (projTemp[6] - projTemp[0]);
                    if (cross1 > 0) {
                        ensureBatchCapacity(visibleCount + 1);
                        int offset = visibleCount * 15;
                        batchBuffer[offset]      = projTemp[0];
                        batchBuffer[offset + 1]  = projTemp[1];
                        batchBuffer[offset + 2]  = projTemp[2];
                        batchBuffer[offset + 3]  = clipOut[3];
                        batchBuffer[offset + 4]  = 1.0f - clipOut[4];

                        batchBuffer[offset + 5]  = projTemp[3];
                        batchBuffer[offset + 6]  = projTemp[4];
                        batchBuffer[offset + 7]  = projTemp[5];
                        batchBuffer[offset + 8]  = clipOut[8];
                        batchBuffer[offset + 9]  = 1.0f - clipOut[9];

                        batchBuffer[offset + 10] = projTemp[6];
                        batchBuffer[offset + 11] = projTemp[7];
                        batchBuffer[offset + 12] = projTemp[8];
                        batchBuffer[offset + 13] = clipOut[13];
                        batchBuffer[offset + 14] = 1.0f - clipOut[14];

                        visibleCount++;
                    }

                    // Triangle 2: p0, p2, p3
                    float cross2 = (projTemp[6] - projTemp[0]) * (projTemp[10] - projTemp[1]) - (projTemp[7] - projTemp[1]) * (projTemp[9] - projTemp[0]);
                    if (cross2 > 0) {
                        ensureBatchCapacity(visibleCount + 1);
                        int offset = visibleCount * 15;
                        batchBuffer[offset]      = projTemp[0];
                        batchBuffer[offset + 1]  = projTemp[1];
                        batchBuffer[offset + 2]  = projTemp[2];
                        batchBuffer[offset + 3]  = clipOut[3];
                        batchBuffer[offset + 4]  = 1.0f - clipOut[4];

                        batchBuffer[offset + 5]  = projTemp[6];
                        batchBuffer[offset + 6]  = projTemp[7];
                        batchBuffer[offset + 7]  = projTemp[8];
                        batchBuffer[offset + 8]  = clipOut[13];
                        batchBuffer[offset + 9]  = 1.0f - clipOut[14];

                        batchBuffer[offset + 10] = projTemp[9];
                        batchBuffer[offset + 11] = projTemp[10];
                        batchBuffer[offset + 12] = projTemp[11];
                        batchBuffer[offset + 13] = clipOut[18];
                        batchBuffer[offset + 14] = 1.0f - clipOut[19];

                        visibleCount++;
                    }
                }
            }
        }

        if (visibleCount > 0) {
            rasterizer.drawTriangles(batchBuffer, visibleCount, material, fb);
        }
    }

    public void postProcess() {
        if (camera.depthVisualizer) {
            int w = fb.width;
            int h = fb.height;
            int[] pixels = fb.pixels;
            float[] zBuf = fb.zBuffer;
            float maxDepth = 1800.0f;
            java.util.stream.IntStream.range(0, h).parallel().forEach(y -> {
                int rowOffset = y * w;
                for (int x = 0; x < w; x++) {
                    int idx = rowOffset + x;
                    float d = zBuf[idx];
                    if (d >= 9999.0f) {
                        pixels[idx] = 0x000000;
                    } else {
                        float norm = (maxDepth - d) / maxDepth;
                        if (norm < 0.0f) norm = 0.0f;
                        if (norm > 1.0f) norm = 1.0f;
                        int val = (int)(norm * 255);
                        pixels[idx] = (val << 16) | (val << 8) | val;
                    }
                }
            });
        }

        if (camera.fisheyeEnabled && camera.fisheyeStrength != 0.0f) {
            int w = fb.width;
            int h = fb.height;
            int[] pixels = fb.pixels;
            int[] temp = fb.scratchPixels;
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
