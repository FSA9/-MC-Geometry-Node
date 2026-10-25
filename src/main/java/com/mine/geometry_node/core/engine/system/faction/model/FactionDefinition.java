package com.mine.geometry_node.core.engine.system.faction.model;

import com.mine.geometry_node.core.node.value.RichTextValue;
import java.util.*;

/** World-authored identity, directed relations and independent numeric rank labels. */
public record FactionDefinition(UUID id, RichTextValue name, RichTextValue description,
                                Map<UUID, Integer> relations, Map<Integer, RichTextValue> ranks) {
    public FactionDefinition {
        Objects.requireNonNull(id, "id");
        name = FactionRules.text(name, false);
        description = FactionRules.text(description, true);
        relations = FactionRules.relations(relations, FactionRules.MAX_FACTIONS);
        Objects.requireNonNull(ranks, "ranks");
        if (ranks.size() > FactionRules.MAX_RANKS) throw new IllegalArgumentException("Too many faction ranks");
        Map<Integer, RichTextValue> checked = new TreeMap<>();
        ranks.forEach((rank, label) -> checked.put(Objects.requireNonNull(rank, "rank"), FactionRules.text(label, false)));
        ranks = Collections.unmodifiableMap(checked);
    }
    public int score(UUID target) { return relations.getOrDefault(Objects.requireNonNull(target), 0); }
    public Optional<RichTextValue> rankName(int value) { return Optional.ofNullable(ranks.get(value)); }
}
