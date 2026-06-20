package fastsoftware3d.scene;

import fastsoftware3d.material.Material;
import fastsoftware3d.model.ObjLoader;
import java.awt.Graphics2D;

public class ModelNode extends SceneNode {

    private final ObjLoader.ModelData model;
    private final Material material;

    public ModelNode(ObjLoader.ModelData model, Material material) {
        this.model    = model;
        this.material = material;
    }

    @Override
    protected void renderSelf(Renderer3D renderer, Transform worldTransform, Graphics2D g) {
        // System.out.println("ModelNode.renderSelf() - model=" + (model != null ? model.faces.size() + " faces" : "null"));
        if (model != null && material != null) {
            renderer.renderModel(model,
                    worldTransform.x, worldTransform.y, worldTransform.z,
                    worldTransform.yaw,
                    material);
        }
    }
}
