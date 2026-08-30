package com.mine.geometry_node.core.engine.blueprint.spatial.forceField;

import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaAddress;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceId;

import java.util.Objects;

/** A live force field whose center and finite support are supplied by an Area. */
public record ForceFieldResource(ForceFieldAddress address,
                                 GraphResourceId owner,
                                 long generation,
                                 AreaAddress area,
                                 ForceFieldMode mode,
                                 double strength) {
    public ForceFieldResource {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(area, "area");
        Objects.requireNonNull(mode, "mode");
        strength = Double.isFinite(strength) ? Math.abs(strength) : 0.0D;
    }
}
