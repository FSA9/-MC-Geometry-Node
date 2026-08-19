package com.mine.geometry_node.client.model.render.backend.host.light.contract;

import com.mine.geometry_node.client.model.runtime.ModelDimensionId;
import com.mine.geometry_node.client.model.runtime.ModelInstanceId;

import java.util.Objects;

/** Complete identity checked before a worker result may replace an instance's active field. */
public record HostLightFieldIdentity(ModelInstanceId instanceId, String assetKey, long placementRevision,
                                     ModelDimensionId dimension, long worldRevision,
                                     long sourceRevision, long algorithmGeneration) {
    public HostLightFieldIdentity(ModelInstanceId instanceId, String assetKey, long placementRevision,
                                  ModelDimensionId dimension, long worldRevision,
                                  long algorithmGeneration) {
        this(instanceId, assetKey, placementRevision, dimension, worldRevision, 0, algorithmGeneration);
    }

    public HostLightFieldIdentity {
        Objects.requireNonNull(instanceId, "instanceId");
        assetKey = Objects.requireNonNull(assetKey, "assetKey").trim();
        if (assetKey.isEmpty()) throw new IllegalArgumentException("assetKey must not be blank");
        Objects.requireNonNull(dimension, "dimension");
        if (placementRevision < 0 || worldRevision < 0 || sourceRevision < 0 || algorithmGeneration < 0) {
            throw new IllegalArgumentException("revisions must not be negative");
        }
    }

    public HostLightFieldId fieldId() {
        String content = instanceId.value() + "/" + assetKey + "/p" + placementRevision
                + "/d" + dimension.value() + "/s" + sourceRevision + "/a" + algorithmGeneration;
        return new HostLightFieldId(content, worldRevision);
    }

    /** True when equal sample bytes produce the same render projection despite capture/source revisions. */
    public boolean sameProjectionDomain(HostLightFieldIdentity other) {
        return other != null
                && instanceId.equals(other.instanceId)
                && assetKey.equals(other.assetKey)
                && placementRevision == other.placementRevision
                && dimension.equals(other.dimension)
                && algorithmGeneration == other.algorithmGeneration;
    }
}
