package com.mine.geometry_node.core.engine.system.model.domain.animation;

public enum ModelAnimationPath {
    TRANSLATION(3),
    ROTATION(4),
    SCALE(3),
    WEIGHTS(-1);

    private final int componentCount;

    ModelAnimationPath(int componentCount) {
        this.componentCount = componentCount;
    }

    public int componentCount() {
        return componentCount;
    }
}
