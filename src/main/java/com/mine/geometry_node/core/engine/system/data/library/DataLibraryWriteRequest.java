package com.mine.geometry_node.core.engine.system.data.library;

import com.mine.geometry_node.core.engine.blueprint.runtime.wait.BlueprintExternalWaitRequest;
import com.mine.geometry_node.core.engine.graph.value.GraphValueSnapshot;
import com.mine.geometry_node.core.node.definition.port.PortType;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Immutable input captured before a Blueprint Data Library write leaves the server thread. */
public record DataLibraryWriteRequest(
        String path,
        PortType type,
        String key,
        @Nullable Object value
) implements BlueprintExternalWaitRequest {
    public DataLibraryWriteRequest {
        path = path == null ? "" : path;
        type = Objects.requireNonNull(type, "type");
        key = key == null ? "" : key;
        value = GraphValueSnapshot.snapshot(value);
    }
}
