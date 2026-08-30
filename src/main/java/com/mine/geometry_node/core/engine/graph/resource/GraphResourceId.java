package com.mine.geometry_node.core.engine.graph.resource;

import com.mine.geometry_node.core.engine.graph.binding.GraphBindingKey;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/** Strong identity of one graph-owned, non-persistent runtime resource. */
public record GraphResourceId(GraphResourceType type,
                              GraphResourceScope scope,
                              GraphBindingKey binding,
                              GraphResourceSelector selector,
                              @Nullable UUID targetEntityId,
                              @Nullable UUID processInstanceId) {
    public GraphResourceId {
        Objects.requireNonNull(type, "type");
        GraphResourceType registeredType = GraphResourceTypeRegistry.INSTANCE.require(type.id());
        if (!registeredType.equals(type)) {
            throw new IllegalArgumentException("Resource type contract differs from registered type: " + type.id());
        }
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(selector, "selector");
        if (!type.allowedSelectors().contains(selector.kind())) {
            throw new IllegalArgumentException(type.id() + " does not allow selector " + selector.kind());
        }
        switch (type.targetEntityPolicy()) {
            case NONE -> {
                if (targetEntityId != null) throw new IllegalArgumentException(type.id() + " does not allow target entity");
            }
            case REQUIRED -> Objects.requireNonNull(targetEntityId, "targetEntityId");
            case OPTIONAL -> { }
        }
        if (type.lifetime() == GraphResourceLifetime.BINDING && processInstanceId != null) {
            throw new IllegalArgumentException("Binding resource cannot carry a process instance id");
        }
        if (type.lifetime() == GraphResourceLifetime.PROCESS && processInstanceId == null) {
            throw new IllegalArgumentException("Process resource requires a process instance id");
        }
    }
}
