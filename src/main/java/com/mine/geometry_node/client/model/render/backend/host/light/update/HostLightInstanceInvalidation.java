package com.mine.geometry_node.client.model.render.backend.host.light.update;

import com.mine.geometry_node.client.model.runtime.ModelInstanceId;

import java.util.Objects;
import java.util.Set;

/** Instance target for placement/occurrence changes, never for shared canonical asset content. */
public record HostLightInstanceInvalidation(ModelInstanceId instanceId,
                                            Set<HostLightInvalidationKind> causes,
                                            long revision) implements HostLightInvalidation {
    public HostLightInstanceInvalidation {
        Objects.requireNonNull(instanceId, "instanceId");
        if (revision < 0) throw new IllegalArgumentException("revision must not be negative");
        causes = HostLightAssetInvalidation.modelCauses(causes);
    }
}
