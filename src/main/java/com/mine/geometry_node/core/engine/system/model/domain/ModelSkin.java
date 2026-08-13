package com.mine.geometry_node.core.engine.system.model.domain;

import java.util.List;

/** Immutable glTF skin shared by every instance of a model definition. */
public record ModelSkin(String name, List<Integer> joints, int skeletonNodeIndex,
                        List<ModelMatrix4> inverseBindMatrices) {
    public static final int MAX_JOINTS = 128;

    public ModelSkin {
        name = name == null ? "" : name;
        joints = joints == null ? List.of() : List.copyOf(joints);
        inverseBindMatrices = inverseBindMatrices == null ? List.of() : List.copyOf(inverseBindMatrices);
        if (joints.isEmpty() || joints.size() > MAX_JOINTS) {
            throw new IllegalArgumentException("skin must contain between 1 and " + MAX_JOINTS + " joints");
        }
        if (joints.stream().anyMatch(index -> index == null || index < 0)) {
            throw new IllegalArgumentException("skin joint indices must not be negative");
        }
        if (skeletonNodeIndex < -1) throw new IllegalArgumentException("skeletonNodeIndex must be -1 or greater");
        if (inverseBindMatrices.size() != joints.size()) {
            throw new IllegalArgumentException("inverse-bind matrix count must match joint count");
        }
    }
}
