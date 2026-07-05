package fastsoftware3d.demo;

import fastdwm.FastDWM;
import fasttheme.FastTheme;

import fastsoftware3d.camera.Camera;
import fastsoftware3d.camera.CameraController;
import fastsoftware3d.core.Framebuffer;
import fastsoftware3d.core.RenderPipeline;
import fastsoftware3d.material.Material;
import fastsoftware3d.model.ObjLoader;
import fastsoftware3d.rasterizer.NativeRasterizer;
import fastsoftware3d.buffers.RenderBuffers;
import fastsoftware3d.scene.Renderer3D;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;

public class Demo {

    private static final int DEFAULT_WIDTH = 960;
    private static final int DEFAULT_HEIGHT = 540;

    private volatile boolean running = true;
    private float cubeRotationY = 0.0f;
    private boolean reflectionEnabled = true;
    private boolean puddleMaskEnabled = true;
    private boolean reflectionBlur = false;
    private float mouseAccumX = 0;
    private float mouseAccumY = 0;

    private final Camera camera = new Camera(0.0f, 4.0f, -15.0f, 0.0f, 0.0f, 65.0f);
    private final CameraController controller = new CameraController(camera);
    private final NativeRasterizer rasterizer = new NativeRasterizer();

    private RenderBuffers buffers;
    private Renderer3D activeRenderer;
    private Renderer3D maskRenderer;
    private Renderer3D refRenderer;

    private Framebuffer maskFb;
    private Framebuffer reflectionFb;
    private Framebuffer smallReflectionFb;
    private int[] smallBlurPix;
    private int[] smallBlurTempPix; // Cached temporary buffer for sliding-window blur
    private int blurDownscale = 4;

    // Models
    private ObjLoader.ModelData cubeModel;
    private ObjLoader.ModelData reflectedCubeModel;
    private ObjLoader.ModelData floorModel;
    private ObjLoader.ModelData skyboxModel;
    private ObjLoader.ModelData reflectedSkyboxModel;

    // Materials
    private Material cubeMaterial;
    private Material floorMaterial;
    private Material skyboxMaterial;
    private Material maskMaterial;
    private Material whiteMaskMaterial;
    private Material blackMaskMaterial;

    private static final float GROUND_Y = 0.0f;

    public Demo(JFrame parentFrame) {
        init3DScene();

        buffers = new RenderBuffers(DEFAULT_WIDTH, DEFAULT_HEIGHT, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        reallocateBuffers(DEFAULT_WIDTH, DEFAULT_HEIGHT);

        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                synchronized (Demo.this) {
                    if (buffers.screenBuffer != null) {
                        g.drawImage(buffers.screenBuffer, 0, 0, getWidth(), getHeight(), null);
                    }
                }
            }
        };
        panel.setPreferredSize(new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT));
        parentFrame.add(panel);
        parentFrame.pack();
        parentFrame.setLocationRelativeTo(null);
        parentFrame.setVisible(true);

        panel.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    running = false;
                    parentFrame.dispose();
                    return;
                }
                if (e.getKeyCode() == KeyEvent.VK_R) reflectionEnabled = !reflectionEnabled;
                if (e.getKeyCode() == KeyEvent.VK_P) puddleMaskEnabled = !puddleMaskEnabled;
                if (e.getKeyCode() == KeyEvent.VK_B) reflectionBlur = !reflectionBlur;
                if (e.getKeyCode() == KeyEvent.VK_V) {
                    if (blurDownscale == 1) blurDownscale = 2;
                    else if (blurDownscale == 2) blurDownscale = 4;
                    else if (blurDownscale == 4) blurDownscale = 8;
                    else if (blurDownscale == 8) blurDownscale = 16;
                    else blurDownscale = 1;
                }
                
                boolean shouldRealloc = controller.onKeySwing(e.getKeyCode(), true);
                if (shouldRealloc) {
                    synchronized (Demo.this) {
                        reallocateBuffers(panel.getWidth(), panel.getHeight());
                    }
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                controller.onKeySwing(e.getKeyCode(), false);
            }
        });

        // Hide cursor
        BufferedImage cursorImg = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Cursor blankCursor = Toolkit.getDefaultToolkit().createCustomCursor(cursorImg, new Point(0, 0), "blank cursor");
        panel.setCursor(blankCursor);

        // Robot for centering mouse
        final java.awt.Robot robot;
        java.awt.Robot tempRobot = null;
        try {
            tempRobot = new java.awt.Robot();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        robot = tempRobot;

        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                panel.requestFocusInWindow();
                if (robot != null) {
                    Point center = getCanvasCenterOnScreen(panel);
                    robot.mouseMove(center.x, center.y);
                }
            }
        });

        panel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (robot != null && panel.isFocusOwner()) {
                    Point center = getCanvasCenterOnScreen(panel);
                    int dx = e.getXOnScreen() - center.x;
                    int dy = e.getYOnScreen() - center.y;

                    if (dx != 0 || dy != 0) {
                        mouseAccumX += dx * 0.3f; // 30% mouse sensitivity
                        mouseAccumY += dy * 0.3f;
                        int mx = (int) mouseAccumX;
                        int my = (int) mouseAccumY;
                        
                        if (mx != 0 || my != 0) {
                            synchronized (Demo.this) {
                                controller.onMouseMove(mx, my, true);
                            }
                            mouseAccumX -= mx;
                            mouseAccumY -= my;
                        }
                        robot.mouseMove(center.x, center.y);
                    }
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                mouseMoved(e);
            }
        });

        panel.setFocusable(true);
        panel.requestFocusInWindow();

        parentFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                running = false;
            }
        });

        FastDWM.beginTimerPeriod(1);

        Thread renderThread = new Thread(() -> {
            long lastTitleUpdate = System.nanoTime();
            long[] frameDeltas = new long[60];
            int frameIdx = 0;
            long validFrames = 0;
            double realFps = 60.0;

            long suiteStart = System.currentTimeMillis();
            double prevTime = 0.0;

            while (running) {
                try {
                    long loopStart = System.nanoTime();

                    // Align to multiple of 8 to prevent SIMD/NativeRasterizer jagged alignment bugs
                    int currentW = panel.getWidth() & ~7;
                    int currentH = panel.getHeight() & ~7;
                    int[] baseDims = buffers.getBaseDimensions(false);
                    if (currentW > 0 && currentH > 0 && (currentW != baseDims[0] || currentH != baseDims[1])) {
                        synchronized (Demo.this) {
                            reallocateBuffers(currentW, currentH);
                        }
                    }

                    double time = (System.currentTimeMillis() - suiteStart) / 1000.0;
                    double deltaTime = Math.max(0.0, Math.min(0.1, time - prevTime));
                    prevTime = time;

                    // Update camera based on input
                    float rotInc = controller.update((float) deltaTime * 0.5f);

                    long nowNano = System.nanoTime();
                    long deltaNano = nowNano - loopStart;
                    frameDeltas[frameIdx] = deltaNano;
                    frameIdx = (frameIdx + 1) % frameDeltas.length;
                    if (validFrames < frameDeltas.length) validFrames++;

                    if (nowNano - lastTitleUpdate >= 100_000_000L) { // 10x per second
                        lastTitleUpdate = nowNano;
                        long sumDeltas = 0;
                        for (int i = 0; i < validFrames; i++) sumDeltas += frameDeltas[i];
                        realFps = (1_000_000_000.0 * validFrames) / Math.max(1, sumDeltas);

                        if (parentFrame.isActive()) {
                            parentFrame.setTitle(String.format("FastSoftware3D Demo - FPS: %d | Refl (R): %s | Puddles (P): %s | Blur (B): %s (1/%d)",
                                    (int) Math.round(realFps), reflectionEnabled ? "ON" : "OFF", puddleMaskEnabled ? "ON" : "OFF", reflectionBlur ? "ON" : "OFF", blurDownscale));
                        }
                    }

                    synchronized (Demo.this) {
                        renderFrame();
                    }

                    panel.repaint();

                    long elapsed = (System.nanoTime() - loopStart) / 1_000_000L;
                    long targetMs = 1; // 1000 FPS cap, prevents 100% CPU burning
                    if (elapsed < targetMs) {
                        try { Thread.sleep(targetMs - elapsed); } catch (InterruptedException ignored) {}
                    }
                } catch (Throwable t) {
                    System.err.println("CRASH IN RENDER LOOP: " + t.getMessage());
                    t.printStackTrace();
                    try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                }
            }

            FastDWM.endTimerPeriod(1);
            System.exit(0);
        });
        renderThread.start();
    }

    private Point getCanvasCenterOnScreen(Component c) {
        try {
            Point loc = c.getLocationOnScreen();
            return new Point(loc.x + c.getWidth() / 2, loc.y + c.getHeight() / 2);
        } catch (Exception e) {
            return new Point(0, 0);
        }
    }

    private void renderFrame() {
        int[] baseDims = buffers.getBaseDimensions(false);
        int sw = baseDims[0];
        int sh = baseDims[1];
        int renderW = maskFb.width;
        int renderH = maskFb.height;

        // 1. Render Foreground Scene
        Arrays.fill(buffers.renderPixels, 0x000000); 
        activeRenderer.getPipeline().setFramebuffer(buffers.renderFb);
        activeRenderer.clear();
        
        // Restore skybox to foreground! 
        if (skyboxModel != null) {
            activeRenderer.renderModel(skyboxModel, camera.x, camera.y, camera.z, 0, skyboxMaterial);
            activeRenderer.clear(); // Clear depth so skybox is pushed to the absolute background
        }
        
        activeRenderer.renderModel(floorModel, 0, GROUND_Y, 0, 0, floorMaterial);
        activeRenderer.renderModel(cubeModel, 0, 0.0f, 0, cubeRotationY, cubeMaterial);

        if (reflectionEnabled) {
            // 2. Render Floor Mask
            Arrays.fill(maskFb.pixels, 0x000000);
            maskFb.clearDepth();
            maskRenderer.getPipeline().setFramebuffer(maskFb);
            maskRenderer.renderModel(floorModel, 0, GROUND_Y, 0, 0.0f, puddleMaskEnabled ? maskMaterial : whiteMaskMaterial);
            maskRenderer.renderModel(cubeModel, 0, 0.0f, 0, cubeRotationY, blackMaskMaterial);

            // 3. Render Reflected Scene
            Framebuffer targetRefFb = reflectionFb;
            if (reflectionBlur && blurDownscale > 1) {
                int rw = renderW / blurDownscale;
                int rh = renderH / blurDownscale;
                if (smallReflectionFb == null || smallReflectionFb.width != rw || smallReflectionFb.height != rh) {
                    smallReflectionFb = new Framebuffer(rw, rh, new int[rw * rh]);
                }
                targetRefFb = smallReflectionFb;
            }

            Arrays.fill(targetRefFb.pixels, 0xFF00FF); // Magenta as transparent key
            refRenderer.getPipeline().setFramebuffer(targetRefFb);
            refRenderer.clear();
            
            if (skyboxModel != null) {
                // The reflection of the skybox moves with the mirrored camera position
                refRenderer.renderModel(reflectedSkyboxModel, camera.x, -camera.y, camera.z, 0, skyboxMaterial);
                targetRefFb.clearDepth(); 
            }

            // Restore the reflected cube, positioned perfectly below the mirror plane
            refRenderer.renderModel(reflectedCubeModel, 0, 0.0f, 0, cubeRotationY, cubeMaterial);

            // Apply Blur if enabled
            if (reflectionBlur) {
                int bw = renderW / blurDownscale;
                int bh = renderH / blurDownscale;
                if (smallBlurPix == null || smallBlurPix.length != bw * bh) {
                    smallBlurPix = new int[bw * bh];
                }
                
                // Target is ALREADY at low res! Just convert Magenta to ARGB 0
                int[] src = targetRefFb.pixels;
                for (int i = 0; i < src.length; i++) {
                    int p = src[i];
                    if (p == 0xFF00FF) {
                        smallBlurPix[i] = 0;
                    } else {
                        smallBlurPix[i] = 0xFF000000 | (p & 0xFFFFFF);
                    }
                }
                
                // Blur the small buffer! Radius 2 on a 1/4 scaled image is equivalent to radius 8 on full res.
                applyBlur(smallBlurPix, bw, bh, 2); 
            }

            // 4. Compositing (Fresnel-based Pre-multiplied Alpha Reflection - OPTIMIZED & PARALLEL)
            int[] mainPix = buffers.renderPixels;
            int[] maskPix = maskFb.pixels;
            int[] refPix = reflectionFb.pixels;
            int blurShift = Integer.numberOfTrailingZeros(blurDownscale);

            java.util.stream.IntStream.range(0, renderH).parallel().forEach(y -> {
                // Pre-calculate Fresnel multiplier for the entire scanline (integer 0-256)
                float screenYPercent = (float) y / renderH; 
                float fresnel = 1.0f - (screenYPercent * 0.8f); 
                int reflStrengthFixed = (int)(0.4f * fresnel * 256.0f); // Doubled reflection strength!

                int rowOffset = y * renderW;
                for (int x = 0; x < renderW; x++) {
                    int i = rowOffset + x;
                    int maskVal = maskPix[i];
                    if (maskVal != 0x000000) { // Black means no floor (or masked by cube)
                        int puddleIntensity = (maskVal >> 16) & 0xFF; // Red channel of the puddle mask
                        if (puddleIntensity < 10) continue; // Skip dry areas

                        // Modulate reflection strength with puddle intensity
                        int localReflStrength = (reflStrengthFixed * puddleIntensity) >> 8;
                        if (localReflStrength == 0) continue;

                        int aRf, rRf, gRf, bRf;
                        
                        if (reflectionBlur) {
                            int bw = renderW >> blurShift;
                            int bh = renderH >> blurShift;
                            int sx = Math.min(x >> blurShift, bw - 1);
                            int sy = Math.min(y >> blurShift, bh - 1);
                            int refCol = smallBlurPix[sy * bw + sx];
                            aRf = (refCol >>> 24);
                            if (aRf == 0) continue;
                            rRf = (refCol >> 16) & 0xFF;
                            gRf = (refCol >> 8) & 0xFF;
                            bRf = refCol & 0xFF;
                        } else {
                            int refCol = refPix[i];
                            if (refCol == 0xFF00FF) continue;
                            aRf = 255;
                            rRf = (refCol >> 16) & 0xFF;
                            gRf = (refCol >> 8) & 0xFF;
                            bRf = refCol & 0xFF;
                        }
                        
                        // Apply Fresnel and Puddle Mask (fast integer math)
                        aRf = (aRf * localReflStrength) >> 8;
                        rRf = (rRf * localReflStrength) >> 8;
                        gRf = (gRf * localReflStrength) >> 8;
                        bRf = (bRf * localReflStrength) >> 8;

                        int bgCol = mainPix[i];
                        int rBg = (bgCol >> 16) & 0xFF;
                        int gBg = (bgCol >> 8) & 0xFF;
                        int bBg = bgCol & 0xFF;
                        
                        // Pre-multiplied alpha composite: Output = Foreground + Background * (1 - Alpha)
                        int invA = 255 - aRf;
                        int rOut = rRf + ((rBg * invA) >> 8);
                        int gOut = gRf + ((gBg * invA) >> 8);
                        int bOut = bRf + ((bBg * invA) >> 8);
                        
                        mainPix[i] = (rOut << 16) | (gOut << 8) | bOut;
                    }
                }
            });
        }

        // 5. Render Normal Cube in foreground
        activeRenderer.renderModel(cubeModel, 0, 0, 0, cubeRotationY, cubeMaterial);

        // 6. Post Process
        activeRenderer.getPipeline().postProcess();
        
        // 7. Downsample if SSAA
        if (controller.getSsaaFactor() > 1) {
            downsample(sw, sh);
        } else {
            System.arraycopy(buffers.renderPixels, 0, buffers.screenPixels, 0, renderW * renderH);
        }
    }

    private void applyBlur(int[] pixels, int w, int h, int radius) {
        if (smallBlurTempPix == null || smallBlurTempPix.length != w * h) {
            smallBlurTempPix = new int[w * h];
        }
        int[] temp = smallBlurTempPix;
        int div = radius * 2 + 1;
        int invDiv = (1 << 16) / div; // Use fixed-point multiplication instead of slow division
        
        for (int y = 0; y < h; y++) {
            int sumA = 0, sumR = 0, sumG = 0, sumB = 0;
            int rowStart = y * w;
            for (int k = -radius; k <= radius; k++) {
                int x = Math.max(0, Math.min(w - 1, k));
                int p = pixels[rowStart + x];
                sumA += (p >>> 24);
                sumR += (p >> 16) & 0xFF;
                sumG += (p >> 8) & 0xFF;
                sumB += p & 0xFF;
            }
            for (int x = 0; x < w; x++) {
                temp[rowStart + x] = (((sumA * invDiv) >> 16) << 24) | (((sumR * invDiv) >> 16) << 16) | (((sumG * invDiv) >> 16) << 8) | ((sumB * invDiv) >> 16);
                int rightX = Math.min(w - 1, x + radius + 1);
                int leftX = Math.max(0, x - radius);
                int pRight = pixels[rowStart + rightX];
                int pLeft = pixels[rowStart + leftX];
                sumA += (pRight >>> 24) - (pLeft >>> 24);
                sumR += ((pRight >> 16) & 0xFF) - ((pLeft >> 16) & 0xFF);
                sumG += ((pRight >> 8) & 0xFF) - ((pLeft >> 8) & 0xFF);
                sumB += (pRight & 0xFF) - (pLeft & 0xFF);
            }
        }
        for (int x = 0; x < w; x++) {
            int sumA = 0, sumR = 0, sumG = 0, sumB = 0;
            for (int k = -radius; k <= radius; k++) {
                int y = Math.max(0, Math.min(h - 1, k));
                int p = temp[y * w + x];
                sumA += (p >>> 24);
                sumR += (p >> 16) & 0xFF;
                sumG += (p >> 8) & 0xFF;
                sumB += p & 0xFF;
            }
            for (int y = 0; y < h; y++) {
                pixels[y * w + x] = (((sumA * invDiv) >> 16) << 24) | (((sumR * invDiv) >> 16) << 16) | (((sumG * invDiv) >> 16) << 8) | ((sumB * invDiv) >> 16);
                int bottomY = Math.min(h - 1, y + radius + 1);
                int topY = Math.max(0, y - radius);
                int pBottom = temp[bottomY * w + x];
                int pTop = temp[topY * w + x];
                sumA += (pBottom >>> 24) - (pTop >>> 24);
                sumR += ((pBottom >> 16) & 0xFF) - ((pTop >> 16) & 0xFF);
                sumG += ((pBottom >> 8) & 0xFF) - ((pTop >> 8) & 0xFF);
                sumB += (pBottom & 0xFF) - (pTop & 0xFF);
            }
        }
    }

    private void downsample(int sw, int sh) {
        int ssaa = controller.getSsaaFactor();
        int[] src = buffers.renderPixels;
        int[] dst = buffers.screenPixels;
        int srcW = sw * ssaa;

        for (int y = 0; y < sh; y++) {
            int dstIdx = y * sw;
            for (int x = 0; x < sw; x++) {
                int r = 0, g = 0, b = 0;
                int baseSrc = (y * ssaa) * srcW + (x * ssaa);
                for (int dy = 0; dy < ssaa; dy++) {
                    int rowOff = baseSrc + dy * srcW;
                    for (int dx = 0; dx < ssaa; dx++) {
                        int px = src[rowOff + dx];
                        r += (px >> 16) & 0xFF;
                        g += (px >> 8) & 0xFF;
                        b += px & 0xFF;
                    }
                }
                int count = ssaa * ssaa;
                int shift = Integer.numberOfTrailingZeros(count);
                dst[dstIdx + x] = ((r >> shift) << 16) | ((g >> shift) << 8) | (b >> shift);
            }
        }
    }

    private ObjLoader.ModelData mirrorModel(ObjLoader.ModelData source) {
        ObjLoader.ModelData m = new ObjLoader.ModelData();
        if (source.vertexCount == 0) return m;
        m.vertexCount = source.vertexCount;
        m.vertices = new float[m.vertexCount * 3];
        for (int i = 0; i < m.vertexCount; i++) {
            int off = i * 3;
            m.vertices[off] = source.vertices[off];
            m.vertices[off + 1] = -source.vertices[off + 1]; // Invert Y
            m.vertices[off + 2] = source.vertices[off + 2];
        }
        
        if (source.uvs != null) {
            m.uvs = new float[source.uvs.length];
            System.arraycopy(source.uvs, 0, m.uvs, 0, source.uvs.length);
        }

        m.faceCount = source.faceCount;
        m.vIndices = new int[m.faceCount * 3];
        m.uvIndices = new int[m.faceCount * 3];
        for (int i = 0; i < m.faceCount; i++) {
            int off = i * 3;
            // Invert winding order (0, 2, 1)
            m.vIndices[off] = source.vIndices[off];
            m.vIndices[off + 1] = source.vIndices[off + 2];
            m.vIndices[off + 2] = source.vIndices[off + 1];

            m.uvIndices[off] = source.uvIndices[off];
            m.uvIndices[off + 1] = source.uvIndices[off + 2];
            m.uvIndices[off + 2] = source.uvIndices[off + 1];
        }
        m.boundingRadius = source.boundingRadius;
        return m;
    }

    private ObjLoader.ModelData createSkybox(ObjLoader.ModelData source) {
        ObjLoader.ModelData m = new ObjLoader.ModelData();
        if (source.vertexCount == 0) return m;
        float scale = 400.0f; // Big enough to surround the camera
        
        // Find center to perfectly center the skybox
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < source.vertexCount; i++) {
            int off = i * 3;
            float vx = source.vertices[off];
            float vy = source.vertices[off + 1];
            float vz = source.vertices[off + 2];
            if (vx < minX) minX = vx;
            if (vy < minY) minY = vy;
            if (vz < minZ) minZ = vz;
            if (vx > maxX) maxX = vx;
            if (vy > maxY) maxY = vy;
            if (vz > maxZ) maxZ = vz;
        }
        float cx = (minX + maxX) / 2.0f;
        float cy = (minY + maxY) / 2.0f;
        float cz = (minZ + maxZ) / 2.0f;

        m.vertexCount = source.vertexCount;
        m.vertices = new float[m.vertexCount * 3];
        for (int i = 0; i < m.vertexCount; i++) {
            int off = i * 3;
            m.vertices[off] = (source.vertices[off] - cx) * scale;
            m.vertices[off + 1] = (source.vertices[off + 1] - cy) * scale;
            m.vertices[off + 2] = (source.vertices[off + 2] - cz) * scale;
        }

        if (source.uvs != null) {
            m.uvs = new float[source.uvs.length];
            System.arraycopy(source.uvs, 0, m.uvs, 0, source.uvs.length);
        }

        // We filter faces, so we don't know the faceCount yet
        int maxFaces = source.faceCount;
        int[] tempVIndices = new int[maxFaces * 3];
        int[] tempUvIndices = new int[maxFaces * 3];
        int validFaces = 0;

        for (int i = 0; i < source.faceCount; i++) {
            int off = i * 3;
            int i0 = source.vIndices[off];
            int i1 = source.vIndices[off + 1];
            int i2 = source.vIndices[off + 2];

            float v0x = source.vertices[i0 * 3];
            float v0y = source.vertices[i0 * 3 + 1];
            float v0z = source.vertices[i0 * 3 + 2];

            float v1x = source.vertices[i1 * 3];
            float v1y = source.vertices[i1 * 3 + 1];
            float v1z = source.vertices[i1 * 3 + 2];

            float v2x = source.vertices[i2 * 3];
            float v2y = source.vertices[i2 * 3 + 1];
            float v2z = source.vertices[i2 * 3 + 2];

            float ny = (v1z - v0z) * (v2x - v0x) - (v1x - v0x) * (v2z - v0z);
            if (ny < -0.1f) continue; // Skip faces pointing down

            int destOff = validFaces * 3;
            // Invert winding order
            tempVIndices[destOff] = i0;
            tempVIndices[destOff + 1] = i2;
            tempVIndices[destOff + 2] = i1;

            tempUvIndices[destOff] = source.uvIndices[off];
            tempUvIndices[destOff + 1] = source.uvIndices[off + 2];
            tempUvIndices[destOff + 2] = source.uvIndices[off + 1];

            validFaces++;
        }

        m.faceCount = validFaces;
        m.vIndices = new int[validFaces * 3];
        m.uvIndices = new int[validFaces * 3];
        System.arraycopy(tempVIndices, 0, m.vIndices, 0, validFaces * 3);
        System.arraycopy(tempUvIndices, 0, m.uvIndices, 0, validFaces * 3);

        m.boundingRadius = source.boundingRadius * scale;
        return m;
    }

    private void init3DScene() {
        blackMaskMaterial = Material.solidColor(0x000000);
        whiteMaskMaterial = Material.solidColor(0xFFFFFF);
        
        try {
            File docsCubeObj = new File("docs/cube.obj");
            if (docsCubeObj.exists()) {
                cubeModel = ObjLoader.load("docs/cube.obj");
                reflectedCubeModel = mirrorModel(cubeModel);
                cubeMaterial = Material.fromPng("docs/cube.png");
                floorModel = ObjLoader.load("docs/floor.obj");
                floorMaterial = Material.fromPng("docs/floor.png");
                maskMaterial = Material.fromPng("docs/puddle_mask.png");
            } else {
                cubeModel = ObjLoader.load("../docs/cube.obj");
                reflectedCubeModel = mirrorModel(cubeModel);
                cubeMaterial = Material.fromPng("../docs/cube.png");
                floorModel = ObjLoader.load("../docs/floor.obj");
                floorMaterial = Material.fromPng("../docs/floor_texture.png");
                maskMaterial = Material.fromPng("../docs/puddle_mask.png");
            }
            if (floorMaterial == null) floorMaterial = Material.solidColor(0x333333);
            if (maskMaterial == null) maskMaterial = Material.solidColor(0xFFFFFF);

            skyboxMaterial = Material.fromPng("docs/skymap.png");
            if (skyboxMaterial == null) {
                skyboxMaterial = Material.fromPng("../docs/skymap.png");
            }
            if (skyboxMaterial != null && cubeModel != null) {
                skyboxModel = createSkybox(cubeModel);
                reflectedSkyboxModel = mirrorModel(skyboxModel);
            }
        } catch (Exception e) {
            System.err.println("Failed to load user models, falling back: " + e.getMessage());
            cubeModel = new ObjLoader.ModelData();
            reflectedCubeModel = mirrorModel(cubeModel);
            floorModel = new ObjLoader.ModelData();
            cubeMaterial = Material.solidColor(0xFFFFFF);
            floorMaterial = Material.solidColor(0x888888);
        }
    }

    private void reallocateBuffers(int w, int h) {
        int ssaa = controller.getSsaaFactor();
        int renderW = w * ssaa;
        int renderH = h * ssaa;
        
        buffers.reallocateForSize(w, h, ssaa);
        
        maskFb = new Framebuffer(renderW, renderH, new int[renderW * renderH]);
        reflectionFb = new Framebuffer(renderW, renderH, new int[renderW * renderH]);

        activeRenderer = new Renderer3D(new RenderPipeline(camera, buffers.renderFb, rasterizer));
        maskRenderer = new Renderer3D(new RenderPipeline(camera, maskFb, rasterizer));
        refRenderer = new Renderer3D(new RenderPipeline(camera, reflectionFb, rasterizer));
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        JFrame frame = new JFrame("FastSoftware3D Demo v1");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        frame.addNotify();
        try {
            long hwnd = FastTheme.getWindowHandle(frame);
            FastTheme.setTitleBarDarkMode(hwnd, true);
        } catch (Throwable t) {
            System.err.println("FastTheme native features not available: " + t.getMessage());
        }

        new Demo(frame);
    }
}
