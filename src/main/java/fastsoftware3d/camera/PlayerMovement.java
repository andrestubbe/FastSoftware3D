package fastsoftware3d.camera;

import fastsoftware3d.physics.WallCollider;

public class PlayerMovement {

    // Physics Constants
    private static final float GRAVITY_PULL = 2500.0f;
    private static final float JUMP_FORCE_BASE = 500.0f;
    private static final float JUMP_FORCE_CHARGE_MULTI = 1000.0f;
    private static final float FLOOR_Y = 100.0f;
    
    private static final float MAX_SPEED_CROUCH = 200.0f;
    private static final float MAX_SPEED_WALK = 500.0f;
    private static final float MAX_SPEED_SPRINT = 1000.0f;

    // Movement state
    public float currentVelForward = 0.0f;
    public float currentVelStrafe = 0.0f;
    public float currentMaxSpeed = 0.0f;
    public float lastDirFwd = 0.0f;
    public float lastDirStrafe = 0.0f;

    // Physics state
    public WallCollider collisionSys;
    public float jumpCharge = 0.0f;
    public boolean isGrounded = true;
    public float velocityY = 0.0f;
    public float physicalY = 100.0f;

    private final float[] slideOut = new float[2];
    private final float[] repulsionOut = new float[2];
    private final float[] futureRepulsionOut = new float[2];

    public float nextX, nextZ;

    // Signals for the Camera Rig
    public float dodgeSteer = 0.0f;
    public boolean justHitWall = false;
    public float lastImpactVelocityY = 0.0f;

    public void update(float deltaTime, Camera camera, InputManager input, PlayerSettings settings, float sinYaw, float cosYaw) {
        dodgeSteer = 0.0f;
        justHitWall = false;
        lastImpactVelocityY = 0.0f;

        calculatePlayerIntent(input);
        applyAcceleration(deltaTime, input);
        applyFriction(deltaTime, settings);
        
        applyHorizontalMovement(deltaTime, camera, sinYaw, cosYaw);
        applyHoverboardObstacleAvoidance(deltaTime, camera, settings, sinYaw, cosYaw);
        resolveWallCollisions(camera, settings);
        
        applyGravityAndJumping(deltaTime, camera, input, settings);
    }

    private void calculatePlayerIntent(InputManager input) {
        float inputFwd = 0.0f;
        float inputStrafe = 0.0f;
        if (input.moveFwd) inputFwd += 1.0f;
        if (input.moveBwd) inputFwd -= 1.0f;
        if (input.strafeRight) inputStrafe += 1.0f;
        if (input.strafeLeft) inputStrafe -= 1.0f;

        boolean isMoving = (inputFwd != 0.0f || inputStrafe != 0.0f);
        if (isMoving) {
            float l = (float) Math.sqrt(inputFwd * inputFwd + inputStrafe * inputStrafe);
            lastDirFwd = inputFwd / l;
            lastDirStrafe = inputStrafe / l;
        }
    }

    private void applyAcceleration(float deltaTime, InputManager input) {
        float targetMaxSpeed = 0.0f;
        if (input.moveFwd || input.moveBwd || input.strafeLeft || input.strafeRight) {
            if (input.crouchDown) targetMaxSpeed = MAX_SPEED_CROUCH;
            else if (input.shiftDown) targetMaxSpeed = MAX_SPEED_SPRINT;
            else targetMaxSpeed = MAX_SPEED_WALK;
        }

        if (currentMaxSpeed < targetMaxSpeed) {
            float accelRate = (currentMaxSpeed >= MAX_SPEED_WALK) ? 166.6f : 500.0f;
            currentMaxSpeed += accelRate * deltaTime;
            if (currentMaxSpeed > targetMaxSpeed) currentMaxSpeed = targetMaxSpeed;
        } else if (currentMaxSpeed > targetMaxSpeed) {
            float decelRate = 333.3f;
            currentMaxSpeed -= decelRate * deltaTime;
            if (currentMaxSpeed < targetMaxSpeed) currentMaxSpeed = targetMaxSpeed;
        }
    }

    private void applyFriction(float deltaTime, PlayerSettings settings) {
        float targetVelForward = lastDirFwd * currentMaxSpeed;
        float targetVelStrafe = lastDirStrafe * currentMaxSpeed;

        float interpolationStep;
        if (settings.hoverboardMode) {
            interpolationStep = isGrounded ? Math.min(1.0f, 3.5f * deltaTime) : Math.min(1.0f, 1.0f * deltaTime);
        } else {
            if (isGrounded || !settings.collisionEnabled) {
                interpolationStep = Math.min(1.0f, 15.0f * deltaTime);
            } else {
                interpolationStep = Math.min(1.0f, 1.5f * deltaTime);
            }
        }
        currentVelForward += (targetVelForward - currentVelForward) * interpolationStep;
        currentVelStrafe += (targetVelStrafe - currentVelStrafe) * interpolationStep;
    }

    private void applyHorizontalMovement(float deltaTime, Camera camera, float sinYaw, float cosYaw) {
        float fwdX = -sinYaw;
        float fwdZ = cosYaw;
        float rightX = cosYaw;
        float rightZ = sinYaw;

        nextX = camera.x + (fwdX * currentVelForward + rightX * currentVelStrafe) * deltaTime;
        nextZ = camera.z + (fwdZ * currentVelForward + rightZ * currentVelStrafe) * deltaTime;
    }

    private void applyHoverboardObstacleAvoidance(float deltaTime, Camera camera, PlayerSettings settings, float sinYaw, float cosYaw) {
        if (!settings.hoverboardMode || !settings.collisionEnabled || collisionSys == null) return;
        
        float currentSpeed = getCurrentSpeed();
        float speedFactor = Math.min(1.0f, currentSpeed / 500.0f);
        
        float feelerRadius = 25.0f + 95.0f * speedFactor;
        collisionSys.calculateObstacleAvoidanceVector(camera.x, physicalY, camera.z, feelerRadius, repulsionOut);
        
        float fwdX = -sinYaw;
        float fwdZ = cosYaw;
        float pushDotFwd = repulsionOut[0] * fwdX + repulsionOut[1] * fwdZ;
        if (pushDotFwd < 0) {
            repulsionOut[0] -= fwdX * pushDotFwd * 0.85f;
            repulsionOut[1] -= fwdZ * pushDotFwd * 0.85f;
        }

        float repelForce = 1500.0f * (speedFactor * speedFactor);
        nextX += repulsionOut[0] * repelForce * deltaTime;
        nextZ += repulsionOut[1] * repelForce * deltaTime;

        float lookAheadDist = currentSpeed * 0.4f;
        float futureX = camera.x + fwdX * lookAheadDist;
        float futureZ = camera.z + fwdZ * lookAheadDist;
        collisionSys.calculateObstacleAvoidanceVector(futureX, physicalY, futureZ, feelerRadius, futureRepulsionOut);

        float totalSteerX = repulsionOut[0] + futureRepulsionOut[0] * 1.5f;
        float totalSteerZ = repulsionOut[1] + futureRepulsionOut[1] * 1.5f;

        if (totalSteerX != 0.0f || totalSteerZ != 0.0f) {
            dodgeSteer = fwdZ * totalSteerX - fwdX * totalSteerZ;
        }
    }

    private void resolveWallCollisions(Camera camera, PlayerSettings settings) {
        boolean collision = false;
        if (settings.collisionEnabled && collisionSys != null) {
            collisionSys.calculateSlidingPosition(camera.x, physicalY, camera.z, nextX, nextZ, 15.0f, slideOut);
            float diffX = nextX - slideOut[0];
            float diffZ = nextZ - slideOut[1];

            if (diffX * diffX + diffZ * diffZ > 0.01f) {
                float currentSpeed = getCurrentSpeed();
                if (currentSpeed > 150.0f) {
                    currentVelForward *= -0.3f;
                    currentVelStrafe *= -0.3f;
                    justHitWall = true;
                }
            }
            nextX = slideOut[0];
            nextZ = slideOut[1];
        } else if (settings.collisionEnabled && collisionSys == null) {
            if (nextX < -2000.0f || nextX > 2000.0f || nextZ < -900.0f || nextZ > 500.0f) {
                collision = true;
            }
        }

        if (!collision) {
            camera.x = nextX;
            camera.z = nextZ;
        }
    }

    private void applyGravityAndJumping(float deltaTime, Camera camera, InputManager input, PlayerSettings settings) {
        if (settings.collisionEnabled) {
            if (input.jumpHeld) {
                if (isGrounded) jumpCharge = Math.min(jumpCharge + deltaTime, 1.0f);
            } else {
                if (isGrounded && jumpCharge > 0.0f) {
                    velocityY = JUMP_FORCE_BASE + (jumpCharge / 1.0f) * JUMP_FORCE_CHARGE_MULTI;
                    isGrounded = false;
                    jumpCharge = 0.0f;
                }
            }

            velocityY -= GRAVITY_PULL * deltaTime;
            physicalY += velocityY * deltaTime;

            if (physicalY <= FLOOR_Y) {
                if (!isGrounded && velocityY < -100.0f) {
                    lastImpactVelocityY = velocityY;
                }
                physicalY = FLOOR_Y;
                isGrounded = true;
                velocityY = 0.0f;
            }
        } else {
            if (input.moveUp) camera.y += currentMaxSpeed * deltaTime;
            if (input.moveDown) camera.y -= currentMaxSpeed * deltaTime;
        }
    }

    public float getCurrentSpeed() {
        return (float) Math.sqrt(currentVelForward * currentVelForward + currentVelStrafe * currentVelStrafe);
    }
}
