package fastsoftware3d.ascii;

public final class FastGlyphDensity {
    public static final String FONT = "Consolas";
    public static final int FONT_SIZE = 24;
    public static final int VERSION = 1;
    public static final int SCAN_RESOLUTION = 32;

    private static final char[] GLYPHS = {
        (char) 0x0020, (char) 0x0060, (char) 0x002e, (char) 0x002d, (char) 0x003a, (char) 0x0027, (char) 0x005e, (char) 0x005f, (char) 0x002c, (char) 0x0021, (char) 0x003b, (char) 0x0022, (char) 0x002f, (char) 0x002a, (char) 0x003f, 
        (char) 0x002b, (char) 0x0076, (char) 0x0037, (char) 0x007c, (char) 0x0028, (char) 0x0054, (char) 0x004a, (char) 0x0059, (char) 0x0031, (char) 0x006c, (char) 0x0033, (char) 0x0046, (char) 0x0074, (char) 0x005b, (char) 0x0032, 
        (char) 0x0049, (char) 0x0035, (char) 0x0056, (char) 0x005a, (char) 0x0050, (char) 0x0034, (char) 0x0036, (char) 0x0045, (char) 0x0058, (char) 0x0048, (char) 0x004f, (char) 0x0047, (char) 0x0052, (char) 0x0044, (char) 0x0057, 
        (char) 0x0042, (char) 0x0038, (char) 0x0030, (char) 0x004d, (char) 0x0023, (char) 0x0051, (char) 0x0025, (char) 0x0026, (char) 0x0067, (char) 0x0040, 
    };

    private static final float[] DENSITY = {
        0.000f, 0.070f, 0.094f, 0.109f, 0.125f, 0.141f, 0.188f, 0.203f, 0.211f, 0.258f, 0.273f, 0.281f, 0.313f, 0.328f, 0.336f, 
        0.344f, 0.352f, 0.367f, 0.375f, 0.383f, 0.391f, 0.406f, 0.414f, 0.430f, 0.438f, 0.445f, 0.453f, 0.461f, 0.469f, 0.477f, 
        0.484f, 0.492f, 0.500f, 0.516f, 0.523f, 0.539f, 0.547f, 0.563f, 0.570f, 0.578f, 0.586f, 0.594f, 0.602f, 0.625f, 0.648f, 
        0.656f, 0.672f, 0.680f, 0.695f, 0.703f, 0.734f, 0.766f, 0.797f, 0.805f, 1.000f, 
    };

    public static char getGlyphForOpacity(float opacity) {
        if (opacity <= 0.0f) return GLYPHS[0];
        if (opacity >= 1.0f) return GLYPHS[GLYPHS.length - 1];
        // Binary search for closest density
        int low = 0;
        int high = DENSITY.length - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            float midVal = DENSITY[mid];
            if (midVal < opacity) low = mid + 1;
            else if (midVal > opacity) high = mid - 1;
            else return GLYPHS[mid];
        }
        // low is the insertion point. Find closest between low and low-1
        if (low >= DENSITY.length) return GLYPHS[GLYPHS.length - 1];
        if (low == 0) return GLYPHS[0];
        float diffNext = DENSITY[low] - opacity;
        float diffPrev = opacity - DENSITY[low - 1];
        return diffNext < diffPrev ? GLYPHS[low] : GLYPHS[low - 1];
    }

    public static float getOpacityForGlyph(char c) {
        for (int i = 0; i < GLYPHS.length; i++) {
            if (GLYPHS[i] == c) return DENSITY[i];
        }
        return 0.0f; // Unknown glyph
    }

    public static String getPalette(int steps) {
        StringBuilder sb = new StringBuilder(steps);
        for (int i = 0; i < steps; i++) {
            sb.append(getGlyphForOpacity((float) i / (steps - 1)));
        }
        return sb.toString();
    }
}
