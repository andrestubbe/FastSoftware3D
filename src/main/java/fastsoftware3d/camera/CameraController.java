package fastsoftware3d.camera;

import fastsoftware3d.physics.WallCollider;

public class CameraController {

    public final Camera camera;
    public final PlayerSettings settings;
    public final InputManager input;
    public final PlayerMovement movement;

    // Game Feel: Spring Damper State
    public float currentEyeOffset = 0.0f;
    public float targetEyeOffset = 0.0f;
    public float eyeOffsetVelocity = 0.0f;

    public float currentPitchOffset = 0.0f;
    public float pitchOffsetVelocity = 0.0f;
    public float targetPitchOffset = 0.0f;

    // Turning/Banking state
    public float lastYaw = 0.0f;
    public float currentRotVelocity = 0.0f;
    public float rotationSmoothing = 12.0f;
    public float bankingFactor = 0.08f; 

    // Head bobbing state
    public float bobTime = 0.0f;
    public float currentBobY = 0.0f;
    public float bobAmplitude = 4.0f;  
    public float bobFrequency = 7.0f;  

    private float sinYaw, cosYaw;

    public CameraController(Camera camera) {
        this.camera = camera;
        this.settings = new PlayerSettings();
        
        // Ensure starting FOV matches the camera
        this.settings.baseFov = camera.fov; 

        this.input = new InputManager(camera, settings);
        this.movement = new PlayerMovement();
        
        this.lastYaw = camera.yaw;
    }

    // Proxy for physics initialization
    public void setCollisionSystem(WallCollider collisionSys) {
        this.movement.collisionSys = collisionSys;
    }
    
    // Some public getters mapped for UI / Renderer
    public boolean isAsciiMode() { return settings.asciiMode; }
    public int getSsaaFactor() { return settings.ssaaFactor; }
    public boolean isEdgeAware() { return settings.edgeAware; }
    public float getBaseFov() { return settings.baseFov; }

    public float update(float deltaTime) {
        processKeyboardRotation(deltaTime);
        updateRotationalBanking(deltaTime);
        
        movement.update(deltaTime, camera, input, settings, sinYaw, cosYaw);
        
        applyLeaningAndBanking(deltaTime);
        applySpringDamperAndCameraShake(deltaTime);
        updateHeadBobbingAndFov(deltaTime);

        camera.pitch -= currentPitchOffset;
        return 0.8f * deltaTime;
    }

    private void processKeyboardRotation(float deltaTime) {
        float rotSpeed = 1.5f * deltaTime;
        if (input.rotLeft) camera.yaw -= rotSpeed;
        if (input.rotRight) camera.yaw += rotSpeed;
        if (input.rotUp) camera.pitch = Math.min(1.4f, camera.pitch + rotSpeed);
        if (input.rotDown) camera.pitch = Math.max(-1.4f, camera.pitch - rotSpeed);

        float pitchForce = (targetPitchOffset - currentPitchOffset) * 150.0f - pitchOffsetVelocity * 15.0f;
        pitchOffsetVelocity += pitchForce * deltaTime;
        currentPitchOffset += pitchOffsetVelocity * deltaTime;
        targetPitchOffset = 0.0f; 

        sinYaw = (float) Math.sin(camera.yaw);
        cosYaw = (float) Math.cos(camera.yaw);
    }

    private void updateRotationalBanking(float deltaTime) {
        float deltaYaw = camera.yaw - lastYaw;
        lastYaw = camera.yaw;

        float targetRotVelocity = 0.0f;
        if (deltaTime > 0.0f) {
            targetRotVelocity = deltaYaw / deltaTime;
        }
        float maxRotVel = 5.0f;
        targetRotVelocity = Math.max(-maxRotVel, Math.min(maxRotVel, targetRotVelocity));
        float rotInterpolationStep = Math.min(1.0f, rotationSmoothing * deltaTime);
        currentRotVelocity += (targetRotVelocity - currentRotVelocity) * rotInterpolationStep;
    }

    private void applyLeaningAndBanking(float deltaTime) {
        float targetRoll = 0.0f;
        float baseTargetPitchOffset = 0.0f;

        if (settings.hoverboardMode) {
            if (input.strafeLeft && !input.strafeRight) targetRoll = (float) Math.toRadians(7.5f);
            else if (input.strafeRight && !input.strafeLeft) targetRoll = (float) Math.toRadians(-7.5f);
            
            if (input.moveFwd && !input.moveBwd) baseTargetPitchOffset = (float) Math.toRadians(-6.0f);
            else if (input.moveBwd && !input.moveFwd) baseTargetPitchOffset = (float) Math.toRadians(6.0f);
        } else {
            if (input.strafeLeft && !input.strafeRight) targetRoll = (float) Math.toRadians(2.5f);
            else if (input.strafeRight && !input.strafeLeft) targetRoll = (float) Math.toRadians(-2.5f);
        }

        targetRoll += -currentRotVelocity * bankingFactor;

        float rollSpeed = 2.5f * deltaTime;
        camera.roll += (targetRoll - camera.roll) * Math.min(1.0f, rollSpeed);

        if (settings.hoverboardMode) {
            targetPitchOffset = baseTargetPitchOffset;
            camera.yaw += camera.roll * 1.5f * deltaTime;
            
            if (movement.dodgeSteer != 0.0f) {
                camera.yaw -= movement.dodgeSteer * 2.5f * Math.min(1.0f, movement.getCurrentSpeed() / 500.0f) * deltaTime;
                camera.roll -= movement.dodgeSteer * 3.5f * Math.min(1.0f, movement.getCurrentSpeed() / 500.0f) * deltaTime;
            }
        }
    }

    private void applySpringDamperAndCameraShake(float deltaTime) {
        if (settings.collisionEnabled) {
            targetEyeOffset = input.crouchDown ? -50.0f : 0.0f;

            if (movement.justHitWall) {
                pitchOffsetVelocity += movement.getCurrentSpeed() * 0.015f;
            }

            if (movement.lastImpactVelocityY < -100.0f) {
                currentEyeOffset += movement.lastImpactVelocityY * 0.025f;
                movement.lastImpactVelocityY = 0.0f; 
            }

            float springStiff = 150.0f;
            float springDamp = 15.0f;
            float eyeForce = (targetEyeOffset - currentEyeOffset) * springStiff - eyeOffsetVelocity * springDamp;
            eyeOffsetVelocity += eyeForce * deltaTime;
            currentEyeOffset += eyeOffsetVelocity * deltaTime;

            if (currentEyeOffset < -75.0f) {
                currentEyeOffset = -75.0f;
                eyeOffsetVelocity = 0.0f;
            }
            if (currentEyeOffset > 50.0f) {
                currentEyeOffset = 50.0f;
                eyeOffsetVelocity = 0.0f;
            }

            camera.y = movement.physicalY + currentEyeOffset;
            camera.pitch += currentPitchOffset;
        } else {
            camera.pitch += currentPitchOffset; 
        }
    }

    private void updateHeadBobbingAndFov(float deltaTime) {
        float currentSpeed = movement.getCurrentSpeed();
        float speedSq = movement.currentVelForward * movement.currentVelForward + movement.currentVelStrafe * movement.currentVelStrafe;
        
        float targetBobY = 0.0f;
        if (speedSq > 10.0f) {
            if (settings.hoverboardMode) {
                bobTime += deltaTime * 2.5f * (currentSpeed / 500.0f);
                targetBobY = (float) Math.sin(bobTime) * 2.0f;
            } else {
                bobTime += deltaTime * bobFrequency * (currentSpeed / 200.0f);
                targetBobY = (float) Math.sin(bobTime) * bobAmplitude;
            }
        }
        float bobInterp = settings.hoverboardMode ? 5.0f : 15.0f;
        currentBobY += (targetBobY - currentBobY) * Math.min(1.0f, bobInterp * deltaTime);

        float fovWarpFactor = Math.max(0.0f, Math.min(1.0f, (currentSpeed - 500.0f) / 500.0f));
        camera.fov = settings.baseFov + (fovWarpFactor * (settings.hoverboardMode ? 35.0f : 20.0f));
    }

    public boolean onKey(int vKey, boolean isPressed) {
        return input.onKey(vKey, isPressed);
    }

    public boolean onKeySwing(int keyCode, boolean isPressed) {
        return input.onKeySwing(keyCode, isPressed);
    }

    public void onMouseMove(int deltaX, int deltaY, boolean isDrag) {
        input.onMouseMove(deltaX, deltaY, isDrag);
    }
}
