package com.mine.geometry_node.client.model.render.backend.host.light.update;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Asset-wide target for canonical receiver or occluder content shared by every instance. */
public record HostLightAssetInvalidation(String assetKey,
                                         Set<HostLightInvalidationKind> causes,
                                         long revision) implements HostLightInvalidation {
    public HostLightAssetInvalidation {
        assetKey = assetKey == null ? "" : assetKey.trim();
        if (assetKey.isEmpty()) throw new IllegalArgumentException("assetKey must not be blank");
        if (revision < 0) throw new IllegalArgumentException("revision must not be negative");
        causes = modelCauses(causes);
    }

    static Set<HostLightInvalidationKind> modelCauses(Set<HostLightInvalidationKind> values) {
        Objects.requireNonNull(values, "causes");
        if (values.isEmpty() || !EnumSet.of(HostLightInvalidationKind.MODEL_OCCLUDER,
                HostLightInvalidationKind.RECEIVER).containsAll(values)) {
            throw new IllegalArgumentException("asset/instance invalidation needs model/receiver causes");
        }
        return Set.copyOf(values);
    }
}
