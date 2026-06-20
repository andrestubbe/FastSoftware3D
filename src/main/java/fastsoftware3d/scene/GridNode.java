package fastsoftware3d.scene;

import java.awt.Graphics2D;

public class GridNode extends SceneNode {

    private final int halfGrid;
    private final float spacing;
    private final float floorY;

    public GridNode() {
        this.halfGrid = 5;
        this.spacing = 80.0f;
        this.floorY = -120.0f;
    }

    @Override
    protected void renderSelf(Renderer3D renderer, Transform worldTransform, Graphics2D g) {
        SceneUtilities.drawFloorGrid(renderer, g, worldTransform, halfGrid, spacing, floorY);
    }
}
