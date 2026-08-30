package com.mine.geometry_node.core.engine.graph.debug;

import com.mine.geometry_node.core.engine.graph.resource.GraphResourceId;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;

/** Typed ownership of one cached debug-render source. */
public record DebugSourceId(DebugRenderChannel channel, Owner owner) {
    public DebugSourceId {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(owner, "owner");
    }

    public static DebugSourceId graph(DebugRenderChannel channel, GraphResourceId resourceId) {
        return new DebugSourceId(channel, new Owner.Graph(resourceId));
    }

    public static DebugSourceId schematicPlacement(ResourceKey<Level> dimension, String key) {
        return new DebugSourceId(DebugRenderChannel.SCHEMATIC, new Owner.SchematicPlacement(dimension, key));
    }

    public sealed interface Owner permits Owner.Graph, Owner.SchematicPlacement {
        record Graph(GraphResourceId resourceId) implements Owner {
            public Graph {
                Objects.requireNonNull(resourceId, "resourceId");
            }
        }

        record SchematicPlacement(ResourceKey<Level> dimension, String key) implements Owner {
            public SchematicPlacement {
                Objects.requireNonNull(dimension, "dimension");
                key = key == null ? "" : key.trim();
                if (key.isEmpty()) throw new IllegalArgumentException("Schematic placement key cannot be empty");
            }
        }
    }
}
