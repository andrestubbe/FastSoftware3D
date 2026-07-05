package fastsoftware3d.scene;

import fastsoftware3d.material.Material;
import fastsoftware3d.model.ObjLoader;
import java.io.File;

/**
 * Centrally manages scene data creation so both desktop and terminal demos
 * share the exact same scene graph.
 */
public final class SceneFactory {

    // Store all loaded models for physics initialization
    public static final java.util.List<ObjLoader.ModelData> loadedCollisionModels = new java.util.ArrayList<>();

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
    private static ObjLoader.ModelData cachedRoomModel = null;
    private static Material cachedWallMat = null;

    public static Scene createWolfScene(float scale, boolean useOctree) {
        if (cachedRoomModel == null) {
            cachedRoomModel = loadRoom(scale);
            cachedWallMat = loadWallMaterial();
        }

        Scene scene = new Scene();
        if (cachedRoomModel.faceCount > 0) {
            SceneNode rootNode = useOctree ? OctreeBuilder.build(cachedRoomModel, cachedWallMat) : new ModelNode(cachedRoomModel, cachedWallMat);
            scene.getRoot().addChild(rootNode);
        }
        return scene;
    }

    public static Scene createCustomScene(String objPath, String pngPath, float scale, boolean useOctree) {
        ObjLoader.ModelData m = new ObjLoader.ModelData();
        try {
            m = ObjLoader.load(objPath);
            scaleModel(m, scale, true);
            loadedCollisionModels.add(m);
        } catch (Exception e) {
            System.err.println("Failed to load " + objPath + ": " + e.getMessage());
        }

        Material mat = Material.solidColor(0x6E6E6E);
        try {
            mat = Material.fromPng(pngPath);
        } catch (Exception e) {
            System.err.println("Failed to load " + pngPath + ": " + e.getMessage());
        }

        Scene scene = new Scene();
        if (m.faceCount > 0) {
            SceneNode rootNode = useOctree ? OctreeBuilder.build(m, mat) : new ModelNode(m, mat);
            scene.getRoot().addChild(rootNode);
        }
        return scene;
    }

    public static void appendCustomModel(Scene scene, String objPath, String pngPath, float scale, boolean useOctree) {
        ObjLoader.ModelData m = new ObjLoader.ModelData();
        try {
            m = ObjLoader.load(objPath);
            scaleModel(m, scale, true); // Flip X and Z for Blender imports
            loadedCollisionModels.add(m); // Add all models for collision
        } catch (Exception e) {
            System.err.println("Failed to load " + objPath + ": " + e.getMessage());
        }

        Material mat = Material.solidColor(0x6E6E6E);
        try {
            mat = Material.fromPng(pngPath);
        } catch (Exception e) {
            System.err.println("Failed to load " + pngPath + ": " + e.getMessage());
        }

        if (m.faceCount > 0) {
            SceneNode rootNode = useOctree ? OctreeBuilder.build(m, mat) : new ModelNode(m, mat);
            scene.getRoot().addChild(rootNode);
        }
    }

    private static ObjLoader.ModelData loadRoom(float scale) {
        String[] candidates = {"docs/wolfenstein.obj", "wolfenstein.obj", "room.obj", "../room.obj", "docs/room.obj"};
        for (String path : candidates) {
            File f = new File(path);
            if (f.exists()) {
                try {
                    ObjLoader.ModelData m = ObjLoader.load(f.getPath());
                    scaleModel(m, scale, false); // Do not flip the legacy Wolfenstein model
                    loadedCollisionModels.add(m);
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

    private static void scaleModel(ObjLoader.ModelData m, float s, boolean mirrorSwapXZ) {
        for (int i = 0; i < m.vertexCount; i++) {
            int off = i * 3;
            float oldX = m.vertices[off];
            float oldY = m.vertices[off + 1];
            float oldZ = m.vertices[off + 2];
            
            if (mirrorSwapXZ) {
                m.vertices[off] = oldZ * s;
                m.vertices[off + 1] = oldY * s;
                m.vertices[off + 2] = oldX * s;
            } else {
                m.vertices[off] = oldX * s;
                m.vertices[off + 1] = oldY * s;
                m.vertices[off + 2] = oldZ * s;
            }
        }
        
        if (mirrorSwapXZ) {
            // Mirroring changes handedness, we must reverse triangle winding to not break backface culling
            for (int i = 0; i < m.faceCount; i++) {
                int off = i * 3;
                int vTmp = m.vIndices[off + 1];
                m.vIndices[off + 1] = m.vIndices[off + 2];
                m.vIndices[off + 2] = vTmp;
                
                int uvTmp = m.uvIndices[off + 1];
                m.uvIndices[off + 1] = m.uvIndices[off + 2];
                m.uvIndices[off + 2] = uvTmp;
            }
        }
        float maxSq = 0;
        for (int i = 0; i < m.vertexCount; i++) {
            int off = i * 3;
            float vx = m.vertices[off];
            float vy = m.vertices[off + 1];
            float vz = m.vertices[off + 2];
            float sq = vx*vx + vy*vy + vz*vz;
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
