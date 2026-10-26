package com.mine.geometry_node.core.engine.metadata.target;

import com.mine.geometry_node.core.engine.graph.resource.GraphResourceReferences;
import net.minecraft.world.entity.Entity;
import java.util.Objects;
import java.util.UUID;

/** Typed public metadata owners used by graph nodes; storage remains with each domain. */
public sealed interface GraphMetadataTarget {
    record EntityTarget(Entity entity) implements GraphMetadataTarget {
        public EntityTarget { Objects.requireNonNull(entity); }
    }
    record DimensionTarget(String dimension) implements GraphMetadataTarget {
        public DimensionTarget { Objects.requireNonNull(dimension); }
    }
    enum GlobalTarget implements GraphMetadataTarget { INSTANCE }
    record ResourceTarget(GraphResourceReferences.Reference reference) implements GraphMetadataTarget {
        public ResourceTarget { Objects.requireNonNull(reference); }
    }
    record FactionTarget(UUID id) implements GraphMetadataTarget {
        public FactionTarget { Objects.requireNonNull(id); }
    }
}
