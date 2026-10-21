package com.mine.geometry_node.core.cache;

import java.util.Objects;

/** Stable business identity; multiple live owners may register the same descriptor. */
public record CacheDescriptor(String id, String translationKey, Domain domain, Storage storage, Lifetime lifetime) {
    public enum Domain { CLIENT, SERVER, COMMON }
    public enum Storage { MEMORY, DISK, GPU, MIXED }
    public enum Lifetime { APPLICATION, CONNECTION, WORLD, OPERATION }

    public CacheDescriptor {
        if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("Cache id must be namespaced: " + id);
        if (translationKey == null || translationKey.isBlank()) throw new IllegalArgumentException("Missing cache label");
        Objects.requireNonNull(domain);
        Objects.requireNonNull(storage);
        Objects.requireNonNull(lifetime);
    }
}
