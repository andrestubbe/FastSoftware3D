package fastsoftware3d.terminal;

import fastcore.FastCore;
import java.io.File;

/**
 * @class FastTerminal
 * @brief FastTerminal main native JNI boundary and capabilities manager.
 * 
 * Interacts with Win32 backend console interfaces to query terminal dimensions, 
 * configure raw console flags, track window coordinate offsets, focus states, and mouse bounds.
 */
public class FastTerminal {

    static {
        FastCore.loadLibrary("fastsoftware3d");
    }

    /**
     * @brief Queries the dynamic dimensions of the active console screen buffer.
     * 
     * @return jintArray containing [columns, rows], or null on JNI query failures.
     */
    public static native int[] getTerminalSize();

    /**
     * @brief Query dynamic console buffer dimensions with a safe premium fallback.
     * 
     * @param defaultCols Standard fallback column width.
     * @param defaultRows Standard fallback row height.
     * @return 2-element array containing [cols, rows].
     */
    public static int[] getWindowSize(int defaultCols, int defaultRows) {
        try {
            int[] size = getTerminalSize();
            if (size != null && size[0] > 0 && size[1] > 0) {
                return size;
            }
        } catch (Throwable ignored) {
        }
        return new int[]{defaultCols, defaultRows};
    }

    /**
     * @brief Configures standard Win32 console mode flags (toggling raw input modes).
     * 
     * @param enableRaw True to enable direct non-blocking raw mode, false to restore default console buffer flags.
     */
    public static native void setRawMode(boolean enableRaw);

    /**
     * @brief Configures high-precision Virtual Terminal raw modes for standard input/output.
     * 
     * @param enableRaw True to enable direct non-blocking raw mode with VT input, false to restore defaults.
     */
    public static native void setAnsiRawMode(boolean enableRaw);

    /**
     * @brief Retrieves detailed hardware rect boundaries, client offsets, and console font character cell sizes.
     * 
     * Indexes:
     * - [0]: rect.left (pixels)
     * - [1]: rect.top (pixels)
     * - [2]: clientOffset.x (pixels)
     * - [3]: clientOffset.y (pixels)
     * - [4]: fontWidth (pixels)
     * - [5]: fontHeight (pixels)
     * - [6]: clientWidth (pixels)
     * - [7]: clientHeight (pixels)
     * 
     * @return int array containing hardware console window info metrics.
     */
    public static native int[] getConsoleWindowInfo();

    /**
     * @brief Checks if our console window or parent host currently holds focus in Windows.
     * 
     * Walks owner and ancestor chains, making it fully compatible with modern host terminals like wt.exe.
     * 
     * @return True if terminal is focused, False otherwise.
     */
    public static native boolean isTerminalFocused();

    /**
     * @brief Determines if the OS mouse cursor is currently hovering within terminal boundaries.
     * 
     * @return True if hovering, False otherwise.
     */
    public static native boolean isMouseOverTerminal();

    /**
     * @brief Toggles system mouse pointer cursor visibility globally.
     * 
     * @param visible True to show the standard system mouse cursor, False to hide it.
     */
    public static native void setSystemCursorVisible(boolean visible);

    /**
     * @brief Reads the visible console screen buffer as a snapshot.
     *
     * Returns a flat int array with layout: [cols, rows, cp0, fg0, bg0, cp1, fg1, bg1, ...]
     * where each cell is a Unicode codepoint + 24-bit RGB foreground + 24-bit RGB background.
     * Total length = 2 + cols * rows * 3.
     *
     * Returns [0, 0] if the read fails (e.g. running inside Windows Terminal's pseudo-console).
     *
     * @return int[] Flat snapshot array, or [0, 0] on failure.
     */
    public static native int[] readConsoleOutput();

    /**
     * @brief Returns the current console cursor position as [col, row] (0-based).
     *
     * @return int[] [col, row], or [0, 0] on failure.
     */
    public static native int[] getCursorPosition();

    /**
     * @brief Sets the console window title.
     * 
     * @param title The new window title.
     */
    public static native void setTitle(String title);

    /**
     * @brief Captures the current visible console screen buffer as a FastTerminalScene.
     *
     * @param defaultCols Standard fallback column width.
     * @param defaultRows Standard fallback row height.
     * @return FastTerminalScene populated with the snapshot, or a transparent scene on failure.
     */
    public static FastTerminalScene captureScreen(int defaultCols, int defaultRows) {
        int w = defaultCols;
        int h = defaultRows;
        try {
            int[] size = getTerminalSize();
            if (size != null && size[0] > 0 && size[1] > 0) {
                w = size[0];
                h = size[1];
            }
        } catch (Throwable ignored) {}

        FastTerminalScene scene = new FastTerminalScene(0, 0, w, h);
        try {
            int[] snap = readConsoleOutput();
            if (snap != null && snap.length >= 2 && snap[0] > 0 && snap[1] > 0) {
                int sC = snap[0], sR = snap[1];
                int uC = Math.min(sC, w), uR = Math.min(sR, h);
                for (int r = 0; r < uR; r++) {
                    for (int c = 0; c < uC; c++) {
                        int b = 2 + (r * sC + c) * 3;
                        scene.writeCell(c, r, snap[b], snap[b+1], snap[b+2]);
                    }
                }
            }
        } catch (Throwable ignored) {}
        return scene;
    }
}
