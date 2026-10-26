package com.mine.geometry_node.core.engine.system.entity.index;

import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectEntry;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import com.mine.geometry_node.core.engine.system.faction.model.FactionRules;

/** A merged list row. Appearance and owner data are deliberately obtained separately. */
public record EntityIndexEntry(WorldObjectEntry entry, boolean loaded, boolean registered,
                               boolean alive, long capturedAtMillis, long revision, Set<UUID> factions) {
    public EntityIndexEntry {
        Objects.requireNonNull(entry);
        if (factions.size() > FactionRules.MAX_ENTITY_REFERENCES)
            throw new IllegalArgumentException("Too many entity faction memberships");
        factions = Set.copyOf(factions);
        if (capturedAtMillis < 0 || revision < 0 || registered != (revision > 0))
            throw new IllegalArgumentException("Invalid entity index state");
    }
    public EntityIndexEntry(WorldObjectEntry entry, boolean loaded, boolean registered,
                            boolean alive, long capturedAtMillis, long revision) {
        this(entry, loaded, registered, alive, capturedAtMillis, revision, Set.of());
    }
}
