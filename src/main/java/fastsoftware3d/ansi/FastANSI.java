package fastsoftware3d.ansi;

/**
 * High-Performance, Zero-Allocation ANSI and VT100/VT220/Xterm Escape Sequence Parser.
 * Processes characters procedurally without object instantiation, calling back listeners with primitive state values.
 */
public class FastANSI {

    // Color Type Constants — see FastSGR.COLOR_TYPE_*
    public static final int COLOR_TYPE_4BIT  = FastSGR.COLOR_TYPE_4BIT;
    public static final int COLOR_TYPE_8BIT  = FastSGR.COLOR_TYPE_8BIT;
    public static final int COLOR_TYPE_24BIT = FastSGR.COLOR_TYPE_24BIT;

    // --- ANSI Control & Style Constants ---
    public static final String ESC = "\033";
    public static final String CSI = "\033[";

    // Formatting & Styles — derived from FastSGR parameter numbers
    public static final String RESET            = CSI + FastSGR.RESET         + "m";
    public static final String BOLD             = CSI + FastSGR.BOLD_ON       + "m";
    public static final String BOLD_OFF         = CSI + FastSGR.BOLD_OFF      + "m";
    public static final String ITALIC           = CSI + FastSGR.ITALIC_ON     + "m";
    public static final String ITALIC_OFF       = CSI + FastSGR.ITALIC_OFF    + "m";
    public static final String UNDERLINE        = CSI + FastSGR.UNDERLINE_ON  + "m";
    public static final String UNDERLINE_OFF    = CSI + FastSGR.UNDERLINE_OFF + "m";
    public static final String BLINK            = CSI + FastSGR.BLINK_ON      + "m";
    public static final String BLINK_OFF        = CSI + FastSGR.BLINK_OFF     + "m";
    public static final String INVERT           = CSI + FastSGR.INVERT_ON     + "m";
    public static final String INVERT_OFF       = CSI + FastSGR.INVERT_OFF    + "m";
    public static final String HIDE             = CSI + FastSGR.HIDE_ON       + "m";
    public static final String HIDE_OFF         = CSI + FastSGR.HIDE_OFF      + "m";
    public static final String STRIKETHROUGH    = CSI + FastSGR.STRIKE_ON     + "m";
    public static final String STRIKETHROUGH_OFF= CSI + FastSGR.STRIKE_OFF    + "m";

    // 4-bit Foreground Colors (Standard) — derived from FastSGR
    public static final String FG_BLACK   = CSI + FastSGR.FG_BLACK   + "m";
    public static final String FG_RED     = CSI + FastSGR.FG_RED     + "m";
    public static final String FG_GREEN   = CSI + FastSGR.FG_GREEN   + "m";
    public static final String FG_YELLOW  = CSI + FastSGR.FG_YELLOW  + "m";
    public static final String FG_BLUE    = CSI + FastSGR.FG_BLUE    + "m";
    public static final String FG_MAGENTA = CSI + FastSGR.FG_MAGENTA + "m";
    public static final String FG_CYAN    = CSI + FastSGR.FG_CYAN    + "m";
    public static final String FG_WHITE   = CSI + FastSGR.FG_WHITE   + "m";
    public static final String FG_DEFAULT = CSI + FastSGR.FG_RESET   + "m";

    // 4-bit Foreground Colors (Bright) — derived from FastSGR
    public static final String FG_BRIGHT_BLACK   = CSI + FastSGR.FG_BRIGHT_BLACK   + "m";
    public static final String FG_BRIGHT_RED     = CSI + FastSGR.FG_BRIGHT_RED     + "m";
    public static final String FG_BRIGHT_GREEN   = CSI + FastSGR.FG_BRIGHT_GREEN   + "m";
    public static final String FG_BRIGHT_YELLOW  = CSI + FastSGR.FG_BRIGHT_YELLOW  + "m";
    public static final String FG_BRIGHT_BLUE    = CSI + FastSGR.FG_BRIGHT_BLUE    + "m";
    public static final String FG_BRIGHT_MAGENTA = CSI + FastSGR.FG_BRIGHT_MAGENTA + "m";
    public static final String FG_BRIGHT_CYAN    = CSI + FastSGR.FG_BRIGHT_CYAN    + "m";
    public static final String FG_BRIGHT_WHITE   = CSI + FastSGR.FG_BRIGHT_WHITE   + "m";

    // 4-bit Background Colors (Standard) — derived from FastSGR
    public static final String BG_BLACK   = CSI + FastSGR.BG_BLACK   + "m";
    public static final String BG_RED     = CSI + FastSGR.BG_RED     + "m";
    public static final String BG_GREEN   = CSI + FastSGR.BG_GREEN   + "m";
    public static final String BG_YELLOW  = CSI + FastSGR.BG_YELLOW  + "m";
    public static final String BG_BLUE    = CSI + FastSGR.BG_BLUE    + "m";
    public static final String BG_MAGENTA = CSI + FastSGR.BG_MAGENTA + "m";
    public static final String BG_CYAN    = CSI + FastSGR.BG_CYAN    + "m";
    public static final String BG_WHITE   = CSI + FastSGR.BG_WHITE   + "m";
    public static final String BG_DEFAULT = CSI + FastSGR.BG_RESET   + "m";

    // 4-bit Background Colors (Bright) — derived from FastSGR
    public static final String BG_BRIGHT_BLACK   = CSI + FastSGR.BG_BRIGHT_BLACK   + "m";
    public static final String BG_BRIGHT_RED     = CSI + FastSGR.BG_BRIGHT_RED     + "m";
    public static final String BG_BRIGHT_GREEN   = CSI + FastSGR.BG_BRIGHT_GREEN   + "m";
    public static final String BG_BRIGHT_YELLOW  = CSI + FastSGR.BG_BRIGHT_YELLOW  + "m";
    public static final String BG_BRIGHT_BLUE    = CSI + FastSGR.BG_BRIGHT_BLUE    + "m";
    public static final String BG_BRIGHT_MAGENTA = CSI + FastSGR.BG_BRIGHT_MAGENTA + "m";
    public static final String BG_BRIGHT_CYAN    = CSI + FastSGR.BG_BRIGHT_CYAN    + "m";
    public static final String BG_BRIGHT_WHITE   = CSI + FastSGR.BG_BRIGHT_WHITE   + "m";

    // Common Control Operations
    public static final String ALT_BUFFER_ON = "\033[?1049h";
    public static final String ALT_BUFFER_OFF = "\033[?1049l";
    public static final String CURSOR_HIDE = "\033[?25l";
    public static final String CURSOR_SHOW = "\033[?25h";
    public static final String CLEAR_SCREEN = "\033[2J";
    public static final String CLEAR_LINE = "\033[2K";
    public static final String CURSOR_HOME = "\033[1;1H";

    // --- Fluent Builder / Generator Utilities ---

    /** Generates a 24-bit true color foreground escape code. */
    public static String fg(int r, int g, int b) {
        return CSI + FastSGR.FG_EXT + ";" + FastSGR.EXT_24BIT + ";" + r + ";" + g + ";" + b + "m";
    }

    /** Generates a 24-bit true color background escape code. */
    public static String bg(int r, int g, int b) {
        return CSI + FastSGR.BG_EXT + ";" + FastSGR.EXT_24BIT + ";" + r + ";" + g + ";" + b + "m";
    }

    /** Generates an 8-bit (256-color) index foreground escape code. */
    public static String fg(int index) {
        return CSI + FastSGR.FG_EXT + ";" + FastSGR.EXT_8BIT + ";" + index + "m";
    }

    /** Generates an 8-bit (256-color) index background escape code. */
    public static String bg(int index) {
        return CSI + FastSGR.BG_EXT + ";" + FastSGR.EXT_8BIT + ";" + index + "m";
    }

    /** Generates a cursor positioning escape code. */
    public static String cursorTo(int row, int col) {
        return CSI + row + ";" + col + "H";
    }

    /**
     * Interface to receive low-overhead callbacks for every parsed ANSI sequence.
     * Implementing classes can process telemetry state natively with zero GC impact.
     */
    public interface ANSIListener {
        // Default byte array methods for native parsing (zero-allocation implementations should override these)
        default void onText(byte[] text, int offset, int length) {
            onText(new String(text, offset, length, java.nio.charset.StandardCharsets.UTF_8), 0, length);
        }
        default void onWindowTitle(byte[] title, int offset, int length) {
            onWindowTitle(new String(title, offset, length, java.nio.charset.StandardCharsets.UTF_8), 0, length);
        }
        default void onUnsupportedSequence(byte[] raw, int offset, int length) {
            onUnsupportedSequence(new String(raw, offset, length, java.nio.charset.StandardCharsets.UTF_8), 0, length);
        }

        // Plain Text Blocks
        void onText(CharSequence text, int start, int end);

        // Text Formatting (SGR - Select Graphic Rendition)
        void onReset();
        void onBold(boolean enable);
        void onItalic(boolean enable);
        void onUnderline(boolean enable);
        void onBlink(boolean enable);
        void onInvert(boolean enable);
        void onHide(boolean enable);
        void onStrikethrough(boolean enable);

        // Color Control (r, g, b are used for 24-bit, or raw index is passed in 'r' for 4/8-bit)
        void onForegroundColor(int colorType, int r, int g, int b);
        void onBackgroundColor(int colorType, int r, int g, int b);

        // Cursor Controls
        void onCursorPosition(int row, int col);
        void onCursorUp(int count);
        void onCursorDown(int count);
        void onCursorForward(int count);
        void onCursorBackward(int count);
        void onCursorNextLine(int count);
        void onCursorPrevLine(int count);
        void onCursorHorizontalAbsolute(int col);

        // Clearing and Erasing
        void onEraseInDisplay(int mode); // 0=cursor to end, 1=start to cursor, 2=entire screen, 3=scrollback
        void onEraseInLine(int mode);    // 0=cursor to end, 1=start to cursor, 2=entire line

        // Scrolling
        void onScrollUp(int count);
        void onScrollDown(int count);

        // Private Operating Modes (e.g. Alternate Buffer, Mouse tracking)
        void onPrivateMode(int mode, boolean enable); // mode (e.g. 1049=alt buffer, 25=cursor show)

        // Device Controls
        void onDeviceStatusReport(); // \033[6n
        
        // Window Title Control (OSC)
        void onWindowTitle(CharSequence title, int start, int end);

        // Fallback for debugging
        void onUnsupportedSequence(CharSequence raw, int start, int end);
    }

    /**
     * Zero-allocation, state-machine-driven parser for UTF-16 CharSequence.
     * Evaluates ESC, CSI, OSC, and private escape ranges purely using primitives.
     */
    public static void parse(CharSequence input, ANSIListener listener) {
        if (input == null || listener == null) return;

        int len = input.length();
        int textStart = 0;
        int i = 0;

        // Buffer array to hold parsed numerical parameters in CSI sequences (up to 16 parameters)
        int[] params = new int[16];
        int paramCount = 0;

        while (i < len) {
            char c = input.charAt(i);

            // Detect Escape character (\033 or \u001B)
            if (c == 27) {
                // If we accumulated normal text, flush it now
                if (i > textStart) {
                    listener.onText(input, textStart, i);
                }

                // Move past ESC
                i++;
                if (i >= len) {
                    textStart = len;
                    break;
                }

                char next = input.charAt(i);

                // 1. CSI - Control Sequence Introducer: ESC [
                if (next == '[') {
                    i++; // Move past '['
                    
                    boolean isPrivate = false;
                    if (i < len && input.charAt(i) == '?') {
                        isPrivate = true;
                        i++;
                    }

                    // Reset parameters
                    paramCount = 0;
                    int currentParam = -1;

                    // Parse numerical parameters
                    int seqStart = i;
                    while (i < len) {
                        char seqChar = input.charAt(i);

                        if (seqChar >= '0' && seqChar <= '9') {
                            if (currentParam == -1) {
                                currentParam = 0;
                            }
                            currentParam = currentParam * 10 + (seqChar - '0');
                            i++;
                        } else if (seqChar == ';') {
                            params[paramCount++] = (currentParam == -1) ? 0 : currentParam;
                            currentParam = -1;
                            if (paramCount >= params.length) break; // Overflow protection
                            i++;
                        } else {
                            // Non-numeric, non-separator character indicates the end of CSI sequence
                            if (currentParam != -1) {
                                params[paramCount++] = currentParam;
                            }
                            break;
                        }
                    }

                    if (i < len) {
                        char cmd = input.charAt(i);
                        i++; // Consume command character

                        if (isPrivate) {
                            // Private modes (e.g. ?25h, ?1049h, ?1049l)
                            int mode = (paramCount > 0) ? params[0] : 0;
                            if (cmd == 'h') {
                                listener.onPrivateMode(mode, true);
                            } else if (cmd == 'l') {
                                listener.onPrivateMode(mode, false);
                            } else {
                                listener.onUnsupportedSequence(input, seqStart - 3, i);
                            }
                        } else {
                            // Standard CSI Commands
                            switch (cmd) {
                                case 'm': // SGR (Select Graphic Rendition) - Colors & Styles
                                    if (paramCount == 0) {
                                        listener.onReset();
                                    } else {
                                        parseSGR(params, paramCount, listener);
                                    }
                                    break;
                                case 'H': // Cup - Cursor Position
                                case 'f':
                                    int row = (paramCount > 0) ? params[0] : 1;
                                    int col = (paramCount > 1) ? params[1] : 1;
                                    listener.onCursorPosition(row, col);
                                    break;
                                case 'A': // CUU - Cursor Up
                                    listener.onCursorUp((paramCount > 0) ? params[0] : 1);
                                    break;
                                case 'B': // CUD - Cursor Down
                                    listener.onCursorDown((paramCount > 0) ? params[0] : 1);
                                    break;
                                case 'C': // CUF - Cursor Forward
                                    listener.onCursorForward((paramCount > 0) ? params[0] : 1);
                                    break;
                                case 'D': // CUB - Cursor Backward
                                    listener.onCursorBackward((paramCount > 0) ? params[0] : 1);
                                    break;
                                case 'E': // CNL - Cursor Next Line
                                    listener.onCursorNextLine((paramCount > 0) ? params[0] : 1);
                                    break;
                                case 'F': // CPL - Cursor Preceding Line
                                    listener.onCursorPrevLine((paramCount > 0) ? params[0] : 1);
                                    break;
                                case 'G': // CHA - Cursor Horizontal Absolute
                                    listener.onCursorHorizontalAbsolute((paramCount > 0) ? params[0] : 1);
                                    break;
                                case 'J': // ED - Erase in Display
                                    listener.onEraseInDisplay((paramCount > 0) ? params[0] : 0);
                                    break;
                                case 'K': // EL - Erase in Line
                                    listener.onEraseInLine((paramCount > 0) ? params[0] : 0);
                                    break;
                                case 'S': // SU - Scroll Up
                                    listener.onScrollUp((paramCount > 0) ? params[0] : 1);
                                    break;
                                case 'T': // SD - Scroll Down
                                    listener.onScrollDown((paramCount > 0) ? params[0] : 1);
                                    break;
                                case 'n': // DSR - Device Status Report
                                    if (paramCount > 0 && params[0] == 6) {
                                        listener.onDeviceStatusReport();
                                    } else {
                                        listener.onUnsupportedSequence(input, seqStart - 2, i);
                                    }
                                    break;
                                default:
                                    listener.onUnsupportedSequence(input, seqStart - 2, i);
                                    break;
                            }
                        }
                    }
                    textStart = i;
                }
                // 2. OSC - Operating System Command: ESC ] (e.g., Set Window Title)
                else if (next == ']') {
                    i++; // Move past ']'
                    int oscStart = i;

                    // Read until BEL (\u0007) or ST (ESC \)
                    int contentEnd = -1;
                    while (i < len) {
                        char oscChar = input.charAt(i);
                        if (oscChar == 7) { // BEL
                            contentEnd = i;
                            i++;
                            break;
                        } else if (oscChar == 27 && i + 1 < len && input.charAt(i + 1) == '\\') { // ESC \ (ST)
                            contentEnd = i;
                            i += 2;
                            break;
                        }
                        i++;
                    }

                    if (contentEnd != -1) {
                        // Check if it sets window title (starts with '0;' or '2;')
                        if (contentEnd - oscStart >= 2 && 
                            (input.charAt(oscStart) == '0' || input.charAt(oscStart) == '2') && 
                            input.charAt(oscStart + 1) == ';') {
                            listener.onWindowTitle(input, oscStart + 2, contentEnd);
                        } else {
                            listener.onUnsupportedSequence(input, oscStart - 2, i);
                        }
                    }
                    textStart = i;
                }
                // 3. Fallback for standalone single-char ESC controls (like ESC M scroll backward, ESC D scroll forward)
                else {
                    if (next == 'M') {
                        listener.onScrollDown(1);
                    } else if (next == 'D') {
                        listener.onScrollUp(1);
                    } else {
                        listener.onUnsupportedSequence(input, i - 1, i + 1);
                    }
                    i++;
                    textStart = i;
                }
            } else {
                i++;
            }
        }

        // Flush any remaining trailing text
        if (i > textStart) {
            listener.onText(input, textStart, i);
        }
    }

    /**
     * Zero-allocation, native byte array parser.
     */
    public static void parse(byte[] input, int offset, int length, ANSIListener listener) {
        if (input == null || listener == null) return;

        int len = offset + length;
        int textStart = offset;
        int i = offset;

        // Buffer array to hold parsed numerical parameters in CSI sequences (up to 16 parameters)
        int[] params = new int[16];
        int paramCount = 0;

        while (i < len) {
            byte c = input[i];

            // Detect Escape character (\033 or \u001B)
            if (c == 27) {
                // If we accumulated normal text, flush it now
                if (i > textStart) {
                    listener.onText(input, textStart, i - textStart);
                }

                // Move past ESC
                i++;
                if (i >= len) {
                    textStart = len;
                    break;
                }

                byte next = input[i];

                // 1. CSI - Control Sequence Introducer: ESC [
                if (next == '[') {
                    i++; // Move past '['
                    
                    boolean isPrivate = false;
                    if (i < len && input[i] == '?') {
                        isPrivate = true;
                        i++;
                    }

                    // Reset parameters
                    paramCount = 0;
                    int currentParam = -1;

                    // Parse numerical parameters
                    int seqStart = i;
                    while (i < len) {
                        byte seqChar = input[i];

                        if (seqChar >= '0' && seqChar <= '9') {
                            if (currentParam == -1) {
                                currentParam = 0;
                            }
                            currentParam = currentParam * 10 + (seqChar - '0');
                            i++;
                        } else if (seqChar == ';') {
                            params[paramCount++] = (currentParam == -1) ? 0 : currentParam;
                            currentParam = -1;
                            if (paramCount >= params.length) break; // Overflow protection
                            i++;
                        } else {
                            // Non-numeric, non-separator character indicates the end of CSI sequence
                            if (currentParam != -1) {
                                params[paramCount++] = currentParam;
                            }
                            break;
                        }
                    }

                    if (i < len) {
                        byte cmd = input[i];
                        i++; // Consume command character

                        if (isPrivate) {
                            // Private modes (e.g. ?25h, ?1049h, ?1049l)
                            int mode = (paramCount > 0) ? params[0] : 0;
                            if (cmd == 'h') {
                                listener.onPrivateMode(mode, true);
                            } else if (cmd == 'l') {
                                listener.onPrivateMode(mode, false);
                            } else {
                                listener.onUnsupportedSequence(input, seqStart - 3, i - (seqStart - 3));
                            }
                        } else {
                            // Standard CSI Commands
                            switch (cmd) {
                                case 'm': // SGR (Select Graphic Rendition) - Colors & Styles
                                    if (paramCount == 0) {
                                        listener.onReset();
                                    } else {
                                        parseSGR(params, paramCount, listener);
                                    }
                                    break;
                                case 'H': // Cup - Cursor Position
                                case 'f':
                                    int row = (paramCount > 0) ? params[0] : 1;
                                    int col = (paramCount > 1) ? params[1] : 1;
                                    listener.onCursorPosition(row, col);
                                    break;
                                case 'A': // CUU - Cursor Up
                                    listener.onCursorUp((paramCount > 0) ? params[0] : 1);
                                    break;
                                case 'B': // CUD - Cursor Down
                                    listener.onCursorDown((paramCount > 0) ? params[0] : 1);
                                    break;
                                case 'C': // CUF - Cursor Forward
                                    listener.onCursorForward((paramCount > 0) ? params[0] : 1);
                                    break;
                                case 'D': // CUB - Cursor Backward
                                    listener.onCursorBackward((paramCount > 0) ? params[0] : 1);
                                    break;
                                case 'E': // CNL - Cursor Next Line
                                    listener.onCursorNextLine((paramCount > 0) ? params[0] : 1);
                                    break;
                                case 'F': // CPL - Cursor Preceding Line
                                    listener.onCursorPrevLine((paramCount > 0) ? params[0] : 1);
                                    break;
                                case 'G': // CHA - Cursor Horizontal Absolute
                                    listener.onCursorHorizontalAbsolute((paramCount > 0) ? params[0] : 1);
                                    break;
                                case 'J': // ED - Erase in Display
                                    listener.onEraseInDisplay((paramCount > 0) ? params[0] : 0);
                                    break;
                                case 'K': // EL - Erase in Line
                                    listener.onEraseInLine((paramCount > 0) ? params[0] : 0);
                                    break;
                                case 'S': // SU - Scroll Up
                                    listener.onScrollUp((paramCount > 0) ? params[0] : 1);
                                    break;
                                case 'T': // SD - Scroll Down
                                    listener.onScrollDown((paramCount > 0) ? params[0] : 1);
                                    break;
                                case 'n': // DSR - Device Status Report
                                    if (paramCount > 0 && params[0] == 6) {
                                        listener.onDeviceStatusReport();
                                    } else {
                                        listener.onUnsupportedSequence(input, seqStart - 2, i - (seqStart - 2));
                                    }
                                    break;
                                default:
                                    listener.onUnsupportedSequence(input, seqStart - 2, i - (seqStart - 2));
                                    break;
                            }
                        }
                    }
                    textStart = i;
                }
                // 2. OSC - Operating System Command: ESC ] (e.g., Set Window Title)
                else if (next == ']') {
                    i++; // Move past ']'
                    int oscStart = i;

                    // Read until BEL (\u0007) or ST (ESC \)
                    int contentEnd = -1;
                    while (i < len) {
                        byte oscChar = input[i];
                        if (oscChar == 7) { // BEL
                            contentEnd = i;
                            i++;
                            break;
                        } else if (oscChar == 27 && i + 1 < len && input[i + 1] == '\\') { // ESC \ (ST)
                            contentEnd = i;
                            i += 2;
                            break;
                        }
                        i++;
                    }

                    if (contentEnd != -1) {
                        // Check if it sets window title (starts with '0;' or '2;')
                        if (contentEnd - oscStart >= 2 && 
                            (input[oscStart] == '0' || input[oscStart] == '2') && 
                            input[oscStart + 1] == ';') {
                            listener.onWindowTitle(input, oscStart + 2, contentEnd - (oscStart + 2));
                        } else {
                            listener.onUnsupportedSequence(input, oscStart - 2, i - (oscStart - 2));
                        }
                    }
                    textStart = i;
                }
                // 3. Fallback for standalone single-char ESC controls (like ESC M scroll backward, ESC D scroll forward)
                else {
                    if (next == 'M') {
                        listener.onScrollDown(1);
                    } else if (next == 'D') {
                        listener.onScrollUp(1);
                    } else {
                        listener.onUnsupportedSequence(input, i - 1, (i + 1) - (i - 1));
                    }
                    i++;
                    textStart = i;
                }
            } else {
                i++;
            }
        }

        // Flush any remaining trailing text
        if (i > textStart) {
            listener.onText(input, textStart, i - textStart);
        }
    }

    /**
     * Resolves the Select Graphic Rendition (SGR) parameter stack.
     * <p>
     * Uses named constants from {@link FastSGR} instead of magic numbers.
     * The switch handles all single-value codes; range checks cover 4-bit color
     * bands; the extended block handles 8-bit (38;5;idx) and 24-bit (38;2;r;g;b).
     * Bitwise OR in range checks avoids short-circuit branching for better
     * branch-predictor behaviour on hot paths.
     */
    private static void parseSGR(int[] params, int count, ANSIListener listener) {
        int i = 0;
        while (i < count) {
            int v = params[i];

            // --- Single-value SGR codes (styles + resets) ---
            switch (v) {
                case FastSGR.RESET:          listener.onReset();                                        i++; continue;
                case FastSGR.BOLD_ON:        listener.onBold(true);                                     i++; continue;
                case FastSGR.BOLD_OFF:       listener.onBold(false);                                    i++; continue;
                case FastSGR.ITALIC_ON:      listener.onItalic(true);                                   i++; continue;
                case FastSGR.ITALIC_OFF:     listener.onItalic(false);                                  i++; continue;
                case FastSGR.UNDERLINE_ON:   listener.onUnderline(true);                                i++; continue;
                case FastSGR.UNDERLINE_OFF:  listener.onUnderline(false);                               i++; continue;
                case FastSGR.BLINK_ON:       listener.onBlink(true);                                    i++; continue;
                case FastSGR.BLINK_OFF:      listener.onBlink(false);                                   i++; continue;
                case FastSGR.INVERT_ON:      listener.onInvert(true);                                   i++; continue;
                case FastSGR.INVERT_OFF:     listener.onInvert(false);                                  i++; continue;
                case FastSGR.HIDE_ON:        listener.onHide(true);                                     i++; continue;
                case FastSGR.HIDE_OFF:       listener.onHide(false);                                    i++; continue;
                case FastSGR.STRIKE_ON:      listener.onStrikethrough(true);                            i++; continue;
                case FastSGR.STRIKE_OFF:     listener.onStrikethrough(false);                           i++; continue;
                case FastSGR.FG_RESET:       listener.onForegroundColor(COLOR_TYPE_4BIT, -1, 0, 0);    i++; continue;
                case FastSGR.BG_RESET:       listener.onBackgroundColor(COLOR_TYPE_4BIT, -1, 0, 0);    i++; continue;
            }

            // --- 4-bit Foreground: standard (30–37) and bright (90–97) ---
            if ((v >= FastSGR.FG_BASE && v <= FastSGR.FG_BASE + 7) |
                (v >= FastSGR.FG_BRIGHT_BASE && v <= FastSGR.FG_BRIGHT_BASE + 7)) {
                int idx = (v < FastSGR.FG_BRIGHT_BASE)
                        ? (v - FastSGR.FG_BASE)
                        : (v - FastSGR.FG_BRIGHT_BASE + 8);
                listener.onForegroundColor(COLOR_TYPE_4BIT, idx, 0, 0);
                i++;
                continue;
            }

            // --- 4-bit Background: standard (40–47) and bright (100–107) ---
            if ((v >= FastSGR.BG_BASE && v <= FastSGR.BG_BASE + 7) |
                (v >= FastSGR.BG_BRIGHT_BASE && v <= FastSGR.BG_BRIGHT_BASE + 7)) {
                int idx = (v < FastSGR.BG_BRIGHT_BASE)
                        ? (v - FastSGR.BG_BASE)
                        : (v - FastSGR.BG_BRIGHT_BASE + 8);
                listener.onBackgroundColor(COLOR_TYPE_4BIT, idx, 0, 0);
                i++;
                continue;
            }

            // --- Extended color: 8-bit (38;5;idx / 48;5;idx) or 24-bit (38;2;r;g;b / 48;2;r;g;b) ---
            if (v == FastSGR.FG_EXT || v == FastSGR.BG_EXT) {
                boolean fg = (v == FastSGR.FG_EXT);

                if (i + 2 < count && params[i + 1] == FastSGR.EXT_8BIT) {
                    // 8-bit index color
                    int colorIdx = params[i + 2];
                    if (fg) listener.onForegroundColor(COLOR_TYPE_8BIT, colorIdx, 0, 0);
                    else    listener.onBackgroundColor(COLOR_TYPE_8BIT, colorIdx, 0, 0);
                    i += 3;
                    continue;
                }

                if (i + 4 < count && params[i + 1] == FastSGR.EXT_24BIT) {
                    // 24-bit true color
                    int r = params[i + 2], g = params[i + 3], b = params[i + 4];
                    if (fg) listener.onForegroundColor(COLOR_TYPE_24BIT, r, g, b);
                    else    listener.onBackgroundColor(COLOR_TYPE_24BIT, r, g, b);
                    i += 5;
                    continue;
                }

                // Malformed extended sequence — skip introducer
                i++;
                continue;
            }

            // Unknown SGR parameter — skip
            i++;
        }
    }
}
