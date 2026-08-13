package com.mine.geometry_node.core.engine.system.model.tangent;

/** Minimal mutable vector API required by the vendored algorithm. */
final class Vector3f implements Cloneable {
    float x, y, z;

    Vector3f() {}
    Vector3f(float x, float y, float z) { set(x, y, z); }

    Vector3f set(float x, float y, float z) { this.x = x; this.y = y; this.z = z; return this; }
    Vector3f set(Vector3f value) { return set(value.x, value.y, value.z); }
    Vector3f add(Vector3f value) { return new Vector3f(x + value.x, y + value.y, z + value.z); }
    Vector3f addLocal(Vector3f value) { return set(x + value.x, y + value.y, z + value.z); }
    Vector3f subtract(Vector3f value) { return new Vector3f(x - value.x, y - value.y, z - value.z); }
    Vector3f subtractLocal(Vector3f value) { return set(x - value.x, y - value.y, z - value.z); }
    Vector3f mult(float scalar) { return new Vector3f(x * scalar, y * scalar, z * scalar); }
    Vector3f multLocal(float scalar) { return set(x * scalar, y * scalar, z * scalar); }
    float dot(Vector3f value) { return x * value.x + y * value.y + z * value.z; }
    float lengthSquared() { return dot(this); }
    float length() { return (float) Math.sqrt(dot(this)); }
    Vector3f normalizeLocal() { float length = length(); return length == 0.0F ? this : multLocal(1.0F / length); }

    @Override public Vector3f clone() { return new Vector3f(x, y, z); }
    @Override public boolean equals(Object object) {
        return object instanceof Vector3f value
                && Float.floatToIntBits(x) == Float.floatToIntBits(value.x)
                && Float.floatToIntBits(y) == Float.floatToIntBits(value.y)
                && Float.floatToIntBits(z) == Float.floatToIntBits(value.z);
    }
    @Override public int hashCode() {
        int result = Float.floatToIntBits(x);
        result = 31 * result + Float.floatToIntBits(y);
        return 31 * result + Float.floatToIntBits(z);
    }
}
