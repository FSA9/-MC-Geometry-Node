package com.mine.geometry_node.core.engine.graph.resource;

import com.mine.geometry_node.core.engine.graph.binding.GraphBindingKey;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.UUID;

/** Structured selector for one graph-resource lifecycle release. */
public sealed interface GraphResourceRelease {
    boolean matches(GraphResourceId resourceId);

    record Binding(GraphResourceScope scope, GraphBindingKey binding) implements GraphResourceRelease {
        public Binding {
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(binding, "binding");
        }

        @Override
        public boolean matches(GraphResourceId resourceId) {
            return resourceId.type().lifetime() == GraphResourceLifetime.BINDING
                    && resourceId.scope().equals(scope)
                    && resourceId.binding().equals(binding);
        }
    }

    record Process(UUID processId) implements GraphResourceRelease {
        public Process {
            Objects.requireNonNull(processId, "processId");
        }

        @Override
        public boolean matches(GraphResourceId resourceId) {
            return processId.equals(resourceId.processInstanceId());
        }
    }

    record Owner(GraphResourceScope scope) implements GraphResourceRelease {
        public Owner {
            Objects.requireNonNull(scope, "scope");
        }

        @Override
        public boolean matches(GraphResourceId resourceId) {
            return resourceId.scope().equals(scope);
        }
    }

    record Entity(ResourceKey<Level> dimension, UUID entityId) implements GraphResourceRelease {
        public Entity {
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(entityId, "entityId");
        }

        @Override
        public boolean matches(GraphResourceId resourceId) {
            return resourceId.scope().dimension().equals(dimension)
                    && (resourceId.scope() instanceof GraphResourceScope.EntityScope entityScope
                    && entityId.equals(entityScope.ownerId())
                    || entityId.equals(resourceId.targetEntityId()));
        }
    }

    record LevelScope(ResourceKey<Level> dimension) implements GraphResourceRelease {
        public LevelScope {
            Objects.requireNonNull(dimension, "dimension");
        }

        @Override
        public boolean matches(GraphResourceId resourceId) {
            return resourceId.scope().dimension().equals(dimension);
        }
    }

    enum Server implements GraphResourceRelease {
        INSTANCE;

        @Override
        public boolean matches(GraphResourceId resourceId) {
            return true;
        }
    }
}
