package fastsoftware3d.physics;

import fastsoftware3d.model.ObjLoader;

import java.util.ArrayList;
import java.util.List;

public class WallCollider {

    public static class Wall {
        public float x1, z1, x2, z2;
        public float minY, maxY;
        public Wall(float x1, float z1, float x2, float z2, float minY, float maxY) {
            this.x1 = x1;
            this.z1 = z1;
            this.x2 = x2;
            this.z2 = z2;
            this.minY = minY;
            this.maxY = maxY;
        }
    }

    private static final float CELL_SIZE = 200.0f;
    private static final int GRID_SIZE = 4096;
    private static final int GRID_MASK = GRID_SIZE - 1;
    @SuppressWarnings("unchecked")
    private final List<Wall>[] grid = new List[GRID_SIZE];
    private int totalWallCount = 0;
    
    private static final int MAX_NEARBY_WALLS = 1024;
    private final Wall[] nearbyWallsBuffer = new Wall[MAX_NEARBY_WALLS];
    private int nearbyWallsCount = 0;

    private int getCellKey(int cx, int cz) {
        long key = (((long) cx) << 32) | (cz & 0xFFFFFFFFL);
        int hash = (int)(key ^ (key >>> 32));
        hash ^= (hash >>> 20) ^ (hash >>> 12);
        return (hash ^ (hash >>> 7) ^ (hash >>> 4)) & GRID_MASK;
    }

    private void addWall(Wall w) {
        totalWallCount++;
        int minCX = (int) Math.floor(Math.min(w.x1, w.x2) / CELL_SIZE);
        int maxCX = (int) Math.floor(Math.max(w.x1, w.x2) / CELL_SIZE);
        int minCZ = (int) Math.floor(Math.min(w.z1, w.z2) / CELL_SIZE);
        int maxCZ = (int) Math.floor(Math.max(w.z1, w.z2) / CELL_SIZE);

        for (int x = minCX; x <= maxCX; x++) {
            for (int z = minCZ; z <= maxCZ; z++) {
                int key = getCellKey(x, z);
                if (grid[key] == null) {
                    grid[key] = new ArrayList<>();
                }
                grid[key].add(w);
            }
        }
    }

    public WallCollider() {
        System.out.println("WallCollider initialized empty.");
    }

    public WallCollider(ObjLoader.ModelData modelData) {
        addModel(modelData);
    }

    public void addModel(ObjLoader.ModelData modelData) {
        if (modelData == null || modelData.faceCount == 0) return;

        // Extract vertical walls from the model data
        for (int i = 0; i < modelData.faceCount; i++) {
            int idxOff = i * 3;
            int i0 = modelData.vIndices[idxOff];
            int i1 = modelData.vIndices[idxOff + 1];
            int i2 = modelData.vIndices[idxOff + 2];
            
            float x0 = modelData.vertices[i0 * 3];
            float y0 = modelData.vertices[i0 * 3 + 1];
            float z0 = modelData.vertices[i0 * 3 + 2];
            
            float x1 = modelData.vertices[i1 * 3];
            float y1 = modelData.vertices[i1 * 3 + 1];
            float z1 = modelData.vertices[i1 * 3 + 2];
            
            float x2 = modelData.vertices[i2 * 3];
            float y2 = modelData.vertices[i2 * 3 + 1];
            float z2 = modelData.vertices[i2 * 3 + 2];

            // Calculate face normal
            float uX = x1 - x0;
            float uY = y1 - y0;
            float uZ = z1 - z0;
            
            float vX = x2 - x0;
            float vY = y2 - y0;
            float vZ = z2 - z0;

            float nx = uY * vZ - uZ * vY;
            float ny = uZ * vX - uX * vZ;
            float nz = uX * vY - uY * vX;
            
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len > 0) {
                ny /= len;
            }

            // If the normal is perfectly horizontal, it's a vertical wall
            if (Math.abs(ny) < 0.1f) {
                float minY = Math.min(y0, Math.min(y1, y2));
                float maxY = Math.max(y0, Math.max(y1, y2));
                
                // To prevent duplicate overlapping segments, we just take the longest edge
                // or just all three edges and rely on distance checks
                addWall(new Wall(x0, z0, x1, z1, minY, maxY));
                addWall(new Wall(x1, z1, x2, z2, minY, maxY));
                addWall(new Wall(x2, z2, x0, z0, minY, maxY));
            }
        }
        System.out.println("WallCollider spatial grid updated. Total wall segments: " + totalWallCount);
    }

    private float distToSegmentSq(float px, float pz, Wall line) {
        float l2 = (line.x2 - line.x1) * (line.x2 - line.x1) + (line.z2 - line.z1) * (line.z2 - line.z1);
        if (l2 == 0) {
            float dx = px - line.x1;
            float dz = pz - line.z1;
            return dx * dx + dz * dz;
        }
        float t = Math.max(0, Math.min(1, ((px - line.x1) * (line.x2 - line.x1) + (pz - line.z1) * (line.z2 - line.z1)) / l2));
        float projX = line.x1 + t * (line.x2 - line.x1);
        float projZ = line.z1 + t * (line.z2 - line.z1);
        
        float dx = px - projX;
        float dz = pz - projZ;
        return dx * dx + dz * dz;
    }

    private void fillNearbyWalls(float px, float pz, float radius) {
        nearbyWallsCount = 0;
        int minCX = (int) Math.floor((px - radius) / CELL_SIZE);
        int maxCX = (int) Math.floor((px + radius) / CELL_SIZE);
        int minCZ = (int) Math.floor((pz - radius) / CELL_SIZE);
        int maxCZ = (int) Math.floor((pz + radius) / CELL_SIZE);
        
        for (int x = minCX; x <= maxCX; x++) {
            for (int z = minCZ; z <= maxCZ; z++) {
                List<Wall> cell = grid[getCellKey(x, z)];
                if (cell != null) {
                    for (int i = 0; i < cell.size(); i++) {
                        if (nearbyWallsCount < MAX_NEARBY_WALLS) {
                            nearbyWallsBuffer[nearbyWallsCount++] = cell.get(i);
                        }
                    }
                }
            }
        }
    }

    /**
     * Checks if moving to (nextX, startZ) collides. If so, return startX. Otherwise return nextX.
     * Does the same for Z independently.
     * This achieves smooth wall sliding.
     */
    public void calculateSlidingPosition(float startX, float startY, float startZ, float nextX, float nextZ, float radius, float[] out) {
        float finalX = nextX;
        float finalZ = nextZ;
        
        // Define player's Y bounding box
        float playerMinY = startY - 60.0f;
        float playerMaxY = startY + 20.0f;

        fillNearbyWalls(startX, startZ, radius + Math.max(Math.abs(nextX - startX), Math.abs(nextZ - startZ)));
        float radSq = radius * radius;

        // X movement check
        boolean collidesX = false;
        for (int i = 0; i < nearbyWallsCount; i++) {
            Wall wall = nearbyWallsBuffer[i];
            if (playerMaxY >= wall.minY && playerMinY <= wall.maxY) {
                if (distToSegmentSq(nextX, startZ, wall) < radSq) {
                    collidesX = true;
                    break;
                }
            }
        }
        if (collidesX) {
            finalX = startX;
        }

        // Z movement check
        boolean collidesZ = false;
        for (int i = 0; i < nearbyWallsCount; i++) {
            Wall wall = nearbyWallsBuffer[i];
            if (playerMaxY >= wall.minY && playerMinY <= wall.maxY) {
                if (distToSegmentSq(finalX, nextZ, wall) < radSq) {
                    collidesZ = true;
                    break;
                }
            }
        }
        if (collidesZ) {
            finalZ = startZ;
        }

        out[0] = finalX;
        out[1] = finalZ;
    }

    /**
     * Calculates a soft separation vector away from nearby walls.
     * This is used for "Auto-Dodge" to swing around obstacles.
     */
    public void calculateObstacleAvoidanceVector(float px, float py, float pz, float radius, float[] outRepulsion) {
        float repX = 0.0f;
        float repZ = 0.0f;

        float playerMinY = py - 60.0f;
        float playerMaxY = py + 20.0f;

        fillNearbyWalls(px, pz, radius);
        float radSq = radius * radius;

        for (int i = 0; i < nearbyWallsCount; i++) {
            Wall wall = nearbyWallsBuffer[i];
            if (playerMaxY >= wall.minY && playerMinY <= wall.maxY) {
                float l2 = (wall.x2 - wall.x1) * (wall.x2 - wall.x1) + (wall.z2 - wall.z1) * (wall.z2 - wall.z1);
                float projX = px;
                float projZ = pz;
                if (l2 != 0) {
                    float t = Math.max(0, Math.min(1, ((px - wall.x1) * (wall.x2 - wall.x1) + (pz - wall.z1) * (wall.z2 - wall.z1)) / l2));
                    projX = wall.x1 + t * (wall.x2 - wall.x1);
                    projZ = wall.z1 + t * (wall.z2 - wall.z1);
                }

                float dx = px - projX;
                float dz = pz - projZ;
                float distSq = dx * dx + dz * dz;

                if (distSq > 0.0001f && distSq < radSq) {
                    float dist = (float) Math.sqrt(distSq);
                    float pushFactor = (radius - dist) / radius; // 1 at wall, 0 at radius edge
                    
                    // We use linear push factor so the "field" starts pushing gently earlier
                    // and doesn't wait until the player is right next to the wall to spike.
                    
                    repX += (dx / dist) * pushFactor;
                    repZ += (dz / dist) * pushFactor;
                }
            }
        }
        outRepulsion[0] = repX;
        outRepulsion[1] = repZ;
    }
}
