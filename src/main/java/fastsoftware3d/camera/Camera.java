package fastsoftware3d.camera;

public class Camera {
    public float x;
    public float y;
    public float z;
    public float yaw;
    public float pitch;
    public float fov;

    public boolean fisheyeEnabled = false;
    public float fisheyeStrength = 0.15f;

    public Camera() {
        this(465.0f, 360.0f, -612.0f, -0.68f, -0.54f, 150.0f);
    }

    public Camera(float x, float y, float z, float yaw, float pitch, float fov) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.fov = fov;
    }

    public float[] transformToCamera(float wx, float wy, float wz) {
        float tx = wx - x;
        float ty = wy - y;
        float tz = wz - z;

        float cosY = (float) Math.cos(-yaw);
        float sinY = (float) Math.sin(-yaw);
        float x1 = tx * cosY - tz * sinY;
        float z1 = tx * sinY + tz * cosY;

        float cosP = (float) Math.cos(-pitch);
        float sinP = (float) Math.sin(-pitch);
        float x2 = x1;
        float y2 = ty * cosP - z1 * sinP;
        float z2 = ty * sinP + z1 * cosP;

        return new float[]{x2, y2, z2};
    }

    public float[] project(float[] cameraPoint, int width, int height) {
        float cx = cameraPoint[0];
        float cy = cameraPoint[1];
        float cz = cameraPoint[2];

        if (cz <= 0.05f) return null;

        float focalLength = (float) (width / 2.0f / Math.tan(Math.toRadians(fov / 2.0f)));
        float scale = focalLength / cz;
        float screenX = width / 2.0f + cx * scale;
        float screenY = height / 2.0f - cy * scale;

        return new float[]{screenX, screenY, cz};
    }
}
