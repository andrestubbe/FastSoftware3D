package fastsoftware3d.ansi.cli;

import fastsoftware3d.ansi.FastANSI;
import fastsoftware3d.ansi.FastAnsiImage;
import fastsoftware3d.ansi.FastAnsiImage.Mode;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * FastANSI CLI Converter.
 *
 * Converts images, animated GIFs, and video files to ANSI art.
 *
 * Usage:
 *   run-converter  <input>                       preview in terminal
 *   run-converter  <input>  --output out.ansi    save ANSI text to file
 *   run-converter  <input>  --output play.sh     save self-playing bash script
 *   run-converter  <input>  --output play.bat    save self-playing Windows batch
 *
 * Options:
 *   -m, --mode   HALF_BLOCK | FULL_BLOCK | RAMP | HYBRID   (default: HALF_BLOCK)
 *   -w, --width  <columns>   (default: auto-detect)
 *   -h, --height <rows>      (default: auto-detect)
 *       --fps    <number>    override FPS for video
 *       --loop               loop GIF / video indefinitely in preview
 *   -o, --output <file>      write to file instead of terminal
 *       --help               show this message
 */
public class Converter {

    // ── ANSI cursor control ──────────────────────────────────────────────────
    private static final String HOME = "\033[H";

    // =========================================================================
    public static void main(String[] args) throws Exception {

        // ── Argument parsing ─────────────────────────────────────────────────
        String inputPath  = null;
        String outputPath = null;
        Mode   mode       = Mode.HALF_BLOCK;
        int    width      = -1;
        int    height     = -1;
        double fpsOverride = -1;
        boolean loop      = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--help", "-?" -> { printHelp(); return; }
                case "--loop"       -> loop = true;
                case "--mode",  "-m" -> mode  = Mode.valueOf(args[++i].toUpperCase());
                case "--width", "-w" -> width  = Integer.parseInt(args[++i]);
                case "--height","-h" -> height = Integer.parseInt(args[++i]);
                case "--fps"         -> fpsOverride = Double.parseDouble(args[++i]);
                case "--output","-o" -> outputPath = args[++i];
                default -> {
                    if (!args[i].startsWith("-")) inputPath = args[i];
                    else { System.err.println("Unknown option: " + args[i]); printHelp(); return; }
                }
            }
        }

        if (inputPath == null) { printHelp(); return; }

        File input = new File(inputPath);
        if (!input.exists()) { System.err.println("File not found: " + inputPath); System.exit(1); }

        // ── Terminal size ────────────────────────────────────────────────────
        int[] term = detectTerminalSize();
        if (width  < 1) width  = term[0];
        if (height < 1) height = term[1];

        String ext = ext(inputPath);

        // ── Dispatch ─────────────────────────────────────────────────────────
        if (outputPath != null) {
            convertToFile(new File(inputPath), ext, outputPath, mode, width, height, fpsOverride, loop);
        } else {
            previewInTerminal(input, ext, mode, width, height, fpsOverride, loop);
        }
    }

    // =========================================================================
    // PREVIEW IN TERMINAL
    // =========================================================================

    private static void previewInTerminal(File input, String ext,
                                          Mode mode, int cols, int rows,
                                          double fpsOverride, boolean loop) throws Exception {
        System.out.print(FastANSI.ALT_BUFFER_ON + FastANSI.CURSOR_HIDE);
        System.out.flush();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.print(FastANSI.RESET + FastANSI.CURSOR_SHOW + FastANSI.ALT_BUFFER_OFF);
            System.out.flush();
        }));

        if (isVideo(ext)) {
            requireVideoBackend();
            do {
                try (FastAnsiImage.FrameSource src = FastAnsiImage.fromVideoFile(input, cols, rows, mode)) {
                    playToTerminal(src, mode, cols, rows, fpsOverride, "VIDEO");
                }
            } while (loop);

        } else if ("gif".equals(ext)) {
            do {
                try (FastAnsiImage.FrameSource src = FastAnsiImage.fromGif(input)) {
                    playToTerminal(src, mode, cols, rows, fpsOverride, "GIF");
                }
            } while (loop);

        } else {
            // Still image — cycle all modes
            BufferedImage img = ImageIO.read(input);
            if (img == null) { System.err.println("Cannot read image: " + input); return; }
            showAllModes(img, cols, rows, loop);
        }

        Thread.sleep(Long.MAX_VALUE);
    }

    private static void playToTerminal(FastAnsiImage.FrameSource src, Mode mode,
                                       int cols, int rows,
                                       double fpsOverride, String label) throws Exception {
        double fps    = fpsOverride > 0 ? fpsOverride : src.getFps();
        long frameNs  = fps > 0 ? (long)(1_000_000_000.0 / fps) : 0;
        long count    = 0;
        long t0       = System.nanoTime();

        while (src.hasNext()) {
            long ft    = System.nanoTime();
            BufferedImage frame = src.nextFrame();
            if (frame == null) break;

            count++;
            double liveFps = count / ((System.nanoTime() - t0) / 1e9);

            System.out.print(HOME);
            System.out.print(FastAnsiImage.toString(frame, cols, rows - 1, mode));
            System.out.print(FastANSI.cursorTo(rows, 1));
            System.out.print(FastANSI.bg(0,0,0) + FastANSI.fg(70,70,70)
                + String.format(" %-6s  %-10s  %dx%d  %5.1f fps  frame %-7d",
                    label, mode, cols, rows, liveFps, count)
                + FastANSI.RESET);
            System.out.flush();

            if (frameNs > 0) {
                long sleep = (frameNs - (System.nanoTime() - ft)) / 1_000_000;
                if (sleep > 0) Thread.sleep(sleep);
            }
        }
    }

    private static void showAllModes(BufferedImage img, int cols, int rows,
                                     boolean loop) throws Exception {
        Mode[] modes = Mode.values();
        do {
            for (Mode m : modes) {
                System.out.print(HOME);
                System.out.print(FastAnsiImage.toString(img, cols, rows - 1, m));
                System.out.print(FastANSI.cursorTo(rows, 1));
                System.out.print(FastANSI.bg(0,0,0) + FastANSI.fg(150,150,150)
                    + " IMAGE  " + m + "  (cycling modes — press Ctrl+C to exit) "
                    + FastANSI.RESET);
                System.out.flush();
                Thread.sleep(3000);
            }
        } while (loop);
    }

    // =========================================================================
    // CONVERT TO FILE
    // =========================================================================

    private static void convertToFile(File input, String ext, String outputPath,
                                      Mode mode, int cols, int rows,
                                      double fpsOverride, boolean loop) throws Exception {
        String outExt = ext(outputPath);

        System.err.printf("[converter] %s → %s  (%s, %dx%d)%n",
            input.getName(), outputPath, mode, cols, rows);

        if (isVideo(ext)) {
            requireVideoBackend();
            if ("sh".equals(outExt)) {
                saveVideoScript(input, outputPath, mode, cols, rows, fpsOverride, false, loop);
            } else if ("bat".equals(outExt) || "cmd".equals(outExt)) {
                saveVideoScript(input, outputPath, mode, cols, rows, fpsOverride, true, loop);
            } else {
                // .ansi — save all frames concatenated (cursor-home between frames)
                saveVideoAnsiFile(input, outputPath, mode, cols, rows, fpsOverride);
            }

        } else if ("gif".equals(ext)) {
            try (FastAnsiImage.FrameSource src = FastAnsiImage.fromGif(input)) {
                if ("sh".equals(outExt) || "bat".equals(outExt) || "cmd".equals(outExt)) {
                    saveFrameScript(src, outputPath, mode, cols, rows, fpsOverride,
                                    "bat".equals(outExt) || "cmd".equals(outExt), loop);
                } else {
                    saveFramesAnsiFile(src, outputPath, mode, cols, rows);
                }
            }

        } else {
            // Still image
            BufferedImage img = ImageIO.read(input);
            if (img == null) { System.err.println("Cannot read: " + input); return; }
            String ansi = FastAnsiImage.toString(img, cols, rows, mode);
            Files.writeString(Path.of(outputPath), ansi, StandardCharsets.UTF_8);
            System.err.printf("[converter] Saved %d chars → %s%n", ansi.length(), outputPath);
        }
    }

    // ── Save video as .ansi file (all frames, cursor-home between) ───────────
    private static void saveVideoAnsiFile(File input, String outputPath, Mode mode,
                                          int cols, int rows, double fpsOverride) throws Exception {
        try (FastAnsiImage.FrameSource src = FastAnsiImage.fromVideoFile(input, cols, rows, mode);
             BufferedWriter out = new BufferedWriter(
                 new OutputStreamWriter(new FileOutputStream(outputPath), StandardCharsets.UTF_8))) {
            saveFramesAnsiFile(src, out, mode, cols, rows);
        }
    }

    private static void saveFramesAnsiFile(FastAnsiImage.FrameSource src, String outputPath,
                                            Mode mode, int cols, int rows) throws Exception {
        try (BufferedWriter out = new BufferedWriter(
                 new OutputStreamWriter(new FileOutputStream(outputPath), StandardCharsets.UTF_8))) {
            saveFramesAnsiFile(src, out, mode, cols, rows);
        }
    }

    private static void saveFramesAnsiFile(FastAnsiImage.FrameSource src, BufferedWriter out,
                                            Mode mode, int cols, int rows) throws Exception {
        long frame = 0;
        while (src.hasNext()) {
            BufferedImage f = src.nextFrame();
            if (f == null) break;
            out.write(HOME);
            out.write(FastAnsiImage.toString(f, cols, rows, mode));
            frame++;
            if (frame % 10 == 0) System.err.printf("[converter] frame %d…%n", frame);
        }
        System.err.printf("[converter] Done. %d frames written.%n", frame);
    }

    // ── Save as self-playing shell script ─────────────────────────────────────
    private static void saveVideoScript(File input, String outputPath, Mode mode,
                                        int cols, int rows, double fpsOverride,
                                        boolean isWindows, boolean loop) throws Exception {
        try (FastAnsiImage.FrameSource src = FastAnsiImage.fromVideoFile(input, cols, rows, mode)) {
            saveFrameScript(src, outputPath, mode, cols, rows, fpsOverride, isWindows, loop);
        }
    }

    private static void saveFrameScript(FastAnsiImage.FrameSource src, String outputPath,
                                        Mode mode, int cols, int rows, double fpsOverride,
                                        boolean isWindows, boolean loop) throws Exception {
        double fps   = fpsOverride > 0 ? fpsOverride : src.getFps();
        long   delayMs = fps > 0 ? (long)(1000.0 / fps) : 100;

        try (BufferedWriter out = new BufferedWriter(
                 new OutputStreamWriter(new FileOutputStream(outputPath), StandardCharsets.UTF_8))) {

            if (isWindows) {
                out.write("@echo off\r\nchcp 65001 >nul\r\n");
                if (loop) out.write(":loop\r\n");
            } else {
                out.write("#!/bin/bash\n");
                out.write("printf '\\033[?1049h\\033[?25l'\n");   // alt buffer, hide cursor
                out.write("trap 'printf \"\\033[?25h\\033[?1049l\"; exit' INT TERM\n");
                if (loop) out.write("while true; do\n");
            }

            long frame = 0;
            while (src.hasNext()) {
                BufferedImage f = src.nextFrame();
                if (f == null) break;

                String ansi = FastAnsiImage.toString(f, cols, rows, mode)
                                           .replace("\\", "\\\\")
                                           .replace("'", "\\'");

                if (isWindows) {
                    // Windows: use PowerShell for Unicode/ANSI output
                    out.write("powershell -Command \"[Console]::Write([char]27 + '[H')\"");
                    out.write("\r\n");
                    // Frames embedded as raw ANSI are tricky in bat; use a temp file approach
                    // To avoid cluttering the root, write frames to a subfolder
                    String framesDir = outputPath + ".frames";
                    new File(framesDir).mkdirs();
                    String frameFileName = new File(framesDir).getName() + "\\frame" + frame + ".ansi";
                    String frameFilePath = framesDir + "\\frame" + frame + ".ansi";
                    out.write("type \"%~dp0" + frameFileName + "\"\r\n");
                    out.write("timeout /t 0 /nobreak >nul\r\n");
                    Files.writeString(Path.of(frameFilePath),
                        HOME + FastAnsiImage.toString(f, cols, rows, mode),
                        StandardCharsets.UTF_8);
                } else {
                    out.write("printf '\\033[H");
                    // Escape single quotes in the ANSI string for bash printf
                    String frameStr = FastAnsiImage.toString(f, cols, rows, mode)
                        .replace("\\", "\\\\")
                        .replace("'", "'\\''");
                    out.write(frameStr);
                    out.write("'\n");
                    out.write("sleep " + String.format("%.4f", delayMs / 1000.0) + "\n");
                }

                frame++;
                if (frame % 10 == 0) System.err.printf("[converter] frame %d…%n", frame);
            }

            if (isWindows) {
                if (loop) out.write("goto loop\r\n");
            } else {
                if (loop) out.write("done\n");
                out.write("printf '\\033[?25h\\033[?1049l'\n");   // restore
            }

            System.err.printf("[converter] Done. %d frames → %s%n", frame, outputPath);
            if (!isWindows) {
                new File(outputPath).setExecutable(true);
                System.err.println("[converter] chmod +x applied.");
            }
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static String ext(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? "" : path.substring(dot + 1).toLowerCase();
    }

    private static boolean isVideo(String ext) {
        return Set.of("mp4","mkv","avi","webm","mov","flv","wmv","ts","m4v").contains(ext);
    }

    private static void requireVideoBackend() {
        if (!FastAnsiImage.ffmpegAvailable() && !FastAnsiImage.jcodecAvailable()) {
            System.err.println("ERROR: No video backend found.");
            System.err.println("Ensure JCodec is on classpath or ffmpeg is on PATH.");
            System.exit(1);
        }
    }

    private static int[] detectTerminalSize() {
        // Try Windows console API via environment vars (set by ConEmu, Windows Terminal, etc.)
        try {
            String cols = System.getenv("COLUMNS");
            String rows = System.getenv("LINES");
            if (cols != null && rows != null)
                return new int[]{Integer.parseInt(cols), Integer.parseInt(rows)};
        } catch (Exception ignored) {}
        // Try PowerShell (Windows)
        try {
            Process p = new ProcessBuilder("powershell", "-Command",
                "$h=$Host.UI.RawUI; \"$($h.WindowSize.Width) $($h.WindowSize.Height)\"").start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            String[] parts = out.split("\\s+");
            if (parts.length >= 2)
                return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
        } catch (Exception ignored) {}
        // Try stty (Unix/Mac)
        try {
            Process p = new ProcessBuilder("stty", "size")
                .redirectInput(ProcessBuilder.Redirect.from(new File("/dev/tty"))).start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            String[] parts = out.split("\\s+");
            if (parts.length >= 2)
                return new int[]{Integer.parseInt(parts[1]), Integer.parseInt(parts[0])};
        } catch (Exception ignored) {}
        return new int[]{120, 40};
    }

    private static void printHelp() {
        System.out.println("""
            ╔═══════════════════════════════════════════════════════════╗
            ║           FastANSI Converter  —  run-converter            ║
            ╚═══════════════════════════════════════════════════════════╝

            Usage:
              run-converter <input>                  preview in terminal
              run-converter <input> -o out.ansi      save ANSI text file
              run-converter <input> -o play.sh       save self-playing bash script
              run-converter <input> -o play.bat      save self-playing Windows batch

            Input types:
              Images    .png  .jpg  .jpeg  .bmp  .webp
              Video     .mp4  .mkv  .avi  .webm  .mov  (requires ffmpeg on PATH)
              Animation .gif  (no ffmpeg needed)

            Options:
              -m, --mode   <mode>     HALF_BLOCK | FULL_BLOCK | RAMP | HYBRID
                                      default: HALF_BLOCK (best quality, 2× vert. res.)
              -w, --width  <cols>     output width   (default: terminal width)
              -h, --height <rows>     output height  (default: terminal height)
                  --fps    <n>        override playback FPS
                  --loop              loop video/GIF in preview
              -o, --output <file>     write to file instead of terminal
                  --help              show this help

            Examples:
              run-converter photo.jpg
              run-converter clip.mp4 --mode RAMP
              run-converter clip.mp4 -o clip.sh -w 80 -h 24 --fps 12
              run-converter anim.gif --loop
              run-converter anim.gif -o anim.ansi
            """);
    }
}
