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
import fastsoftware3d.core.RenderPipeline;
import fastsoftware3d.core.Framebuffer;
import fastsoftware3d.material.Material;
import fastsoftware3d.model.ObjLoader;
import fastsoftware3d.rasterizer.NativeRasterizer;
import fastsoftware3d.scene.Scene;
import fastsoftware3d.scene.SceneFactory;
import fastsoftware3d.scene.Renderer3D;
import fastsoftware3d.scene.ModelNode;
import fastsoftware3d.scene.GridNode;
import fastsoftware3d.buffers.RenderBuffers;

import java.awt.Graphics2D;
import fastsoftware3d.ascii.FastGlyphDensity;
import java.io.File;

public class Demo3DTerminal {

    static {
        try {
            fastcore.FastCore.loadLibrary("fastsoftware3d");
        } catch (Throwable t) {
            System.err.println("Failed to load native fast3d library in Demo3DTerminal: " + t.getMessage());
        }
    }

    private static final int DEFAULT_COLS = 120;
    private static final int DEFAULT_ROWS = 30;

    private static volatile boolean running = true;

    private static float cubeRotationY = 0.0f;

    // Camera Setup
    private static final Camera camera = new Camera(165f, 140f, -250.0f, -0.68f, -0.54f, 65.0f);
    private static final CameraController controller = new CameraController(camera);

    private static RenderBuffers buffers;
    private static Renderer3D activeRenderer;

    private static Scene scene;
    private static ModelNode cubeNode;

    public static void main(String[] args) {
        try {
            // Enable Win32 Raw input mode (disables QuickEdit text selection) and VT modes
            FastTerminal.setRawMode(true);
            FastTerminal.setAnsiRawMode(true);
        } catch (Throwable t) {
            System.err.println("Failed to set raw console modes: " + t.getMessage());
            t.printStackTrace();
        }

        System.out.println("Starting Fast3D Terminal Demo...");

        // Initialize FastKeyboard JNI listener
        final FastKeyboard keyboard = new FastKeyboardImpl();

        // Switch to alternative buffer and hide cursor first
        System.out.print(FastANSI.ALT_BUFFER_ON + FastANSI.CURSOR_HIDE + FastANSI.CLEAR_SCREEN);
        System.out.flush();

        // Initialize FastMouse JNI listener using native Raw Input
        final FastMouse mouse = FastMouse.open();
        mouse.startListening(new FastMouseListener() {
            @Override
            public void onMouseMove(long deviceHandle, int deltaX, int deltaY, int absoluteX, int absoluteY) {
                if (FastTerminal.isTerminalFocused()) {
                    synchronized (Demo3DTerminal.class) {
                        controller.onMouseMove(deltaX, deltaY, true);
                    }
                }
            }

            @Override
            public void onMouseButton(long deviceHandle, int buttonId, boolean isPressed) {
            }

            @Override
            public void onMouseWheel(long deviceHandle, int delta) {
            }
        });

        // Initialize FastDWM timer period
        fastdwm.FastDWM.beginTimerPeriod(1);

        // Shutdown hook to cleanly exit alternative buffer
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running = false;
            fastdwm.FastDWM.endTimerPeriod(1);
            System.out.print(FastANSI.RESET + FastANSI.CURSOR_SHOW + FastANSI.ALT_BUFFER_OFF);
            System.out.flush();
            try {
                keyboard.stopListening();
            } catch (Throwable ignored) {
            }
            try {
                mouse.stopListening();
            } catch (Throwable ignored) {
            }
            try {
                FastTerminal.setRawMode(false);
                FastTerminal.setAnsiRawMode(false);
            } catch (Throwable ignored) {
            }
        }));

        int cols = DEFAULT_COLS;
        int rows = DEFAULT_ROWS;

        try {
            int[] size = FastTerminal.getTerminalSize();
            if (size != null && size[0] > 0 && size[1] > 0) {
                cols = size[0];
                rows = size[1];
            }
        } catch (Throwable ignored) {
        }

        FastTerminalRenderer renderer = new FastTerminalRenderer(cols, rows);
        FastTerminalScene canvas = new FastTerminalScene(0, 0, cols, rows);
        renderer.addScene(canvas);

        init3DScene();
        buffers = new RenderBuffers(cols * 2, rows * 4, cols, rows);  // Dummy screen dims for terminal

        // Set 4x SSAA for terminal rendering
        controller.ssaaFactor = 4;
        reallocateBuffers(cols, rows);

        // Keyboard controls using FastKeyboard JNI Raw Input
        keyboard.startListening((deviceHandle, vKey, makeCode, isPressed, isE0, timestamp, keyChar) -> {
            // Only process key events if terminal has active window focus
            if (!FastTerminal.isTerminalFocused()) {
                return;
            }
            boolean shouldRealloc = controller.onKey(vKey, isPressed);
            if (shouldRealloc) {
                synchronized (Demo3DTerminal.class) {
                    reallocateBuffers(renderer.getWidth(), renderer.getHeight());
                }
            }
            if (vKey == 0x1B && isPressed) { // Escape
                running = false;
            }
        });

        long frameTimeTargetMs = 1000 / 120; // Target 120 FPS
        long lastFpsUpdateTime = System.currentTimeMillis();
        int fpsFrameCount = 0;
        double realFps = 60.0;

        long suiteStartTime = System.currentTimeMillis();
        double prevTime = 0.0;

        while (running) {
            long startTime = System.nanoTime();

            // 1. Handle viewport resizing
            int[] size = FastTerminal.getWindowSize(cols, rows);
            if (renderer.resize(size[0], size[1])) {
                cols = size[0];
                rows = size[1];
                canvas.resize(cols, rows);
                synchronized (Demo3DTerminal.class) {
                    reallocateBuffers(cols, rows);
                }
            }

            long now = System.currentTimeMillis();
            double time = (now - suiteStartTime) / 1000.0;
            double deltaTime = time - prevTime;
            if (deltaTime < 0.0) deltaTime = 0.0;
            if (deltaTime > 0.1) deltaTime = 0.1;
            prevTime = time;

            // Update camera via controller and apply cube rotation increment
            float rotInc = controller.update((float) deltaTime * 0.1f);
            cubeRotationY += rotInc;

            // Update FPS
            fpsFrameCount++;
            if (now - lastFpsUpdateTime >= 1000) {
                realFps = (fpsFrameCount * 1000.0) / (now - lastFpsUpdateTime);
                fpsFrameCount = 0;
                lastFpsUpdateTime = now;
            }

            synchronized (Demo3DTerminal.class) {
                // 2. Clear color and depth buffers
                java.util.Arrays.fill(buffers.renderPixels, 0x000000);
                activeRenderer.clear();

                cubeNode.getTransform().yaw = cubeRotationY;
                scene.update((float) deltaTime);

                // 3. Render 3D Scene into high-res target
                Graphics2D renderG = buffers.renderBuffer.createGraphics();
                scene.render(activeRenderer, renderG);
                renderG.dispose();
                activeRenderer.getPipeline().postProcess();

                // 4. Downsample SSAA render buffer to terminal half-block cells
                writeHalfBlocks(
                        buffers.renderPixels,
                        cols * controller.ssaaFactor,
                        rows * 2 * controller.ssaaFactor,
                        canvas,
                        cols,
                        rows,
                        controller.ssaaFactor
                );

                // Draw FPS overlay in the top-right corner (green on black, no margin/padding)
                String fpsStr = String.format("%d", (int) Math.round(realFps));
                int textLen = fpsStr.length();
                int startCol = cols - textLen;
                if (startCol >= 0) {
                    for (int i = 0; i < textLen; i++) {
                        char c = fpsStr.charAt(i);
                        canvas.writeCell(startCol + i, 0, c, 0x00FF00, 0x000000); // green on black
                    }
                }
            }

            renderer.render();

            // Frame rate limiting
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
            if (elapsedMs < frameTimeTargetMs) {
                try {
                    Thread.sleep(frameTimeTargetMs - elapsedMs);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    private static void init3DScene() {
        SceneFactory.SceneCreationResult result = SceneFactory.createDefaultScene();
        scene = result.scene;
        cubeNode = result.cubeNode;
    }

    private static void reallocateBuffers(int cols, int rows) {
        int ssaa = controller.ssaaFactor;
        buffers.reallocateForSize(cols, rows * 2, ssaa);
        Framebuffer framebuffer = new Framebuffer(
                cols * ssaa,
                rows * 2 * ssaa,
                buffers.renderPixels
        );
        RenderPipeline pipeline = new RenderPipeline(camera, framebuffer, new NativeRasterizer());
        activeRenderer = new Renderer3D(pipeline);
    }

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
