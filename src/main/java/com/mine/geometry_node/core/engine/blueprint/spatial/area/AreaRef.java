package com.mine.geometry_node.core.engine.blueprint.spatial.area;

import java.util.Objects;
import java.util.UUID;

/** Opaque reference to one concrete generation of a runtime Area. */
public record AreaRef(AreaAddress address, UUID incarnation) {
    public AreaRef {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(incarnation, "incarnation");
    }
}
