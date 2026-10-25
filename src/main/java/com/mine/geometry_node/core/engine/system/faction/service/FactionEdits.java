package com.mine.geometry_node.core.engine.system.faction.service;

import com.mine.geometry_node.core.engine.system.faction.model.*;
import com.mine.geometry_node.core.engine.system.faction.storage.FactionJsonCodec;
import com.mine.geometry_node.core.node.value.RichTextValue;
import java.util.*;

/** Pure, all-or-nothing catalogue commands shared by trusted integrations and the server endpoint. */
public final class FactionEdits {
    private FactionEdits() {}

    public static FactionCatalog apply(FactionCatalog before, String operation, Map<String, Object> payload) {
        Objects.requireNonNull(before); Objects.requireNonNull(payload);
        Map<UUID, FactionDefinition> definitions = new LinkedHashMap<>();
        before.factions().forEach(faction -> definitions.put(faction.id(), faction));
        switch (operation) {
            case "READ" -> { fields(payload, Set.of(), Set.of()); return before; }
            case "CREATE" -> {
                fields(payload, Set.of("name"), Set.of("description"));
                RichTextValue name = FactionJsonCodec.text(payload.get("name"), false);
                if (!before.nameAvailable(name, null)) throw new IllegalArgumentException("duplicate_name");
                UUID id;
                do { id = UUID.randomUUID(); } while (definitions.containsKey(id));
                definitions.put(id, new FactionDefinition(id, name,
                        payload.containsKey("description") ? FactionJsonCodec.text(payload.get("description"), true) : RichTextValue.EMPTY,
                        Map.of(), Map.of()));
            }
            case "DELETE" -> {
                fields(payload, Set.of("ids"), Set.of());
                Set<UUID> deleted = new HashSet<>();
                for (Object raw : FactionJsonCodec.list(payload.get("ids"), FactionRules.MAX_FACTIONS)) {
                    UUID id = FactionJsonCodec.uuid(raw);
                    if (!deleted.add(id)) throw new IllegalArgumentException("Duplicate deleted faction");
                    require(definitions, id);
                }
                deleted.forEach(definitions::remove);
                definitions.replaceAll((id, faction) -> {
                    Map<UUID, Integer> relations = new LinkedHashMap<>(faction.relations());
                    relations.keySet().removeAll(deleted);
                    return new FactionDefinition(id, faction.name(), faction.description(), relations, faction.ranks());
                });
            }
            case "UPDATE", "RELATION", "RANK", "REMOVE_RANK" -> {
                String idField = operation.equals("RELATION") ? "source" : "id";
                UUID id = FactionJsonCodec.uuid(payload.get(idField));
                FactionDefinition current = require(definitions, id);
                RichTextValue name = current.name(), description = current.description();
                Map<UUID, Integer> relations = new LinkedHashMap<>(current.relations());
                Map<Integer, RichTextValue> ranks = new TreeMap<>(current.ranks());
                switch (operation) {
                    case "UPDATE" -> {
                        fields(payload, Set.of("id"), Set.of("name", "description"));
                        if (payload.containsKey("name")) {
                            name = FactionJsonCodec.text(payload.get("name"), false);
                            // Keeping the same visible key may restyle even a legacy duplicate; only renames introduce a new key.
                            if (!FactionRules.nameKey(name).equals(FactionRules.nameKey(current.name()))
                                    && !before.nameAvailable(name, id))
                                throw new IllegalArgumentException("duplicate_name");
                        }
                        if (payload.containsKey("description")) description = FactionJsonCodec.text(payload.get("description"), true);
                    }
                    case "RELATION" -> {
                        fields(payload, Set.of("source", "target", "score"), Set.of());
                        UUID target = FactionJsonCodec.uuid(payload.get("target"));
                        require(definitions, target);
                        int score = FactionRules.score(FactionJsonCodec.integer(payload.get("score")));
                        if (score == 0) relations.remove(target); else relations.put(target, score);
                    }
                    case "RANK" -> {
                        fields(payload, Set.of("id", "level", "name"), Set.of("previous"));
                        int level = FactionJsonCodec.integer(payload.get("level"));
                        RichTextValue label = FactionJsonCodec.text(payload.get("name"), false);
                        if (payload.containsKey("previous")) {
                            int previous = FactionJsonCodec.integer(payload.get("previous"));
                            if (!ranks.containsKey(previous)) throw new IllegalStateException("rank_unavailable");
                            if (previous != level && ranks.containsKey(level)) throw new IllegalArgumentException("Duplicate rank level");
                            ranks.remove(previous);
                        } else if (ranks.containsKey(level)) throw new IllegalArgumentException("Duplicate rank level");
                        ranks.put(level, label);
                    }
                    case "REMOVE_RANK" -> {
                        fields(payload, Set.of("id", "level"), Set.of());
                        int level = FactionJsonCodec.integer(payload.get("level"));
                        if (ranks.remove(level) == null) throw new IllegalStateException("rank_unavailable");
                    }
                }
                definitions.put(id, new FactionDefinition(id, name, description, relations, ranks));
            }
            default -> throw new IllegalArgumentException("Unknown faction operation");
        }
        List<FactionDefinition> factions = List.copyOf(definitions.values());
        if (factions.equals(before.factions())) return before;
        return new FactionCatalog(Math.incrementExact(before.revision()), factions);
    }

    private static FactionDefinition require(Map<UUID, FactionDefinition> definitions, UUID id) {
        FactionDefinition faction = definitions.get(id);
        if (faction == null) throw new IllegalStateException("faction_unavailable");
        return faction;
    }
    private static void fields(Map<String, Object> payload, Set<String> required, Set<String> optional) {
        if (!payload.keySet().containsAll(required) || payload.keySet().stream().anyMatch(key -> !required.contains(key) && !optional.contains(key)))
            throw new IllegalArgumentException("Unexpected faction command fields");
    }
}
