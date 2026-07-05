package fastsoftware3d.demo;

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
import fastsoftware3d.rasterizer.NativeRasterizer;
import fastsoftware3d.physics.WallCollider;
import java.awt.Cursor;
import java.awt.Robot;
import java.awt.Toolkit;
import fastsoftware3d.buffers.RenderBuffers;

import java.util.Arrays;

import javax.swing.JFrame;
import java.awt.Canvas;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Toolkit;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;

public class Brutalo extends Canvas {

    private static final int WIDTH = 1173;
    private static final int HEIGHT = 610;

    private static final int LOW_WIDTH = 120;
    private static final int LOW_HEIGHT = 60;

    private static final float SCALE = 100.0f;

    private final RenderBuffers buffers;
    private Renderer3D activeRenderer;

    private boolean lowResMode = false;

    private Scene sceneFlat;
    private Scene sceneOctree;
    private Scene scene;
    private boolean useOctree = false; // default off for demo for max FPS
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

    public Brutalo(JFrame parentFrame) {
        this.parentFrame = parentFrame;
        this.buffers = new RenderBuffers(WIDTH, HEIGHT, LOW_WIDTH, LOW_HEIGHT);
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setIgnoreRepaint(true);
        initBuffers();
        init3DScene();
        initInput();
    }

    private synchronized void reallocateBuffers() {
        buffers.reallocate(lowResMode, controller.getSsaaFactor());

        int[] baseDims = buffers.getBaseDimensions(lowResMode);
        int renderW = baseDims[0] * controller.getSsaaFactor();
        int renderH = baseDims[1] * controller.getSsaaFactor();

        Framebuffer framebuffer = new Framebuffer(renderW, renderH, buffers.renderPixels);
        RenderPipeline pipeline = new RenderPipeline(camera, framebuffer, new NativeRasterizer());
        activeRenderer = new Renderer3D(pipeline);
    }

    private void initBuffers() {
        reallocateBuffers();
    }

    private void init3DScene() {
        sceneFlat = SceneFactory.createWolfScene(SCALE, false);
        sceneOctree = SceneFactory.createWolfScene(SCALE, true);
        scene = useOctree ? sceneOctree : sceneFlat;
        System.out.println("✓ Wolfenstein Scene initialized. Camera: (" + camera.x + ", " + camera.y + ", " + camera.z + ")");
        
        if (!SceneFactory.loadedCollisionModels.isEmpty()) {
            fastsoftware3d.physics.WallCollider collider = new fastsoftware3d.physics.WallCollider();
            for (fastsoftware3d.model.ObjLoader.ModelData m : SceneFactory.loadedCollisionModels) {
                collider.addModel(m);
            }
            controller.setCollisionSystem(collider);
        }
    }

    private void initInput() {
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_P:
                        synchronized (Brutalo.this) {
                            lowResMode = !lowResMode;
                            reallocateBuffers();
                        }
                        break;
                    case KeyEvent.VK_O:
                        synchronized (Brutalo.this) {
                            useOctree = !useOctree;
                            scene = useOctree ? sceneOctree : sceneFlat;
                            System.out.println("Octree Culling: " + (useOctree ? "ON" : "OFF"));
                        }
                        break;
                    default:
                        boolean shouldRealloc = controller.onKeySwing(e.getKeyCode(), true);
                        if (shouldRealloc) {
                            synchronized (Brutalo.this) {
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
                    case KeyEvent.VK_O:
                        break;
                    default:
                        controller.onKeySwing(e.getKeyCode(), false);
                        break;
                }
            }
        });

        // Hide cursor
        BufferedImage cursorImg = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Cursor blankCursor = Toolkit.getDefaultToolkit().createCustomCursor(
                cursorImg, new Point(0, 0), "blank cursor");
        setCursor(blankCursor);

        // Robot for centering mouse
        final Robot robot;
        Robot tempRobot = null;
        try {
            tempRobot = new Robot();
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
                        synchronized (Brutalo.this) {
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
            long lastTitleUpdate = System.nanoTime();
            
            long[] frameDeltas = new long[60];
            int frameIdx = 0;
            long validFrames = 0;

            long frameTimeTarget = 1_000_000_000L / 120;
            long lastRenderTime = System.nanoTime();

            while (true) {
                long now = System.nanoTime();
                if (now - lastRenderTime < frameTimeTarget) {
                    Thread.yield();
                    continue;
                }
                lastRenderTime = now;

                long deltaNano = now - lastTime;
                float deltaTime = deltaNano / 1_000_000_000.0f;
                lastTime = now;
                
                frameDeltas[frameIdx] = deltaNano;
                frameIdx = (frameIdx + 1) % frameDeltas.length;
                if (validFrames < frameDeltas.length) validFrames++;

                controller.update(deltaTime);

                Graphics2D g2d = buffers.screenBuffer.createGraphics();

                synchronized (this) {
                    Arrays.fill(buffers.renderPixels, 0x000000);
                    activeRenderer.clear();

                    scene.update(deltaTime);

                    Graphics2D renderG = buffers.renderBuffer.createGraphics();
                    scene.render(activeRenderer, renderG);
                    renderG.dispose();
                    activeRenderer.getPipeline().postProcess();

                    int[] baseDims = buffers.getBaseDimensions(lowResMode);
                    int baseW = baseDims[0];
                    int baseH = baseDims[1];
                    int ssaaFactor = controller.getSsaaFactor();
                    int renderW = baseW * ssaaFactor;
                    int renderH = baseH * ssaaFactor;

                    if (ssaaFactor > 1 && buffers.downsamplePixels != null) {
                        fastsoftware3d.util.TerminalDownsampler.downsample(buffers.renderPixels, renderW, renderH, buffers.downsamplePixels, baseW, baseH, ssaaFactor);
                        if (baseW == WIDTH && baseH == HEIGHT) {
                            System.arraycopy(buffers.downsamplePixels, 0, buffers.screenPixels, 0, baseW * baseH);
                        } else {
                            for (int y = 0; y < baseH && y < HEIGHT; y++) {
                                System.arraycopy(buffers.downsamplePixels, y * baseW, buffers.screenPixels, y * WIDTH, Math.min(baseW, WIDTH));
                            }
                        }
                    } else {
                        if (baseW == WIDTH && baseH == HEIGHT) {
                            System.arraycopy(buffers.renderPixels, 0, buffers.screenPixels, 0, baseW * baseH);
                        } else {
                            for (int y = 0; y < baseH && y < HEIGHT; y++) {
                                System.arraycopy(buffers.renderPixels, y * baseW, buffers.screenPixels, y * WIDTH, Math.min(baseW, WIDTH));
                            }
                        }
                    }
                }

                g2d.drawImage(buffers.screenBuffer, 0, 0, null);
                g2d.dispose();

                Graphics g = bs.getDrawGraphics();
                g.drawImage(buffers.screenBuffer, 0, 0, null);

                g.dispose();
                bs.show();

                long nowNano = System.nanoTime();
                if (nowNano - lastTitleUpdate >= 100_000_000L) { // Update title 10x a second
                    lastTitleUpdate = nowNano;
                    long sum = 0;
                    for (long t : frameDeltas) sum += t;
                    if (sum > 0) {
                        currentFps = (int) ((validFrames * 1_000_000_000L) / sum);
                    }
                    int[] baseDims = buffers.getBaseDimensions(lowResMode);
                    int mipMode = NativeRasterizer.mipmapMode;
                    String[] mipNames = {"Nearest", "Discrete", "Dithered", "Tril-Blend", "Bilinear", "Trilinear"};
                    String mipStr = (mipMode >= 0 && mipMode < mipNames.length) ? mipNames[mipMode] : "Unknown";

                    String title = String.format("FastSoftware3D Demo v1 [Wolfenstein Scene] | FPS: %d | Res: %d x %d | SSAA: %dx | Filter: %s | Octree: %s",
                            currentFps, baseDims[0], baseDims[1], controller.getSsaaFactor(), mipStr, useOctree ? "ON" : "OFF");
                    parentFrame.setTitle(title);
                }
            }
        }).start();
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("FastSoftware3D - Brutalo Demo");
        Brutalo canvas = new Brutalo(frame);
        frame.add(canvas);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setLocationRelativeTo(null);

        // Hide cursor on the parent frame
        BufferedImage cursorImg = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Cursor blankCursor = Toolkit.getDefaultToolkit().createCustomCursor(
                cursorImg, new Point(0, 0), "blank cursor");
        frame.setCursor(blankCursor);

        frame.addNotify();
        try {
            long hwnd = FastTheme.getWindowHandle(frame);
            FastTheme.setTitleBarDarkMode(hwnd, true);
            FastTheme.setTitleBarColor(hwnd, 0, 0, 0);
            FastTheme.setTitleBarTextColor(hwnd, 255, 255, 255);
            // FastTheme.setWindowTransparency(hwnd, 224);
        } catch (Exception e) {
            System.err.println("FastTheme dark mode failed: " + e.getMessage());
        }

        frame.setVisible(true);
        canvas.start();
    }
}
