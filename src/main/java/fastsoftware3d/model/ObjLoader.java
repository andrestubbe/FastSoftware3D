package fastsoftware3d.model;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class ObjLoader {

    public static class ModelData {
        public List<float[]> vertices = new ArrayList<>();
        public List<float[]> uvs = new ArrayList<>();
        public List<Face> faces = new ArrayList<>();
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
                        model.vertices.add(new float[]{x, y, z});
                    }
                    break;

                case "vt": // Texture coordinate: vt u v
                    if (tokens.length >= 3) {
                        float u = Float.parseFloat(tokens[1]);
                        float v = Float.parseFloat(tokens[2]);
                        model.uvs.add(new float[]{u, v});
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
                            // Resolve 1-based index (and negative index if any)
                            if (vIdx > 0) {
                                vIndices[i] = vIdx - 1;
                            } else {
                                vIndices[i] = model.vertices.size() + vIdx;
                            }

                            // Parse UV index if present
                            if (parts.length > 1 && !parts[1].isEmpty()) {
                                int uvIdx = Integer.parseInt(parts[1]);
                                if (uvIdx > 0) {
                                    uvIndices[i] = uvIdx - 1;
                                } else {
                                    uvIndices[i] = model.uvs.size() + uvIdx;
                                }
                            } else {
                                uvIndices[i] = -1;
                            }
                        }

                        // Support polygon triangulation (fan triangulation) for quads and higher polygons
                        for (int i = 1; i < count - 1; i++) {
                            Face face = new Face();
                            face.vIndices = new int[]{vIndices[0], vIndices[i], vIndices[i + 1]};
                            face.uvIndices = new int[]{uvIndices[0], uvIndices[i], uvIndices[i + 1]};
                            model.faces.add(face);
                        }
                    }
                    break;
            }
        }

        // Calculate bounding radius for model-level frustum culling
        float maxDistSq = 0.0f;
        for (float[] v : model.vertices) {
            float distSq = v[0]*v[0] + v[1]*v[1] + v[2]*v[2];
            if (distSq > maxDistSq) {
                maxDistSq = distSq;
            }
        }
        model.boundingRadius = (float) Math.sqrt(maxDistSq);
    }
}
