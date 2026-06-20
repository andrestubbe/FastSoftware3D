package fastsoftware3d.camera;

import java.awt.event.KeyEvent;

public class CameraController {

    public final Camera camera;

    // Movement flags
    public volatile boolean moveFwd;
    public volatile boolean moveBwd;
    public volatile boolean strafeLeft;
    public volatile boolean strafeRight;
    public volatile boolean moveUp;
    public volatile boolean moveDown;
    public volatile boolean rotLeft;
    public volatile boolean rotRight;
    public volatile boolean rotUp;
    public volatile boolean rotDown;
    public volatile boolean fovInc, fovDec;

    // SHIFT acceleration
    public volatile boolean shiftDown;

    // AA and FOV state
    public volatile int ssaaFactor = 1;
    public volatile float baseFov = 150.0f;
    public volatile boolean asciiMode = false;
    public volatile boolean collisionEnabled = false;

    public CameraController(Camera camera) {
        this.camera = camera;
        this.baseFov = camera.fov;
    }

    public float update(float deltaTime) {
        float speed = 200.0f * deltaTime;
        float rotSpeed = 1.5f * deltaTime;

        // SHIFT acceleration
        if (shiftDown) speed *= 3.0f;

        // Forward vector (yaw-only to keep movement on the XZ plane)
        float fwdX = -(float) Math.sin(camera.yaw);
        float fwdZ = (float) Math.cos(camera.yaw);

        float nextX = camera.x;
        float nextZ = camera.z;

        if (moveFwd) {
            nextX += fwdX * speed;
            nextZ += fwdZ * speed;
        }
        if (moveBwd) {
            nextX -= fwdX * speed;
            nextZ -= fwdZ * speed;
        }

        // Right vector (perpendicular to forward)
        float rightX = (float) Math.cos(camera.yaw);
        float rightZ = (float) Math.sin(camera.yaw);

        if (strafeLeft) {
            nextX -= rightX * speed;
            nextZ -= rightZ * speed;
        }
        if (strafeRight) {
            nextX += rightX * speed;
            nextZ += rightZ * speed;
        }

        // Collision bounds (scaled based on wolfenstein.obj coordinates)
        // Outer room boundaries: X in [-2000, 2000], Z in [-900, 500] approx.
        // Columns located at grid points like X = -1800, -1400, -1200, -1000, etc.
        boolean collision = false;
        
        if (collisionEnabled) {
            // Room boundaries mapping:
            // The corridor at the entrance goes from X = -2000 to -1800, and Z = -900 to 500.
            // Let's restrict the player to stay inside the room corridors.
            // Outer wall bounds:
            if (nextX < -2000.0f || nextX > 2000.0f || nextZ < -900.0f || nextZ > 500.0f) {
                collision = true;
            }

            // Pillar collision detection based on ground-level coordinates (approx. Y=0 to Y=2 Blender units, scaled by 100)
            float pillarRadius = 35.0f; 
            if (!collision) {
                // Check pillars grid in the hall: X from -1800 to 1800 (step 200), Z from -700 to 300 (step 200)
                for (float px = -1800.0f; px <= 1800.0f; px += 200.0f) {
                    for (float pz = -700.0f; pz <= 300.0f; pz += 200.0f) {
                        // Skip the entrance area (approx. X: -1900, Z: -226) where the player spawns so they don't get stuck instantly
                        if (Math.abs(px - -1800.0f) < 50.0f && Math.abs(pz - -300.0f) < 150.0f) {
                            continue;
                        }
                        
                        float dx = nextX - px;
                        float dz = nextZ - pz;
                        if (dx * dx + dz * dz < (pillarRadius + 15.0f) * (pillarRadius + 15.0f)) {
                            collision = true;
                            break;
                        }
                    }
                    if (collision) break;
                }
            }
        }

        if (!collision) {
            camera.x = nextX;
            camera.z = nextZ;
        }

        if (moveUp) camera.y += speed;
        if (moveDown) camera.y -= speed;

        if (rotLeft)  camera.yaw -= rotSpeed;
        if (rotRight) camera.yaw += rotSpeed;
        if (rotUp) camera.pitch = Math.min(1.4f, camera.pitch + rotSpeed);
        if (rotDown) camera.pitch = Math.max(-1.4f, camera.pitch - rotSpeed);

        if (fovInc) baseFov = Math.min(170.0f, baseFov + 40.0f * deltaTime);
        if (fovDec) baseFov = Math.max(10.0f, baseFov - 40.0f * deltaTime);

        camera.fov = baseFov;

        return 0.8f * deltaTime;
    }

    public boolean onKey(int vKey, boolean isPressed) {
        switch (vKey) {
            case 0x57:
                moveFwd = isPressed;
                return false; // W
            case 0x53:
                moveBwd = isPressed;
                return false; // S
            case 0x41:
                strafeLeft = isPressed;
                return false; // A
            case 0x44:
                strafeRight = isPressed;
                return false; // D
            case 0x51:
                moveUp = isPressed;
                return false; // Q
            case 0x45:
                moveDown = isPressed;
                return false; // E

            case 0x25:
                rotLeft = isPressed;
                return false; // ←
            case 0x27:
                rotRight = isPressed;
                return false; // →
            case 0x26:
                rotUp = isPressed;
                return false; // ↑
            case 0x28:
                rotDown = isPressed;
                return false; // ↓

            case 0x10:
                shiftDown = isPressed;
                return false; // SHIFT

            case 0xBB:
            case 0x6B:
                fovInc = isPressed;
                return false; // +
            case 0xBD:
            case 0x6D:
                fovDec = isPressed;
                return false; // -

            case 0x4F: // O → SSAA toggle
                if (isPressed) {
                    if (ssaaFactor == 1) ssaaFactor = 2;
                    else if (ssaaFactor == 2) ssaaFactor = 4;
                    else if (ssaaFactor == 4) ssaaFactor = 8;
                    else if (ssaaFactor == 8) ssaaFactor = 16;
                    else ssaaFactor = 1;
                    return true;
                }
                return false;

            case 0x4B: // K → Fisheye toggle
                if (isPressed) {
                    camera.fisheyeEnabled = !camera.fisheyeEnabled;
                    return true; // request buffer/realloc update if needed, or simple redraw
                }
                return false;

            case 0x55: // U → Decrease strength / transition to pincushion distortion (limit -0.4f)
                if (isPressed) {
                    camera.fisheyeStrength = Math.max(-0.4f, camera.fisheyeStrength - 0.05f);
                    return true;
                }
                return false;

            case 0x49: // I → Increase strength / transition to barrel distortion (limit 1.0f)
                if (isPressed) {
                    camera.fisheyeStrength = Math.min(1.0f, camera.fisheyeStrength + 0.05f);
                    return true;
                }
                return false;

            case 0x4D: // M → Toggle ASCII Mode
                if (isPressed) {
                    asciiMode = !asciiMode;
                    return true;
                }
                return false;

            case 0x43: // C → Toggle Collision Detection
                if (isPressed) {
                    collisionEnabled = !collisionEnabled;
                    return true;
                }
                return false;

            case 0x46: // F key (formerly FXAA, now does nothing or can be ignored)
                return false;
        }
        return false;
    }

    public boolean onKeySwing(int keyCode, boolean isPressed) {
        return onKey(keyCode, isPressed);
    }

    public void onMouseMove(int deltaX, int deltaY, boolean isDrag) {
        if (isDrag) {
            camera.yaw   -= deltaX * 0.006f; // 3x faster yaw
            camera.pitch += deltaY * 0.002f; // flipped Y axis
            camera.pitch  = Math.max(-1.4f, Math.min(1.4f, camera.pitch));
        }
    }
}
