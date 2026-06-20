package fastsoftware3d;

/**
 * FastSoftware3D Main Entry / Library Initializer.
 */
public final class FastSoftware3D {
    static {
        try {
            fastcore.FastCore.loadLibrary("fastsoftware3d");
        } catch (Throwable t) {
            System.err.println("FastSoftware3D: native library failed to load. " + t.getMessage());
        }
    }

    public static void initialize() {
        // Triggers static initializer
    }
}