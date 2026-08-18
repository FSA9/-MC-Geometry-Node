package com.mine.geometry_node.client.model.render.backend.host.light.source;

import com.mine.geometry_node.client.model.runtime.ModelDimensionId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Canonically-ordered immutable source set suitable for worker capture input. */
public record HostLightSourceSnapshot(ModelDimensionId dimension,
                                      long revision,
                                      List<HostLightSource> sources) {
    public HostLightSourceSnapshot {
        Objects.requireNonNull(dimension, "dimension");
        if (revision < 0) throw new IllegalArgumentException("snapshot revision must not be negative");
        Objects.requireNonNull(sources, "sources");
        ArrayList<HostLightSource> ordered = new ArrayList<>(sources);
        ordered.forEach(source -> Objects.requireNonNull(source, "source"));
        ordered.sort((left, right) -> left.id().compareTo(right.id()));
        Set<HostLightSourceId> ids = new HashSet<>();
        for (HostLightSource source : ordered) {
            if (!dimension.equals(source.id().dimension())) {
                throw new IllegalArgumentException("source dimension does not match snapshot");
            }
            if (!ids.add(source.id())) throw new IllegalArgumentException("duplicate source id: " + source.id());
        }
        sources = List.copyOf(ordered);
    }
}
