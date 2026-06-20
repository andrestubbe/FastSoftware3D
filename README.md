# FastSoftware3D [ALPHA-2026-06] — High-Performance Software 3D Renderer & Console Terminal Engine for Java

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-17+-blue.svg)](https://www.java.com)
[![Platform](https://img.shields.io/badge/Platform-Windows%2010+-lightgrey.svg)]()
[![JitPack](https://img.shields.io/badge/JitPack-ready-green.svg)](https://jitpack.io/#andrestubbe/FastSoftware3D)

**⚡ A micro-optimized, zero-dependency software 3D rendering pipeline and console terminal engine for Java. Supports real-time perspective-correct texture mapping, ANSI true color rendering, post-processing effects, animations, and console hooks.**

---

[![FastSoftware3D Showcase](docs/screensho.png)](docs/screensho.png)

---

## Key Features
- **🚀 Hybrid Viewports** — Output to standard desktop windows (Swing) or directly to raw consoles in 24-bit True Color.
- **⚡ Native Rasterization** — Dynamic C++ JNI rasterizer kernel with automatic pure-Java fallback.
- **📦 Consolidated Engine** — Unifies math, culling, timelines, UTF-8/ASCII processing, ANSI parser, and Win32 console hooks.
- **🔮 Post-Effects** — Built-in linear depth-based fog, Anti-Aliasing (FXAA), SSAA downsampling, and Barrel/Fisheye lens distortion.

---

## Quick Start — Desktop Demo

```java
import fastsoftware3d.camera.Camera;
import fastsoftware3d.core.Framebuffer;
import fastsoftware3d.core.RenderPipeline;
import fastsoftware3d.rasterizer.NativeRasterizer;
import fastsoftware3d.scene.ModelNode;
import fastsoftware3d.scene.Scene;
import fastsoftware3d.scene.Renderer3D;
import fastsoftware3d.model.ObjLoader;
import fastsoftware3d.material.Material;

public class Demo {
    public static void main(String[] args) throws Exception {
        // 1. Setup Camera and Framebuffer
        Camera camera = new Camera(0, 0, -10, 0, 0, 60);
        int[] pixels = new int[800 * 600];
        Framebuffer fb = new Framebuffer(800, 600, pixels);
        
        // 2. Instantiate Render Pipeline
        RenderPipeline pipeline = new RenderPipeline(camera, fb, new NativeRasterizer());
        Renderer3D renderer = new Renderer3D(pipeline);
        
        // 3. Create Scene and load models
        Scene scene = new Scene();
        ObjLoader.ModelData model = ObjLoader.load("docs/room.obj");
        Material wallMat = Material.fromPng("docs/wall.png");
        
        scene.getRoot().addChild(new ModelNode(model, wallMat));
        
        // 4. Render Frame
        renderer.clear();
        scene.render(renderer, null);
        pipeline.postProcess();
    }
}
```

---

## 📊 Performance
FastSoftware3D uses JNI-accelerated scanline rasterization for maximum console throughput:

| Rasterization Type | Frame Time (640x480) | Speedup |
| :--- | :--- | :--- |
| Pure-Java Fallback | 12.4 ms | **1.0x** |
| JNI C++ Rasterizer | 2.1 ms | **5.9x** |

---

## Technical Examples & Hero Demos
Run the automated batch scripts to preview the engine in action:
*   run-demo.bat — Launches the interactive desktop 3D window.
*   run-terminal-demo.bat — Renders a rotating 3D scene inside the cmd/Windows Terminal.
*   run-wolf-terminal-demo.bat — Renders a textured first-person Wolfenstein-like level in the terminal.

---

## Installation
Add the JitPack repository and the dependencies to your pom.xml:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.andrestubbe</groupId>
        <artifactId>FastSoftware3D</artifactId>
        <version>main-SNAPSHOT</version>
    </dependency>
</dependencies>
```

---

## License
FastSoftware3D is released under the **MIT License**.
