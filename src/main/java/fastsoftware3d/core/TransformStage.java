package fastsoftware3d.core;

import fastsoftware3d.camera.Camera;

/**
 * Handles world → camera space transforms and simple backface checks.
 */
public final class TransformStage {

    /**
     * Transform a world-space point into camera-space.
     */
    public float[] worldToCamera(float wx, float wy, float wz, Camera cam) {
        float[] res = new float[3];
        worldToCameraZeroAlloc(wx, wy, wz, cam, res, 0);
        return res;
    }

    /**
     * Transform a world-space point into camera-space with zero allocations.
     */
    public void worldToCameraZeroAlloc(float wx, float wy, float wz, Camera cam, float[] dest, int offset) {
        float tx = wx - cam.x;
        float ty = wy - cam.y;
        float tz = wz - cam.z;

        // Yaw rotation around Y axis (by -cam.yaw for view space)
        float cosY = (float) Math.cos(-cam.yaw);
        float sinY = (float) Math.sin(-cam.yaw);
        float x1 = tx * cosY - tz * sinY;
        float z1 = tx * sinY + tz * cosY;

        // Pitch rotation around X axis (by -cam.pitch for view space)
        float cosP = (float) Math.cos(-cam.pitch);
        float sinP = (float) Math.sin(-cam.pitch);
        float x2 = x1;
        float y2 = ty * cosP - z1 * sinP;
        float z2 = ty * sinP + z1 * cosP;

        // Roll rotation around Z axis (by -cam.roll for view space)
        float cosR = (float) Math.cos(-cam.roll);
        float sinR = (float) Math.sin(-cam.roll);
        dest[offset]     = x2 * cosR - y2 * sinR;
        dest[offset + 1] = x2 * sinR + y2 * cosR;
        dest[offset + 2] = z2;
    }

    /**
     * Simple backface test in camera space using triangle vertices.
     * Returns true if triangle is facing away from the camera (normal pointing backwards).
     */
    public boolean isBackface(float[] v0, float[] v1, float[] v2) {
        float ax = v1[0] - v0[0];
        float ay = v1[1] - v0[1];
        float az = v1[2] - v0[2];

        float bx = v2[0] - v0[0];
        float by = v2[1] - v0[1];
        float bz = v2[2] - v0[2];

        // Normal = A × B
        float nx = ay * bz - az * by;
        float ny = az * bx - ax * bz;
        float nz = ax * by - ay * bx;

        // In camera space, camera looks down -Z.
        // If normal.z >= 0 → backface.
        return nz >= 0.0f;
    }
}
