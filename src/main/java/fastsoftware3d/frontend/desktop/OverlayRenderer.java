package fastsoftware3d.frontend.desktop;

import fastsoftware3d.camera.Camera;
import fastsoftware3d.camera.CameraController;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;

/**
 * Renders overlay HUD information on top of the 3D scene.
 * Displays FPS, camera position, camera orientation, resolution mode, anti-aliasing settings.
 */
public class OverlayRenderer {

    private static final Font FONT = new Font("Monospaced", Font.PLAIN, 12);
    private static final Color TEXT_COLOR = new Color(0, 255, 0); // Green
    private static final Color BG_COLOR = new Color(0, 0, 0, 180); // Semi-transparent black

    public static void drawOverlay(Graphics2D g, int fps, Camera camera, boolean lowResMode, CameraController controller) {
        int padding = 20;
        int lineHeight = 15;
        int y = padding;

        g.setFont(FONT);
        g.setColor(TEXT_COLOR);

        // Draw semi-transparent background for text area
        int textWidth = 350;
        int textHeight = 7 * lineHeight + 10;
        g.setColor(BG_COLOR);
        g.fillRect(padding - 5, y - lineHeight, textWidth, textHeight);

        // Reset color for text
        g.setColor(TEXT_COLOR);

        // Line 1: FPS
        g.drawString(String.format("FPS: %d", fps), padding, y);
        y += lineHeight;

        // Line 2: Camera Position
        g.drawString(String.format("Pos: (%.1f, %.1f, %.1f)", camera.x, camera.y, camera.z), padding, y);
        y += lineHeight;

        // Line 3: Camera Orientation
        g.drawString(String.format("Yaw: %.2f  Pitch: %.2f", camera.yaw, camera.pitch), padding, y);
        y += lineHeight;

        // Line 4: FOV
        g.drawString(String.format("FOV: %.1f°", controller.baseFov), padding, y);
        y += lineHeight;

        // Line 5: Resolution mode
        String resMode = lowResMode ? "LOW-RES (120x60)" : "FULL (1173x610)";
        g.drawString(String.format("Resolution: %s (Press 'P' to Toggle)", resMode), padding, y);
        y += lineHeight;

        // Line 6: Anti-Aliasing
        g.drawString(String.format("Anti-Aliasing: %s (Press 'O' to Toggle)", controller.ssaaFactor == 1 ? "None (1x)" : (controller.ssaaFactor + "x SSAA")), padding, y);
        y += lineHeight;

        // Line 7: Controls hint
        g.drawString("WASD=Move  Arrows=Rotate  Mouse=Look  +/-=FOV", padding, y);
    }
}
