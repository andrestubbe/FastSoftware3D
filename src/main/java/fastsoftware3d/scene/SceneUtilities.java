package fastsoftware3d.scene;

import java.awt.BasicStroke;
import java.awt.Graphics2D;

public final class SceneUtilities {

    private SceneUtilities() {
    }

    public static void drawFloorGrid(Renderer3D renderer, Graphics2D g, Transform worldTransform, int halfGrid, float spacing, float floorY) {
        float offsetX = worldTransform.x;
        float offsetY = worldTransform.y;
        float offsetZ = worldTransform.z;

        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

        for (int i = -halfGrid; i <= halfGrid; i++) {
            float coord = i * spacing;

            drawWorldLine(g, renderer, coord + offsetX, floorY + offsetY, -halfGrid * spacing + offsetZ, coord + offsetX, floorY + offsetY, halfGrid * spacing + offsetZ, i == 0);

            drawWorldLine(g, renderer, -halfGrid * spacing + offsetX, floorY + offsetY, coord + offsetZ, halfGrid * spacing + offsetX, floorY + offsetY, coord + offsetZ, i == 0);
        }
    }

    private static void drawWorldLine(Graphics2D g, Renderer3D renderer, float x1, float y1, float z1, float x2, float y2, float z2, boolean isCenter) {
        float[] c1 = renderer.transformToCamera(x1, y1, z1);
        float[] c2 = renderer.transformToCamera(x2, y2, z2);

        if (c1[2] <= 0.1f && c2[2] <= 0.1f) return;

        if (c1[2] <= 0.1f) {
            float t = (0.1f - c1[2]) / (c2[2] - c1[2]);
            c1[0] = c1[0] + (c2[0] - c1[0]) * t;
            c1[1] = c1[1] + (c2[1] - c1[1]) * t;
            c1[2] = 0.1f;
        } else if (c2[2] <= 0.1f) {
            float t = (0.1f - c2[2]) / (c1[2] - c2[2]);
            c2[0] = c2[0] + (c1[0] - c2[0]) * t;
            c2[1] = c2[1] + (c1[1] - c2[1]) * t;
            c2[2] = 0.1f;
        }

        float[] p1 = renderer.project(c1);
        float[] p2 = renderer.project(c2);
        if (p1 == null || p2 == null) return;

        if (isCenter) {
            g.setColor(java.awt.Color.WHITE);
            g.setStroke(new BasicStroke(2.0f));
        } else {
            g.setColor(java.awt.Color.GRAY);
            g.setStroke(new BasicStroke(1.0f));
        }

        g.drawLine((int) p1[0], (int) p1[1], (int) p2[0], (int) p2[1]);
    }
}
