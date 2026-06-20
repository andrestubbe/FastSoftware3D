package fastsoftware3d.terminal;

import fastsoftware3d.ansi.FastANSI;
import fastsoftware3d.ascii.FastASCIIWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @class FastTerminalRenderer
 * @brief High-performance double-buffered ANSI rendering and screen compositing engine.
 *
 * Composites multiple FastTerminalScene layers and converts screen diff state transitions
 * to optimized 24-bit True Color ANSI escape streams.
 *
 * Rendering pipeline per frame:
 *   clear -> compositeScenes -> choose strategy -> syncPrevBuffers -> flushOutput
 *
 * Strategies (in priority order):
 *   1. Dirty-rectangles  - when dirtyRectanglesEnabled + diff mode + no forced redraw
 *   2. Full redraw       - when diffRenderingEnabled=false or forceFullRedraw=true
 *   3. Diff / double-buffer - default; only changed cells are emitted
 */
public final class FastTerminalRenderer {

    //  ANSI escape fragments 
    private static final String RGB_FG_PREFIX = FastANSI.CSI + "38;2;";
    private static final String RGB_BG_PREFIX = FastANSI.CSI + "48;2;";
    private static final String RGB_SUFFIX    = "m";

    //  Scene registry 
    private List<FastTerminalScene> scenes = new ArrayList<>();

    //  Dimensions 
    private int width;
    private int height;

    //  Composite buffers (current frame) 
    private int[] compositeCodepoints;
    private int[] compositeFg;
    private int[] compositeBg;

    //  Prev buffers (last flushed frame, for diff) 
    private int[] prevCodepoints;
    private int[] prevFg;
    private int[] prevBg;

    //  Rendering state 
    private boolean forceFullRedraw        = true;
    private boolean diffRenderingEnabled   = true;
    private boolean dirtyRectanglesEnabled = false;
    private int     lastFlushedBytes       = 0;

    //  Reused per-frame native output buffer (avoids per-frame allocation) 
    private byte[] outBuffer;
    private int outLen;

    // ════════════════════════════════════════════════════════════════════════
    // Construction / lifecycle
    // ════════════════════════════════════════════════════════════════════════

    /**
     * @brief Allocates all double-buffered structures.
     * @param width  Initial terminal column count.
     * @param height Initial terminal row count.
     */
    public FastTerminalRenderer(final int width, final int height) {
        this.width  = width;
        this.height = height;
        int cells = width * height;
        this.compositeCodepoints = new int[cells];
        this.compositeFg         = new int[cells];
        this.compositeBg         = new int[cells];
        this.prevCodepoints      = new int[cells];
        this.prevFg              = new int[cells];
        this.prevBg              = new int[cells];
        // 25 bytes per cell is plenty for extreme worst case: CSI 38;2;255;255;255m + CSI 48;2;255;255;255m + 4-byte UTF8
        this.outBuffer = new byte[cells * 40 + 1024]; 
        clear();
        clearPrev();
    }

    /** @brief Registers an overlay scene layer. */
    public void addScene(final FastTerminalScene scene) {
        this.scenes.add(scene);
    }

    // ════════════════════════════════════════════════════════════════════════
    // Main render entry point
    // ════════════════════════════════════════════════════════════════════════

    /**
     * @brief Composites all layers and writes changes to stdout.
     */
    public void render() {
        clear();
        compositeScenes();
        outLen = 0;

        if (dirtyRectanglesEnabled && diffRenderingEnabled && !forceFullRedraw) {
            if (renderDirtyRectangles()) {
                flushOutput();
            } else {
                lastFlushedBytes = 0;
            }
            return;
        }

        if (!diffRenderingEnabled || forceFullRedraw) {
            renderFullRedraw();
        } else {
            renderDiff();
        }

        syncPrevBuffers();
        flushOutput();
    }

    // ════════════════════════════════════════════════════════════════════════
    // Rendering strategies
    // ════════════════════════════════════════════════════════════════════════

    private boolean renderDirtyRectangles() {
        int minX = width, maxX = -1, minY = height, maxY = -1;
        final int cells = compositeCodepoints.length;

        for (int i = 0; i < cells; i++) {
            if (compositeCodepoints[i] != prevCodepoints[i]
                    | compositeFg[i] != prevFg[i]
                    | compositeBg[i] != prevBg[i]) {
                int y = i / width;
                int x = i - y * width;
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
            }
        }

        if (maxX == -1) return false;

        int curFg = -2, curBg = -2;
        for (int row = minY; row <= maxY; row++) {
            outLen += moveCursor(outBuffer, outLen, row, minX);
            curFg = -2; curBg = -2;
            int base = row * width;
            for (int col = minX; col <= maxX; col++) {
                int i  = base + col;
                int cp = compositeCodepoints[i];
                if (cp == -99) continue;
                int fg = compositeFg[i];
                int bg = compositeBg[i];
                if (fg != curFg) { outLen += emitFg(outBuffer, outLen, fg); curFg = fg; }
                if (bg != curBg) { outLen += emitBg(outBuffer, outLen, bg); curBg = bg; }
                if (Character.isValidCodePoint(cp)) {
                    outLen += FastASCIIWriter.writeUtf8(outBuffer, outLen, cp);
                } else {
                    outBuffer[outLen++] = ' ';
                }
            }
        }
        outLen += FastASCIIWriter.writeAscii(outBuffer, outLen, FastANSI.RESET);

        // Sync only the dirty region
        for (int row = minY; row <= maxY; row++) {
            int offset = row * width + minX;
            int len    = maxX - minX + 1;
            System.arraycopy(compositeCodepoints, offset, prevCodepoints, offset, len);
            System.arraycopy(compositeFg,         offset, prevFg,         offset, len);
            System.arraycopy(compositeBg,         offset, prevBg,         offset, len);
        }
        return true;
    }

    private void renderFullRedraw() {
        outLen += FastASCIIWriter.writeAscii(outBuffer, outLen, FastANSI.CURSOR_HOME);
        int curFg = -2, curBg = -2;
        final int cells = compositeCodepoints.length;

        for (int i = 0; i < cells; i++) {
            int cp = compositeCodepoints[i];
            if (cp != -99) {
                int fg = compositeFg[i];
                int bg = compositeBg[i];
                if (fg != curFg) { outLen += emitFg(outBuffer, outLen, fg); curFg = fg; }
                if (bg != curBg) { outLen += emitBg(outBuffer, outLen, bg); curBg = bg; }
                if (Character.isValidCodePoint(cp)) {
                    outLen += FastASCIIWriter.writeUtf8(outBuffer, outLen, cp);
                } else {
                    outBuffer[outLen++] = ' ';
                }
            }
            // Newline at end of each row (skip last row to avoid scroll)
            if ((i + 1) % width == 0 && (i + 1) < cells) {
                outLen += FastASCIIWriter.writeAscii(outBuffer, outLen, FastANSI.RESET);
                outBuffer[outLen++] = '\n';
                curFg = -2; curBg = -2;
            }
        }
        outLen += FastASCIIWriter.writeAscii(outBuffer, outLen, FastANSI.RESET);
        forceFullRedraw = false;
    }

    private void renderDiff() {
        int curFg = -2, curBg = -2;
        int expectedPos = -1;
        final int cells = compositeCodepoints.length;

        for (int i = 0; i < cells; i++) {
            int cp = compositeCodepoints[i];
            int fg = compositeFg[i];
            int bg = compositeBg[i];

            if (cp == prevCodepoints[i] && fg == prevFg[i] && bg == prevBg[i]) continue;

            if (cp == -99) {
                expectedPos = ((i + 1) % width == 0) ? -1 : i + 1;
                continue;
            }

            if (i != expectedPos) {
                int gap = i - expectedPos;
                // Gap-fill: cheaper than a cursor jump for small same-row gaps
                if (expectedPos >= 0 && gap > 0 && gap <= 4
                        && (i / width) == ((expectedPos - 1) / width)) {
                    for (int g = expectedPos; g < i; g++) {
                        int gcp = compositeCodepoints[g];
                        if (gcp == -99) continue;
                        int gfg = compositeFg[g];
                        int gbg = compositeBg[g];
                        if (gfg != curFg) { outLen += emitFg(outBuffer, outLen, gfg); curFg = gfg; }
                        if (gbg != curBg) { outLen += emitBg(outBuffer, outLen, gbg); curBg = gbg; }
                        if (Character.isValidCodePoint(gcp)) {
                            outLen += FastASCIIWriter.writeUtf8(outBuffer, outLen, gcp);
                        } else {
                            outBuffer[outLen++] = ' ';
                        }
                    }
                } else {
                    outLen += moveCursor(outBuffer, outLen, i / width, i % width);
                }
            }

            if (fg != curFg) { outLen += emitFg(outBuffer, outLen, fg); curFg = fg; }
            if (bg != curBg) { outLen += emitBg(outBuffer, outLen, bg); curBg = bg; }
            if (Character.isValidCodePoint(cp)) {
                outLen += FastASCIIWriter.writeUtf8(outBuffer, outLen, cp);
            } else {
                outBuffer[outLen++] = ' ';
            }

            expectedPos = ((i + 1) % width == 0) ? -1 : i + 1;
        }
        outLen += FastASCIIWriter.writeAscii(outBuffer, outLen, FastANSI.RESET);
    }

    // ════════════════════════════════════════════════════════════════════════
    // Pipeline helpers
    // ════════════════════════════════════════════════════════════════════════

    private void compositeScenes() {
        for (final FastTerminalScene scene : scenes) {
            if (scene.isDirty()) {
                scene.update();
                scene.setDirty(false);
            }
            insertScene(scene);
        }
    }

    private void syncPrevBuffers() {
        System.arraycopy(compositeCodepoints, 0, prevCodepoints, 0, compositeCodepoints.length);
        System.arraycopy(compositeFg,         0, prevFg,         0, compositeFg.length);
        System.arraycopy(compositeBg,         0, prevBg,         0, compositeBg.length);
    }

    private void flushOutput() {
        if (outLen == 0) { lastFlushedBytes = 0; return; }
        lastFlushedBytes = outLen;
        System.out.write(outBuffer, 0, outLen);
        System.out.flush();
    }

    // ════════════════════════════════════════════════════════════════════════
    // ANSI emission helpers
    // ════════════════════════════════════════════════════════════════════════

    private static int emitFg(final byte[] buf, int offset, final int fg) {
        if (fg == -1) {
            return FastASCIIWriter.writeAscii(buf, offset, FastANSI.FG_DEFAULT);
        }
        int start = offset;
        offset += FastASCIIWriter.writeAscii(buf, offset, RGB_FG_PREFIX);
        offset += FastASCIIWriter.writeInt(buf, offset, (fg >>> 16) & 0xFF);
        buf[offset++] = ';';
        offset += FastASCIIWriter.writeInt(buf, offset, (fg >>> 8) & 0xFF);
        buf[offset++] = ';';
        offset += FastASCIIWriter.writeInt(buf, offset, fg & 0xFF);
        buf[offset++] = 'm';
        return offset - start;
    }

    private static int emitBg(final byte[] buf, int offset, final int bg) {
        if (bg == -1) {
            return FastASCIIWriter.writeAscii(buf, offset, FastANSI.BG_DEFAULT);
        }
        int start = offset;
        offset += FastASCIIWriter.writeAscii(buf, offset, RGB_BG_PREFIX);
        offset += FastASCIIWriter.writeInt(buf, offset, (bg >>> 16) & 0xFF);
        buf[offset++] = ';';
        offset += FastASCIIWriter.writeInt(buf, offset, (bg >>> 8) & 0xFF);
        buf[offset++] = ';';
        offset += FastASCIIWriter.writeInt(buf, offset, bg & 0xFF);
        buf[offset++] = 'm';
        return offset - start;
    }

    private static int moveCursor(final byte[] buf, int offset, final int row, final int col) {
        int start = offset;
        offset += FastASCIIWriter.writeAscii(buf, offset, FastANSI.CSI);
        offset += FastASCIIWriter.writeInt(buf, offset, row + 1);
        buf[offset++] = ';';
        offset += FastASCIIWriter.writeInt(buf, offset, col + 1);
        buf[offset++] = 'H';
        return offset - start;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Scene compositing
    // ════════════════════════════════════════════════════════════════════════

    private void insertScene(final FastTerminalScene scene) {
        final int[] srcCp  = scene.getCodepointBuffer();
        final int[] srcFg  = scene.getFgBuffer();
        final int[] srcBg  = scene.getBgBuffer();
        final int srcWidth  = scene.getWidth();
        final int srcHeight = scene.getHeight();
        final int dstX      = scene.getX();
        final int dstY      = scene.getY();
        final boolean transparent = scene.isTransparentBackground();

        for (int row = 0; row < srcHeight; row++) {
            final int dstRow = dstY + row;
            if (dstRow < 0 || dstRow >= height) continue;

            int srcPos = row * srcWidth;
            int dstPos = dstRow * width + dstX;
            int length = srcWidth;

            // Clip left edge
            if (dstX < 0) {
                srcPos -= dstX;
                length += dstX;
                dstPos  = dstRow * width;
            }
            // Clip right edge
            if (dstX + length > width) length = width - dstX;
            if (length <= 0) continue;

            if (!transparent) {
                System.arraycopy(srcCp, srcPos, compositeCodepoints, dstPos, length);
                System.arraycopy(srcFg, srcPos, compositeFg,         dstPos, length);
                System.arraycopy(srcBg, srcPos, compositeBg,         dstPos, length);
            } else {
                for (int i = 0; i < length; i++) {
                    int scp = srcCp[srcPos + i];
                    int sfg = srcFg[srcPos + i];
                    int sbg = srcBg[srcPos + i];
                    if (scp == ' ' && sfg == -1 && sbg == -1) continue;
                    compositeCodepoints[dstPos + i] = scp;
                    compositeFg        [dstPos + i] = sfg;
                    compositeBg        [dstPos + i] = sbg;
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Buffer management
    // ════════════════════════════════════════════════════════════════════════

    public void clear() {
        Arrays.fill(compositeCodepoints, ' ');
        Arrays.fill(compositeFg,         -1);
        Arrays.fill(compositeBg,         -1);
    }

    public void clearPrev() {
        if (prevCodepoints != null) {
            Arrays.fill(prevCodepoints, ' ');
            Arrays.fill(prevFg,         -1);
            Arrays.fill(prevBg,         -1);
        }
        forceFullRedraw = true;
    }

    public void suppressInitialFullRedraw() {
        forceFullRedraw = false;
    }

    public boolean resize(final int newWidth, final int newHeight) {
        if (newWidth <= 0 || newHeight <= 0)                       return false;
        if (newWidth == width && newHeight == height)              return false;

        width  = newWidth;
        height = newHeight;
        int cells = newWidth * newHeight;

        compositeCodepoints = new int[cells];
        compositeFg         = new int[cells];
        compositeBg         = new int[cells];
        prevCodepoints      = new int[cells];
        prevFg              = new int[cells];
        prevBg              = new int[cells];
        
        outBuffer = new byte[cells * 40 + 1024];

        clear();
        clearPrev();
        return true;
    }

    public void dispose() {
        if (scenes != null) { scenes.clear(); scenes = null; }
        compositeCodepoints = null;
        compositeFg         = null;
        compositeBg         = null;
        prevCodepoints      = null;
        prevFg              = null;
        prevBg              = null;
        outBuffer           = null;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Overlay restore (shutdown hook use)
    // ════════════════════════════════════════════════════════════════════════

    public void renderAbsolute() {
        clear();
        compositeScenes();

        outLen = 0;

        // Find last row with any non-blank content (scan from bottom)
        int lastContentRow = 0;
        outer:
        for (int row = height - 1; row >= 0; row--) {
            int base = row * width;
            for (int col = 0; col < width; col++) {
                int i = base + col;
                if (!((compositeCodepoints[i] == ' ' || compositeCodepoints[i] == 0)
                        && compositeFg[i] == -1 && compositeBg[i] == -1)) {
                    lastContentRow = row;
                    break outer;
                }
            }
        }

        int curFg = -2, curBg = -2;
        for (int row = 0; row <= lastContentRow; row++) {
            outLen += moveCursor(outBuffer, outLen, row, 0);
            curFg = -2; curBg = -2;
            int base = row * width;
            for (int col = 0; col < width; col++) {
                int i  = base + col;
                int cp = compositeCodepoints[i];
                int fg = compositeFg[i];
                int bg = compositeBg[i];
                if (cp == -99) { outBuffer[outLen++] = ' '; continue; }
                if (fg != curFg) { outLen += emitFg(outBuffer, outLen, fg); curFg = fg; }
                if (bg != curBg) { outLen += emitBg(outBuffer, outLen, bg); curBg = bg; }
                if (Character.isValidCodePoint(cp)) {
                    outLen += FastASCIIWriter.writeUtf8(outBuffer, outLen, cp);
                } else {
                    outBuffer[outLen++] = ' ';
                }
            }
        }

        int promptRow = Math.min(lastContentRow + 2, height);
        outLen += FastASCIIWriter.writeAscii(outBuffer, outLen, FastANSI.RESET);
        outLen += moveCursor(outBuffer, outLen, promptRow - 1, 0); // moveCursor is 0-based internally, adds +1

        System.out.write(outBuffer, 0, outLen);
        System.out.flush();
    }

    // ════════════════════════════════════════════════════════════════════════
    // Accessors
    // ════════════════════════════════════════════════════════════════════════

    public boolean isDiffRenderingEnabled()    { return diffRenderingEnabled; }
    public boolean isDirtyRectanglesEnabled()  { return dirtyRectanglesEnabled; }
    public int     getLastFlushedBytes()       { return lastFlushedBytes; }
    public int     getWidth()                  { return width; }
    public int     getHeight()                 { return height; }

    public void setDiffRenderingEnabled(boolean enabled) {
        diffRenderingEnabled = enabled;
        if (!enabled) forceFullRedraw = true;
    }

    public void setDirtyRectanglesEnabled(boolean enabled) {
        dirtyRectanglesEnabled = enabled;
        if (enabled) clearPrev();
    }
}
