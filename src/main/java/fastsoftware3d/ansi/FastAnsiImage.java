package fastsoftware3d.ansi;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;

/**
 * FastAnsiImage — everything image & video → ANSI in one place.
 *
 * All variations, zero dependency on FastTerminal or any rendering library.
 * Writes output through the {@link CellConsumer} interface, which
 * FastTerminalScene, CanvasModel, or any cell buffer can implement.
 *
 * ── Modes ──────────────────────────────────────────────────────────────────
 *   HALF_BLOCK  ▀/▄/█  fg=top pixel, bg=bottom pixel. 2× vertical res. Best quality.
 *   FULL_BLOCK  █       bg = pixel colour. Pure 1:1 block pixels.
 *   RAMP        ASCII   Luminance → density character, coloured fg. Classic art.
 *   HYBRID      Both    bg pixel colour + fg density char for visual depth.
 *
 * ── Still image ─────────────────────────────────────────────────────────────
 *   FastAnsiImage.draw(img, consumer, x, y, cols, rows, Mode.HALF_BLOCK);
 *   String ansi = FastAnsiImage.toString(img, cols, rows, Mode.HALF_BLOCK);
 *
 * ── Video / animation ───────────────────────────────────────────────────────
 *   FrameSource src = FastAnsiImage.fromGif(file);
 *   FrameSource src = FastAnsiImage.fromVideoFile(file, cols, rows, mode);
 *   FrameSource src = FastAnsiImage.fromImages(list, 24.0);
 *   AtomicBoolean stop = FastAnsiImage.playAsync(src, consumer, 0,0,cols,rows,mode, onFrame);
 */
public final class FastAnsiImage {

    // ── Mode ─────────────────────────────────────────────────────────────────
    public enum Mode { HALF_BLOCK, FULL_BLOCK, RAMP, HYBRID }

    /** Luminance ramp (darkest → brightest). */
    private static final char[] RAMP_CHARS = " .,:;i1tfLCG08@█".toCharArray();

    private FastAnsiImage() {}

    // =========================================================================
    // STILL IMAGE → CellConsumer
    // =========================================================================

    /**
     * Draw a scaled image into any CellConsumer at position (x, y).
     *
     * @param src      Source image (any size — scaled automatically).
     * @param target   Destination (FastTerminalScene, CanvasModel, …).
     * @param x, y     Top-left destination cell.
     * @param cols     Width in terminal columns.
     * @param rows     Height in terminal rows.
     * @param mode     Rendering mode.
     */
    public static void draw(BufferedImage src, CellConsumer target,
                            int x, int y, int cols, int rows, Mode mode) {
        BufferedImage scaled = scale(src, cols, pixelH(rows, mode));
        for (int row = 0; row < rows; row++)
            for (int col = 0; col < cols; col++)
                writeCell(scaled, col, row, target, x + col, y + row, mode);
    }

    // =========================================================================
    // STILL IMAGE → ANSI String  (no CellConsumer, pure string output)
    // =========================================================================

    /**
     * Render a BufferedImage as an ANSI-coloured string ready to print to stdout.
     */
    public static String toString(BufferedImage src, int cols, int rows, Mode mode) {
        BufferedImage scaled = scale(src, cols, pixelH(rows, mode));
        StringBuilder sb = new StringBuilder(cols * rows * 30);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++)
                appendCell(scaled, col, row, sb, mode);
            sb.append(FastANSI.RESET);
            if (row < rows - 1) sb.append('\n');
        }
        return sb.toString();
    }

    // =========================================================================
    // FRAME SOURCE — abstraction over all video/animation inputs
    // =========================================================================

    /**
     * A source of sequential BufferedImage frames.
     * Covers files, GIFs, cameras, and image lists.
     */
    public interface FrameSource extends AutoCloseable {
        /** Return the next frame, or null if done. */
        BufferedImage nextFrame();
        /** True while more frames remain. */
        boolean hasNext();
        /** Target playback FPS. 0 = no pacing. */
        double getFps();
        /** Total frame count, or -1 if unknown (live/streaming). */
        int getFrameCount();
        @Override void close();
    }

    // ── Animated GIF ─────────────────────────────────────────────────────────
    /**
     * Open an animated GIF as a FrameSource.
     * Frame durations are read from GIF metadata (centiseconds → ms).
     */
    public static FrameSource fromGif(File file) throws IOException {
        ImageInputStream iis = ImageIO.createImageInputStream(file);
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext()) throw new IOException("No GIF ImageReader available");
        ImageReader reader = readers.next();
        reader.setInput(iis, false);
        int count = reader.getNumImages(true);
        List<BufferedImage> frames = new ArrayList<>(count);
        List<Integer>       delays = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            frames.add(reader.read(i));
            delays.add(gifDelay(reader.getImageMetadata(i)));
        }
        reader.dispose();
        iis.close();
        double fps = delays.isEmpty() ? 10.0
                   : 1000.0 / Math.max(10, delays.stream().mapToInt(d->d).average().orElse(100));
        return new ListFrameSource(frames, fps);
    }

    // ── Video file — auto backend (JCodec or FFmpeg) ─────────────────────────
    /**
     * Open a video file using the best available backend:
     *   1. JCodec  — pure Java MP4/H.264 decoder.  No install needed.
     *                Add org.jcodec:jcodec-javase:0.2.5 to your classpath.
     *   2. FFmpeg  — universal fallback.  Requires ffmpeg on PATH.
     *
     * JCodec supports: MP4, MOV (H.264 / MPEG-4).
     * FFmpeg supports: everything (mp4, mkv, avi, webm, mov, …).
     *
     * @param file   Video file.
     * @param cols   Output width in terminal columns.
     * @param rows   Output height in terminal rows.
     * @param mode   Rendering mode (affects pixel height for HALF_BLOCK).
     */
    public static FrameSource fromVideoFile(File file, int cols, int rows, Mode mode)
            throws IOException {
        if (jcodecAvailable()) {
            return fromVideoFileJCodec(file, cols, rows, mode);
        }
        if (!ffmpegAvailable()) {
            throw new IOException(
                "No video backend found.\n" +
                "Option A (pure Java): add org.jcodec:jcodec-javase:0.2.5 to classpath.\n" +
                "Option B (universal): install ffmpeg and add it to PATH."
            );
        }
        return fromVideoFileFfmpeg(file, cols, rows, mode);
    }

    // ── JCodec backend (pure Java, no install) ───────────────────────────────
    /**
     * Pure-Java MP4/H.264 decoder via JCodec.
     * Requires {@code org.jcodec:jcodec-javase} on classpath.
     * Frames are decoded at native resolution then scaled in Java.
     */
    public static FrameSource fromVideoFileJCodec(File file, int cols, int rows, Mode mode)
            throws IOException {
        if (!jcodecAvailable())
            throw new IOException("JCodec not on classpath: add org.jcodec:jcodec-javase:0.2.5");
        try {
            // Reflective access so JCodec stays optional at compile time
            Class<?> grabClass  = Class.forName("org.jcodec.api.awt.AWTFrameGrab");
            Class<?> nioClass   = Class.forName("org.jcodec.common.io.NIOUtils");
            Object   channel    = nioClass.getMethod("readableChannel", File.class).invoke(null, file);
            Object   grab       = grabClass.getMethod("createAWTFrameGrab", Class.forName("org.jcodec.common.io.SeekableByteChannel"))
                                           .invoke(null, channel);

            // Extract FPS and frame count from track metadata
            Object   track      = grab.getClass().getMethod("getVideoTrack").invoke(grab);
            Object   meta       = track.getClass().getMethod("getMeta").invoke(track);
            double   totalDur   = (double) meta.getClass().getMethod("getTotalDuration").invoke(meta);
            int      totalFr    = (int)((Integer) meta.getClass().getMethod("getTotalFrames").invoke(meta));
            double   fps        = (totalDur > 0) ? totalFr / totalDur : 24.0;

            int pixH = pixelH(rows, mode);
            final Object grabFinal = grab;

            return new FrameSource() {
                private boolean done = false;

                @Override public BufferedImage nextFrame() {
                    if (done) return null;
                    try {
                        BufferedImage full = (BufferedImage) grabFinal.getClass().getMethod("getFrame").invoke(grabFinal);
                        if (full == null) { done = true; return null; }
                        // Scale to target size
                        return scale(full, cols, pixH);
                    } catch (Exception e) { done = true; return null; }
                }

                @Override public boolean hasNext()       { return !done; }
                @Override public double  getFps()        { return fps; }
                @Override public int     getFrameCount() { return totalFr; }
                @Override public void    close() {
                    try {
                        // Close the underlying SeekableByteChannel
                        Object ch = grabFinal.getClass().getMethod("getVideoTrack").invoke(grabFinal);
                        ch.getClass().getMethod("close").invoke(ch);
                    } catch (Exception ignored) {}
                }
            };
        } catch (Exception e) {
            throw new IOException("JCodec error: " + e.getMessage(), e);
        }
    }

    // ── FFmpeg backend ────────────────────────────────────────────────────────
    /**
     * Video decoder via FFmpeg subprocess pipe.
     * Requires {@code ffmpeg} on PATH. Supports all formats.
     */
    public static FrameSource fromVideoFileFfmpeg(File file, int cols, int rows, Mode mode)
            throws IOException {
        if (!ffmpegAvailable()) throw new IOException("ffmpeg not found on PATH");
        int    pixH = pixelH(rows, mode);
        double fps  = probeFps(file);

        ProcessBuilder pb = new ProcessBuilder(
            "ffmpeg", "-i", file.getAbsolutePath(),
            "-f", "rawvideo", "-pix_fmt", "rgb24",
            "-vf", "scale=" + cols + ":" + pixH, "-"
        );
        pb.redirectErrorStream(false);
        Process proc   = pb.start();
        int     fBytes = cols * pixH * 3;

        return new FrameSource() {
            private final InputStream in   = proc.getInputStream();
            private final byte[]      buf  = new byte[fBytes];
            private boolean done = false;

            @Override public BufferedImage nextFrame() {
                if (done) return null;
                try {
                    int read = 0;
                    while (read < fBytes) {
                        int n = in.read(buf, read, fBytes - read);
                        if (n < 0) { done = true; return null; }
                        read += n;
                    }
                    BufferedImage img = new BufferedImage(cols, pixH, BufferedImage.TYPE_INT_RGB);
                    int[] px = new int[cols * pixH];
                    for (int i = 0; i < px.length; i++) {
                        int r = buf[i*3] & 0xFF, g = buf[i*3+1] & 0xFF, b = buf[i*3+2] & 0xFF;
                        px[i] = (r<<16)|(g<<8)|b;
                    }
                    img.setRGB(0, 0, cols, pixH, px, 0, cols);
                    return img;
                } catch (IOException e) { done = true; return null; }
            }
            @Override public boolean hasNext()       { return !done; }
            @Override public double  getFps()        { return fps; }
            @Override public int     getFrameCount() { return -1; }
            @Override public void    close()         {
                proc.destroyForcibly();
                try { in.close(); } catch (IOException ignored) {}
            }
        };
    }

    // ── Image list / sequence ─────────────────────────────────────────────────
    /** Wrap a pre-loaded list of frames at a fixed FPS. */
    public static FrameSource fromImages(List<BufferedImage> frames, double fps) {
        return new ListFrameSource(frames, fps);
    }

    /** Load every image in a directory as a sorted filename sequence. */
    public static FrameSource fromDirectory(File dir, double fps) throws IOException {
        File[] files = dir.listFiles(f -> {
            String n = f.getName().toLowerCase();
            return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg");
        });
        if (files == null || files.length == 0)
            throw new IOException("No images in: " + dir);
        java.util.Arrays.sort(files);
        List<BufferedImage> frames = new ArrayList<>(files.length);
        for (File f : files) frames.add(ImageIO.read(f));
        return new ListFrameSource(frames, fps);
    }

    // =========================================================================
    // PLAYBACK
    // =========================================================================

    /**
     * Play a FrameSource into a CellConsumer in real time (blocking).
     * Run this from a dedicated thread or register it with FastAnimation.
     *
     * @param source   Any FrameSource.
     * @param target   Destination consumer (FastTerminalScene, etc.).
     * @param x, y     Placement in consumer coordinate space.
     * @param cols     Width in cells.
     * @param rows     Height in cells.
     * @param mode     Rendering mode.
     * @param onFrame  Called after each frame is written — use to present/flush.
     * @param stop     Set to true externally to stop playback.
     */
    public static void play(FrameSource source,
                            CellConsumer target,
                            int x, int y, int cols, int rows,
                            Mode mode, Runnable onFrame,
                            AtomicBoolean stop) {
        double fps     = source.getFps();
        long   frameNs = fps > 0 ? (long)(1_000_000_000.0 / fps) : 0;
        while (source.hasNext() && !stop.get()) {
            long t0    = System.nanoTime();
            BufferedImage frame = source.nextFrame();
            if (frame == null) break;
            draw(frame, target, x, y, cols, rows, mode);
            onFrame.run();
            if (frameNs > 0) {
                long sleep = (frameNs - (System.nanoTime() - t0)) / 1_000_000;
                if (sleep > 0) try { Thread.sleep(sleep); } catch (InterruptedException e) { break; }
            }
        }
    }

    /**
     * Async variant — launches a daemon thread, returns the stop flag.
     * Set the returned AtomicBoolean to true to stop playback.
     */
    public static AtomicBoolean playAsync(FrameSource source,
                                          CellConsumer target,
                                          int x, int y, int cols, int rows,
                                          Mode mode, Runnable onFrame) {
        AtomicBoolean stop = new AtomicBoolean(false);
        Thread t = new Thread(() -> {
            play(source, target, x, y, cols, rows, mode, onFrame, stop);
            try { source.close(); } catch (Exception ignored) {}
        }, "FastAnsiImage-player");
        t.setDaemon(true);
        t.start();
        return stop;
    }

    // =========================================================================
    // OFFLINE PRE-RENDER  (returns a String per frame for maximum portability)
    // =========================================================================

    /**
     * Pre-render all frames to ANSI strings.
     * Play back by printing each string with a cursor-home prefix.
     */
    public static List<String> preRenderToStrings(FrameSource source,
                                                   int cols, int rows, Mode mode) {
        List<String> out = new ArrayList<>();
        while (source.hasNext()) {
            BufferedImage f = source.nextFrame();
            if (f == null) break;
            out.add(toString(f, cols, rows, mode));
        }
        return out;
    }

    /** @deprecated Use {@link #toString(BufferedImage, int, int, Mode)} */
    public static String render(BufferedImage src, int cols, int rows, Mode mode) {
        return toString(src, cols, rows, mode);
    }

    // =========================================================================
    // FFmpeg helpers
    // =========================================================================

    private static Boolean ffmpegPresent = null;

    /** Returns true if {@code ffmpeg} is available on PATH. */
    public static boolean ffmpegAvailable() {
        if (ffmpegPresent != null) return ffmpegPresent;
        try {
            Process p = new ProcessBuilder("ffmpeg", "-version").start();
            ffmpegPresent = (p.waitFor() == 0);
        } catch (Exception e) { ffmpegPresent = false; }
        return ffmpegPresent;
    }

    private static Boolean jcodecPresent = null;

    /**
     * Returns true if JCodec (org.jcodec:jcodec-javase) is on the classpath.
     * Checked once via reflection and cached.
     */
    public static boolean jcodecAvailable() {
        if (jcodecPresent != null) return jcodecPresent;
        try {
            Class.forName("org.jcodec.api.awt.AWTFrameGrab");
            jcodecPresent = true;
        } catch (ClassNotFoundException e) { jcodecPresent = false; }
        return jcodecPresent;
    }

    private static double probeFps(File file) {
        try {
            Process p = new ProcessBuilder(
                "ffprobe", "-v", "error",
                "-select_streams", "v:0",
                "-show_entries", "stream=r_frame_rate",
                "-of", "default=noprint_wrappers=1:nokey=1",
                file.getAbsolutePath()
            ).start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            if (out.contains("/")) {
                String[] parts = out.split("/");
                return Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]);
            }
            return Double.parseDouble(out);
        } catch (Exception e) { return 24.0; }
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private static int pixelH(int rows, Mode mode) {
        return (mode == Mode.HALF_BLOCK) ? rows * 2 : rows;
    }

    private static BufferedImage scale(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                           RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    /** Write one cell to a CellConsumer. */
    private static void writeCell(BufferedImage img, int col, int row,
                                  CellConsumer target, int tx, int ty, Mode mode) {
        switch (mode) {
            case HALF_BLOCK -> {
                int top = img.getRGB(col, row*2)   & 0xFFFFFF;
                int bot = img.getRGB(col, row*2+1) & 0xFFFFFF;
                target.writeCell(tx, ty, '▀', top, bot);
            }
            case FULL_BLOCK -> {
                int rgb = img.getRGB(col, row) & 0xFFFFFF;
                target.writeCell(tx, ty, '█', rgb, rgb);
            }
            case RAMP -> {
                int rgb = img.getRGB(col, row);
                int r=(rgb>>16)&0xFF, g=(rgb>>8)&0xFF, b=rgb&0xFF;
                target.writeCell(tx, ty, rampChar(r,g,b), rgb&0xFFFFFF, 0x000000);
            }
            case HYBRID -> {
                int rgb = img.getRGB(col, row);
                int r=(rgb>>16)&0xFF, g=(rgb>>8)&0xFF, b=rgb&0xFF;
                int bright = (Math.min(255,r+60)<<16)|(Math.min(255,g+60)<<8)|Math.min(255,b+60);
                target.writeCell(tx, ty, rampChar(r,g,b), bright, rgb&0xFFFFFF);
            }
        }
    }

    /** Append one ANSI-encoded cell to a StringBuilder (string output path). */
    private static void appendCell(BufferedImage img, int col, int row,
                                   StringBuilder sb, Mode mode) {
        switch (mode) {
            case HALF_BLOCK -> {
                int top = img.getRGB(col, row*2)   & 0xFFFFFF;
                int bot = img.getRGB(col, row*2+1) & 0xFFFFFF;
                sb.append(FastANSI.fg(top>>16,(top>>8)&0xFF,top&0xFF))
                  .append(FastANSI.bg(bot>>16,(bot>>8)&0xFF,bot&0xFF))
                  .append('▀');
            }
            case FULL_BLOCK -> {
                int rgb = img.getRGB(col, row) & 0xFFFFFF;
                sb.append(FastANSI.bg(rgb>>16,(rgb>>8)&0xFF,rgb&0xFF)).append(' ');
            }
            case RAMP -> {
                int rgb = img.getRGB(col, row);
                int r=(rgb>>16)&0xFF, g=(rgb>>8)&0xFF, b=rgb&0xFF;
                sb.append(FastANSI.fg(r,g,b)).append(rampChar(r,g,b));
            }
            case HYBRID -> {
                int rgb = img.getRGB(col, row);
                int r=(rgb>>16)&0xFF, g=(rgb>>8)&0xFF, b=rgb&0xFF;
                sb.append(FastANSI.bg(r,g,b))
                  .append(FastANSI.fg(Math.min(255,r+60),Math.min(255,g+60),Math.min(255,b+60)))
                  .append(rampChar(r,g,b));
            }
        }
    }

    private static char rampChar(int r, int g, int b) {
        int L = (int)(0.2126*r + 0.7152*g + 0.0722*b);
        return RAMP_CHARS[L * (RAMP_CHARS.length - 1) / 255];
    }

    private static int gifDelay(IIOMetadata meta) {
        try {
            org.w3c.dom.Node tree = meta.getAsTree("javax_imageio_gif_image_1.0");
            org.w3c.dom.NodeList ch = tree.getChildNodes();
            for (int i = 0; i < ch.getLength(); i++) {
                org.w3c.dom.Node n = ch.item(i);
                if ("GraphicControlExtension".equals(n.getNodeName())) {
                    org.w3c.dom.Node d = n.getAttributes().getNamedItem("delayTime");
                    if (d != null) return Integer.parseInt(d.getNodeValue()) * 10;
                }
            }
        } catch (Exception ignored) {}
        return 100;
    }

    // ── ListFrameSource ───────────────────────────────────────────────────────
    private static class ListFrameSource implements FrameSource {
        private final List<BufferedImage> frames;
        private final double fps;
        private int idx = 0;

        ListFrameSource(List<BufferedImage> f, double fps) { this.frames = f; this.fps = fps; }

        @Override public BufferedImage nextFrame()    { return idx < frames.size() ? frames.get(idx++) : null; }
        @Override public boolean       hasNext()      { return idx < frames.size(); }
        @Override public double        getFps()       { return fps; }
        @Override public int           getFrameCount(){ return frames.size(); }
        @Override public void          close()        {}
    }
}
