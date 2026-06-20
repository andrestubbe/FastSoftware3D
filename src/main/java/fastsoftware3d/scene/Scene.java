package fastsoftware3d.scene;

import java.awt.Graphics2D;

public class Scene {

    private final SceneNode root = new SceneNode() {
        @Override
        protected void renderSelf(Renderer3D renderer, Transform worldTransform, Graphics2D g) {
            // Root node has no direct rendering.
        }
    };

    public SceneNode getRoot() {
        return root;
    }

    public void update(float deltaTime) {
        root.update(deltaTime);
    }

    public void render(Renderer3D renderer, Graphics2D g) {
        // System.out.println("Scene.render() called");
        root.render(renderer, new Transform(), g);
    }
}
