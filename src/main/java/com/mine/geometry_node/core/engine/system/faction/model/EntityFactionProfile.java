package com.mine.geometry_node.core.engine.system.faction.model;

import java.util.*;

/** Instance-owned state, independent of vanilla tags, teams and faction-to-faction scores. */
public record EntityFactionProfile(Map<UUID, Integer> memberships, Map<UUID, Integer> relations) {
    public static final EntityFactionProfile EMPTY = new EntityFactionProfile(Map.of(), Map.of());
    public EntityFactionProfile {
        Objects.requireNonNull(memberships, "memberships");
        if (memberships.size() > FactionRules.MAX_ENTITY_REFERENCES) throw new IllegalArgumentException("Too many faction memberships");
        Map<UUID, Integer> checked = new LinkedHashMap<>();
        memberships.forEach((id, rank) -> checked.put(Objects.requireNonNull(id), Objects.requireNonNull(rank)));
        memberships = Collections.unmodifiableMap(checked); // Rank zero still represents membership.
        relations = FactionRules.relations(relations, FactionRules.MAX_ENTITY_REFERENCES);
    }
    public int score(UUID target) { return relations.getOrDefault(Objects.requireNonNull(target), 0); }
    public boolean isEmpty() { return memberships.isEmpty() && relations.isEmpty(); }

    /** Existing dangling entries may survive unrelated edits or be removed, never added or changed. */
    public void validateReplacement(EntityFactionProfile previous, FactionCatalog catalog) {
        Set<UUID> live = new HashSet<>();
        catalog.factions().forEach(faction -> live.add(faction.id()));
        validateReferences(memberships, previous.memberships, live);
        validateReferences(relations, previous.relations, live);
    }
    private static void validateReferences(Map<UUID, Integer> next, Map<UUID, Integer> before, Set<UUID> live) {
        next.forEach((id, value) -> {
            if (!live.contains(id) && !Objects.equals(before.get(id), value))
                throw new IllegalArgumentException("Faction no longer exists");
        });
    }
}
