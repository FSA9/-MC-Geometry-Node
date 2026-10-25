package com.mine.geometry_node.core.engine.system.faction.model;

import com.mine.geometry_node.core.node.value.RichTextValue;
import java.util.*;

/** Detached immutable world snapshot. A missing relation is neutral, including self-relations. */
public record FactionCatalog(long revision, List<FactionDefinition> factions) {
    public static final FactionCatalog EMPTY = new FactionCatalog(0, List.of());
    public FactionCatalog {
        if (revision < 0) throw new IllegalArgumentException("Invalid faction revision");
        factions = List.copyOf(factions);
        if (factions.size() > FactionRules.MAX_FACTIONS) throw new IllegalArgumentException("Too many factions");
        Set<UUID> ids = new HashSet<>();
        for (var faction : factions) if (!ids.add(faction.id())) throw new IllegalArgumentException("Duplicate faction id");
        for (var faction : factions) if (!ids.containsAll(faction.relations().keySet()))
            throw new IllegalArgumentException("Faction relation references a missing target");
    }
    public Optional<FactionDefinition> find(UUID id) {
        Objects.requireNonNull(id);
        return factions.stream().filter(faction -> faction.id().equals(id)).findFirst();
    }
    /** Checks a proposed name, excluding the edited identity (null for creation). Legacy duplicates remain readable. */
    public boolean nameAvailable(RichTextValue name, UUID excludedId) {
        String key = FactionRules.nameKey(name);
        return factions.stream().noneMatch(faction -> !faction.id().equals(excludedId)
                && FactionRules.nameKey(faction.name()).equals(key));
    }
    public int score(UUID source, UUID target) {
        Objects.requireNonNull(target);
        return find(source).map(faction -> faction.score(target)).orElse(0);
    }
}
