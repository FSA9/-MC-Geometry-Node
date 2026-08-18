package com.mine.geometry_node.client.model.render.backend.host.light.update;

import com.mine.geometry_node.client.model.runtime.ModelDimensionId;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Spatial world dirty region. Model/output invalidation intentionally uses other target records. */
public record HostLightDirtyRegion(ModelDimensionId dimension,
                                   int minX, int minY, int minZ,
                                   int maxX, int maxY, int maxZ,
                                   Set<HostLightInvalidationKind> causes,
                                   long revision) implements HostLightInvalidation {
    public HostLightDirtyRegion {
        Objects.requireNonNull(dimension, "dimension");
        if (minX >= maxX || minY >= maxY || minZ >= maxZ) {
            throw new IllegalArgumentException("half-open dirty region must have positive volume");
        }
        if (revision < 0) throw new IllegalArgumentException("revision must not be negative");
        causes = immutableCauses(causes);
        if (!EnumSet.of(HostLightInvalidationKind.SOURCE, HostLightInvalidationKind.WORLD_OCCLUDER)
                .containsAll(causes)) {
            throw new IllegalArgumentException("world dirty region only accepts spatial world causes");
        }
    }

    private static Set<HostLightInvalidationKind> immutableCauses(Set<HostLightInvalidationKind> values) {
        Objects.requireNonNull(values, "causes");
        if (values.isEmpty()) throw new IllegalArgumentException("causes must not be empty");
        return Set.copyOf(values);
    }
}
