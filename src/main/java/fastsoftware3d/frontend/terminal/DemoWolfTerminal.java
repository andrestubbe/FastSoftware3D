package fastsoftware3d.frontend.terminal;

import fastsoftware3d.ansi.FastANSI;
import fastsoftware3d.terminal.FastTerminal;
import fastsoftware3d.terminal.FastTerminalRenderer;
import fastsoftware3d.terminal.FastTerminalScene;
import fastkeyboard.FastKeyboard;
import fastkeyboard.FastKeyboardImpl;
import fastmouse.FastMouse;
import fastmouse.FastMouseListener;

import fastsoftware3d.camera.Camera;
import fastsoftware3d.camera.CameraController;
import fastsoftware3d.core.Framebuffer;
import fastsoftware3d.core.RenderPipeline;
import fastsoftware3d.material.Material;
import fastsoftware3d.model.ObjLoader;
import fastsoftware3d.rasterizer.NativeRasterizer;
import fastsoftware3d.buffers.RenderBuffers;
import fastsoftware3d.scene.ModelNode;
import fastsoftware3d.scene.Renderer3D;
import fastsoftware3d.scene.Scene;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import fastsoftware3d.ascii.FastGlyphDensity;
import fastdwm.FastDWM;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.util.Arrays;

/**
 * Wolfenstein-style terminal demo.
 * Loads a wavefront OBJ room scene and lets the player walk through it.
 *
 * Controls:
 *   W/S         - move forward/backward (XZ plane)
 *   A/D         - strafe
 *   Q/E         - fly up/down
 *   Mouse drag  - look around
 *   Arrow keys  - rotate camera
 *   SHIFT       - sprint (3x speed)
 *   +/-         - FOV
 *   ESC         - quit
 */
public class DemoWolfTerminal {

    static {
        try {
            fastcore.FastCore.loadLibrary("fastsoftware3d");
        } catch (Throwable t) {
            System.err.println("Failed to load native library: " + t.getMessage());
        }
    }

    // Scene scale: room.obj uses Blender units (0..~20). Multiply by SCALE.
    private static final float SCALE = 100.0f;
    private static final int   SSAA  = 4;

    // Spawn inside the room entrance, looking inward
    private static final Camera camera = new Camera(
            32.0f,                // X
            100.0f,               // Y eye height
            -226.0f,              // Z
            0.0f,                 // yaw: looking forward (+Z)
            -0.05f,               // slight downward pitch
            75.0f                 // FOV
    );
    private static final CameraController controller = new CameraController(camera);

    private static volatile boolean running = true;

    // Render buffers
    private static BufferedImage renderBuffer;
    private static int[]         renderPixels;

    private static Renderer3D activeRenderer;
    private static Scene      scene;

    private static int cols = 120;
    private static int rows = 30;

    public static void main(String[] args) {
        try {
            FastTerminal.setRawMode(true);
            FastTerminal.setAnsiRawMode(true);
        } catch (Throwable t) {
            System.err.println("Raw console mode failed: " + t.getMessage());
        }

        System.out.print(FastANSI.ALT_BUFFER_ON + FastANSI.CURSOR_HIDE + FastANSI.CLEAR_SCREEN);
        System.out.flush();

        // Input setup
        final FastKeyboard keyboard = new FastKeyboardImpl();
        final FastMouse    mouse    = FastMouse.open();
        mouse.startListening(new FastMouseListener() {
            @Override public void onMouseMove(long h, int dx, int dy, int ax, int ay) {
                if (FastTerminal.isTerminalFocused())
                    synchronized (DemoWolfTerminal.class) { controller.onMouseMove(dx, dy, true); }
            }
            @Override public void onMouseButton(long h, int btn, boolean pressed) {}
            @Override public void onMouseWheel(long h, int delta) {}
        });

        // Initialize FastDWM timer period
        FastDWM.beginTimerPeriod(1);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running = false;
            FastDWM.endTimerPeriod(1);
            System.out.print(FastANSI.RESET + FastANSI.CURSOR_SHOW + FastANSI.ALT_BUFFER_OFF);
            System.out.flush();
            try { keyboard.stopListening(); } catch (Throwable ignored) {}
            try { mouse.stopListening();    } catch (Throwable ignored) {}
            try { FastTerminal.setRawMode(false); FastTerminal.setAnsiRawMode(false); } catch (Throwable ignored) {}
        }));

        // Terminal renderer
        try {
            int[] sz = FastTerminal.getTerminalSize();
            if (sz != null && sz[0] > 0 && sz[1] > 0) { cols = sz[0]; rows = sz[1]; }
        } catch (Throwable ignored) {}

        FastTerminalRenderer termRenderer = new FastTerminalRenderer(cols, rows);
        FastTerminalScene    canvas       = new FastTerminalScene(0, 0, cols, rows);
        termRenderer.addScene(canvas);

        // Scene and initial buffers
        controller.ssaaFactor = SSAA;
        initScene();
        allocateBuffers(cols, rows);

        // Keyboard
        keyboard.startListening((handle, vKey, makeCode, isPressed, isE0, ts, keyChar) -> {
            if (!FastTerminal.isTerminalFocused()) return;
            boolean shouldRealloc = controller.onKey(vKey, isPressed);
            if (shouldRealloc) {
                synchronized (DemoWolfTerminal.class) {
                    allocateBuffers(cols, rows);
                }
            }
            if (vKey == 0x1B && isPressed) running = false;
        });

        // Render loop
        final long TARGET_MS  = 1000 / 120;
        long       suiteStart = System.currentTimeMillis();
        double     prevTime   = 0.0;

        long lastFpsUpdateTime = System.currentTimeMillis();
        int fpsFrameCount = 0;
        double realFps = 60.0;

        while (running) {
            long loopStart = System.nanoTime();

            // Resize
            int[] sz = FastTerminal.getWindowSize(cols, rows);
            if (termRenderer.resize(sz[0], sz[1])) {
                cols = sz[0]; rows = sz[1];
                canvas.resize(cols, rows);
                synchronized (DemoWolfTerminal.class) { allocateBuffers(cols, rows); }
            }

            double time      = (System.currentTimeMillis() - suiteStart) / 1000.0;
            double deltaTime = Math.max(0.0, Math.min(0.1, time - prevTime));
            prevTime = time;

            controller.update((float) deltaTime);
            camera.y = 1.0f * SCALE; // fixed eye height

            // Update FPS
            fpsFrameCount++;
            long nowMs = System.currentTimeMillis();
            if (nowMs - lastFpsUpdateTime >= 1000) {
                realFps = (fpsFrameCount * 1000.0) / (nowMs - lastFpsUpdateTime);
                fpsFrameCount = 0;
                lastFpsUpdateTime = nowMs;
            }

            synchronized (DemoWolfTerminal.class) {
                // Clear to black background
                Arrays.fill(renderPixels, 0x000000);
                activeRenderer.clear();

                Graphics2D g = renderBuffer.createGraphics();
                scene.render(activeRenderer, g);
                g.dispose();
                activeRenderer.getPipeline().postProcess();

                // Downsample SSAA render buffer to terminal half-block cells
                writeHalfBlocks(renderPixels, cols * controller.ssaaFactor, rows * 2 * controller.ssaaFactor, canvas, cols, rows, controller.ssaaFactor);
            }

            int mode = fastsoftware3d.rasterizer.NativeRasterizer.mipmapMode;
            String modeStr = "None";
            if (mode == 1) modeStr = "Tweaked Discrete";
            else if (mode == 2) modeStr = "Dithered";
            else if (mode == 3) modeStr = "Bilinear Level Blend";
            FastTerminal.setTitle(String.format("FPS: %d | X: %.2f, Y: %.2f, Z: %.2f | SSAA: %dx | Mipmap Mode: %s", (int) Math.round(realFps), camera.x, camera.y, camera.z, controller.ssaaFactor, modeStr));

            termRenderer.render();

            long elapsed = (System.nanoTime() - loopStart) / 1_000_000L;
            if (elapsed < TARGET_MS) {
                try { Thread.sleep(TARGET_MS - elapsed); } catch (InterruptedException ignored) {}
            }
        }

        System.out.print(FastANSI.RESET + FastANSI.CURSOR_SHOW + FastANSI.ALT_BUFFER_OFF);
        System.out.flush();
        try { keyboard.stopListening(); } catch (Throwable ignored) {}
        try { mouse.stopListening();    } catch (Throwable ignored) {}
        try { FastTerminal.setRawMode(false); FastTerminal.setAnsiRawMode(false); } catch (Throwable ignored) {}
        System.exit(0);
    }



    // ------------------------------------------------------------------
    // Scene init
    // ------------------------------------------------------------------
    private static void initScene() {
        scene = fastsoftware3d.scene.SceneFactory.createWolfScene(SCALE);
    }

    // ------------------------------------------------------------------
    // Buffer management
    // ------------------------------------------------------------------
    private static void allocateBuffers(int cols, int rows) {
        int ssaa = controller.ssaaFactor;
        int renderW = cols * ssaa;
        int renderH = rows * 2 * ssaa; // *2 for half-block vertical resolution

        renderBuffer = new BufferedImage(renderW, renderH, BufferedImage.TYPE_INT_RGB);
        renderPixels = ((DataBufferInt) renderBuffer.getRaster().getDataBuffer()).getData();

        Framebuffer    fb       = new Framebuffer(renderW, renderH, renderPixels);
        RenderPipeline pipeline = new RenderPipeline(camera, fb, new NativeRasterizer());
        activeRenderer = new Renderer3D(pipeline);
    }

    // ------------------------------------------------------------------
    // Half-block downsampling: each terminal cell = top half + bottom half
    // Unicode UPPER HALF BLOCK (▀) fg=top color, bg=bottom color
    // ------------------------------------------------------------------
    private static void writeHalfBlocks(int[] src, int srcW, int srcH,
                                        FastTerminalScene canvas,
                                        int cols, int rows, int ssaa) {
        for (int row = 0; row < rows; row++) {
            int yTop = row * 2 * ssaa;
            int yBot = (row * 2 + 1) * ssaa;

            for (int col = 0; col < cols; col++) {
                int xBase = col * ssaa;

                // Average top block
                int rT = 0, gT = 0, bT = 0;
                int countT = 0;
                for (int dy = 0; dy < ssaa; dy++) {
                    int yy = yTop + dy;
                    if (yy < 0 || yy >= srcH) continue;
                    int rowOff = yy * srcW + xBase;
                    for (int dx = 0; dx < ssaa; dx++) {
                        int xx = xBase + dx;
                        if (xx < 0 || xx >= srcW) continue;
                        int srcIdx = rowOff + dx;
                        if (srcIdx >= 0 && srcIdx < src.length) {
                            int px = src[srcIdx];
                            rT += (px >> 16) & 0xFF;
                            gT += (px >>  8) & 0xFF;
                            bT +=  px        & 0xFF;
                            countT++;
                        }
                    }
                }
                int topC = 0;
                if (countT > 0) {
                    topC = ((rT / countT) << 16) | ((gT / countT) << 8) | (bT / countT);
                }

                // Average bottom block
                int rB = 0, gB = 0, bB = 0;
                int countB = 0;
                for (int dy = 0; dy < ssaa; dy++) {
                    int yy = yBot + dy;
                    if (yy < 0 || yy >= srcH) continue;
                    int rowOff = yy * srcW + xBase;
                    for (int dx = 0; dx < ssaa; dx++) {
                        int xx = xBase + dx;
                        if (xx < 0 || xx >= srcW) continue;
                        int srcIdx = rowOff + dx;
                        if (srcIdx >= 0 && srcIdx < src.length) {
                            int px = src[srcIdx];
                            rB += (px >> 16) & 0xFF;
                            gB += (px >>  8) & 0xFF;
                            bB +=  px        & 0xFF;
                            countB++;
                        }
                    }
                }
                int botC = 0;
                if (countB > 0) {
                    botC = ((rB / countB) << 16) | ((gB / countB) << 8) | (bB / countB);
                }

                if (controller.asciiMode) {
                    int r = 0, g = 0, b = 0;
                    int totalCount = countT + countB;
                    if (totalCount > 0) {
                        r = (rT + rB) / totalCount;
                        g = (gT + gB) / totalCount;
                        b = (bT + bB) / totalCount;
                    }
                    int color = (r << 16) | (g << 8) | b;
                    float brightness = (0.299f * r + 0.587f * g + 0.114f * b) / 255.0f;
                    char glyph = FastGlyphDensity.getGlyphForOpacity(brightness);
                    canvas.writeCell(col, row, glyph, color, 0x000000);
                } else {
                    canvas.writeCell(col, row, '\u2580', topC, botC); // ▀
                }
            }
        }
    }
}