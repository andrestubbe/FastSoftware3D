package fastsoftware3d.core;

import fastsoftware3d.camera.Camera;

/**
 * Handles camera-space → screen-space projection.
 */
public final class ProjectionStage {

    /**
     * Project a camera-space point into screen-space.
     *
     * @param camPoint [x, y, z] in camera space
     * @param cam      camera (for FOV)
     * @param width    framebuffer width
     * @param height   framebuffer height
     * @return [sx, sy, depth] screen-space coords (depth = -z)
     */
    public float[] project(float[] camPoint, Camera cam, int width, int height) {
        float x = camPoint[0];
        float y = camPoint[1];
        float z = camPoint[2];

        if (z < 0.1f) return null;

        float focalLength = (float) (width / 2.0f / Math.tan(Math.toRadians(cam.fov / 2.0f)));
        float scale = focalLength / z;

        float sx = width  / 2.0f + x * scale;
        float sy = height / 2.0f - y * scale;

        return new float[]{sx, sy, z};
    }
}
