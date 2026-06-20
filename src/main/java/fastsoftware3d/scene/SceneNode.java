package fastsoftware3d.scene;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class SceneNode {
    private SceneNode parent;
    private final List<SceneNode> children = new ArrayList<>();
    protected final Transform transform = new Transform();

    public Transform getTransform() {
        return transform;
    }

    public SceneNode getParent() {
        return parent;
    }

    public List<SceneNode> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public void addChild(SceneNode child) {
        if (child == null) {
            return;
        }
        if (child.parent != null) {
            child.parent.children.remove(child);
        }
        child.parent = this;
        children.add(child);
    }

    public void removeChild(SceneNode child) {
        if (child != null && children.remove(child)) {
            child.parent = null;
        }
    }

    public void update(float deltaTime) {
        updateSelf(deltaTime);
        for (SceneNode child : children) {
            child.update(deltaTime);
        }
    }

    protected void updateSelf(float deltaTime) {
    }

    public void render(Renderer3D renderer, Transform parentTransform, Graphics2D g) {
        Transform worldTransform = Transform.combine(parentTransform, transform);
        renderSelf(renderer, worldTransform, g);
        // System.out.println("  SceneNode.render() - " + children.size() + " children");
        for (SceneNode child : children) {
            child.render(renderer, worldTransform, g);
        }
    }

    protected abstract void renderSelf(Renderer3D renderer, Transform worldTransform, Graphics2D g);
}
