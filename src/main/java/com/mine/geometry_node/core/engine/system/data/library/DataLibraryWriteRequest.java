package com.mine.geometry_node.core.engine.system.data.library;

import com.mine.geometry_node.core.engine.graph.runtime.ExternalWaitRequest;
import com.mine.geometry_node.core.engine.graph.value.GraphValueSnapshot;
import com.mine.geometry_node.core.node.definition.port.PortType;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Immutable input captured before a graph Data Library write leaves the server thread. */
public record DataLibraryWriteRequest(
        String path,
        PortType type,
        String key,
        @Nullable Object value
) implements ExternalWaitRequest {
    public DataLibraryWriteRequest {
        path = path == null ? "" : path;
        type = Objects.requireNonNull(type, "type");
        key = key == null ? "" : key;
        value = value instanceof Entity entity
                ? DataLibraryEntityReference.capture(entity)
                : GraphValueSnapshot.snapshot(value);
    }
}
