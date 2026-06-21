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
        float[] res = new float[3];
        boolean valid = projectZeroAlloc(camPoint[0], camPoint[1], camPoint[2], cam, width, height, res, 0);
        return valid ? res : null;
    }

    /**
     * Project a camera-space point into screen-space with zero allocations.
     *
     * @param cx, cy, cz in camera space
     * @param cam        camera (for FOV)
     * @param width      framebuffer width
     * @param height     framebuffer height
     * @param dest       destination array
     * @param destOff    offset in destination array
     * @return true if point is in front of the camera and projected successfully, false otherwise
     */
    public boolean projectZeroAlloc(float cx, float cy, float cz, Camera cam, int width, int height, float[] dest, int destOff) {
        if (cz < 2.0f) return false;

        float focalLength = (float) (width / 2.0f / Math.tan(Math.toRadians(cam.fov / 2.0f)));
        float scale = focalLength / cz;

        dest[destOff]     = width  / 2.0f + cx * scale;
        dest[destOff + 1] = height / 2.0f - cy * scale;
        dest[destOff + 2] = cz;

        return true;
    }
}
