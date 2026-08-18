package com.mine.geometry_node.client.model.render.backend.host.light.source;

import com.mine.geometry_node.client.model.runtime.ModelDimensionId;

import java.util.Objects;

/** Stable source identity. Revision deliberately belongs to source/snapshot content, not this key. */
public record HostLightSourceId(ModelDimensionId dimension,
                                String provider,
                                HostLightSourceKind kind,
                                int sectionX,
                                int sectionY,
                                int sectionZ,
                                String localIdentity) implements Comparable<HostLightSourceId> {
    public HostLightSourceId {
        Objects.requireNonNull(dimension, "dimension");
        provider = requireToken(provider, "provider");
        Objects.requireNonNull(kind, "kind");
        localIdentity = requireToken(localIdentity, "localIdentity");
    }

    public HostLightSectionKey sectionKey() {
        return new HostLightSectionKey(dimension, sectionX, sectionY, sectionZ);
    }

    @Override public int compareTo(HostLightSourceId other) {
        int result = dimension.value().compareTo(other.dimension.value());
        if (result != 0) return result;
        result = provider.compareTo(other.provider);
        if (result != 0) return result;
        result = kind.compareTo(other.kind);
        if (result != 0) return result;
        result = Integer.compare(sectionX, other.sectionX);
        if (result != 0) return result;
        result = Integer.compare(sectionY, other.sectionY);
        if (result != 0) return result;
        result = Integer.compare(sectionZ, other.sectionZ);
        return result != 0 ? result : localIdentity.compareTo(other.localIdentity);
    }

    private static String requireToken(String value, String name) {
        value = value == null ? "" : value.trim();
        if (value.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
