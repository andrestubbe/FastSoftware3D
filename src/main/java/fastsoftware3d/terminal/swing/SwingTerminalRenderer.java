package fastsoftware3d.terminal.swing;

import fastsoftware3d.terminal.FastTerminalScene;
import fastmouse.FastMouseListener;
import fastkeyboard.FastKeyboardListener;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class SwingTerminalRenderer {
    private JFrame frame;
    private TerminalPanel panel;
    private int cols;
    private int rows;
    
    private int fontW = 10;
    private int fontH = 20;

    private FastMouseListener mouseListener;
    private FastKeyboardListener keyboardListener;
    
    private static java.awt.image.BufferedImage createRoundIcon() {
        java.awt.image.BufferedImage icon = new java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = icon.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillOval(4, 4, 56, 56);
        g.dispose();
        return icon;
    }

    public SwingTerminalRenderer(int cols, int rows, String title) {
        this.cols = cols;
        this.rows = rows;
        
        frame = new JFrame(title != null ? title : "FastTerminal");
        frame.setIconImage(createRoundIcon());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        
        panel = new TerminalPanel();
        
        Font font = new Font("Cascadia Mono", Font.PLAIN, 16);
        if (!font.getFamily().equals("Cascadia Mono")) {
            font = new Font("Consolas", Font.PLAIN, 16);
            if (!font.getFamily().equals("Consolas")) {
                font = new Font(Font.MONOSPACED, Font.PLAIN, 16);
            }
        }
        panel.setFont(font);
        FontMetrics fm = panel.getFontMetrics(font);
        fontW = fm.charWidth('W');
        fontH = fm.getHeight();
        
        panel.setPreferredSize(new Dimension(cols * fontW, rows * fontH));
        
        panel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) { handleMouse(e); }
            @Override
            public void mouseDragged(MouseEvent e) { handleMouse(e); }
        });
        
        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { handleMouseButton(e, true); }
            @Override
            public void mouseReleased(MouseEvent e) { handleMouseButton(e, false); }
        });

        panel.addMouseWheelListener(e -> {
            if (mouseListener != null) {
                mouseListener.onMouseWheel(0, e.getWheelRotation());
            }
        });
        
        frame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) { handleKey(e, true); }
            @Override
            public void keyReleased(KeyEvent e) { handleKey(e, false); }
        });

        frame.add(panel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        try {
            long hwnd = fasttheme.FastTheme.getWindowHandle(frame);
            if (hwnd != 0) {
                fasttheme.FastTheme.setTitleBarDarkMode(hwnd, true);
                fasttheme.FastTheme.setTitleBarColor(hwnd, 0, 0, 0);
            }
        } catch (Throwable t) {
            System.err.println("Could not apply FastTheme: " + t.getMessage());
        }
    }
    
    public void setMouseListener(FastMouseListener listener) {
        this.mouseListener = listener;
    }
    
    public void setKeyboardListener(FastKeyboardListener listener) {
        this.keyboardListener = listener;
    }
    
    private void handleMouse(MouseEvent e) {
        if (mouseListener != null) {
            int cellX = e.getX() / fontW;
            int cellY = e.getY() / fontH;
            if (cellX < 0) cellX = 0;
            if (cellX >= cols) cellX = cols - 1;
            if (cellY < 0) cellY = 0;
            if (cellY >= rows) cellY = rows - 1;
            mouseListener.onMouseMove(0, 0, 0, cellX, cellY);
        }
    }
    
    private void handleMouseButton(MouseEvent e, boolean isPressed) {
        if (mouseListener != null) {
            int btn = -1;
            if (SwingUtilities.isLeftMouseButton(e)) btn = 0;
            else if (SwingUtilities.isRightMouseButton(e)) btn = 1;
            else if (SwingUtilities.isMiddleMouseButton(e)) btn = 2;
            
            if (btn != -1) {
                mouseListener.onMouseButton(0, btn, isPressed);
            }
        }
    }
    
    private void handleKey(KeyEvent e, boolean isPressed) {
        if (keyboardListener != null) {
            int vKey = e.getKeyCode(); 
            String keyCharStr = String.valueOf(e.getKeyChar());
            if (vKey == KeyEvent.VK_BACK_SPACE) vKey = 0x08;
            keyboardListener.onKeyEvent(0, vKey, 0, isPressed, false, System.currentTimeMillis(), keyCharStr);
        }
    }

    public void render(FastTerminalScene scene) {
        panel.setScene(scene);
        panel.repaint();
    }
    
    private class TerminalPanel extends JPanel {
        private FastTerminalScene scene;
        
        public void setScene(FastTerminalScene scene) {
            this.scene = scene;
            this.setBackground(Color.BLACK);
            this.setOpaque(true);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (scene == null) return;
            
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            
            int[] cp = scene.getCodepointBuffer();
            int[] fg = scene.getFgBuffer();
            int[] bg = scene.getBgBuffer();
            
            FontMetrics fm = g2d.getFontMetrics();
            int ascent = fm.getAscent();
            
            for (int y = 0; y < rows; y++) {
                for (int x = 0; x < cols; x++) {
                    int i = y * cols + x;
                    
                    int bgC = bg[i];
                    if (bgC != -1 && bgC != -2) {
                        g2d.setColor(new Color(bgC));
                        g2d.fillRect(x * fontW, y * fontH, fontW, fontH);
                    } else if (bgC == -1) {
                        g2d.setColor(Color.BLACK);
                        g2d.fillRect(x * fontW, y * fontH, fontW, fontH);
                    }
                    
                    int code = cp[i];
                    if (code != ' ' && code != 0 && code != -99) {
                        int fgC = fg[i];
                        if (fgC != -1 && fgC != -2) {
                            g2d.setColor(new Color(fgC));
                        } else {
                            g2d.setColor(Color.WHITE);
                        }
                        
                        g2d.drawString(String.valueOf((char)code), x * fontW, y * fontH + ascent);
                    }
                }
            }
        }
    }
}
