package com.mine.geometry_node.core.engine.graph.scoped;

import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaRef;

import net.minecraft.world.entity.Entity;

import java.util.Objects;

/**
 * Explicit target for persistent or runtime-owned scoped state.
 */
public sealed interface ScopedStateTarget permits ScopedStateTarget.OwnerTarget,
        ScopedStateTarget.AreaTarget,
        ScopedStateTarget.SharedTarget,
        ScopedStateTarget.GroupTarget,
        ScopedStateTarget.WorldTarget {
    static ScopedStateTarget owner(Entity entity) {
        return new OwnerTarget(entity);
    }

    static ScopedStateTarget area(AreaRef area) {
        return new AreaTarget(area);
    }

    static ScopedStateTarget shared() {
        return SharedTarget.INSTANCE;
    }

    static ScopedStateTarget group(Entity entity) {
        return new GroupTarget(entity);
    }

    static ScopedStateTarget world(String dimensionId) {
        return new WorldTarget(dimensionId);
    }

    record OwnerTarget(Entity entity) implements ScopedStateTarget {
        public OwnerTarget {
            Objects.requireNonNull(entity, "entity");
        }
    }

    record AreaTarget(AreaRef area) implements ScopedStateTarget {
        public AreaTarget {
            Objects.requireNonNull(area, "area");
        }
    }

    enum SharedTarget implements ScopedStateTarget {
        INSTANCE
    }

    record GroupTarget(Entity entity) implements ScopedStateTarget {
        public GroupTarget {
            Objects.requireNonNull(entity, "entity");
        }
    }

    record WorldTarget(String dimensionId) implements ScopedStateTarget {
        public WorldTarget {
            dimensionId = dimensionId != null ? dimensionId.trim() : "";
            if (dimensionId.isEmpty()) {
                throw new IllegalArgumentException("dimensionId must not be blank");
            }
        }
    }
}
