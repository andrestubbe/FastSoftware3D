package fastsoftware3d.camera;

import fastsoftware3d.rasterizer.NativeRasterizer;

public class InputManager {

    private final Camera camera;
    private final PlayerSettings settings;

    // Movement intent flags
    public volatile boolean moveFwd, moveBwd, strafeLeft, strafeRight;
    public volatile boolean moveUp, moveDown;
    public volatile boolean rotLeft, rotRight, rotUp, rotDown;
    public volatile boolean shiftDown, crouchDown;
    public volatile boolean jumpHeld;

    public InputManager(Camera camera, PlayerSettings settings) {
        this.camera = camera;
        this.settings = settings;
    }

    public boolean onKey(int vKey, boolean isPressed) {
        switch (vKey) {
            case 0x57: // W
                moveFwd = isPressed;
                return false; 
            case 0x53: // S
                moveBwd = isPressed;
                return false; 
            case 0x41: // A
                strafeLeft = isPressed;
                return false; 
            case 0x44: // D
                strafeRight = isPressed;
                return false; 
            case 0x51: // Q
                moveUp = isPressed;
                return false; 
            case 0x45: // E
                moveDown = isPressed;
                return false; 
            case 0x20: // Space
                jumpHeld = isPressed;
                return false; 

            case 0x25: // ←
                rotLeft = isPressed;
                return false; 
            case 0x27: // →
                rotRight = isPressed;
                return false; 
            case 0x26: // ↑
                rotUp = isPressed;
                return false; 
            case 0x28: // ↓
                rotDown = isPressed;
                return false; 

            case 0x10: // SHIFT
                shiftDown = isPressed;
                return false; 

            case 0x11: // CTRL
                crouchDown = isPressed;
                return false; 

            case 0xBB: // +
            case 0x6B:
                if (isPressed) settings.baseFov = Math.min(170.0f, settings.baseFov + 40.0f * 0.1f);
                return false; 
            case 0xBD: // -
            case 0x6D:
                if (isPressed) settings.baseFov = Math.max(10.0f, settings.baseFov - 40.0f * 0.1f);
                return false; 

            case 0x4F: // O
                if (isPressed) {
                    if (settings.ssaaFactor == 1) settings.ssaaFactor = 2;
                    else if (settings.ssaaFactor == 2) settings.ssaaFactor = 4;
                    else if (settings.ssaaFactor == 4) settings.ssaaFactor = 8;
                    else if (settings.ssaaFactor == 8) settings.ssaaFactor = 16;
                    else settings.ssaaFactor = 1;
                    return true;
                }
                return false;

            case 0x31: // 1
                if (isPressed) {
                    settings.ssaaFactor = 1;
                    return true;
                }
                return false;

            case 0x32: // 2
                if (isPressed) {
                    settings.ssaaFactor = 2;
                    return true;
                }
                return false;

            case 0x33: // 3
                if (isPressed) {
                    settings.ssaaFactor = 4;
                    return true;
                }
                return false;

            case 0x34: // 4
                if (isPressed) {
                    settings.ssaaFactor = 8;
                    return true;
                }
                return false;

            case 0x35: // 5
                if (isPressed) {
                    settings.ssaaFactor = 16;
                    return true;
                }
                return false;

            case 0x4B: // K
                if (isPressed) {
                    camera.fisheyeEnabled = !camera.fisheyeEnabled;
                    return true; 
                }
                return false;

            case 0x55: // U
                if (isPressed) {
                    camera.fisheyeStrength = Math.max(-0.4f, camera.fisheyeStrength - 0.05f);
                    return true;
                }
                return false;

            case 0x49: // I
                if (isPressed) {
                    camera.fisheyeStrength = Math.min(1.0f, camera.fisheyeStrength + 0.05f);
                    return true;
                }
                return false;

            case 0x4D: // M
                if (isPressed) {
                    settings.asciiMode = !settings.asciiMode;
                    return true;
                }
                return false;

            case 0x43: // C
                if (isPressed) {
                    settings.collisionEnabled = !settings.collisionEnabled;
                    System.out.println("Physics & Collision: " + (settings.collisionEnabled ? "ON" : "OFF"));
                    return true;
                }
                return false;

            case 0x48: // H
                if (isPressed) {
                    settings.edgeAware = !settings.edgeAware;
                    return true;
                }
                return false;

            case 0x56: // V
                if (isPressed) {
                    settings.hoverboardMode = !settings.hoverboardMode;
                    System.out.println("Hoverboard Mode: " + (settings.hoverboardMode ? "ON" : "OFF"));
                    return true;
                }
                return false;

            case 0x46: // F
                return false;

            case 0x47: // G
                if (isPressed) {
                    NativeRasterizer.mipmapMode = (NativeRasterizer.mipmapMode + 1) % 6;
                    return true;
                }
                return false;

            case 0x72: // F3
                if (isPressed) {
                    camera.depthVisualizer = !camera.depthVisualizer;
                    return true;
                }
                return false;
        }
        return false;
    }

    public boolean onKeySwing(int keyCode, boolean isPressed) {
        return onKey(keyCode, isPressed);
    }

    public void onMouseMove(int deltaX, int deltaY, boolean isDrag) {
        if (isDrag) {
            camera.yaw -= deltaX * 0.006f; 
            camera.pitch += deltaY * 0.002f; 
            camera.pitch = Math.max(-1.4f, Math.min(1.4f, camera.pitch));
        }
    }
}
