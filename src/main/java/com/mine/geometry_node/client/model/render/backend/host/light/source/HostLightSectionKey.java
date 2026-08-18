package com.mine.geometry_node.client.model.render.backend.host.light.source;

import com.mine.geometry_node.client.model.runtime.ModelDimensionId;

import java.util.Objects;

/** Worker-safe integer section address; it does not retain a Minecraft level or section object. */
public record HostLightSectionKey(ModelDimensionId dimension, int sectionX, int sectionY, int sectionZ) {
    public HostLightSectionKey {
        Objects.requireNonNull(dimension, "dimension");
    }
}
