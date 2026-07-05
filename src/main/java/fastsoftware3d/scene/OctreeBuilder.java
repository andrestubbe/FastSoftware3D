package fastsoftware3d.scene;

import fastsoftware3d.material.Material;
import fastsoftware3d.model.ObjLoader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OctreeBuilder {

    private static final int MAX_FACES = 800; // threshold to split
    private static final int MAX_DEPTH = 6;

    public static SceneNode build(ObjLoader.ModelData model, Material material) {
        return buildRecursive(model, material, 0);
    }

    private static SceneNode buildRecursive(ObjLoader.ModelData model, Material material, int depth) {
        if (model.faceCount <= MAX_FACES || depth >= MAX_DEPTH) {
            return new ModelNode(model, material);
        }

        // 1. Calculate Bounding Box of this model
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

        for (int i = 0; i < model.vertexCount; i++) {
            int off = i * 3;
            float x = model.vertices[off];
            float y = model.vertices[off + 1];
            float z = model.vertices[off + 2];
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
            if (z < minZ) minZ = z;
            if (z > maxZ) maxZ = z;
        }

        float cx = (minX + maxX) * 0.5f;
        float cy = (minY + maxY) * 0.5f;
        float cz = (minZ + maxZ) * 0.5f;

        float dx = maxX - cx;
        float dy = maxY - cy;
        float dz = maxZ - cz;
        float radius = (float) Math.sqrt(dx*dx + dy*dy + dz*dz);

        // 2. Prepare 8 buckets for faces
        @SuppressWarnings("unchecked")
        List<Integer>[] buckets = new ArrayList[8];
        for (int i = 0; i < 8; i++) buckets[i] = new ArrayList<>();

        for (int f = 0; f < model.faceCount; f++) {
            int off = f * 3;
            int i0 = model.vIndices[off] * 3;
            int i1 = model.vIndices[off + 1] * 3;
            int i2 = model.vIndices[off + 2] * 3;

            float fx = (model.vertices[i0] + model.vertices[i1] + model.vertices[i2]) / 3.0f;
            float fy = (model.vertices[i0 + 1] + model.vertices[i1 + 1] + model.vertices[i2 + 1]) / 3.0f;
            float fz = (model.vertices[i0 + 2] + model.vertices[i1 + 2] + model.vertices[i2 + 2]) / 3.0f;

            int octant = 0;
            if (fx > cx) octant |= 1;
            if (fy > cy) octant |= 2;
            if (fz > cz) octant |= 4;

            buckets[octant].add(f);
        }

        // If one bucket gets everything, we can't split effectively. Force leaf.
        for (int i = 0; i < 8; i++) {
            if (buckets[i].size() == model.faceCount) {
                return new ModelNode(model, material);
            }
        }

        OctreeNode parent = new OctreeNode(cx, cy, cz, radius);

        for (int i = 0; i < 8; i++) {
            if (buckets[i].isEmpty()) continue;

            ObjLoader.ModelData subModel = extractSubModel(model, buckets[i]);
            SceneNode child = buildRecursive(subModel, material, depth + 1);
            parent.addChild(child);
        }

        return parent;
    }

    private static ObjLoader.ModelData extractSubModel(ObjLoader.ModelData parent, List<Integer> faceIndices) {
        ObjLoader.ModelData sub = new ObjLoader.ModelData();
        sub.faceCount = faceIndices.size();
        sub.vIndices = new int[sub.faceCount * 3];
        sub.uvIndices = new int[sub.faceCount * 3];

        List<Float> newVerts = new ArrayList<>();
        List<Float> newUvs = new ArrayList<>();

        // map from parent vertex index to sub vertex index
        Map<Integer, Integer> vMap = new HashMap<>();
        Map<Integer, Integer> uvMap = new HashMap<>();

        for (int i = 0; i < sub.faceCount; i++) {
            int oldFaceIdx = faceIndices.get(i);
            int off = oldFaceIdx * 3;

            for (int j = 0; j < 3; j++) {
                int oldV = parent.vIndices[off + j];
                int oldUV = parent.uvIndices[off + j];

                int newV = vMap.computeIfAbsent(oldV, k -> {
                    int nv = newVerts.size() / 3;
                    int pOff = k * 3;
                    newVerts.add(parent.vertices[pOff]);
                    newVerts.add(parent.vertices[pOff + 1]);
                    newVerts.add(parent.vertices[pOff + 2]);
                    return nv;
                });

                int newUV = -1;
                if (oldUV >= 0) {
                    newUV = uvMap.computeIfAbsent(oldUV, k -> {
                        int nuv = newUvs.size() / 2;
                        int pOff = k * 2;
                        newUvs.add(parent.uvs[pOff]);
                        newUvs.add(parent.uvs[pOff + 1]);
                        return nuv;
                    });
                }

                sub.vIndices[i * 3 + j] = newV;
                sub.uvIndices[i * 3 + j] = newUV;
            }
        }

        sub.vertexCount = newVerts.size() / 3;
        sub.vertices = new float[newVerts.size()];
        for (int i = 0; i < newVerts.size(); i++) sub.vertices[i] = newVerts.get(i);

        if (parent.uvs != null) {
            sub.uvs = new float[newUvs.size()];
            for (int i = 0; i < newUvs.size(); i++) sub.uvs[i] = newUvs.get(i);
        }

        // compute boundingRadius from origin (because ModelNode uses distance from its origin)
        float maxSq = 0;
        for (int i = 0; i < sub.vertexCount; i++) {
            int off = i * 3;
            float vx = sub.vertices[off];
            float vy = sub.vertices[off + 1];
            float vz = sub.vertices[off + 2];
            float sq = vx*vx + vy*vy + vz*vz;
            if (sq > maxSq) maxSq = sq;
        }
        sub.boundingRadius = (float) Math.sqrt(maxSq);

        return sub;
    }
}
