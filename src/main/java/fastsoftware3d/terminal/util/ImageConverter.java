package fastsoftware3d.terminal.util;

import fastsoftware3d.terminal.FastTerminalScene;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;

public class ImageConverter {

    /**
     * Converts a java.awt.image.BufferedImage into a FastTerminalScene.
     * 
     * @param originalImage The image to convert
     * @param targetCellWidth The width of the target scene in terminal cells
     * @param targetCellHeight The height of the target scene in terminal cells
     * @param useHalfBlocks If true, uses '▀' for double vertical resolution.
     * @return A pre-rendered FastTerminalScene containing the image.
     */
    public static FastTerminalScene convert(BufferedImage originalImage, int targetCellWidth, int targetCellHeight, boolean useHalfBlocks) {
        if (originalImage == null || targetCellWidth <= 0 || targetCellHeight <= 0) return null;
        
        int pixelW = targetCellWidth;
        int pixelH = useHalfBlocks ? targetCellHeight * 2 : targetCellHeight;
        
        BufferedImage scaled = new BufferedImage(pixelW, pixelH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.drawImage(originalImage, 0, 0, pixelW, pixelH, null);
        g.dispose();
        
        FastTerminalScene scene = new FastTerminalScene(0, 0, targetCellWidth, targetCellHeight);
        
        for (int r = 0; r < targetCellHeight; r++) {
            for (int c = 0; c < targetCellWidth; c++) {
                if (useHalfBlocks) {
                    int topRgb = scaled.getRGB(c, r * 2) & 0xFFFFFF;
                    int botRgb = scaled.getRGB(c, r * 2 + 1) & 0xFFFFFF;
                    scene.writeCell(c, r, '▀', topRgb, botRgb);
                } else {
                    int rgb = scaled.getRGB(c, r) & 0xFFFFFF;
                    scene.writeCell(c, r, '█', rgb, rgb);
                }
            }
        }
        
        return scene;
    }
}
