package fastsoftware3d.scene;

import fastsoftware3d.core.RenderPipeline;
import fastsoftware3d.camera.Camera;
import fastsoftware3d.material.Material;
import fastsoftware3d.model.ObjLoader;

/**
 * Thin facade over RenderPipeline.
 * Keeps the public API that SceneNode subclasses (ModelNode, GridNode) and
 * SceneUtilities depend on, while all logic lives in RenderPipeline.
 */
public final class Renderer3D {

    private final RenderPipeline pipeline;

    public Renderer3D(RenderPipeline pipeline) {
        this.pipeline = pipeline;
    }

    // Frame lifecycle

    public void clear() {
        pipeline.clear();
    }

    // Coordinate helpers (used by SceneUtilities)

    public float[] transformToCamera(float wx, float wy, float wz) {
        return pipeline.transformToCamera(wx, wy, wz);
    }

    public float[] project(float[] cameraPoint) {
        return pipeline.project(cameraPoint);
    }

    // Rendering

    public void renderModel(ObjLoader.ModelData model, float x, float y, float z, float rotationY, Material material) {
        pipeline.renderModel(model, x, y, z, rotationY, material);
    }

    // Accessors

    public RenderPipeline getPipeline() {
        return pipeline;
    }

    public Camera getCamera() {
        return pipeline.getCamera();
    }

    public int getWidth() {
        return pipeline.getFramebuffer().width;
    }
}
