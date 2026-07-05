package fastsoftware3d.model;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class ObjLoader {

    public static class ModelData {
        public float[] vertices;
        public float[] uvs;
        public int[] vIndices;
        public int[] uvIndices;
        public int vertexCount;
        public int faceCount;
        public float boundingRadius = 0.0f;
    }

    public static class Face {
        // Indices of vertices in the face (0-based)
        public int[] vIndices;
        // Indices of UVs in the face (0-based)
        public int[] uvIndices;
    }

    public static ModelData load(String filePath) throws Exception {
        ModelData model = new ModelData();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            parse(reader, model);
        }
        return model;
    }

    public static ModelData load(InputStream in) throws Exception {
        ModelData model = new ModelData();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            parse(reader, model);
        }
        return model;
    }

    private static void parse(BufferedReader reader, ModelData model) throws Exception {
        List<float[]> tempVertices = new ArrayList<>();
        List<float[]> tempUvs = new ArrayList<>();
        List<Face> tempFaces = new ArrayList<>();

        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            String[] tokens = line.split("\\s+");
            if (tokens.length == 0) continue;

            switch (tokens[0]) {
                case "v": // Vertex coordinate: v x y z
                    if (tokens.length >= 4) {
                        float x = Float.parseFloat(tokens[1]);
                        float y = Float.parseFloat(tokens[2]);
                        float z = Float.parseFloat(tokens[3]);
                        tempVertices.add(new float[]{x, y, z});
                    }
                    break;

                case "vt": // Texture coordinate: vt u v
                    if (tokens.length >= 3) {
                        float u = Float.parseFloat(tokens[1]);
                        float v = Float.parseFloat(tokens[2]);
                        tempUvs.add(new float[]{u, v});
                    }
                    break;

                case "f": // Face: f v1/vt1/vn1 v2/vt2/vn2 ...
                    if (tokens.length >= 4) { // Needs at least a triangle
                        int count = tokens.length - 1;
                        int[] vIndices = new int[count];
                        int[] uvIndices = new int[count];

                        for (int i = 0; i < count; i++) {
                            String[] parts = tokens[i + 1].split("/");
                            
                            // Parse vertex index
                            int vIdx = Integer.parseInt(parts[0]);
                            if (vIdx > 0) {
                                vIndices[i] = vIdx - 1;
                            } else {
                                vIndices[i] = tempVertices.size() + vIdx;
                            }

                            // Parse UV index if present
                            if (parts.length > 1 && !parts[1].isEmpty()) {
                                int uvIdx = Integer.parseInt(parts[1]);
                                if (uvIdx > 0) {
                                    uvIndices[i] = uvIdx - 1;
                                } else {
                                    uvIndices[i] = tempUvs.size() + uvIdx;
                                }
                            } else {
                                uvIndices[i] = -1;
                            }
                        }

                        for (int i = 1; i < count - 1; i++) {
                            Face face = new Face();
                            face.vIndices = new int[]{vIndices[0], vIndices[i], vIndices[i + 1]};
                            face.uvIndices = new int[]{uvIndices[0], uvIndices[i], uvIndices[i + 1]};
                            tempFaces.add(face);
                        }
                    }
                    break;
            }
        }

        // Flatten into primitive arrays
        model.vertexCount = tempVertices.size();
        model.vertices = new float[model.vertexCount * 3];
        for (int i = 0; i < model.vertexCount; i++) {
            float[] v = tempVertices.get(i);
            int off = i * 3;
            model.vertices[off] = v[0];
            model.vertices[off + 1] = v[1];
            model.vertices[off + 2] = v[2];
        }

        int uvCount = tempUvs.size();
        model.uvs = new float[uvCount * 2];
        for (int i = 0; i < uvCount; i++) {
            float[] uv = tempUvs.get(i);
            int off = i * 2;
            model.uvs[off] = uv[0];
            model.uvs[off + 1] = uv[1];
        }

        model.faceCount = tempFaces.size();
        model.vIndices = new int[model.faceCount * 3];
        model.uvIndices = new int[model.faceCount * 3];
        for (int i = 0; i < model.faceCount; i++) {
            Face f = tempFaces.get(i);
            int off = i * 3;
            model.vIndices[off] = f.vIndices[0];
            model.vIndices[off + 1] = f.vIndices[1];
            model.vIndices[off + 2] = f.vIndices[2];
            model.uvIndices[off] = f.uvIndices[0];
            model.uvIndices[off + 1] = f.uvIndices[1];
            model.uvIndices[off + 2] = f.uvIndices[2];
        }

        // Calculate bounding radius
        float maxDistSq = 0.0f;
        for (int i = 0; i < model.vertexCount; i++) {
            int off = i * 3;
            float vx = model.vertices[off];
            float vy = model.vertices[off + 1];
            float vz = model.vertices[off + 2];
            float distSq = vx*vx + vy*vy + vz*vz;
            if (distSq > maxDistSq) {
                maxDistSq = distSq;
            }
        }
        model.boundingRadius = (float) Math.sqrt(maxDistSq);
    }
}
