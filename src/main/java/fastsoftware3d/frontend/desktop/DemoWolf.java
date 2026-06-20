package fastsoftware3d.frontend.desktop;

import fasttheme.FastTheme;
import fastsoftware3d.camera.Camera;
import fastsoftware3d.camera.CameraController;
import fastsoftware3d.core.RenderPipeline;
import fastsoftware3d.core.Framebuffer;
import fastsoftware3d.material.Material;
import fastsoftware3d.rasterizer.NativeRasterizer;
import fastsoftware3d.scene.Scene;
import fastsoftware3d.scene.Renderer3D;
import fastsoftware3d.scene.SceneFactory;
import fastsoftware3d.buffers.RenderBuffers;

import javax.swing.JFrame;
import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferStrategy;

public class DemoWolf extends Canvas {

    private static final int WIDTH = 1173;
    private static final int HEIGHT = 610;

    private static final int LOW_WIDTH = 120;
    private static final int LOW_HEIGHT = 60;

    private static final float SCALE = 100.0f;

    private final RenderBuffers buffers;
    private Renderer3D activeRenderer;

    private boolean lowResMode = false;

    private Scene scene;
    private final JFrame parentFrame;
    private Point lastMousePos;
    private int currentFps = 0;

    // Camera matching DemoWolfTerminal
    private final Camera camera = new Camera(
            32.0f,                // X
            100.0f,               // Y eye height
            -226.0f,              // Z
            0.0f,                 // yaw: looking forward (+Z)
            -0.05f,               // slight downward pitch
            75.0f                 // FOV
    );
    private final CameraController controller = new CameraController(camera);

    public DemoWolf(JFrame parentFrame) {
        this.parentFrame = parentFrame;
        this.buffers = new RenderBuffers(WIDTH, HEIGHT, LOW_WIDTH, LOW_HEIGHT);
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setIgnoreRepaint(true);
        initBuffers();
        init3DScene();
        initInput();
    }

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
        reallocateBuffers();
    }

    private void init3DScene() {
        scene = SceneFactory.createWolfScene(SCALE);
        System.out.println("✓ Wolfenstein Scene initialized. Camera: (" + camera.x + ", " + camera.y + ", " + camera.z + ")");
    }

    private void initInput() {
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_P:
                        synchronized (DemoWolf.this) {
                            lowResMode = !lowResMode;
                            reallocateBuffers();
                        }
                        break;
                    default:
                        boolean shouldRealloc = controller.onKeySwing(e.getKeyCode(), true);
                        if (shouldRealloc) {
                            synchronized (DemoWolf.this) {
                                reallocateBuffers();
                            }
                        }
                        break;
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_P:
                        break;
                    default:
                        controller.onKeySwing(e.getKeyCode(), false);
                        break;
                }
            }
        });

        // Hide cursor
        java.awt.image.BufferedImage cursorImg = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Cursor blankCursor = java.awt.Toolkit.getDefaultToolkit().createCustomCursor(
                cursorImg, new Point(0, 0), "blank cursor");
        setCursor(blankCursor);

        // Robot for centering mouse
        final java.awt.Robot robot;
        java.awt.Robot tempRobot = null;
        try {
            tempRobot = new java.awt.Robot();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        robot = tempRobot;

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // Clicking regains/locks focus
                requestFocusInWindow();
                if (robot != null) {
                    Point center = getCanvasCenterOnScreen();
                    robot.mouseMove(center.x, center.y);
                    lastMousePos = new Point(center.x, center.y);
                }
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (robot != null && isFocusOwner()) {
                    Point center = getCanvasCenterOnScreen();
                    int dx = e.getXOnScreen() - center.x;
                    int dy = e.getYOnScreen() - center.y;

                    // Only rotate if there's movement
                    if (dx != 0 || dy != 0) {
                        synchronized (DemoWolf.this) {
                            controller.onMouseMove(dx, dy, true);
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

        setFocusable(true);
        requestFocusInWindow();
    }

    private Point getCanvasCenterOnScreen() {
        try {
            Point loc = getLocationOnScreen();
            return new Point(loc.x + getWidth() / 2, loc.y + getHeight() / 2);
        } catch (Exception e) {
            return new Point(0, 0);
        }
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

                controller.update(deltaTime);
                camera.y = 1.0f * SCALE; // Locked eye height

                Graphics2D g2d = buffers.screenBuffer.createGraphics();

                synchronized (this) {
                    java.util.Arrays.fill(buffers.renderPixels, 0x948D6B);
                    activeRenderer.clear();

                    scene.update(deltaTime);

                    Graphics2D renderG = buffers.renderBuffer.createGraphics();
                    scene.render(activeRenderer, renderG);
                    renderG.dispose();
                    activeRenderer.getPipeline().postProcess();

                    int[] baseDims = buffers.getBaseDimensions(lowResMode);
                    int baseW = baseDims[0];
                    int baseH = baseDims[1];
                    int ssaaFactor = controller.ssaaFactor;
                    int renderW = baseW * ssaaFactor;
                    int renderH = baseH * ssaaFactor;

                    if (ssaaFactor > 1 && buffers.downsamplePixels != null) {
                        fastsoftware3d.frontend.terminal.TerminalDownsampler.downsample(buffers.renderPixels, renderW, renderH,
                                buffers.downsamplePixels, baseW, baseH, ssaaFactor);
                        for (int y = 0; y < baseH && y < HEIGHT; y++) {
                            for (int x = 0; x < baseW && x < WIDTH; x++) {
                                buffers.screenPixels[y * WIDTH + x] = buffers.downsamplePixels[y * baseW + x];
                            }
                        }
                    } else {
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
        JFrame frame = new JFrame("FastSoftware3D - Wolfenstein Desktop Demo");
        DemoWolf canvas = new DemoWolf(frame);
        frame.add(canvas);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setLocationRelativeTo(null);

        // Hide cursor on the parent frame
        java.awt.image.BufferedImage cursorImg = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Cursor blankCursor = java.awt.Toolkit.getDefaultToolkit().createCustomCursor(
                cursorImg, new Point(0, 0), "blank cursor");
        frame.setCursor(blankCursor);

        frame.setVisible(true);
        canvas.start();
    }
}
