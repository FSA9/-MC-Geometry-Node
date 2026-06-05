package com.mine.geometry_node.core.engine.service;

import net.minecraft.world.entity.Entity;

/**
 * Explicit target for graph persistent attributes.
 */
public sealed interface PersistentAttributeTarget permits PersistentAttributeTarget.EntityTarget,
        PersistentAttributeTarget.GlobalTarget,
        PersistentAttributeTarget.ScopeTarget {
    static PersistentAttributeTarget entity(Entity entity) {
        return new EntityTarget(entity);
    }

    static PersistentAttributeTarget global() {
        return GlobalTarget.INSTANCE;
    }

    static PersistentAttributeTarget scope(String scopeId) {
        return new ScopeTarget(scopeId);
    }

    record EntityTarget(Entity entity) implements PersistentAttributeTarget {
    }

    enum GlobalTarget implements PersistentAttributeTarget {
        INSTANCE
    }

    record ScopeTarget(String scopeId) implements PersistentAttributeTarget {
    }
}
