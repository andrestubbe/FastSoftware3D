package fastsoftware3d.terminal;

import fastmouse.FastMouseListener;
import java.io.IOException;
import java.io.InputStream;

/**
 * 🌌 AnsiMouse - Asynchronous Virtual Terminal Mouse Tracker (SGR-1006).
 * Spawns a dedicated low-priority background thread to parse exact character-cell 
 * coordinates directly from the terminal host's standard input stream.
 */
public class AnsiMouse {
    private final FastMouseListener listener;
    private final Thread readerThread;
    private volatile boolean running = false;
    
    private AnsiMouse(FastMouseListener listener) {
        this.listener = listener;
        this.readerThread = new Thread(this::readLoop, "AnsiMouse-Reader");
        this.readerThread.setDaemon(true);
        this.readerThread.setPriority(Thread.MAX_PRIORITY); // Maximum priority for lag-free mouse rendering
    }
    
    /**
     * Opens mouse tracking and registers a listener callback.
     */
    public static AnsiMouse open(FastMouseListener listener) {
        AnsiMouse mouse = new AnsiMouse(listener);
        mouse.start();
        return mouse;
    }
    
    private void start() {
        if (running) return;
        running = true;
        
        // 1. Configure JNI Virtual Terminal raw input state
        try {
            FastTerminal.setAnsiRawMode(true);
        } catch (Throwable t) {
            System.err.println("[AnsiMouse] Warning: Failed to configure Win32 raw mode: " + t.getMessage());
        }
        
        // 2. Write standard ANSI escape sequences to request mouse coordinates
        // \u001b[?1003h = Track all mouse movement/hover
        // \u001b[?1006h = SGR extended format support
        System.out.print("\u001b[?1003h\u001b[?1006h");
        System.out.flush();
        
        // 3. Boot input stream polling thread
        readerThread.start();
    }
    
    /**
     * Gracefully stops the reading thread and restores standard terminal modes.
     */
    public void close() {
        if (!running) return;
        running = false;
        
        // 1. Disable SGR mouse tracking
        System.out.print("\u001b[?1003l\u001b[?1006l");
        System.out.flush();
        
        // 2. Restore console input mode flags
        try {
            FastTerminal.setAnsiRawMode(false);
        } catch (Throwable ignored) {}
        
        // 3. Interrupt standard input blocking read call
        readerThread.interrupt();
    }
    
    private void readLoop() {
        InputStream in = System.in;
        byte[] buffer = new byte[4096];
        byte[] seqBuf = new byte[64];
        int seqLen = 0;
        
        int lastCellX = -1;
        int lastCellY = -1;
        
        while (running) {
            try {
                int read = in.read(buffer);
                if (read <= 0) {
                    if (read == -1) break; // EOF
                    continue;
                }
                
                for (int i = 0; i < read; i++) {
                    byte b = buffer[i];
                    
                    if (seqLen < seqBuf.length) {
                        seqBuf[seqLen++] = b;
                    } else {
                        // Buffer overflow. Cap and reset.
                        seqLen = 0;
                        seqBuf[seqLen++] = b;
                    }
                    
                    if (seqLen == 1 && b != 27) {
                        seqLen = 0;
                        continue;
                    }
                    if (seqLen == 2 && b != '[') {
                        seqLen = 0;
                        if (b == 27) seqBuf[seqLen++] = b;
                        continue;
                    }
                    if (seqLen == 3 && b != '<') {
                        seqLen = 0;
                        if (b == 27) seqBuf[seqLen++] = b;
                        continue;
                    }
                    
                    if (seqLen > 3 && (b == 'M' || b == 'm')) {
                        boolean isPressed = (b == 'M');
                        
                        int start = 3;
                        int end = seqLen - 1;
                        
                        int firstSemi = fastsoftware3d.ascii.FastASCIIReader.readUntil(seqBuf, start, end, (byte) ';');
                        if (firstSemi != -1) {
                            int button = fastsoftware3d.ascii.FastASCIIReader.parseUInt(seqBuf, start, firstSemi);
                            int secondSemi = fastsoftware3d.ascii.FastASCIIReader.readUntil(seqBuf, firstSemi + 1, end, (byte) ';');
                            if (secondSemi != -1) {
                                int col = fastsoftware3d.ascii.FastASCIIReader.parseUInt(seqBuf, firstSemi + 1, secondSemi);
                                int row = fastsoftware3d.ascii.FastASCIIReader.parseUInt(seqBuf, secondSemi + 1, end);
                                
                                int cellX = col - 1;
                                int cellY = row - 1;
                                
                                if (cellX != lastCellX || cellY != lastCellY) {
                                    lastCellX = cellX;
                                    lastCellY = cellY;
                                    listener.onMouseMove(0, 0, 0, cellX, cellY);
                                }
                                
                                if (button == 35) {
                                    // motion already dispatched
                                } else if (button == 64) {
                                    listener.onMouseWheel(0, 1);
                                } else if (button == 65) {
                                    listener.onMouseWheel(0, -1);
                                } else {
                                    int mappedButton = -1;
                                    int rawBtn = button & 3;
                                    if (rawBtn == 0) mappedButton = 0;
                                    else if (rawBtn == 1) mappedButton = 2;
                                    else if (rawBtn == 2) mappedButton = 1;
                                    
                                    if (mappedButton != -1) {
                                        listener.onMouseButton(0, mappedButton, isPressed);
                                    }
                                }
                            }
                        }
                        
                        seqLen = 0;
                    }
                }
            } catch (IOException e) {
                if (!running) break;
            }
        }
    }
}
