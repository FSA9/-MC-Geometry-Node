package com.mine.geometry_node.core.engine.system.scene;

import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Shared transform policy for gestures and property adapters; unregistered types retain full transforms. */
public final class SceneObjectCapabilities {
    public enum Operation { MOVE, ROTATE, SCALE }

    @FunctionalInterface
    public interface Transform {
        SceneObject apply(SceneObject original, Vec3 position, Vec3 rotation, Vec3 scale);
    }

    private record Policy(Set<Operation> operations, Transform transform) {}
    private static final Policy DEFAULT = new Policy(Set.copyOf(EnumSet.allOf(Operation.class)), SceneObject::withTransform);
    private static final Map<String, Policy> POLICIES = new HashMap<>();
    static {
        register(SceneNativeInteractions.TYPE_ID, EnumSet.of(Operation.MOVE, Operation.SCALE),
                SceneNativeInteractions::transformed);
    }

    private SceneObjectCapabilities() {}

    public static synchronized void register(String typeId, Set<Operation> operations, Transform transform) {
        Objects.requireNonNull(typeId);
        if (typeId.isBlank()) throw new IllegalArgumentException("Missing scene type");
        if (POLICIES.putIfAbsent(typeId, new Policy(Set.copyOf(operations), Objects.requireNonNull(transform))) != null)
            throw new IllegalArgumentException("Duplicate scene capabilities: " + typeId);
    }

    private static synchronized Policy policy(SceneObject object) {
        return POLICIES.getOrDefault(object.typeId(), DEFAULT);
    }

    public static boolean supports(SceneObject object, Operation operation) {
        return policy(object).operations().contains(Objects.requireNonNull(operation));
    }

    public static SceneObject transformed(SceneObject original, Vec3 position, Vec3 rotation, Vec3 scale) {
        Policy policy = policy(original);
        if ((!position.equals(original.position()) && !policy.operations().contains(Operation.MOVE))
                || (!rotation.equals(original.rotation()) && !policy.operations().contains(Operation.ROTATE))
                || (!scale.equals(original.scale()) && !policy.operations().contains(Operation.SCALE)))
            throw new IllegalArgumentException("Unsupported scene transform");
        return policy.transform().apply(original, position, rotation, scale);
    }
}
