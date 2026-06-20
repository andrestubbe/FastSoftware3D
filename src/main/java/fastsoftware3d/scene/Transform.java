package fastsoftware3d.scene;

public class Transform {
    public float x = 0.0f;
    public float y = 0.0f;
    public float z = 0.0f;
    public float yaw = 0.0f;
    public float pitch = 0.0f;
    public float roll = 0.0f;
    public float scaleX = 1.0f;
    public float scaleY = 1.0f;
    public float scaleZ = 1.0f;

    public Transform() {
    }

    public Transform(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Transform copy() {
        Transform copy = new Transform();
        copy.x = x;
        copy.y = y;
        copy.z = z;
        copy.yaw = yaw;
        copy.pitch = pitch;
        copy.roll = roll;
        copy.scaleX = scaleX;
        copy.scaleY = scaleY;
        copy.scaleZ = scaleZ;
        return copy;
    }

    public static Transform combine(Transform parent, Transform local) {
        Transform result = parent.copy();

        float lx = local.x * parent.scaleX;
        float ly = local.y * parent.scaleY;
        float lz = local.z * parent.scaleZ;

        float cosY = (float) Math.cos(parent.yaw);
        float sinY = (float) Math.sin(parent.yaw);

        float rx = lx * cosY - lz * sinY;
        float rz = lx * sinY + lz * cosY;

        result.x += rx;
        result.y += ly;
        result.z += rz;

        result.yaw += local.yaw;
        result.pitch += local.pitch;
        result.roll += local.roll;

        result.scaleX *= local.scaleX;
        result.scaleY *= local.scaleY;
        result.scaleZ *= local.scaleZ;

        return result;
    }
}
