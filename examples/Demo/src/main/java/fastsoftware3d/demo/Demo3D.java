package fastsoftware3d.demo;

import fasttheme.FastTheme;
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
import fastsoftware3d.util.TerminalDownsampler;

import javax.swing.JFrame;
import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.io.File;

public class Demo3D extends Canvas {

    private static final int WIDTH = 1173;
    private static final int HEIGHT = 610;

    private static final int LOW_WIDTH = 120;
    private static final int LOW_HEIGHT = 60;

    private final RenderBuffers buffers;
    private Renderer3D activeRenderer;

    // Mode state
    private boolean lowResMode = false; // Toggled via key 'P' - START IN FULLRES

    private Scene scene;
    private ModelNode cubeNode;
    private final JFrame parentFrame;
    private Point lastMousePos;
    private int currentFps = 0;

    private float cubeRotationY = 0.0f;

    private final Camera camera = new Camera(0.0f, 0.0f, -250.0f, 0.0f, 0.0f, 65.0f);
    private final CameraController controller = new CameraController(camera);

    public Demo3D(JFrame parentFrame) {
        this.parentFrame = parentFrame;
        this.buffers = new RenderBuffers(WIDTH, HEIGHT, LOW_WIDTH, LOW_HEIGHT);
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setIgnoreRepaint(true);
        initBuffers();
        init3DScene();
        initInput();
    }
    /**
     * Dynamically reallocate buffers based on the lowResMode and controller.ssaaFactor.
     */
    private synchronized void reallocateBuffers() {
        buffers.reallocate(lowResMode, controller.ssaaFactor);

        int[] baseDims = buffers.getBaseDimensions(lowResMode);
        int renderW = baseDims[0] * controller.ssaaFactor;
        int renderH = baseDims[1] * controller.ssaaFactor;

        Framebuffer framebuffer = new Framebuffer(renderW, renderH, buffers.renderPixels);
        RenderPipeline pipeline = new RenderPipeline(camera, framebuffer, new NativeRasterizer());
        activeRenderer = new Renderer3D(pipeline);
    }
    private void initBuffers() {
        // Dynamically allocate rendering buffers
        reallocateBuffers();
    }

    private void init3DScene() {
        SceneFactory.SceneCreationResult result = SceneFactory.createDefaultScene();
        scene = result.scene;
        cubeNode = result.cubeNode;
        System.out.println("✓ Scene initialized via SceneFactory. Camera: (" + camera.x + ", " + camera.y + ", " + camera.z + ")");
    }

    private void initInput() {
        // Keyboard controls
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_P: // Toggle low-res pixelated mode
                        synchronized (Demo3D.this) {
                            lowResMode = !lowResMode;
                            reallocateBuffers();
                        }
                        break;
                    default:
                        boolean shouldRealloc = controller.onKeySwing(e.getKeyCode(), true);
                        if (shouldRealloc) {
                            synchronized (Demo3D.this) {
                                reallocateBuffers();
                            }
                        }
                        break;
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_P: // No action on release
                        break;
                    default:
                        controller.onKeySwing(e.getKeyCode(), false);
                        break;
                }
            }
        });

        // Mouse controls
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                lastMousePos = e.getPoint();
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (lastMousePos != null) {
                    Point current = e.getPoint();
                    int dx = current.x - lastMousePos.x;
                    int dy = current.y - lastMousePos.y;

                    synchronized (Demo3D.this) {
                        controller.onMouseMove(dx, dy, true);
                    }

                    lastMousePos = current;
                }
            }
        });

        setFocusable(true);
        requestFocusInWindow();
    }

    public void start() {
        createBufferStrategy(3);
        BufferStrategy bs = getBufferStrategy();

        new Thread(() -> {
            long lastTime = System.nanoTime();
            long lastFpsTime = System.nanoTime();
            int frames = 0;

            long frameTimeTarget = 1_000_000_000L / 120;
            long lastRenderTime = System.nanoTime();

            while (true) {
                long now = System.nanoTime();
                if (now - lastRenderTime < frameTimeTarget) {
                    Thread.yield();
                    continue;
                }
                lastRenderTime = now;

                float deltaTime = (now - lastTime) / 1_000_000_000.0f;
                lastTime = now;

                float rotInc = controller.update(deltaTime);

                Graphics2D g2d = buffers.screenBuffer.createGraphics();

                synchronized (this) {
                    // 1. Clear color and depth buffers
                    java.util.Arrays.fill(buffers.renderPixels, 0x000000);
                    activeRenderer.clear();

                    cubeNode.getTransform().yaw = cubeRotationY + rotInc;
                    scene.update(deltaTime);

                    // 2. Render 3D Scene into our high-res SSAA target
                    Graphics2D renderG = buffers.renderBuffer.createGraphics();
                    // System.out.println("About to call scene.render()...");
                    scene.render(activeRenderer, renderG);
                    renderG.dispose();
                    activeRenderer.getPipeline().postProcess();

                    // 3. Downsample or direct blit
                    int[] baseDims = buffers.getBaseDimensions(lowResMode);
                    int baseW = baseDims[0];
                    int baseH = baseDims[1];
                    int ssaaFactor = controller.ssaaFactor;
                    int renderW = baseW * ssaaFactor;
                    int renderH = baseH * ssaaFactor;

                    if (ssaaFactor > 1 && buffers.downsamplePixels != null) {
                        fastsoftware3d.util.TerminalDownsampler.downsample(buffers.renderPixels, renderW, renderH,
                                buffers.downsamplePixels, baseW, baseH, ssaaFactor);
                        // Blit downsampled to screen
                        for (int y = 0; y < baseH && y < HEIGHT; y++) {
                            for (int x = 0; x < baseW && x < WIDTH; x++) {
                                buffers.screenPixels[y * WIDTH + x] = buffers.downsamplePixels[y * baseW + x];
                            }
                        }
                    } else {
                        // Direct blit (no SSAA)
                        for (int y = 0; y < baseH && y < HEIGHT; y++) {
                            for (int x = 0; x < baseW && x < WIDTH; x++) {
                                buffers.screenPixels[y * WIDTH + x] = buffers.renderPixels[y * baseW + x];
                            }
                        }
                    }
                }

                g2d.drawImage(buffers.screenBuffer, 0, 0, null);

                g2d.dispose();

                Graphics g = bs.getDrawGraphics();
                g.drawImage(buffers.screenBuffer, 0, 0, null);

                // Draw FPS in upper right corner (orange on lime-green background)
                String fpsStr = "FPS: " + currentFps;
                g.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 16));
                java.awt.FontMetrics fm = g.getFontMetrics();
                int textWidth = fm.stringWidth(fpsStr);
                int textHeight = fm.getHeight();
                int rectWidth = textWidth + 12;
                int rectHeight = textHeight + 6;
                int rectX = getWidth() - rectWidth - 10;
                int rectY = 10;

                g.setColor(new java.awt.Color(50, 205, 50)); // LimeGreen
                g.fillRect(rectX, rectY, rectWidth, rectHeight);

                g.setColor(new java.awt.Color(255, 140, 0)); // Dark Orange
                g.drawString(fpsStr, rectX + 6, rectY + textHeight - 2);

                g.dispose();
                bs.show();

                frames++;
                if (System.nanoTime() - lastFpsTime >= 1_000_000_000L) {
                    lastFpsTime = System.nanoTime();
                    currentFps = frames;
                    frames = 0;
                }
            }
        }).start();
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("FastSoftware3D - Desktop Demo");
        Demo3D canvas = new Demo3D(frame);
        frame.add(canvas);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setLocationRelativeTo(null);

        frame.addNotify();
        try {
            long hwnd = fasttheme.FastTheme.getWindowHandle(frame);
            fasttheme.FastTheme.setTitleBarDarkMode(hwnd, true);
            fasttheme.FastTheme.setTitleBarColor(hwnd, 0, 0, 0);
            fasttheme.FastTheme.setTitleBarTextColor(hwnd, 255, 255, 255);
            fasttheme.FastTheme.setWindowTransparency(hwnd, 224);
        } catch (Exception e) {
            System.err.println("FastTheme dark mode failed: " + e.getMessage());
        }

        frame.setVisible(true);
        canvas.start();
    }
}
