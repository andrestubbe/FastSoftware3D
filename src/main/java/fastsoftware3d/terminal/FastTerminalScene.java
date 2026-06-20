package fastsoftware3d.terminal;

import java.util.Arrays;

/**
 * @class FastTerminalScene
 * @brief Represents a high-performance grid viewport scene layer.
 * 
 * Manages primitive flat arrays for fast cell storage:
 * - `codepointBuffer`: Holds UTF-32 character codes.
 * - `fgBuffer`: Foreground true colors.
 * - `bgBuffer`: Background true colors.
 * 
 * Supports inline style processing via zero-allocation on-the-fly ANSI escapes.
 */
public class FastTerminalScene implements fastsoftware3d.ansi.CellConsumer {

    private int[] codepointBuffer;
    private int[] fgBuffer;
    private int[] bgBuffer;

    private int x;
    private int y;
    private int width;
    private int height;
    private Runnable updater;
    private boolean dirty;
    private boolean transparentBackground = false;

    /**
     * @brief Allocates all cell buffer layers.
     * 
     * @param x Initial absolute offset X coordinate.
     * @param y Initial absolute offset Y coordinate.
     * @param width Scene viewport width columns.
     * @param height Scene viewport height rows.
     */
    public FastTerminalScene(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.codepointBuffer = new int[width * height];
        this.fgBuffer = new int[width * height];
        this.bgBuffer = new int[width * height];
        this.dirty = true;
        this.clear();
    }

    /**
     * @brief Triggers the lazy frame update hook if registered.
     */
    public void update() {
        if (this.updater != null) {
            this.clear();
            this.updater.run();
        }
    }

    /**
     * @brief Resets all cells back to blank defaults: Space character, -1 foreground, -1 background.
     */
    public void clear() {
        Arrays.fill(this.codepointBuffer, ' ');
        Arrays.fill(this.fgBuffer, -1);
        Arrays.fill(this.bgBuffer, -1);
    }

    /**
     * @brief Dynamically resizes scene arrays in-place.
     * 
     * @param newWidth New column width.
     * @param newHeight New row height.
     * @return True if a resizing transition actually happened, false otherwise.
     */
    public boolean resize(final int newWidth, final int newHeight) {
        if (newWidth <= 0 || newHeight <= 0) return false;
        if (newWidth == this.width && newHeight == this.height) return false;

        this.width = newWidth;
        this.height = newHeight;
        this.codepointBuffer = new int[newWidth * newHeight];
        this.fgBuffer = new int[newWidth * newHeight];
        this.bgBuffer = new int[newWidth * newHeight];
        this.clear();
        this.dirty = true;
        return true;
    }

    /**
     * @brief Adjusts scene's rendering offsets in screen space coordinates.
     * 
     * @param newX Viewport offset columns.
     * @param newY Viewport offset rows.
     */
    public void setPosition(final int newX, final int newY) {
        this.x = newX;
        this.y = newY;
        this.dirty = true;
    }

    /**
     * @brief Writes a specific cell's formatting parameters, performing safe boundary clips.
     * 
     * @param col Target cell column index.
     * @param row Target cell row index.
     * @param codepoint UTF-32 character value.
     * @param fg 24-bit True Color foreground value.
     * @param bg 24-bit True Color background value.
     */
    public void writeCell(int col, int row, int codepoint, int fg, int bg) {
        if (col >= 0 && col < this.width && row >= 0 && row < this.height) {
            int idx = row * this.width + col;
            this.codepointBuffer[idx] = codepoint;
            if (fg != -2) this.fgBuffer[idx] = fg;
            if (bg != -2) this.bgBuffer[idx] = bg;
        }
    }

    /**
     * @brief Writes a standard string sequentially to cells, tracking double-wide continuation boundaries.
     * 
     * @param startCol Starting cell column.
     * @param row Target row.
     * @param text Raw source string.
     * @param fg 24-bit True Color foreground.
     * @param bg 24-bit True Color background.
     */
    public void writeString(int startCol, int row, String text, int fg, int bg) {
        if (row < 0 || row >= this.height) return;
        int len = text.length();
        int col = startCol;
        for (int i = 0; i < len; ) {
            if (col >= this.width) break;
            int cp = text.codePointAt(i);
            int width = fastemojis.FastEmojis.getWidth(cp);
            
            this.writeCell(col, row, cp, fg, bg);
            if (width == 2 && col + 1 < this.width) {
                this.writeCell(col + 1, row, -99, fg, bg); // Native continuation cell marker
            }
            
            col += width;
            i += Character.charCount(cp);
        }
    }

    private static final int[] ANSI_COLORS_24BIT = {
        0x000000, // 0: Black
        0xCD3131, // 1: Red
        0x0DBC79, // 2: Green
        0xE5E510, // 3: Yellow
        0x2472C8, // 4: Blue
        0xBC3FBC, // 5: Magenta
        0x11A8CD, // 6: Cyan
        0xE5E5E5, // 7: White
        0x666666, // 8: Bright Black (Gray)
        0xF14C4C, // 9: Bright Red
        0x23D18B, // 10: Bright Green
        0xF5F543, // 11: Bright Yellow
        0x3B8EEA, // 12: Bright Blue
        0xD670D6, // 13: Bright Magenta
        0x29B8DB, // 14: Bright Cyan
        0xFFFFFF  // 15: Bright White
    };

    /**
     * @brief Interpolates and normalizes 4-bit and 8-bit ANSI codes into standard True Color values.
     * 
     * @param colorType Code color format type (0: 4-bit, 1: 8-bit, 2: 24-bit).
     * @param r Red parameter (or ANSI index if colorType < 2).
     * @param g Green parameter.
     * @param b Blue parameter.
     * @param defaultColor Standard default fallback color.
     * @return Packed 24-bit True Color int.
     */
    private static int toTrueColor(int colorType, int r, int g, int b, int defaultColor) {
        if (colorType == 0) { // 4-bit
            if (r == -1) return defaultColor;
            if (r >= 0 && r < 16) return ANSI_COLORS_24BIT[r];
            return defaultColor;
        } else if (colorType == 1) { // 8-bit
            if (r >= 0 && r < 16) return ANSI_COLORS_24BIT[r];
            if (r >= 16 && r <= 231) {
                int idx = r - 16;
                int red = (idx / 36) * 51;
                int green = ((idx % 36) / 6) * 51;
                int blue = (idx % 6) * 51;
                return (red << 16) | (green << 8) | blue;
            }
            if (r >= 232 && r <= 255) {
                int gray = 8 + (r - 232) * 10;
                return (gray << 16) | (gray << 8) | gray;
            }
            return defaultColor;
        } else if (colorType == 2) { // 24-bit True Color RGB
            return (r << 16) | (g << 8) | b;
        }
        return defaultColor;
    }

    /**
     * @brief Writes a styled ANSI string, parsing tokens in a zero-allocation parsing sweep.
     * 
     * @param startCol Starting column coordinate.
     * @param row Target row.
     * @param text Raw source ANSI formatting string.
     * @param defaultFg Default fallback foreground color.
     * @param defaultBg Default fallback background color.
     */
    public void writeAnsiString(int startCol, int row, String text, int defaultFg, int defaultBg) {
        if (row < 0 || row >= this.height) return;
        
        final int[] col = { startCol };
        final int[] activeFg = { defaultFg };
        final int[] activeBg = { defaultBg };

        fastsoftware3d.ansi.FastANSI.parse(text, new fastsoftware3d.ansi.FastANSI.ANSIListener() {
            @Override
            public void onText(CharSequence text, int start, int end) {
                for (int i = start; i < end; ) {
                    if (col[0] >= width) break;
                    int cp = Character.codePointAt(text, i);
                    int charWidth = fastemojis.FastEmojis.getWidth(cp);
                    
                    writeCell(col[0], row, cp, activeFg[0], activeBg[0]);
                    if (charWidth == 2 && col[0] + 1 < width) {
                        writeCell(col[0] + 1, row, -99, activeFg[0], activeBg[0]);
                    }
                    
                    col[0] += charWidth;
                    i += Character.charCount(cp);
                }
            }

            @Override
            public void onReset() {
                activeFg[0] = defaultFg;
                activeBg[0] = defaultBg;
            }

            @Override
            public void onBold(boolean enable) {}
            @Override
            public void onItalic(boolean enable) {}
            @Override
            public void onUnderline(boolean enable) {}
            @Override
            public void onBlink(boolean enable) {}
            @Override
            public void onInvert(boolean enable) {}
            @Override
            public void onHide(boolean enable) {}
            @Override
            public void onStrikethrough(boolean enable) {}

            @Override
            public void onForegroundColor(int colorType, int r, int g, int b) {
                activeFg[0] = toTrueColor(colorType, r, g, b, defaultFg);
            }

            @Override
            public void onBackgroundColor(int colorType, int r, int g, int b) {
                activeBg[0] = toTrueColor(colorType, r, g, b, defaultBg);
            }

            @Override
            public void onCursorPosition(int row, int col) {}
            @Override
            public void onCursorUp(int count) {}
            @Override
            public void onCursorDown(int count) {}
            @Override
            public void onCursorForward(int count) {}
            @Override
            public void onCursorBackward(int count) {}
            @Override
            public void onCursorNextLine(int count) {}
            @Override
            public void onCursorPrevLine(int count) {}
            @Override
            public void onCursorHorizontalAbsolute(int col) {}
            @Override
            public void onEraseInDisplay(int mode) {}
            @Override
            public void onEraseInLine(int mode) {}
            @Override
            public void onScrollUp(int count) {}
            @Override
            public void onScrollDown(int count) {}
            @Override
            public void onPrivateMode(int mode, boolean enable) {}
            @Override
            public void onDeviceStatusReport() {}
            @Override
            public void onWindowTitle(CharSequence title, int start, int end) {}
            @Override
            public void onUnsupportedSequence(CharSequence raw, int start, int end) {}
        });
    }

    /**
     * @brief Gets dirty flag state.
     * @return True if modified, False otherwise.
     */
    public boolean isDirty() {
        return this.dirty;
    }

    /**
     * @brief Manually flags dirty composition states.
     * @param dirty Target dirty flag state.
     */
    public void setDirty(final boolean dirty) {
        this.dirty = dirty;
    }

    /**
     * @brief Sets lazy update hook runnable.
     * @param updater Target update callback.
     */
    public void setUpdater(final Runnable updater) {
        this.updater = updater;
    }

    /**
     * @brief Gets the low-level character buffer array.
     * @return Codepoints array.
     */
    public int[] getCodepointBuffer() {
        return this.codepointBuffer;
    }

    /**
     * @brief Gets the low-level foreground array.
     * @return Foreground colors array.
     */
    public int[] getFgBuffer() {
        return this.fgBuffer;
    }

    /**
     * @brief Gets the low-level background array.
     * @return Background colors array.
     */
    public int[] getBgBuffer() {
        return this.bgBuffer;
    }

    /**
     * @brief Gets column offset X.
     * @return Columns coordinate.
     */
    public int getX() {
        return this.x;
    }

    /**
     * @brief Gets row offset Y.
     * @return Rows coordinate.
     */
    public int getY() {
        return this.y;
    }

    /**
     * @brief Gets scene width.
     * @return Column count.
     */
    public int getWidth() {
        return this.width;
    }

    /**
     * @brief Gets scene height.
     * @return Row count.
     */
    public int getHeight() {
        return this.height;
    }

    /**
     * @brief Disposes and dereferences buffers inside the scene layer.
     */
    public void dispose() {
        this.codepointBuffer = null;
        this.fgBuffer = null;
        this.bgBuffer = null;
        this.updater = null;
    }

    /**
     * @brief Returns whether this scene uses transparent compositing.
     *
     * When true, cells that are blank (space character, fg=-1, bg=-1) are treated
     * as transparent during compositing — the layer below shows through those cells.
     *
     * @return True if transparent compositing is enabled.
     */
    public boolean isTransparentBackground() {
        return this.transparentBackground;
    }

    /**
     * @brief Enables or disables transparent compositing for this scene.
     *
     * When enabled, blank cells (space, fg=-1, bg=-1) are skipped during compositing
     * so lower layers remain visible through them. Useful for overlay scenes that
     * should only paint specific regions (e.g. a floating panel) without covering
     * the rest of the screen.
     *
     * @param transparent True to enable transparency, false for opaque (default).
     */
    public void setTransparentBackground(boolean transparent) {
        this.transparentBackground = transparent;
    }

    /**
     * @brief Writes a cell with alpha blending (Alpha-Compositing).
     * 
     * If the foreground or background alpha is less than 1.0, it is blended
     * with the existing color in the scene buffers.
     * 
     * @param col Target column.
     * @param row Target row.
     * @param codepoint UTF-32 character value.
     * @param fg Foreground color.
     * @param bg Background color.
     * @param fgAlpha Foreground alpha opacity (0.0 to 1.0).
     * @param bgAlpha Background alpha opacity (0.0 to 1.0).
     */
    public void writeCellAlpha(int col, int row, int codepoint, int fg, int bg, double fgAlpha, double bgAlpha) {
        if (col >= 0 && col < this.width && row >= 0 && row < this.height) {
            int idx = row * this.width + col;
            
            // Blend foreground if needed
            if (fgAlpha < 1.0) {
                int oldFg = this.fgBuffer[idx];
                if (oldFg == -1) oldFg = 0x000000;
                fg = blendColor(oldFg, fg, fgAlpha);
            }
            
            // Blend background if needed
            if (bgAlpha < 1.0) {
                int oldBg = this.bgBuffer[idx];
                if (oldBg == -1) oldBg = 0x000000;
                bg = blendColor(oldBg, bg, bgAlpha);
            }
            
            this.codepointBuffer[idx] = codepoint;
            this.fgBuffer[idx] = fg;
            this.bgBuffer[idx] = bg;
        }
    }

    /**
     * @brief Writes a cell with packed 32-bit ARGB colors (Alpha-Compositing).
     * 
     * Colors are in the format 0xAARRGGBB. If the alpha byte AA is 0, it is treated
     * as fully opaque unless it is explicitly 0x00000000 (fully transparent black).
     * 
     * @param col Target column.
     * @param row Target row.
     * @param codepoint UTF-32 character value.
     * @param fgPacked Packed 32-bit ARGB foreground color.
     * @param bgPacked Packed 32-bit ARGB background color.
     */
    public void writeCellARGB(int col, int row, int codepoint, int fgPacked, int bgPacked) {
        if (col >= 0 && col < this.width && row >= 0 && row < this.height) {
            int idx = row * this.width + col;
            
            // Extract alpha from fg
            int fgAlphaByte = (fgPacked >>> 24) & 0xFF;
            int fgColor = fgPacked & 0xFFFFFF;
            if (fgAlphaByte == 0 && fgPacked != 0) {
                fgAlphaByte = 255;
            }
            if (fgAlphaByte > 0) {
                if (fgAlphaByte < 255) {
                    int oldFg = this.fgBuffer[idx];
                    if (oldFg == -1) oldFg = 0x000000;
                    fgColor = blendColor(oldFg, fgColor, fgAlphaByte / 255.0);
                }
                this.fgBuffer[idx] = fgColor;
            }
            
            // Extract alpha from bg
            int bgAlphaByte = (bgPacked >>> 24) & 0xFF;
            int bgColor = bgPacked & 0xFFFFFF;
            if (bgAlphaByte == 0 && bgPacked != 0) {
                bgAlphaByte = 255;
            }
            if (bgAlphaByte > 0) {
                if (bgAlphaByte < 255) {
                    int oldBg = this.bgBuffer[idx];
                    if (oldBg == -1) oldBg = 0x000000;
                    bgColor = blendColor(oldBg, bgColor, bgAlphaByte / 255.0);
                }
                this.bgBuffer[idx] = bgColor;
            }
            
            if (codepoint != ' ' || fgAlphaByte > 0) {
                this.codepointBuffer[idx] = codepoint;
            }
        }
    }

    /**
     * @brief Writes a standard string sequentially to cells with alpha blending.
     * 
     * @param startCol Starting cell column.
     * @param row Target row.
     * @param text Raw source string.
     * @param fg Foreground color.
     * @param bg Background color.
     * @param fgAlpha Foreground alpha.
     * @param bgAlpha Background alpha.
     */
    public void writeStringAlpha(int startCol, int row, String text, int fg, int bg, double fgAlpha, double bgAlpha) {
        if (row < 0 || row >= this.height) return;
        int len = text.length();
        int col = startCol;
        for (int i = 0; i < len; ) {
            if (col >= this.width) break;
            int cp = text.codePointAt(i);
            int width = fastemojis.FastEmojis.getWidth(cp);
            
            this.writeCellAlpha(col, row, cp, fg, bg, fgAlpha, bgAlpha);
            if (width == 2 && col + 1 < this.width) {
                this.writeCellAlpha(col + 1, row, -99, fg, bg, fgAlpha, bgAlpha);
            }
            
            col += width;
            i += Character.charCount(cp);
        }
    }

    private int blendColor(int color1, int color2, double ratio) {
        if (ratio <= 0.0) return color1;
        if (ratio >= 1.0) return color2;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int r = (int) (r1 * (1.0 - ratio) + r2 * ratio);
        int g = (int) (g1 * (1.0 - ratio) + g2 * ratio);
        int b = (int) (b1 * (1.0 - ratio) + b2 * ratio);

        return (r << 16) | (g << 8) | b;
    }
}
