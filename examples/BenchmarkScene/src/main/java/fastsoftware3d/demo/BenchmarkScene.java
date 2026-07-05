package fastsoftware3d.demo;

import fastsoftware3d.camera.Camera;
import fastsoftware3d.core.RenderPipeline;
import fastsoftware3d.core.Framebuffer;
import fastsoftware3d.rasterizer.NativeRasterizer;
import fastsoftware3d.scene.Scene;
import fastsoftware3d.scene.Renderer3D;
import fastsoftware3d.scene.SceneFactory;

public class BenchmarkScene {
    public static void main(String[] args) {
        System.out.println("Starting Benchmark...");
        
        int width = 1173;
        int height = 610;
        int[] pixels = new int[width * height];
        Framebuffer fb = new Framebuffer(width, height, pixels);
        
        Camera camera = new Camera(32.0f, 100.0f, -226.0f, 0.0f, -0.05f, 75.0f);
        RenderPipeline pipeline = new RenderPipeline(camera, fb, new NativeRasterizer());
        Renderer3D renderer = new Renderer3D(pipeline);
        
        Scene sceneFlat = SceneFactory.createWolfScene(100.0f, false);
        Scene sceneOctree = SceneFactory.createWolfScene(100.0f, true);
        
        // Warmup Flat
        System.out.println("Warming up JVM (JIT compiler)...");
        for (int i = 0; i < 100; i++) {
            java.util.Arrays.fill(pixels, 0);
            renderer.clear();
            sceneFlat.update(0.016f);
            sceneFlat.render(renderer, null);
            renderer.getPipeline().postProcess();
        }
        
        System.out.println("Running Benchmark Flat (1000 frames)...");
        long startFlat = System.nanoTime();
        int frames = 1000;
        for (int i = 0; i < frames; i++) {
            java.util.Arrays.fill(pixels, 0);
            renderer.clear();
            sceneFlat.update(0.016f);
            camera.yaw += 0.01f; // Rotate camera a bit
            sceneFlat.render(renderer, null);
            renderer.getPipeline().postProcess();
        }
        long endFlat = System.nanoTime();

        // Benchmark Octree
        System.out.println("Running Benchmark Octree (1000 frames)...");
        camera.yaw = 0.0f; // Reset camera
        long startOctree = System.nanoTime();
        for (int i = 0; i < frames; i++) {
            java.util.Arrays.fill(pixels, 0);
            renderer.clear();
            sceneOctree.update(0.016f);
            camera.yaw += 0.01f; // Rotate camera a bit
            sceneOctree.render(renderer, null);
            renderer.getPipeline().postProcess();
        }
        long endOctree = System.nanoTime();
        
        double elapsedFlat = (endFlat - startFlat) / 1_000_000_000.0;
        double fpsFlat = frames / elapsedFlat;
        
        double elapsedOctree = (endOctree - startOctree) / 1_000_000_000.0;
        double fpsOctree = frames / elapsedOctree;

        System.out.println(String.format("================================"));
        System.out.println(String.format("BENCHMARK RESULT (Flat): %.2f FPS", fpsFlat));
        System.out.println(String.format("BENCHMARK RESULT (Octree): %.2f FPS", fpsOctree));
        System.out.println(String.format("================================"));
        System.exit(0);
    }
}
