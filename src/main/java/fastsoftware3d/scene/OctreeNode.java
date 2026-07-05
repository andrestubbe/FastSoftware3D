package fastsoftware3d.scene;

import java.awt.Graphics2D;

public class OctreeNode extends SceneNode {
    private final float cx;
    private final float cy;
    private final float cz;
    private final float radius;

    public OctreeNode(float cx, float cy, float cz, float radius) {
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.radius = radius;
    }

    @Override
    public void render(Renderer3D renderer, Transform parentTransform, Graphics2D g) {
        // Octree nodes don't have local transforms, so worldTransform is exactly parentTransform
        float wx = cx + parentTransform.x;
        float wy = cy + parentTransform.y;
        float wz = cz + parentTransform.z;
        
        if (!renderer.getPipeline().isSphereInFrustum(wx, wy, wz, radius)) {
            return;
        }

        // If visible, proceed to render self and children normally
        renderSelf(renderer, parentTransform, g);
        for (SceneNode child : getChildren()) {
            child.render(renderer, parentTransform, g);
        }
    }

    @Override
    protected void renderSelf(Renderer3D renderer, Transform worldTransform, Graphics2D g) {
        // Nothing to render directly
    }
}
