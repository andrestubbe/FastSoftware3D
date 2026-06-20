package fastsoftware3d.ansi;

/**
 * Centralized SGR (Select Graphic Rendition) parameter constants.
 * <p>
 * Provides named constants for every SGR code used by FastANSI, replacing
 * magic numbers throughout the parser and making the SGR spec self-documenting.
 * All constants are compile-time inlined — zero runtime cost.
 *
 * @see <a href="https://www.ecma-international.org/publications-and-standards/standards/ecma-48/">ECMA-48 §8.3.117</a>
 */
public final class FastSGR {

    // -------------------------------------------------------------------------
    // Color Type Discriminators
    // Used in ANSIListener callbacks to identify which color model is in use.
    // -------------------------------------------------------------------------

    /** 4-bit (16-color) palette index. */
    public static final int COLOR_TYPE_4BIT  = 0;
    /** 8-bit (256-color) palette index. */
    public static final int COLOR_TYPE_8BIT  = 1;
    /** 24-bit true color (RGB). */
    public static final int COLOR_TYPE_24BIT = 2;

    // -------------------------------------------------------------------------
    // Reset
    // -------------------------------------------------------------------------

    /** Reset all attributes to default. */
    public static final int RESET = 0;

    // -------------------------------------------------------------------------
    // Text Style — ON
    // -------------------------------------------------------------------------

    /** Bold or increased intensity. */
    public static final int BOLD_ON       = 1;
    /** Faint or decreased intensity (not widely supported). */
    public static final int FAINT_ON      = 2;
    /** Italic. */
    public static final int ITALIC_ON     = 3;
    /** Underline (single). */
    public static final int UNDERLINE_ON  = 4;
    /** Slow blink (&lt; 150 per minute). */
    public static final int BLINK_ON      = 5;
    /** Reverse video (swap fg/bg). */
    public static final int INVERT_ON     = 7;
    /** Conceal (hide) text. */
    public static final int HIDE_ON       = 8;
    /** Crossed-out / strikethrough. */
    public static final int STRIKE_ON     = 9;

    // -------------------------------------------------------------------------
    // Text Style — OFF
    // -------------------------------------------------------------------------

    /** Bold off / normal intensity (also turns off FAINT). */
    public static final int BOLD_OFF      = 22;
    /** Italic off. */
    public static final int ITALIC_OFF    = 23;
    /** Underline off. */
    public static final int UNDERLINE_OFF = 24;
    /** Blink off. */
    public static final int BLINK_OFF     = 25;
    /** Reverse video off. */
    public static final int INVERT_OFF    = 27;
    /** Conceal off (reveal). */
    public static final int HIDE_OFF      = 28;
    /** Strikethrough off. */
    public static final int STRIKE_OFF    = 29;

    // -------------------------------------------------------------------------
    // 4-bit Foreground Colors
    // -------------------------------------------------------------------------

    /** Base SGR code for standard foreground colors (30–37). */
    public static final int FG_BASE        = 30;
    /** Reset foreground to terminal default. */
    public static final int FG_RESET       = 39;
    /** Base SGR code for bright/high-intensity foreground colors (90–97). */
    public static final int FG_BRIGHT_BASE = 90;

    // Standard foreground color codes (30–37)
    public static final int FG_BLACK   = FG_BASE + 0;
    public static final int FG_RED     = FG_BASE + 1;
    public static final int FG_GREEN   = FG_BASE + 2;
    public static final int FG_YELLOW  = FG_BASE + 3;
    public static final int FG_BLUE    = FG_BASE + 4;
    public static final int FG_MAGENTA = FG_BASE + 5;
    public static final int FG_CYAN    = FG_BASE + 6;
    public static final int FG_WHITE   = FG_BASE + 7;

    // Bright foreground color codes (90–97)
    public static final int FG_BRIGHT_BLACK   = FG_BRIGHT_BASE + 0;
    public static final int FG_BRIGHT_RED     = FG_BRIGHT_BASE + 1;
    public static final int FG_BRIGHT_GREEN   = FG_BRIGHT_BASE + 2;
    public static final int FG_BRIGHT_YELLOW  = FG_BRIGHT_BASE + 3;
    public static final int FG_BRIGHT_BLUE    = FG_BRIGHT_BASE + 4;
    public static final int FG_BRIGHT_MAGENTA = FG_BRIGHT_BASE + 5;
    public static final int FG_BRIGHT_CYAN    = FG_BRIGHT_BASE + 6;
    public static final int FG_BRIGHT_WHITE   = FG_BRIGHT_BASE + 7;

    // -------------------------------------------------------------------------
    // 4-bit Background Colors
    // -------------------------------------------------------------------------

    /** Base SGR code for standard background colors (40–47). */
    public static final int BG_BASE        = 40;
    /** Reset background to terminal default. */
    public static final int BG_RESET       = 49;
    /** Base SGR code for bright/high-intensity background colors (100–107). */
    public static final int BG_BRIGHT_BASE = 100;

    // Standard background color codes (40–47)
    public static final int BG_BLACK   = BG_BASE + 0;
    public static final int BG_RED     = BG_BASE + 1;
    public static final int BG_GREEN   = BG_BASE + 2;
    public static final int BG_YELLOW  = BG_BASE + 3;
    public static final int BG_BLUE    = BG_BASE + 4;
    public static final int BG_MAGENTA = BG_BASE + 5;
    public static final int BG_CYAN    = BG_BASE + 6;
    public static final int BG_WHITE   = BG_BASE + 7;

    // Bright background color codes (100–107)
    public static final int BG_BRIGHT_BLACK   = BG_BRIGHT_BASE + 0;
    public static final int BG_BRIGHT_RED     = BG_BRIGHT_BASE + 1;
    public static final int BG_BRIGHT_GREEN   = BG_BRIGHT_BASE + 2;
    public static final int BG_BRIGHT_YELLOW  = BG_BRIGHT_BASE + 3;
    public static final int BG_BRIGHT_BLUE    = BG_BRIGHT_BASE + 4;
    public static final int BG_BRIGHT_MAGENTA = BG_BRIGHT_BASE + 5;
    public static final int BG_BRIGHT_CYAN    = BG_BRIGHT_BASE + 6;
    public static final int BG_BRIGHT_WHITE   = BG_BRIGHT_BASE + 7;

    // -------------------------------------------------------------------------
    // Extended Color (8-bit index / 24-bit RGB)
    // -------------------------------------------------------------------------

    /** Extended foreground color introducer: followed by EXT_8BIT or EXT_24BIT sub-mode. */
    public static final int FG_EXT    = 38;
    /** Extended background color introducer: followed by EXT_8BIT or EXT_24BIT sub-mode. */
    public static final int BG_EXT    = 48;

    /** Sub-mode selector for 8-bit (256-color) index: {@code 38;5;idx} or {@code 48;5;idx}. */
    public static final int EXT_8BIT  = 5;
    /** Sub-mode selector for 24-bit true color: {@code 38;2;r;g;b} or {@code 48;2;r;g;b}. */
    public static final int EXT_24BIT = 2;

    // -------------------------------------------------------------------------

    private FastSGR() {}
}
