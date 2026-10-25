package com.mine.geometry_node.core.engine.system.worldquery.model;

import java.util.Objects;
import java.util.UUID;

/** Entity selectors are independent of spatial range and combine with every other predicate. */
public record WorldObjectEntityFilter(Scope scope, Load load, Registration registration, UUID faction) {
    public enum Scope { ALL, WORLD_OBJECTS, ENTITIES }
    public enum Load { ANY, LOADED, UNLOADED }
    public enum Registration { ANY, REGISTERED, UNREGISTERED }
    public WorldObjectEntityFilter {
        Objects.requireNonNull(scope); Objects.requireNonNull(load); Objects.requireNonNull(registration);
        if (faction != null && (scope != Scope.ENTITIES || registration != Registration.REGISTERED))
            throw new IllegalArgumentException("Faction members require registered entity scope");
    }
    public WorldObjectEntityFilter(Scope scope, Load load, Registration registration) {
        this(scope, load, registration, null);
    }
    public static WorldObjectEntityFilter empty() {
        return new WorldObjectEntityFilter(Scope.ALL, Load.ANY, Registration.ANY);
    }
    public static boolean ordinaryEntity(WorldObjectEntry entry) {
        return entry.provider().equals("native") && !entry.type().equals("minecraft:interaction");
    }
    public boolean matchesScope(WorldObjectEntry entry) {
        return switch (scope) {
            case ALL -> true;
            case WORLD_OBJECTS -> !ordinaryEntity(entry);
            case ENTITIES -> ordinaryEntity(entry);
        };
    }
    public boolean matches(WorldObjectEntry entry) {
        if (!matchesScope(entry)) return false;
        if (load == Load.ANY && registration == Registration.ANY) return true;
        var state = entry.entityState();
        return ordinaryEntity(entry) && state != null
                && (load == Load.ANY || state.loaded() == (load == Load.LOADED))
                && (registration == Registration.ANY || state.registered() == (registration == Registration.REGISTERED))
                && (faction == null || state.factions().contains(faction));
    }
}
