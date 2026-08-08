package com.mine.geometry_node.core.node.value.entity;

import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.entity.PartEntity;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Shared extension point for converting a selected entity part into its template owner.
 */
public final class EntityTemplateTargetResolvers {
    private static final int MAX_PARENT_DEPTH = 32;
    private static final List<EntityTemplateTargetResolver> RESOLVERS = new CopyOnWriteArrayList<>();

    static {
        register(selected -> selected instanceof PartEntity<?> part ? part.getParent() : null);
    }

    private EntityTemplateTargetResolvers() {
    }

    public static void register(EntityTemplateTargetResolver resolver) {
        RESOLVERS.add(Objects.requireNonNull(resolver, "resolver"));
    }

    public static Entity resolve(Entity selected) {
        Objects.requireNonNull(selected, "selected");

        Entity current = selected;
        Set<Entity> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        visited.add(current);

        for (int depth = 0; depth < MAX_PARENT_DEPTH; depth++) {
            Entity parent = resolveParent(current);
            if (parent == null || parent == current || parent.level() != current.level() || !visited.add(parent)) {
                return current;
            }
            current = parent;
        }
        return current;
    }

    private static Entity resolveParent(Entity selected) {
        for (EntityTemplateTargetResolver resolver : RESOLVERS) {
            Entity parent = resolver.resolveParent(selected);
            if (parent != null) {
                return parent;
            }
        }
        return null;
    }
}
