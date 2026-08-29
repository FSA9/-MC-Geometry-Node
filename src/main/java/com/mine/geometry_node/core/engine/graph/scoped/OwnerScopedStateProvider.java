package com.mine.geometry_node.core.engine.graph.scoped;

import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Map;
import java.lang.ref.WeakReference;

/** OWNER provider backed by the owner's serialized graph attachment. */
public final class OwnerScopedStateProvider implements ScopedStateProvider {
    private final String ownerId;
    private final WeakReference<Entity> owner;
    private final ScopedStateNamespace namespace;
    private final int maxEntries;

    public OwnerScopedStateProvider(Entity owner, ScopedStateNamespace namespace, int maxEntries) {
        Entity value = Objects.requireNonNull(owner, "owner");
        this.ownerId = value.getUUID().toString();
        this.owner = new WeakReference<>(value);
        this.namespace = Objects.requireNonNull(namespace, "namespace");
        if (maxEntries <= 0) throw new IllegalArgumentException("maxEntries must be positive");
        this.maxEntries = maxEntries;
    }

    @Override public ScopedStateScope scope() { return ScopedStateScope.OWNER; }
    @Override public String identity() { return ownerId; }
    @Override public boolean available() { return owner.get() != null; }

    @Override
    public @Nullable ScopedStateEntry get(String name) {
        return store().get(namespace, name, registries());
    }

    @Override
    public ScopedStateEntry put(String name, Object value) {
        return store().put(namespace, name, value,
                maxEntries, registries(), this::notifyLimit);
    }

    @Override
    public boolean remove(String name) {
        return store().remove(namespace, name);
    }

    @Override public long revision() { return store().revision(); }
    @Override public boolean hasRecord(String name) { return store().hasRecord(namespace, name); }
    @Override public int size() { return store().size(namespace); }
    @Override public Map<String, ScopedStateEntry> entries(int limit) {
        return store().entries(namespace, registries(), limit);
    }

    private OwnerScopedStateStore store() {
        Entity value = owner.get();
        if (value == null) {
            throw new ScopedStateAccessException("Blackboard owner is unavailable: " + ownerId);
        }
        return value.getData(com.mine.geometry_node.GeometryNode.GRAPH_DATA_ATTACHMENT)
                .ownerScopedState();
    }

    private net.minecraft.core.HolderLookup.Provider registries() {
        return requireOwner().registryAccess();
    }

    private Entity requireOwner() {
        Entity value = owner.get();
        if (value == null) {
            throw new ScopedStateAccessException("Blackboard owner is unavailable: " + ownerId);
        }
        return value;
    }

    private void notifyLimit() {
        Entity valueOwner = requireOwner();
        if (!(valueOwner.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }
        ScopedStateLimitNotifier.notifyLimit(
                level, namespace, ScopedStateScope.OWNER, ownerId, maxEntries);
    }
}
