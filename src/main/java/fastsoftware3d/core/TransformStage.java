package fastsoftware3d.core;

import fastsoftware3d.camera.Camera;

/**
 * Handles world → camera space transforms and simple backface checks.
 */
public final class TransformStage {

    private float m00, m01, m02;
    private float m10, m11, m12;
    private float m20, m21, m22;

    public void prepare(Camera cam) {
        float cosY = (float) Math.cos(-cam.yaw);
        float sinY = (float) Math.sin(-cam.yaw);
        float cosP = (float) Math.cos(-cam.pitch);
        float sinP = (float) Math.sin(-cam.pitch);
        float cosR = (float) Math.cos(-cam.roll);
        float sinR = (float) Math.sin(-cam.roll);

        // Yaw
        float y00 = cosY, y01 = 0, y02 = -sinY;
        float y10 = 0,    y11 = 1, y12 = 0;
        float y20 = sinY, y21 = 0, y22 = cosY;

        // Pitch
        float p00 = 1, p01 = 0,     p02 = 0;
        float p10 = 0, p11 = cosP,  p12 = -sinP;
        float p20 = 0, p21 = sinP,  p22 = cosP;

        // Pitch * Yaw
        float py00 = p00*y00 + p01*y10 + p02*y20;
        float py01 = p00*y01 + p01*y11 + p02*y21;
        float py02 = p00*y02 + p01*y12 + p02*y22;

        float py10 = p10*y00 + p11*y10 + p12*y20;
        float py11 = p10*y01 + p11*y11 + p12*y21;
        float py12 = p10*y02 + p11*y12 + p12*y22;

        float py20 = p20*y00 + p21*y10 + p22*y20;
        float py21 = p20*y01 + p21*y11 + p22*y21;
        float py22 = p20*y02 + p21*y12 + p22*y22;

        // Roll
        float r00 = cosR, r01 = -sinR, r02 = 0;
        float r10 = sinR, r11 = cosR,  r12 = 0;
        float r20 = 0,    r21 = 0,     r22 = 1;

        // Roll * (Pitch * Yaw)
        m00 = r00*py00 + r01*py10 + r02*py20;
        m01 = r00*py01 + r01*py11 + r02*py21;
        m02 = r00*py02 + r01*py12 + r02*py22;

        m10 = r10*py00 + r11*py10 + r12*py20;
        m11 = r10*py01 + r11*py11 + r12*py21;
        m12 = r10*py02 + r11*py12 + r12*py22;

        m20 = r20*py00 + r21*py10 + r22*py20;
        m21 = r20*py01 + r21*py11 + r22*py21;
        m22 = r20*py02 + r21*py12 + r22*py22;
    }

    /**
     * Transform a world-space point into camera-space.
     * Note: prepare() must have been called beforehand.
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

        // Apply precomputed combined rotation matrix
        dest[offset]     = tx * m00 + ty * m01 + tz * m02;
        dest[offset + 1] = tx * m10 + ty * m11 + tz * m12;
        dest[offset + 2] = tx * m20 + ty * m21 + tz * m22;
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
