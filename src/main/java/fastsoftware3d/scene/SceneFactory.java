package fastsoftware3d.scene;

import fastsoftware3d.material.Material;
import fastsoftware3d.model.ObjLoader;
import java.io.File;

/**
 * Centrally manages scene data creation so both desktop and terminal demos
 * share the exact same scene graph.
 */
public final class SceneFactory {

    private SceneFactory() {
    }

    /**
     * Creates and builds a default scene containing a floor grid and a wood crate model.
     * Handles obj loading internally, falling back if necessary.
     *
     * @return The populated Scene, and the ModelNode for rotation updates.
     */
    public static SceneCreationResult createDefaultScene() {
        ObjLoader.ModelData cubeModel;
        try {
            File objFile = new File("box.obj");
            if (objFile.exists()) {
                cubeModel = ObjLoader.load("box.obj");
            } else {
                cubeModel = ObjLoader.load("../box.obj");
            }
        } catch (Exception e) {
            System.err.println("Failed to load box.obj: " + e.getMessage());
            cubeModel = new ObjLoader.ModelData();
        }

        Scene scene = new Scene();
        scene.getRoot().addChild(new GridNode());
        ModelNode cubeNode = new ModelNode(cubeModel, Material.woodCrate());
        scene.getRoot().addChild(cubeNode);

        return new SceneCreationResult(scene, cubeNode);
    }

    /**
     * Creates and builds the Wolfenstein scene (room.obj + wall.png).
     */
    public static Scene createWolfScene(float scale) {
        ObjLoader.ModelData roomModel = loadRoom(scale);
        Material wallMat = loadWallMaterial();

        Scene scene = new Scene();
        ModelNode roomNode = new ModelNode(roomModel, wallMat);
        scene.getRoot().addChild(roomNode);
        return scene;
    }

    private static ObjLoader.ModelData loadRoom(float scale) {
        String[] candidates = {"docs/wolfenstein.obj", "wolfenstein.obj", "room.obj", "../room.obj", "docs/room.obj"};
        for (String path : candidates) {
            File f = new File(path);
            if (f.exists()) {
                try {
                    ObjLoader.ModelData m = ObjLoader.load(f.getPath());
                    scaleModel(m, scale);
                    System.out.println("Loaded room model from: " + path);
                    return m;
                } catch (Exception e) {
                    System.err.println("Failed to load " + path + ": " + e.getMessage());
                }
            }
        }
        System.err.println("room model not found — empty scene");
        return new ObjLoader.ModelData();
    }

    private static void scaleModel(ObjLoader.ModelData m, float s) {
        for (float[] v : m.vertices) {
            v[0] *= s;
            v[1] *= s;
            v[2] *= s;
        }
        float maxSq = 0;
        for (float[] v : m.vertices) {
            float sq = v[0]*v[0] + v[1]*v[1] + v[2]*v[2];
            if (sq > maxSq) maxSq = sq;
        }
        m.boundingRadius = (float) Math.sqrt(maxSq);
    }

    private static Material loadWallMaterial() {
        String[] candidates = {"docs/wolfenstein.png", "wolfenstein.png", "docs/wall.png", "wall.png", "../docs/wall.png"};
        for (String path : candidates) {
            File f = new File(path);
            if (f.exists()) {
                try {
                    System.out.println("Loaded wall material from: " + path);
                    return Material.fromPng(f.getPath());
                } catch (Exception e) {
                    System.err.println("Failed to load " + path + ": " + e.getMessage());
                }
            }
        }
        System.err.println("wall material not found — using fallback grey");
        return Material.solidColor(0x6E6E6E);
    }

    public static class SceneCreationResult {
        public final Scene scene;
        public final ModelNode cubeNode;

        public SceneCreationResult(Scene scene, ModelNode cubeNode) {
            this.scene = scene;
            this.cubeNode = cubeNode;
        }
    }
}
