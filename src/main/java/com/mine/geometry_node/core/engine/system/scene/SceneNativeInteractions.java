package com.mine.geometry_node.core.engine.system.scene;

import com.mine.geometry_node.core.utils.nbt.EntityNbtCompat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;

/** Native Interaction adapter. Position is the entity base; one width controls both horizontal axes. */
public final class SceneNativeInteractions {
    public static final String TYPE_ID = "native/interaction";
    public static final double MAX_DIMENSION = 256;

    private SceneNativeInteractions() {}

    public static SceneObject capture(Interaction entity, long revision) {
        return capture(entity.getUUID(), entity.position(), EntityNbtCompat.saveWithoutId(entity), revision);
    }

    static SceneObject capture(UUID id, Vec3 position, CompoundTag data, long revision) {
        SceneObject object = new SceneObject(id, TYPE_ID, "Interaction", position,
                new Vec3(1, 1, 1), true, revision, Map.of(
                "width", Float.toString(data.getFloatOr("width", 1)),
                "height", Float.toString(data.getFloatOr("height", 1)),
                "responsive", Boolean.toString(data.getBooleanOr("response", false))));
        SceneObjectTypes.validate(object);
        return object;
    }

    public static void apply(Interaction entity, SceneObject object) {
        if (!entity.getUUID().equals(object.id())) throw new IllegalArgumentException("Interaction identity mismatch");
        EntityNbtCompat.load(entity, applyData(EntityNbtCompat.saveWithoutId(entity), object));
        entity.setPos(object.position().x, object.position().y, object.position().z);
    }

    static CompoundTag applyData(CompoundTag original, SceneObject object) {
        SceneObjectTypes.validate(object);
        validate(object);
        // Merge only the three owned properties into a full snapshot, retaining tags, name and other native NBT.
        CompoundTag data = original.copy();
        data.putFloat("width", (float) width(object));
        data.putFloat("height", (float) height(object));
        data.putBoolean("response", Boolean.parseBoolean(object.parameters().get("responsive")));
        return data;
    }

    /** X-only or Z-only scaling changes the shared native width. Conflicting X/Z requests are invalid. */
    static SceneObject transformed(SceneObject original, Vec3 position, Vec3 rotation, Vec3 scale) {
        boolean xChanged = scale.x != original.scale().x;
        boolean zChanged = scale.z != original.scale().z;
        if (xChanged && !zChanged) scale = new Vec3(scale.x, scale.y, scale.x);
        else if (zChanged && !xChanged) scale = new Vec3(scale.z, scale.y, scale.z);
        if (!rotation.equals(Vec3.ZERO) || scale.x != scale.z)
            throw new IllegalArgumentException("Interaction requires zero rotation and equal horizontal scale");
        return original.withTransform(position, rotation, scale);
    }

    public static AABB bounds(SceneObject object) {
        double radius = width(object) / 2;
        Vec3 base = object.position();
        return new AABB(base.x - radius, base.y, base.z - radius,
                base.x + radius, base.y + height(object), base.z + radius);
    }

    public static void validate(SceneObject object) {
        if (!TYPE_ID.equals(object.typeId()) || !object.rotation().equals(Vec3.ZERO)
                || object.scale().x != object.scale().z)
            throw new IllegalArgumentException("Interaction requires zero rotation and equal horizontal scale");
        double width = width(object), height = height(object);
        if (!Double.isFinite(width) || !Double.isFinite(height) || width <= 0 || height <= 0
                || width > MAX_DIMENSION || height > MAX_DIMENSION
                || (float) width <= 0 || (float) height <= 0)
            throw new IllegalArgumentException("Invalid effective interaction dimensions");
    }

    private static double width(SceneObject object) { return object.number("width") * object.scale().x; }
    private static double height(SceneObject object) { return object.number("height") * object.scale().y; }
}
