package fastsoftware3d.ansi;

/**
 * Functional interface for writing a single coloured character cell.
 *
 * Allows FastAnsiImage (and similar converters) to write into any
 * cell-based target — FastTerminalScene, CanvasModel, a test buffer, etc. —
 * without depending on any specific rendering library.
 *
 * FastTerminalScene implements this directly:
 *   FastAnsiImage.draw(img, scene::writeCell, ...);
 *   // or, if FastTerminalScene implements CellConsumer:
 *   FastAnsiImage.draw(img, scene, ...);
 */
@FunctionalInterface
public interface CellConsumer {
    /**
     * Write one character cell.
     *
     * @param x   Column (0-based).
     * @param y   Row (0-based).
     * @param ch  Character to display.
     * @param fg  Foreground colour as 0xRRGGBB.
     * @param bg  Background colour as 0xRRGGBB.
     */
    void writeCell(int x, int y, int codepoint, int fg, int bg);
}
