package com.mine.geometry_node.core.engine.graph.runtime.display;

import com.mine.geometry_node.core.utils.nbt.EntityNbtCompat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Converts graph-facing Display poses into vanilla's entity-orientation plus model-transform layers.
 */
public final class DisplayTransformController {
    private static final String PERSISTENT_DATA_KEY = "geometry_node_display_transform";
    private static final String PIVOT_KEY = "pivot";
    private static final String WORLD_ROLL_KEY = "world_roll";

    private DisplayTransformController() {
    }

    public static void writeTransform(CompoundTag entityTag, Vec3 worldRotation, Vec3 logicalTranslation,
                                      Vec3 localRotation, Vec3 scale, Vec3 pivot) {
        Quaternionf leftRotation = combinedModelRotation(worldRotation.z, localRotation);
        Quaternionf rightRotation = new Quaternionf();
        Vector3f rawTranslation = rawTranslation(logicalTranslation, pivot, leftRotation, scale, rightRotation);

        CompoundTag transformTag = new CompoundTag();
        transformTag.put("translation", floatList(rawTranslation.x, rawTranslation.y, rawTranslation.z));
        transformTag.put("scale", floatList((float) scale.x, (float) scale.y, (float) scale.z));
        transformTag.put("left_rotation", floatList(
                leftRotation.x(), leftRotation.y(), leftRotation.z(), leftRotation.w()));
        transformTag.put("right_rotation", floatList(0.0F, 0.0F, 0.0F, 1.0F));
        entityTag.put("transformation", transformTag);
    }

    public static void initializePose(Display display, Vec3 worldRotation, Vec3 pivot) {
        applyEntityOrientation(display, worldRotation);
        storePose(display, worldRotation, pivot);
    }

    public static void applyTransform(Display display, Vec3 worldRotation, Vec3 logicalTranslation,
                                      Vec3 localRotation, Vec3 scale,
                                      int interpolationTick, int delayTick) {
        Vec3 pivot = pivot(display);
        CompoundTag entityTag = EntityNbtCompat.saveWithoutId(display);
        writeTransform(entityTag, worldRotation, logicalTranslation, localRotation, scale, pivot);
        entityTag.putInt("interpolation_duration", Math.max(0, interpolationTick));
        entityTag.putInt("start_interpolation", Math.max(0, delayTick));
        EntityNbtCompat.load(display, entityTag);
        initializePose(display, worldRotation, pivot);
    }

    public static void setPivot(Display display, Vec3 newPivot) {
        CompoundTag entityTag = EntityNbtCompat.saveWithoutId(display);
        CompoundTag transformTag = entityTag.getCompound("transformation").orElseGet(CompoundTag::new);

        Vector3f rawTranslation = vector3(transformTag.getList("translation").orElse(null), new Vector3f());
        Vector3f scale = vector3(transformTag.getList("scale").orElse(null), new Vector3f(1.0F));
        Quaternionf leftRotation = quaternion(
                transformTag.getList("left_rotation").orElse(null), new Quaternionf());
        Quaternionf rightRotation = quaternion(
                transformTag.getList("right_rotation").orElse(null), new Quaternionf());

        Vector3f logicalTranslation = new Vector3f(rawTranslation)
                .add(transformPoint(pivot(display), leftRotation, scale, rightRotation));
        Vector3f newRawTranslation = logicalTranslation
                .sub(transformPoint(newPivot, leftRotation, scale, rightRotation));
        transformTag.put("translation", floatList(
                newRawTranslation.x, newRawTranslation.y, newRawTranslation.z));
        entityTag.put("transformation", transformTag);

        EntityNbtCompat.load(display, entityTag);
        storePose(display, worldRotation(display), newPivot);
    }

    public static void setWorldRotation(Display display, Vec3 newWorldRotation) {
        CompoundTag entityTag = EntityNbtCompat.saveWithoutId(display);
        CompoundTag transformTag = entityTag.getCompound("transformation").orElseGet(CompoundTag::new);

        Vector3f rawTranslation = vector3(transformTag.getList("translation").orElse(null), new Vector3f());
        Vector3f scale = vector3(transformTag.getList("scale").orElse(null), new Vector3f(1.0F));
        Quaternionf oldLeft = quaternion(
                transformTag.getList("left_rotation").orElse(null), new Quaternionf());
        Quaternionf rightRotation = quaternion(
                transformTag.getList("right_rotation").orElse(null), new Quaternionf());

        float oldWorldRoll = (float) worldRotation(display).z;
        Quaternionf localRotation = worldRoll(oldWorldRoll).invert().mul(oldLeft);
        Quaternionf newLeft = worldRoll((float) newWorldRotation.z).mul(localRotation);
        Vec3 pivot = pivot(display);

        Vector3f logicalTranslation = new Vector3f(rawTranslation)
                .add(transformPoint(pivot, oldLeft, scale, rightRotation));
        Vector3f newRawTranslation = logicalTranslation
                .sub(transformPoint(pivot, newLeft, scale, rightRotation));
        transformTag.put("translation", floatList(
                newRawTranslation.x, newRawTranslation.y, newRawTranslation.z));
        transformTag.put("left_rotation", floatList(
                newLeft.x(), newLeft.y(), newLeft.z(), newLeft.w()));
        entityTag.put("transformation", transformTag);

        EntityNbtCompat.load(display, entityTag);
        initializePose(display, newWorldRotation, pivot);
    }

    public static Vec3 worldRotation(Entity entity) {
        double roll = entity instanceof Display display
                ? display.getPersistentData().getCompound(PERSISTENT_DATA_KEY)
                    .map(data -> (double) data.getFloatOr(WORLD_ROLL_KEY, 0.0F))
                    .orElse(0.0)
                : 0.0;
        return new Vec3(entity.getXRot(), entity.getYRot(), roll);
    }

    private static void applyEntityOrientation(Display display, Vec3 worldRotation) {
        display.setXRot((float) worldRotation.x);
        display.setYRot((float) worldRotation.y);
        display.xRotO = (float) worldRotation.x;
        display.yRotO = (float) worldRotation.y;
    }

    private static void storePose(Display display, Vec3 worldRotation, Vec3 pivot) {
        CompoundTag data = display.getPersistentData().getCompound(PERSISTENT_DATA_KEY)
                .orElseGet(CompoundTag::new);
        data.put(PIVOT_KEY, floatList((float) pivot.x, (float) pivot.y, (float) pivot.z));
        data.putFloat(WORLD_ROLL_KEY, (float) worldRotation.z);
        display.getPersistentData().put(PERSISTENT_DATA_KEY, data);
    }

    private static Vec3 pivot(Display display) {
        return display.getPersistentData().getCompound(PERSISTENT_DATA_KEY)
                .flatMap(data -> data.getList(PIVOT_KEY))
                .map(list -> vector3(list, new Vector3f()))
                .map(value -> new Vec3(value.x, value.y, value.z))
                .orElse(Vec3.ZERO);
    }

    private static Vector3f rawTranslation(Vec3 logicalTranslation, Vec3 pivot,
                                           Quaternionf leftRotation, Vec3 scale,
                                           Quaternionf rightRotation) {
        return new Vector3f((float) logicalTranslation.x, (float) logicalTranslation.y,
                (float) logicalTranslation.z)
                .sub(transformPoint(pivot, leftRotation,
                        new Vector3f((float) scale.x, (float) scale.y, (float) scale.z),
                        rightRotation));
    }

    private static Vector3f transformPoint(Vec3 point, Quaternionf leftRotation,
                                           Vector3f scale, Quaternionf rightRotation) {
        Vector3f transformed = new Vector3f((float) point.x, (float) point.y, (float) point.z);
        rightRotation.transform(transformed);
        transformed.mul(scale);
        leftRotation.transform(transformed);
        return transformed;
    }

    private static Quaternionf combinedModelRotation(double worldRoll, Vec3 localRotation) {
        return worldRoll((float) worldRoll).mul(new Quaternionf().rotationYXZ(
                (float) Math.toRadians(-localRotation.y),
                (float) Math.toRadians(localRotation.x),
                (float) Math.toRadians(localRotation.z)));
    }

    private static Quaternionf worldRoll(float degrees) {
        return new Quaternionf().rotationZ((float) Math.toRadians(degrees));
    }

    private static Vector3f vector3(ListTag list, Vector3f fallback) {
        if (list == null || list.size() < 3) return fallback;
        return new Vector3f(list.getFloatOr(0, fallback.x), list.getFloatOr(1, fallback.y),
                list.getFloatOr(2, fallback.z));
    }

    private static Quaternionf quaternion(ListTag list, Quaternionf fallback) {
        if (list == null || list.size() < 4) return fallback;
        return new Quaternionf(
                list.getFloatOr(0, fallback.x()),
                list.getFloatOr(1, fallback.y()),
                list.getFloatOr(2, fallback.z()),
                list.getFloatOr(3, fallback.w()));
    }

    private static ListTag floatList(float... values) {
        ListTag list = new ListTag();
        for (float value : values) list.add(FloatTag.valueOf(value));
        return list;
    }
}
